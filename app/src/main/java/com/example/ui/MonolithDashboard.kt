package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonolithDashboard(
    viewModel: MonolithViewModel,
    modifier: Modifier = Modifier
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val isVoiceEnabled by viewModel.isVoiceEnabled.collectAsStateWithLifecycle()
    val selectedTemplateName by viewModel.selectedTemplateName.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val minArea by viewModel.minArea.collectAsStateWithLifecycle()

    val conveyorOffset by viewModel.simulatedConveyorOffset.collectAsStateWithLifecycle()
    val isMotionActive by viewModel.simulatedMotionActive.collectAsStateWithLifecycle()
    val matchedTemplateName by viewModel.matchedTemplateName.collectAsStateWithLifecycle()
    val fpsValue by viewModel.mockTelemetryFps.collectAsStateWithLifecycle()

    var showAddTemplateDialog by remember { mutableStateOf(false) }
    var templateNameInput by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Clock
    var systemTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            systemTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // High Tech Scan sweep and indicator animations
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepProgress"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCirc),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground),
        containerColor = SlateBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            var selectedTab by remember { mutableStateOf(0) }
            NavigationBar(
                containerColor = SlateBackground,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(64.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Monitor",
                            tint = if (selectedTab == 0) CyanNeon else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Monitor",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (selectedTab == 0) CyanNeon else Color.Gray
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyanNeon.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Archive",
                            tint = if (selectedTab == 1) CyanNeon else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Archive",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (selectedTab == 1) CyanNeon else Color.Gray
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyanNeon.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System",
                            tint = if (selectedTab == 2) CyanNeon else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "System",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (selectedTab == 2) CyanNeon else Color.Gray
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyanNeon.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- TOP SYSTEM HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, CyanNeonDim.copy(alpha = 0.2f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "MONOLITH OS v1500",
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 1.8.sp,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "System Dashboard",
                        color = Color.White,
                        fontSize = 18.sp,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // System Overlay custom pill Termux:5002
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isServerRunning) EmeraldNeon else Color.Gray,
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        Text(
                            text = "Termux:5002",
                            color = LightAccents,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(systemTime, color = Color.White, fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("84% BATT", fontSize = 9.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }

            // --- DETECTOR WORKING CANVAS GRID ---
            val mainContentWeight = if (isLandscape) 0.65f else 1f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left Panel: Viewfinder & Quick Actions
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(mainContentWeight)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(ViewfinderBg, RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                    ) {
                        // Dynamic Canvas drawing conveyor/tracking simulations
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height

                            // 1. Draw static gridlines (Cyan theme)
                            val gridSpacing = 40.dp.toPx()
                            for (x in 0..canvasW.toInt() step gridSpacing.toInt()) {
                                drawLine(
                                    color = CyanNeon.copy(alpha = 0.05f),
                                    start = Offset(x.toFloat(), 0f),
                                    end = Offset(x.toFloat(), canvasH),
                                    strokeWidth = 1f
                                )
                            }
                            for (y in 0..canvasH.toInt() step gridSpacing.toInt()) {
                                drawLine(
                                    color = CyanNeon.copy(alpha = 0.05f),
                                    start = Offset(0f, y.toFloat()),
                                    end = Offset(canvasW, y.toFloat()),
                                    strokeWidth = 1f
                                )
                            }

                            // 2. Draw Surveillance Corridor Zone highlights (Cyan outline)
                            val zonePoints = listOf(
                                Offset(canvasW * 0.15f, canvasH * 0.2f),
                                Offset(canvasW * 0.85f, canvasH * 0.2f),
                                Offset(canvasW * 0.85f, canvasH * 0.8f),
                                Offset(canvasW * 0.15f, canvasH * 0.8f)
                            )
                            val zonePath = Path().apply {
                                moveTo(zonePoints[0].x, zonePoints[0].y)
                                lineTo(zonePoints[1].x, zonePoints[1].y)
                                lineTo(zonePoints[2].x, zonePoints[2].y)
                                lineTo(zonePoints[3].x, zonePoints[3].y)
                                close()
                            }
                            drawPath(
                                path = zonePath,
                                color = CyanNeon.copy(alpha = 0.04f)
                            )
                            drawPath(
                                path = zonePath,
                                color = CyanNeon.copy(alpha = 0.3f),
                                style = Stroke(width = 3.dp.toPx())
                            )

                            // 3. Draw Conveyor moving component simulations
                            val boxWidth = 120.dp.toPx()
                            val boxHeight = 100.dp.toPx()
                            val rawOffsetPercent = conveyorOffset / 1000f
                            val movingX = canvasW * rawOffsetPercent
                            val movingY = canvasH * 0.5f - (boxHeight / 2)

                            // Moving microchip/electronic board
                            drawRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(movingX - (boxWidth / 2), movingY),
                                size = Size(boxWidth, boxHeight)
                            )
                            drawRect(
                                color = CyanNeonDim,
                                topLeft = Offset(movingX - (boxWidth / 2), movingY),
                                size = Size(boxWidth, boxHeight),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            // Chip core
                            drawRect(
                                color = Color(0xFFD97706),
                                topLeft = Offset(movingX - 20.dp.toPx(), movingY + 30.dp.toPx()),
                                size = Size(40.dp.toPx(), 40.dp.toPx())
                            )

                            // 4. Bounding Box and lines overlay if microchip core is inside sweet spot matches
                            if (matchedTemplateName != null) {
                                val matchBoxWidth = 150.dp.toPx()
                                val matchBoxHeight = 130.dp.toPx()
                                val matchX = movingX - (matchBoxWidth / 2)
                                val matchY = movingY - 15.dp.toPx()

                                drawRect(
                                    color = CyanNeon.copy(alpha = 0.1f),
                                    topLeft = Offset(matchX, matchY),
                                    size = Size(matchBoxWidth, matchBoxHeight)
                                )
                                drawRect(
                                    color = CyanNeon,
                                    topLeft = Offset(matchX, matchY),
                                    size = Size(matchBoxWidth, matchBoxHeight),
                                    style = Stroke(width = 4.dp.toPx())
                                )

                                // Plot features-linking matching strings to represent CV
                                val featureCount = 12
                                for (i in 0 until featureCount) {
                                    val startPt = Offset(matchX + (matchBoxWidth * (i.toFloat() / featureCount)), matchY)
                                    val endPt = Offset(matchX + (matchBoxWidth * ((featureCount - i).toFloat() / featureCount)), matchY + matchBoxHeight)
                                    drawLine(
                                        color = CyanNeon.copy(alpha = 0.35f),
                                        start = startPt,
                                        end = endPt,
                                        strokeWidth = 1f
                                    )
                                }
                            }

                            // 5. Drawing Motion Heatmap vectors
                            if (isMotionActive) {
                                drawRect(
                                    color = WarningRed.copy(alpha = 0.08f),
                                    topLeft = Offset(canvasW * 0.2f, canvasH * 0.4f),
                                    size = Size(canvasW * 0.6f, canvasH * 0.2f)
                                )
                                drawRect(
                                    color = WarningRed,
                                    topLeft = Offset(canvasW * 0.35f, canvasH * 0.45f),
                                    size = Size(100.dp.toPx(), 60.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = WarningRed,
                                    center = Offset(canvasW * 0.35f + 50.dp.toPx(), canvasH * 0.45f + 30.dp.toPx()),
                                    radius = 6.dp.toPx()
                                )
                            }

                            // 6. Draw glowing active Scanner Sweep Line
                            val lineY = canvasH * sweepProgress
                            drawLine(
                                color = CyanNeon,
                                start = Offset(0f, lineY),
                                end = Offset(canvasW, lineY),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawRect(
                                color = CyanNeon.copy(alpha = 0.1f),
                                topLeft = Offset(0f, maxOf(0f, lineY - 14.dp.toPx())),
                                size = Size(canvasW, 14.dp.toPx())
                            )
                        }

                        // Overlay design corners (L-bracket vectors matching HTML design w-16 h-16 border-cyan-500)
                        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                            // Top-Left corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(32.dp)
                                    .border(
                                        width = 3.dp,
                                        color = CyanNeon.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(topStart = 6.dp)
                                    )
                            )
                            // Top-Right corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                                    .border(
                                        width = 3.dp,
                                        color = CyanNeon.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(topEnd = 6.dp)
                                    )
                            )
                            // Bottom-Left corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .size(32.dp)
                                    .border(
                                        width = 3.dp,
                                        color = CyanNeon.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(bottomStart = 6.dp)
                                    )
                            )
                            // Bottom-Right corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(32.dp)
                                    .border(
                                        width = 3.dp,
                                        color = CyanNeon.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(bottomEnd = 6.dp)
                                    )
                            )
                        }

                        // High Tech Overlay Bounding Target HUD Box if matched
                        if (matchedTemplateName != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(200.dp)
                                    .border(2.dp, CyanNeon.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .background(CyanNeon.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                // Subtitle top tag
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .background(CyanNeon.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .border(1.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "TARGET_${matchedTemplateName?.uppercase() ?: "OBJ"}",
                                        color = CyanNeon,
                                        fontSize = 9.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        text = "98.4%",
                                        color = CyanNeon,
                                        fontSize = 9.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }

                                // Centered pulse node
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size((12 * pulseScale).dp)
                                        .background(CyanNeon, RoundedCornerShape(50))
                                )

                                Text(
                                    text = "TRACKING ACTIVE",
                                    color = CyanNeon.copy(alpha = 0.6f),
                                    fontSize = 8.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Overlay text HUD indicators
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .border(1.dp, CyanNeonDim.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("PROCESSING", fontSize = 9.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text("ПИКСЕЛИ: 480x360@15FPS", fontSize = 9.sp, color = CyanNeon, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text("МЕТОД: COMPUTER VISION / ORB", fontSize = 9.sp, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text(
                                    text = "ЧАСТОТА: ${String.format("%.1f", fpsValue)} FPS",
                                    fontSize = 9.sp,
                                    color = CyanNeon,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "СОСТОЯНИЕ ЗОНЫ: " + if (isMotionActive) "АКТИВНОСТЬ" else "БЕЗОПАСНО",
                                    fontSize = 9.sp,
                                    color = if (isMotionActive) WarningRed else EmeraldNeon,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }

                        // Overlay Alerts text indicator
                        if (isMotionActive) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(WarningRed.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                                    .padding(vertical = 6.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    "⚠️ ДВИЖЕНИЕ В ЗОНЕ X12",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Actions Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddTemplateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeonDim),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .heightIn(min = 52.dp)
                                .border(1.dp, CyanNeon.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                                .testTag("create_scan_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ФИКСИРОВАТЬ СКАН",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleWebServer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServerRunning) WarningRed.copy(alpha = 0.2f) else SlateSurface
                            ),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 52.dp)
                                .border(1.dp, if (isServerRunning) WarningRed else CyanNeon.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                                .testTag("web_server_toggle")
                        ) {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = "Сервер", tint = if (isServerRunning) WarningRed else Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServerRunning) "ОСТАНОВИТЬ" else "ЗАПУСТИТЬ WEB",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleVoiceEnabled() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 52.dp)
                                .border(1.dp, if (isVoiceEnabled) CyanNeon.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                        ) {
                            Icon(
                                imageVector = if (isVoiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = "Звук",
                                tint = if (isVoiceEnabled) CyanNeon else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isVoiceEnabled) "ГОЛОС: ВКЛ" else "ГОЛОС: ВЫКЛ",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }

                // Split Panel configurations (Right sidebar in Landscape / standard column stack below in Portrait)
                if (isLandscape) {
                    Column(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, EmeraldDim.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfigControls(
                            viewModel = viewModel,
                            sensitivity = sensitivity,
                            minArea = minArea,
                            selectedTemplateName = selectedTemplateName,
                            templates = templates
                        )

                        scansTablePanel(
                            templates = templates,
                            onSelect = { viewModel.selectActiveTemplate(it) },
                            onDelete = { viewModel.deleteTemplate(it) },
                            selectedTemplateName = selectedTemplateName,
                            modifier = Modifier.weight(1f)
                        )

                        logsTerminalPanel(
                            logs = logs,
                            onClear = { viewModel.clearHistoryLogs() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Stack below in Portrait mode
            if (!isLandscape) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConfigControls(
                        viewModel = viewModel,
                        sensitivity = sensitivity,
                        minArea = minArea,
                        selectedTemplateName = selectedTemplateName,
                        templates = templates
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        scansTablePanel(
                            templates = templates,
                            onSelect = { viewModel.selectActiveTemplate(it) },
                            onDelete = { viewModel.deleteTemplate(it) },
                            selectedTemplateName = selectedTemplateName,
                            modifier = Modifier.weight(1f)
                        )

                        logsTerminalPanel(
                            logs = logs,
                            onClear = { viewModel.clearHistoryLogs() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS COMPONENTS ---
    if (showAddTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showAddTemplateDialog = false },
            title = { Text("СОХРАНИТЬ НОВЫЙ ШАБЛОН", color = CyanNeon, fontSize = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontStyle = MaterialTheme.typography.titleMedium.fontStyle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Задайте условный буквенно-цифровой код для текущей сигнатуры платы/детали. Алгоритм ORB извлечет 350 ключевых точек.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = templateNameInput,
                        onValueChange = { templateNameInput = it },
                        placeholder = { Text("CHIP_SECURE_12", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = CyanNeonDim,
                            focusedTextColor = CyanNeon,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (templateNameInput.trim().isNotEmpty()) {
                            viewModel.saveNewTemplate(templateNameInput.trim())
                            templateNameInput = ""
                            showAddTemplateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeonDim)
                ) {
                    Text("СОХРАНИТЬ", color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTemplateDialog = false }) {
                    Text("ОТМЕНА", color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            },
            containerColor = SlateSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// --- SUB-PANEL COMPONENTS ---

@Composable
fun ConfigControls(
    viewModel: MonolithViewModel,
    sensitivity: Int,
    minArea: Int,
    selectedTemplateName: String,
    templates: List<ScanTemplate>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "ПД-ФИЛЬТРЫ КОНТРОЛЛЕРА",
                fontSize = 10.sp,
                color = CyanNeon,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp
            )

            // Sensitivity slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ЧУВСТВИТЕЛЬНОСТЬ (%)", fontSize = 9.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("$sensitivity", fontSize = 9.sp, color = CyanNeon, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                Slider(
                    value = sensitivity.toFloat(),
                    onValueChange = { viewModel.updateSensitivity(it.toInt()) },
                    valueRange = 5f..90f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanNeon,
                        activeTrackColor = CyanNeon,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            // Min Object Area slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("МИН. ПЛОЩАДЬ ДЕТЕКЦИИ (px)", fontSize = 9.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("$minArea px", fontSize = 9.sp, color = CyanNeon, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                Slider(
                    value = minArea.toFloat(),
                    onValueChange = { viewModel.updateMinArea(it.toInt()) },
                    valueRange = 100f..10000f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanNeon,
                        activeTrackColor = CyanNeon,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }
        }
    }
}

@Composable
fun scansTablePanel(
    templates: List<ScanTemplate>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    selectedTemplateName: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "БАЗА ШАБЛОНОВ (SCANS)",
                fontSize = 10.sp,
                color = CyanNeon,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (templates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Шаблоны отсутствуют", fontSize = 10.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(templates) { t ->
                        val isSelected = selectedTemplateName == t.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) CyanNeon.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CyanNeon else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelect(if (isSelected) "" else t.name) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.name, fontSize = 11.sp, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
                                Text("${t.keypointsCount} ORB points • ${t.width}x${t.height}", fontSize = 8.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                            IconButton(
                                onClick = { onDelete(t.name) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = WarningRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun logsTerminalPanel(
    logs: List<DetectionLog>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ЖУРНАЛ СОБЫТИЙ",
                    fontSize = 10.sp,
                    color = CyanNeon,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "ОЧИСТИТЬ",
                    fontSize = 9.sp,
                    color = WarningRed,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.clickable { onClear() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Событий нет", fontSize = 10.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                        val tagColor = when(log.type) {
                            "MATCH" -> CyanNeon
                            "MOTION" -> WarningRed
                            "SYSTEM" -> Color.Gray
                            else -> CyanNeon
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f))
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("[$timeString]", fontSize = 8.sp, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text(
                                    text = log.type,
                                    fontSize = 8.sp,
                                    color = tagColor,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(log.description, fontSize = 9.sp, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1)
                            }
                            Text(log.details, fontSize = 8.sp, color = Color.LightGray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
