package com.spendwise.app.ui.components.glass

import android.app.ActivityManager
import android.content.Context
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.sin

private val ScenePlum950 = Color(0xFF1A0810)
private val ScenePlum900 = Color(0xFF2A0F1A)
private val ScenePlum700 = Color(0xFF5A1A2E)
private val ScenePlumDeep = Color(0xFF11050A)
private val SceneGoldLight = Color(0xFFF3DBA0)
private val SceneGold = Color(0xFFD4AF6A)
private val SceneGoldDark = Color(0xFF8A6420)

private data class AndroidCoinSpec(
    val sizeDp: Dp,
    val alignment: Alignment,
    val offsetX: Dp,
    val offsetY: Dp,
    val isFar: Boolean,
    val baseRotY: Float,
    val baseRotX: Float,
    val floatAmpDp: Float,
    val durationMs: Int,
    val staggerMs: Int
)

private val ANDROID_COINS = listOf(
    AndroidCoinSpec(
        sizeDp = 58.dp,
        alignment = Alignment.TopStart,
        offsetX = (-14).dp,
        offsetY = (-24).dp,
        isFar = false,
        baseRotY = -16f,
        baseRotX = 12f,
        floatAmpDp = 9f,
        durationMs = 6800,
        staggerMs = 80
    ),
    AndroidCoinSpec(
        sizeDp = 44.dp,
        alignment = Alignment.TopEnd,
        offsetX = 12.dp,
        offsetY = (-16).dp,
        isFar = true,
        baseRotY = 20f,
        baseRotX = -10f,
        floatAmpDp = 7f,
        durationMs = 8600,
        staggerMs = 160
    ),
    AndroidCoinSpec(
        sizeDp = 52.dp,
        alignment = Alignment.BottomStart,
        offsetX = (-10).dp,
        offsetY = 22.dp,
        isFar = false,
        baseRotY = 18f,
        baseRotX = -12f,
        floatAmpDp = 8f,
        durationMs = 9400,
        staggerMs = 240
    ),
    AndroidCoinSpec(
        sizeDp = 40.dp,
        alignment = Alignment.BottomEnd,
        offsetX = 10.dp,
        offsetY = 18.dp,
        isFar = true,
        baseRotY = -14f,
        baseRotX = 14f,
        floatAmpDp = 6f,
        durationMs = 7800,
        staggerMs = 320
    )
)

/**
 * Reusable Royal Plum & Champagne Gold 3D perspective login scene.
 * Pure visual wrapper around existing authentication forms:
 * - Gyroscope tilt (SensorManager TYPE_ROTATION_VECTOR, max +/-6 deg) with drag fallback
 * - 15-layer extruded slab depth behind the card (2 gold edge layers + 13 dark plum)
 * - Floating Spendwise "S" emblem half above the card top edge
 * - Scattered 3D ₹ gold coins with depth-of-field styling and staggered loops
 * - Respects system reduce-motion and Battery Saver / low-RAM flat fallback
 */
@Composable
fun Login3DScene(
    scrollModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // System accessibility & power-saving checks
    val reduceMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val isFlatFallback = remember(context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        (powerManager?.isPowerSaveMode == true) || (activityManager?.isLowRamDevice == true)
    }

    val restingRotX = if (isFlatFallback) 0f else 3f
    val restingRotY = if (isFlatFallback) 0f else -7f

    var targetTiltX by remember { mutableFloatStateOf(restingRotX) }
    var targetTiltY by remember { mutableFloatStateOf(restingRotY) }
    var hasGyro by remember { mutableStateOf(false) }

    // Gyroscope sensor listener (low-pass filtered, max +/-6 degrees)
    DisposableEffect(context, reduceMotion, isFlatFallback) {
        if (reduceMotion || isFlatFallback) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

            if (sensorManager == null || rotationSensor == null) {
                hasGyro = false
                onDispose { }
            } else {
                hasGyro = true
                var filteredPitch = 0f
                var filteredRoll = 0f
                var basePitch: Float? = null
                var baseRoll: Float? = null
                val rotationMatrix = FloatArray(9)
                val orientation = FloatArray(3)

                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

                        if (basePitch == null) basePitch = pitchDeg
                        if (baseRoll == null) baseRoll = rollDeg

                        val deltaPitch = (pitchDeg - (basePitch ?: pitchDeg)).coerceIn(-24f, 24f)
                        val deltaRoll = (rollDeg - (baseRoll ?: rollDeg)).coerceIn(-24f, 24f)

                        val alpha = 0.12f
                        filteredPitch += alpha * (deltaPitch - filteredPitch)
                        filteredRoll += alpha * (deltaRoll - filteredRoll)

                        targetTiltX = restingRotX + (filteredPitch * 0.25f).coerceIn(-6f, 6f)
                        targetTiltY = restingRotY + (filteredRoll * 0.25f).coerceIn(-6f, 6f)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }

    val animTiltX by animateFloatAsState(
        targetValue = if (reduceMotion || isFlatFallback) restingRotX else targetTiltX,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "CardTiltX"
    )
    val animTiltY by animateFloatAsState(
        targetValue = if (reduceMotion || isFlatFallback) restingRotY else targetTiltY,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "CardTiltY"
    )

    // 500ms spring entrance animation
    var entered by remember { mutableStateOf(reduceMotion || isFlatFallback) }
    LaunchedEffect(Unit) {
        entered = true
    }

    val entranceProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = if (reduceMotion || isFlatFallback) {
            snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)
        },
        label = "CardEntrance"
    )

    val dragFallbackModifier = if (!hasGyro && !reduceMotion && !isFlatFallback) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    targetTiltX = restingRotX
                    targetTiltY = restingRotY
                },
                onDragCancel = {
                    targetTiltX = restingRotX
                    targetTiltY = restingRotY
                }
            ) { change, dragAmount ->
                change.consume()
                targetTiltY = (targetTiltY + dragAmount.x * 0.08f)
                    .coerceIn(restingRotY - 6f, restingRotY + 6f)
                targetTiltX = (targetTiltX - dragAmount.y * 0.08f)
                    .coerceIn(restingRotX - 6f, restingRotX + 6f)
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ScenePlum900, ScenePlum950, ScenePlumDeep)
                )
            )
            .then(dragFallbackModifier)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        // Pre-blurred Ambient Glows + Subtle S-Shaped Curve (Hidden from screen readers)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { }
        ) {
            // Soft plum glow at top-left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ScenePlum700.copy(alpha = 0.72f),
                        ScenePlum700.copy(alpha = 0f)
                    ),
                    center = Offset(size.width * 0.12f, size.height * 0.14f),
                    radius = size.maxDimension * 0.45f
                ),
                radius = size.maxDimension * 0.45f,
                center = Offset(size.width * 0.12f, size.height * 0.14f)
            )

            // Faint gold glow at bottom-right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SceneGold.copy(alpha = 0.20f),
                        SceneGold.copy(alpha = 0f)
                    ),
                    center = Offset(size.width * 0.90f, size.height * 0.88f),
                    radius = size.maxDimension * 0.42f
                ),
                radius = size.maxDimension * 0.42f,
                center = Offset(size.width * 0.90f, size.height * 0.88f)
            )

            // Very subtle pre-blurred S-shaped curve (layered translucent strokes)
            val sPath = Path().apply {
                moveTo(size.width * 0.85f, size.height * 0.08f)
                cubicTo(
                    size.width * 0.50f, size.height * 0.05f,
                    size.width * 0.25f, size.height * 0.28f,
                    size.width * 0.38f, size.height * 0.44f
                )
                cubicTo(
                    size.width * 0.52f, size.height * 0.58f,
                    size.width * 0.76f, size.height * 0.62f,
                    size.width * 0.72f, size.height * 0.80f
                )
                cubicTo(
                    size.width * 0.68f, size.height * 0.94f,
                    size.width * 0.35f, size.height * 0.96f,
                    size.width * 0.14f, size.height * 0.90f
                )
            }
            drawPath(
                path = sPath,
                color = SceneGold.copy(alpha = 0.03f),
                style = Stroke(width = 76.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = sPath,
                color = SceneGold.copy(alpha = 0.045f),
                style = Stroke(width = 34.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft blurred shadow on the "floor" under the card
                if (!isFlatFallback) {
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 26.dp)
                            .fillMaxWidth(0.88f)
                            .height(42.dp)
                            .clearAndSetSemantics { }
                    ) {
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xD9050103),
                                    Color(0x66100409),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width / 2f
                            )
                        )
                    }
                }

                // Scattered 3D ₹ Gold Coins around perimeter (skip on flat fallback)
                if (!isFlatFallback) {
                    ANDROID_COINS.forEach { spec ->
                        GoldCoin3DCanvas(
                            spec = spec,
                            tiltX = animTiltX,
                            tiltY = animTiltY,
                            reduceMotion = reduceMotion,
                            modifier = Modifier
                                .align(spec.alignment)
                                .offset(x = spec.offsetX, y = spec.offsetY)
                                .zIndex(if (spec.isFar) 0f else 3f)
                        )
                    }
                }

                // Main Tilted 3D Card Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .graphicsLayer {
                            rotationX = animTiltX
                            rotationY = animTiltY
                            cameraDistance = 20f * density
                            translationY = (1f - entranceProgress) * 48f * density
                            alpha = entranceProgress.coerceIn(0f, 1f)
                        }
                ) {
                    // 15-Layer Extruded Slab Edge behind front face (First 2 Gold, Rest Dark Plum)
                    if (!isFlatFallback) {
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .clearAndSetSemantics { }
                        ) {
                            val cornerPx = 16.dp.toPx()
                            val rotYRad = (animTiltY * PI / 180.0).toFloat()
                            val rotXRad = (animTiltX * PI / 180.0).toFloat()
                            val stepX = (-sin(rotYRad.toDouble()).toFloat() * 3.8f).coerceIn(-1.4f, 1.4f).dp.toPx()
                            val stepY = (sin(rotXRad.toDouble()).toFloat() * 3.8f + 0.45f).dp.toPx()

                            val layerCount = 15
                            for (layer in layerCount downTo 1) {
                                val dx = stepX * layer
                                val dy = stepY * layer
                                val darkness = (layer - 2).coerceAtLeast(0) / (layerCount - 2f)
                                val layerColor = when {
                                    layer == 1 -> SceneGold
                                    layer == 2 -> SceneGoldDark
                                    else -> Color(
                                        red = (0.16f - darkness * 0.11f).coerceAtLeast(0.03f),
                                        green = (0.06f - darkness * 0.04f).coerceAtLeast(0.01f),
                                        blue = (0.10f - darkness * 0.06f).coerceAtLeast(0.02f),
                                        alpha = 0.98f
                                    )
                                }
                                drawRoundRect(
                                    color = layerColor,
                                    topLeft = Offset(dx, dy),
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                                )
                            }
                        }
                    }

                    // Card Front Face: Plum Glass Gradient, Top-Left Sheen, 1.5dp Gold Border, Inner Glow
                    val cardShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        ScenePlum700.copy(alpha = 0.84f),
                                        ScenePlum900.copy(alpha = 0.93f),
                                        ScenePlum950.copy(alpha = 0.97f)
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(900f, 1400f)
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        SceneGoldLight.copy(alpha = 0.80f),
                                        SceneGold.copy(alpha = 0.55f),
                                        SceneGoldDark.copy(alpha = 0.45f)
                                    )
                                ),
                                shape = cardShape
                            )
                    ) {
                        // Top-left specular sheen & soft inner glow
                        Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .clearAndSetSemantics { }
                        ) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        SceneGoldLight.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.16f, size.height * 0.08f),
                                    radius = size.width * 0.72f
                                )
                            )
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        SceneGold.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = size.height * 0.65f
                                )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 42.dp, bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = content
                        )
                    }

                    // Floating Spendwise "S" Logo centered half-outside top edge, lifted toward viewer
                    FloatingSpendwiseSLogo(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(
                                x = (-animTiltY * 0.8f).dp,
                                y = (-28 - animTiltX * 0.6f).dp
                            )
                            .zIndex(4f)
                            .graphicsLayer {
                                scaleX = if (isFlatFallback) 1f else 1.06f
                                scaleY = if (isFlatFallback) 1f else 1.06f
                                cameraDistance = 20f * density
                            }
                    )
                }
            }
        }
    }
}

/**
 * Floating 56dp Royal Plum tile with embossed Champagne Gold "S" monogram.
 */
@Composable
fun FloatingSpendwiseSLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val tileShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 18.dp,
                shape = tileShape,
                spotColor = Color.Black,
                ambientColor = SceneGold.copy(alpha = 0.45f)
            )
            .clip(tileShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(ScenePlum700, ScenePlum900, ScenePlum950)
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(SceneGoldLight, SceneGold, SceneGoldDark)
                ),
                shape = tileShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(30.dp)
                .clearAndSetSemantics { }
        ) {
            val w = this.size.width
            val h = this.size.height
            val sPath = Path().apply {
                moveTo(w * 0.74f, h * 0.27f)
                cubicTo(
                    w * 0.70f, h * 0.16f,
                    w * 0.58f, h * 0.12f,
                    w * 0.48f, h * 0.12f
                )
                cubicTo(
                    w * 0.30f, h * 0.12f,
                    w * 0.22f, h * 0.22f,
                    w * 0.22f, h * 0.33f
                )
                cubicTo(
                    w * 0.22f, h * 0.46f,
                    w * 0.36f, h * 0.49f,
                    w * 0.50f, h * 0.52f
                )
                cubicTo(
                    w * 0.67f, h * 0.56f,
                    w * 0.78f, h * 0.61f,
                    w * 0.78f, h * 0.74f
                )
                cubicTo(
                    w * 0.78f, h * 0.87f,
                    w * 0.64f, h * 0.92f,
                    w * 0.48f, h * 0.92f
                )
                cubicTo(
                    w * 0.32f, h * 0.92f,
                    w * 0.21f, h * 0.85f,
                    w * 0.18f, h * 0.73f
                )
            }
            drawPath(
                path = sPath,
                brush = Brush.linearGradient(
                    colors = listOf(SceneGoldLight, SceneGold, SceneGoldDark),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                style = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Thick 3D Champagne Gold Coin with ₹ emblem, radial face highlight, inner rim ring, and extruded edge.
 */
@Composable
private fun GoldCoin3DCanvas(
    spec: AndroidCoinSpec,
    tiltX: Float,
    tiltY: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    var visible by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            kotlinx.coroutines.delay(spec.staggerMs.toLong())
            visible = true
        }
    }

    val coinAlpha by animateFloatAsState(
        targetValue = if (visible) (if (spec.isFar) 0.72f else 1f) else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "CoinFade"
    )

    val floatOffset: Float
    val animRotY: Float
    if (reduceMotion) {
        floatOffset = 0f
        animRotY = spec.baseRotY
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "CoinLoop_${spec.staggerMs}")
        val loopVal by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = spec.durationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "CoinFloat"
        )
        floatOffset = loopVal * spec.floatAmpDp
        animRotY = spec.baseRotY + loopVal * 10f
    }

    val parallaxFactor = if (spec.isFar) 0.35f else 0.85f

    Canvas(
        modifier = modifier
            .size(spec.sizeDp)
            .clearAndSetSemantics { }
            .graphicsLayer {
                alpha = coinAlpha
                translationX = tiltY * parallaxFactor * density
                translationY = (floatOffset - tiltX * parallaxFactor) * density
                rotationY = animRotY + tiltY * 0.4f
                rotationX = spec.baseRotX + tiltX * 0.4f
                cameraDistance = 16f * density
            }
    ) {
        val radius = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Pre-blurred outer DOF halo for far coins
        if (spec.isFar) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SceneGold.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius * 1.25f
                ),
                radius = radius * 1.25f,
                center = Offset(cx, cy)
            )
        }

        // Stacked thick coin edge layers (dark gold)
        val edgeLayers = 6
        val edgeOffsetStep = (radius * 0.032f)
        for (i in edgeLayers downTo 1) {
            val edgeColor = if (i == 1) SceneGoldDark else Color(0xFF5E4110)
            drawCircle(
                color = edgeColor,
                radius = radius,
                center = Offset(cx + i * edgeOffsetStep * 0.6f, cy + i * edgeOffsetStep)
            )
        }

        // Front gold face with radial highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SceneGoldLight, SceneGold, SceneGoldDark),
                center = Offset(cx - radius * 0.32f, cy - radius * 0.34f),
                radius = radius * 1.35f
            ),
            radius = radius,
            center = Offset(cx, cy)
        )

        // Outer bright rim & inner recessed ring
        drawCircle(
            color = SceneGoldLight.copy(alpha = 0.85f),
            radius = radius - 1.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(
            color = SceneGoldDark.copy(alpha = 0.60f),
            radius = radius * 0.77f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.1.dp.toPx())
        )

        // Crisp ₹ glyph centered inside the coin
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = radius * 0.92f
                color = android.graphics.Color.rgb(60, 37, 5)
                isFakeBoldText = true
                alpha = if (spec.isFar) 185 else 235
            }
            val yOffset = -(paint.descent() + paint.ascent()) / 2f
            canvas.nativeCanvas.drawText("₹", cx, cy + yOffset, paint)
        }
    }
}
