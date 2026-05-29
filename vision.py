# -*- coding: utf-8 -*-
import cv2
import numpy as np
import os
import json
import time

class VisionSystem:
    def __init__(self, scans_dir="scans"):
        self.scans_dir = scans_dir
        if not os.path.exists(scans_dir):
            os.makedirs(scans_dir)
        self.orb = cv2.ORB_create(nfeatures=1000)
        self.bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
        self.templates = {}
        self.prev_frame_gray = None
        self.load_templates()

    def load_templates(self):
        """Loads NPZ scan templates containing keypoints, descriptors, and metadata."""
        self.templates = {}
        for file in os.listdir(self.scans_dir):
            if file.endswith(".npz"):
                path = os.path.join(self.scans_dir, file)
                try:
                    data = np.load(path, allow_pickle=True)
                    name = file[:-4]
                    self.templates[name] = {
                        "descriptors": data["descriptors"],
                        "keypoints_pts": data["keypoints_pts"],
                        "width": int(data.get("width", 480)),
                        "height": int(data.get("height", 360))
                    }
                except Exception as e:
                    print(f"Error loading template {file}: {e}")

    def add_template(self, name, image):
        """Extracts features and saves them as an NPZ file."""
        if len(image.shape) == 3:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        else:
            gray = image
        
        height, width = gray.shape[:2]
        kp, des = self.orb.detectAndCompute(gray, None)
        
        if des is None or len(des) == 0:
            return False, "No features detected"

        # Keypoints are not directly pickleable easily, so we store coordinates
        keypoints_pts = np.array([k.pt for k in kp], dtype=np.float32)
        
        path = os.path.join(self.scans_dir, f"{name}.npz")
        np.savez(path, descriptors=des, keypoints_pts=keypoints_pts, width=width, height=height)
        
        self.templates[name] = {
            "descriptors": des,
            "keypoints_pts": keypoints_pts,
            "width": width,
            "height": height
        }
        return True, f"Saved {len(kp)} keypoints"

    def remove_template(self, name):
        """Deletes template NPZ file."""
        path = os.path.join(self.scans_dir, f"{name}.npz")
        if os.path.exists(path):
            os.remove(path)
        if name in self.templates:
            del self.templates[name]
        return True

    def toggle_opencl(self, enable=True):
        """Enables GPU acceleration via OpenCL if available."""
        cv2.ocl.setUseOpenCL(enable)
        return cv2.ocl.useOpenCL()

    def detect_motion(self, frame_gray, zones, sensitivity=25, min_area=500):
        """Detects motion inside configured zones using basic threshold difference."""
        motion_detected = False
        motion_events = []
        
        if self.prev_frame_gray is None:
            self.prev_frame_gray = frame_gray.copy()
            return motion_detected, motion_events
            
        # Absolute difference between current frame and previous frame
        frame_delta = cv2.absdiff(self.prev_frame_gray, frame_gray)
        thresh = cv2.threshold(frame_delta, sensitivity, 255, cv2.THRESH_BINARY)[1]
        
        # Dilate to fill gaps
        thresh = cv2.dilate(thresh, None, iterations=2)
        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        self.prev_frame_gray = frame_gray.copy()
        
        for contour in contours:
            area = cv2.contourArea(contour)
            if area < min_area:
                continue
                
            x, y, w, h = cv2.boundingRect(contour)
            cx, cy = x + w // 2, y + h // 2
            
            # Check if centroid is inside any of the surveillance zones
            in_zone = None
            if zones:
                for zone in zones:
                    zone_id = zone.get("id", "Default")
                    # Zone specification has normalized points or screen coordinates
                    # Let's assume absolute coordinates [x_min, y_min, x_max, y_max] or polygon
                    pts = zone.get("points", []) # list of [x, y] in degrees/scale
                    if len(pts) >= 3:
                        poly = np.array(pts, dtype=np.int32)
                        if cv2.pointPolygonTest(poly, (cx, cy), False) >= 0:
                            in_zone = zone_id
                            break
                    elif len(pts) == 2: # bounding box coordinates [top_left, bottom_right]
                        tl, br = pts
                        if tl[0] <= cx <= br[0] and tl[1] <= cy <= br[1]:
                            in_zone = zone_id
                            break
            else:
                in_zone = "Main Screen"
                
            if in_zone:
                motion_detected = True
                motion_events.append({
                    "box": [x, y, x + w, y + h],
                    "centroid": [cx, cy],
                    "zone": in_zone,
                    "area": float(area)
                })
                
        return motion_detected, motion_events

    def process_frame(self, frame, selected_template_name=None, zones=None, motion_settings=None):
        """
        Main processing loop.
        - Scales frame to 480x360 internally for 350%+ speedup.
        - Identifies a specific template or loops all templates for match.
        - Performs motion analyses.
        - Returns coordinates, telemetry, and bounding-boxes.
        """
        h_orig, w_orig = frame.shape[:2]
        w_proc, h_proc = 480, 360
        scale_x = w_orig / w_proc
        scale_y = h_orig / h_proc
        
        resized = cv2.resize(frame, (w_proc, h_proc))
        gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
        
        # Extract features from current frame
        kp_cur, des_cur = self.orb.detectAndCompute(gray, None)
        
        detections = []
        
        # 1. Feature Matching Section
        if des_cur is not None and len(des_cur) > 0:
            templates_to_check = {}
            if selected_template_name and selected_template_name in self.templates:
                templates_to_check[selected_template_name] = self.templates[selected_template_name]
            else:
                templates_to_check = self.templates
                
            for name, temp in templates_to_check.items():
                des_temp = temp["descriptors"]
                kp_temp_pts = temp["keypoints_pts"]
                
                # Match descriptors using BF Hamming
                matches = self.bf.match(des_temp, des_cur)
                # Sort matches by distance
                matches = sorted(matches, key=lambda x: x.distance)
                
                # Select good matches
                good_matches = [m for m in matches if m.distance < 45]
                
                if len(good_matches) >= 10: # threshold for detection
                    # Find coordinates for Homography
                    src_pts = np.float32([kp_temp_pts[m.queryIdx] for m in good_matches]).reshape(-1, 1, 2)
                    dst_pts = np.float32([kp_cur[m.trainIdx].pt for m in good_matches]).reshape(-1, 1, 2)
                    
                    try:
                        H, mask = cv2.findHomography(src_pts, dst_pts, cv2.RANSAC, 5.0)
                        if H is not None:
                            # Use template width and height to form corners
                            t_w, t_h = temp["width"], temp["height"]
                            pts = np.float32([[0, 0], [0, t_h], [t_w, t_h], [t_w, 0]]).reshape(-1, 1, 2)
                            dst_corners = cv2.perspectiveTransform(pts, H)
                            
                            # Convert to list & scale back to original resolution
                            scale_corners = []
                            for pt in dst_corners:
                                rx = int(pt[0][0] * scale_x)
                                ry = int(pt[0][1] * scale_y)
                                scale_corners.append([rx, ry])
                                
                            detections.append({
                                "template": name,
                                "confidence": float(len(good_matches)) / len(kp_temp_pts),
                                "match_count": len(good_matches),
                                "corners": scale_corners
                            })
                    except Exception as e:
                        # Homography error
                        pass

        # 2. Motion Detection Section
        m_sens = 25
        m_area = 500
        if motion_settings:
            m_sens = motion_settings.get("sensitivity", 25)
            m_area = motion_settings.get("min_area", 500)
            
        # Scale zones coordinates to processing frame size
        proc_zones = []
        if zones:
            for zone in zones:
                scaled_pts = []
                for pt in zone.get("points", []):
                    # Pt is [x, y] in original / browser scale coordinates of dimensions w_orig x h_orig
                    sx = int(pt[0] * (w_proc / w_orig))
                    sy = int(pt[1] * (h_proc / h_orig))
                    scaled_pts.append([sx, sy])
                proc_zones.append({
                    "id": zone.get("id"),
                    "points": scaled_pts
                })

        motion_detected, motion_events = self.detect_motion(gray, proc_zones, sensitivity=m_sens, min_area=m_area)
        
        # Scale motion event rectangles back to original frame size
        scaled_motion_events = []
        for me in motion_events:
            box = me["box"]
            bx1 = int(box[0] * scale_x)
            by1 = int(box[1] * scale_y)
            bx2 = int(box[2] * scale_x)
            by2 = int(box[3] * scale_y)
            cx = int(me["centroid"][0] * scale_x)
            cy = int(me["centroid"][1] * scale_y)
            scaled_motion_events.append({
                "box": [bx1, by1, bx2, by2],
                "centroid": [cx, cy],
                "zone": me["zone"],
                "area": float(me["area"] * scale_x * scale_y)
            })

        return {
            "detections": detections,
            "motion_detected": motion_detected,
            "motion_events": scaled_motion_events,
            "timestamp": time.time(),
            "telemetry": {
                "features_current": len(kp_cur),
                "fps": 0.0, # calculated externally
                "cpu_temp": 42.0 # optional
            }
        }
