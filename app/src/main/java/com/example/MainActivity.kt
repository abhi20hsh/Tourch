package com.example

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var userThemeSelection by remember { mutableStateOf<Boolean?>(null) }
            val systemTheme = isSystemInDarkTheme()
            val darkTheme = userThemeSelection ?: systemTheme

            MyApplicationTheme(darkTheme = darkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TorchScreen(
                        modifier = Modifier.padding(innerPadding),
                        darkTheme = darkTheme,
                        onToggleTheme = {
                            // Cycle default -> Light -> Dark -> default
                            userThemeSelection = when (userThemeSelection) {
                                null -> !systemTheme // toggle to opposite of system
                                true -> false        // go to light
                                false -> null        // go back to system default
                            }
                        },
                        isOverridden = userThemeSelection != null
                    )
                }
            }
        }
    }
}

@Composable
fun TorchScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    isOverridden: Boolean
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    var targetCameraId by remember { mutableStateOf<String?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isTorchAvailable by remember { mutableStateOf(true) }

    // Identify the appropriate Camera with Flash unit
    LaunchedEffect(cameraManager) {
        try {
            val idList = cameraManager.cameraIdList
            var foundId: String? = null
            for (id in idList) {
                val hasFlash = cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) {
                    foundId = id
                    break
                }
            }
            if (foundId != null) {
                targetCameraId = foundId
            } else {
                isTorchAvailable = false
                if (idList.isNotEmpty()) {
                    targetCameraId = idList[0]
                }
            }
        } catch (e: Exception) {
            isTorchAvailable = false
        }
    }

    // Register a persistent callback to the camera hardware torch status
    // This keeps the app in 100% sync if other apps or system quick toggles control the torch!
    DisposableEffect(cameraManager, targetCameraId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == targetCameraId) {
                    isTorchOn = enabled
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == targetCameraId) {
                    isTorchAvailable = false
                }
            }
        }

        try {
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // Callback failed to register, typical on emulators
        }

        onDispose {
            try {
                cameraManager.unregisterTorchCallback(callback)
            } catch (e: Exception) {}
        }
    }

    // Fire the hardware action
    val toggleTorch = {
        val id = targetCameraId
        if (id != null && isTorchAvailable) {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // Set the inverse mode
                cameraManager.setTorchMode(id, !isTorchOn)
            } catch (e: Exception) {
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.flashlight_not_available),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Modern glowing pulse animation specs for ON state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Dynamic coloring based on application status (torch state + theme mode)
    val beamColor = Color(0x33FFB300) // soft golden translucent beam
    val accentGolden = Color(0xFFFFB300)
    val inactiveGrey = if (darkTheme) Color(0xFF333537) else Color(0xFFE3E4E6)
    val textStateColor = if (isTorchOn) accentGolden else (if (darkTheme) Color.White else Color(0xFF1B1B1F))

    // Interactive button size scale on tap
    val buttonScaleState by animateFloatAsState(
        targetValue = if (isTorchOn) 1.05f else 0.95f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "buttonScale"
    )

    // Animated colors
    val animatedBackgroundStart by animateColorAsState(
        targetValue = if (isTorchOn) {
            if (darkTheme) Color(0xFF1E1602) else Color(0xFFFFFBE6)
        } else {
            if (darkTheme) Color(0xFF121315) else Color(0xFFF6F8FA)
        },
        animationSpec = tween(600),
        label = "bgStart"
    )

    val animatedBackgroundEnd by animateColorAsState(
        targetValue = if (isTorchOn) {
            if (darkTheme) Color(0xFF0D0A02) else Color(0xFFFFF0B3)
        } else {
            if (darkTheme) Color(0xFF090A0B) else Color(0xFFEDEFF2)
        },
        animationSpec = tween(600),
        label = "bgEnd"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedBackgroundStart, animatedBackgroundEnd)
                )
            )
    ) {
        // Shine Cone Beam canvas: shine light from the top centre when torch is active
        AnimatedVisibility(
            visible = isTorchOn,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width * 0.15f, size.height)
                    lineTo(size.width * 0.85f, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(beamColor, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.9f
                    )
                )
            }
        }

        // Header / Top Row controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Capitalized Application Identifier
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = textStateColor,
                fontFamily = FontFamily.Monospace
            )

            // Manual Dark/Light/Auto Selection Toggle
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleTheme()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (darkTheme) Color(0x33FFFFFF) else Color(0x0A000000),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (isOverridden) accentGolden else Color.Transparent,
                        shape = CircleShape
                    )
                    .testTag("theme_toggle_button")
            ) {
                val iconColor = if (isOverridden) accentGolden else (if (darkTheme) Color.White else Color.Black)
                if (darkTheme) {
                    MoonIcon(
                        color = iconColor,
                        backgroundColor = if (darkTheme) Color(0xFF121315) else Color(0xFFF6F8FA)
                    )
                } else {
                    SunIcon(color = iconColor)
                }
            }
        }

        // Central visual interactive toggle area
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top Status Label
            Text(
                text = stringResource(R.string.status_label).uppercase(),
                fontSize = 12.sp,
                letterSpacing = 2.7.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTorchOn) accentGolden else Color.Gray,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Huge State Text (Dynamic + Capitalized according to locale)
            Text(
                text = if (isTorchOn) {
                    stringResource(R.string.torch_on).uppercase()
                } else {
                    stringResource(R.string.torch_off).uppercase()
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = textStateColor,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )

            Spacer(modifier = Modifier.height(56.dp))

            // Main physical round power controller with reactive glowing waves
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
            ) {
                // Glow Pulse Ring (Visible and animated when torch active)
                if (isTorchOn) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(pulseScale)
                            .background(
                                color = accentGolden.copy(alpha = pulseAlpha),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(pulseScale * 0.85f)
                            .background(
                                color = accentGolden.copy(alpha = pulseAlpha * 0.7f),
                                shape = CircleShape
                            )
                    )
                }

                // Inner Main Circular Frame
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .scale(buttonScaleState)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isTorchOn) {
                                    listOf(accentGolden, accentGolden.copy(alpha = 0.15f))
                                } else {
                                    if (darkTheme) {
                                        listOf(Color(0xFF2E3135), Color(0xFF1E2124))
                                    } else {
                                        listOf(Color(0xFFFFFFFF), Color(0xFFE5E8EB))
                                    }
                                }
                            ),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,  // Custom design haptic scale instead of default simple ripple
                            onClick = toggleTorch
                        )
                        .border(
                            width = 3.dp,
                            color = if (isTorchOn) Color.White else inactiveGrey,
                            shape = CircleShape
                        )
                        .testTag("torch_toggle_button")
                ) {
                    // Power symbol beautifully drawn inside on Custom Canvas
                    val powerColor = if (isTorchOn) Color.White else (if (darkTheme) Color.LightGray else Color(0xFF4A4D50))
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val strokeWidth = 8.dp.toPx()
                        val sizePx = size.width

                        // Core IEC power circle arc (open at top)
                        drawArc(
                            color = powerColor,
                            startAngle = -240f,
                            sweepAngle = 300f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Top vertical central pin line
                        drawLine(
                            color = powerColor,
                            start = Offset(x = sizePx / 2f, y = 4.dp.toPx()),
                            end = Offset(x = sizePx / 2f, y = sizePx / 2.5f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Subdued helper descriptive state indicator
            Text(
                text = if (isTorchOn) {
                    stringResource(R.string.tap_to_turn_off).uppercase()
                } else {
                    stringResource(R.string.tap_to_turn_on).uppercase()
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isTorchOn) accentGolden.copy(alpha = 0.8f) else Color.Gray,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }

        // Clean informative status footer or warning is camera flash unavailable
        if (!isTorchAvailable) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .background(Color(0x1ADB5555), shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Warning",
                    tint = Color(0xFFDB5555),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.flashlight_not_available),
                    fontSize = 11.sp,
                    color = Color(0xFFDB5555),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Custom Sun painting component using drawing primitives
@Composable
fun SunIcon(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 6.dp.toPx()

        // Sun center body
        drawCircle(color = color, radius = radius, center = center)

        // Shining beams radiating from center (8 lines)
        for (i in 0 until 8) {
            val angle = (i * 45f) * (Math.PI / 180f).toFloat()
            val startX = center.x + (radius + 2.dp.toPx()) * cos(angle)
            val startY = center.y + (radius + 2.dp.toPx()) * sin(angle)
            val endX = center.x + (radius + 6.dp.toPx()) * cos(angle)
            val endY = center.y + (radius + 6.dp.toPx()) * sin(angle)

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

// Custom Moon painting component using overlapping subtraction circle
@Composable
fun MoonIcon(
    modifier: Modifier = Modifier,
    color: Color,
    backgroundColor: Color
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = 8.dp.toPx()

        // Outer base shining yellow glow/circle
        drawCircle(
            color = color,
            radius = radius,
            center = center
        )

        // Inner subtraction circle offset to simulate beautiful crescent
        drawCircle(
            color = backgroundColor,
            radius = radius * 0.92f,
            center = Offset(center.x - 5.dp.toPx(), center.y - 3.dp.toPx())
        )
    }
}
