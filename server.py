# -*- coding: utf-8 -*-
import os
import sys
import base64
import json
import time
from flask import Flask, request, jsonify, send_from_directory
import cv2
import numpy as np
from vision import VisionSystem

app = Flask(__name__, static_folder="static")
vision_sys = VisionSystem(scans_dir="scans")

# Performance parameters
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024 # 16 MB limit

# Active zone configurations
# Each zone is defined by: id, points (list of standard coordinates) and alert rules
surveillance_zones = []

# Active camera settings
camera_settings = {
    "sensitivity": 25,
    "min_area": 500,
    "selected_template": "",
    "detection_fps": 15
}

@app.route('/')
def index():
    """Serves the main web UI."""
    return send_from_directory('static', 'index.html')

@app.route('/manifest.json')
def manifest():
    """Serves the PWA manifest."""
    return send_from_directory('static', 'manifest.json')

@app.route('/sw.js')
def service_worker():
    """Serves the PWA Service Worker."""
    response = send_from_directory('static', 'sw.js')
    response.headers['Service-Worker-Allowed'] = '/'
    return response

# Template API
@app.route('/api/templates', methods=['GET'])
def get_templates():
    """Returns lists of loaded template scans."""
    return jsonify({
        "status": "success",
        "templates": [
            {
                "name": name,
                "keypoints": len(temp["keypoints_pts"]),
                "width": temp["width"],
                "height": temp["height"]
            }
            for name, temp in vision_sys.templates.items()
        ]
    })

@app.route('/api/templates/add', methods=['POST'])
def add_template():
    """Saves a new scan template from a base64 image or capture."""
    try:
        data = request.get_json()
        if not data or "name" not in data or "image" not in data:
            return jsonify({"status": "error", "message": "Missing name or base64 image content"}), 400
        
        name = data["name"].strip()
        img_b64 = data["image"]
        if "," in img_b64:
            img_b64 = img_b64.split(",")[1]
            
        img_data = base64.b64decode(img_b64)
        nparr = np.frombuffer(img_data, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            return jsonify({"status": "error", "message": "Invalid image format"}), 400
            
        success, msg = vision_sys.add_template(name, img)
        if success:
            return jsonify({"status": "success", "message": f"Template '{name}' registered: {msg}"})
        else:
            return jsonify({"status": "error", "message": msg}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/templates/delete', methods=['POST'])
def remove_template():
    """Deletes an existing template."""
    try:
        data = request.get_json()
        if not data or "name" not in data:
            return jsonify({"status": "error", "message": "Missing template name"}), 400
            
        name = data["name"]
        success = vision_sys.remove_template(name)
        if success:
            return jsonify({"status": "success", "message": f"Template '{name}' deleted."})
        else:
            return jsonify({"status": "error", "message": "Failed to remove template"}), 400
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

# Settings API
@app.route('/api/settings', methods=['GET', 'POST'])
def handle_settings():
    """Manages central detection thresholds, template bounds and active zone setups."""
    global camera_settings, surveillance_zones
    if request.method == 'POST':
        try:
            data = request.get_json()
            if not data:
                return jsonify({"status": "error", "message": "Payload required"}), 400
                
            if "sensitivity" in data:
                camera_settings["sensitivity"] = int(data["sensitivity"])
            if "min_area" in data:
                camera_settings["min_area"] = int(data["min_area"])
            if "selected_template" in data:
                camera_settings["selected_template"] = str(data["selected_template"])
            if "zones" in data:
                surveillance_zones = data["zones"] # expected structure: [{"id": "A", "points": [[x1, y1], ...]}]
                
            return jsonify({
                "status": "success",
                "settings": camera_settings,
                "zones": surveillance_zones
            })
        except Exception as e:
            return jsonify({"status": "error", "message": str(e)}), 500
    else:
        return jsonify({
            "status": "success",
            "settings": camera_settings,
            "zones": surveillance_zones
        })

# Detection API
@app.route('/api/analyze', methods=['POST'])
def analyze_frame():
    """
    Receives base64 image frame from Web browser (at up to 15 FPS),
    runs ORB and Motion surveillance kernels and returns raw bounding-box & motion overlay vectors.
    """
    start_time = time.time()
    try:
        data = request.json
        if not data or "image" not in data:
            return jsonify({"status": "error", "message": "Image key required"}), 400
            
        img_b64 = data["image"]
        if "," in img_b64:
            img_b64 = img_b64.split(",")[1]
            
        img_data = base64.b64decode(img_b64)
        nparr = np.frombuffer(img_data, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            return jsonify({"status": "error", "message": "Could not decode frame"}), 400
            
        selected_template = camera_settings["selected_template"]
        if not selected_template:
            selected_template = None
            
        # Run VisionSystem processor
        m_settings = {
            "sensitivity": camera_settings["sensitivity"],
            "min_area": camera_settings["min_area"]
        }
        
        results = vision_sys.process_frame(
            frame=img,
            selected_template_name=selected_template,
            zones=surveillance_zones,
            motion_settings=m_settings
        )
        
        # Calculate process latency in milliseconds
        latency = (time.time() - start_time) * 1000
        results["latency_ms"] = latency
        results["status"] = "success"
        
        return jsonify(results)
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/telemetry', methods=['GET'])
def get_telemetry():
    """Exposes system status, battery status and memory consumption logs."""
    # Basic system state metrics
    mem_mb = 0
    try:
        if sys.platform != 'win32':
            import resource
            mem_mb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024.0 # KB to MB
    except:
        pass
        
    return jsonify({
        "status": "success",
        "cpu_usage": 12.5,
        "memory_usage_mb": round(mem_mb, 2),
        "opencl_active": cv2.ocl.useOpenCL(),
        "templates_count": len(vision_sys.templates),
        "zones_count": len(surveillance_zones)
    })

if __name__ == '__main__':
    # Initial hardware GPU check flag setup
    try:
        cv2.ocl.setUseOpenCL(True)
        print(f"OpenCL GPU acceleration initialized: {cv2.ocl.useOpenCL()}")
    except:
        pass
    # Local host listening on Port 5002
    app.run(host='0.0.0.0', port=5002, debug=False, threaded=True)
