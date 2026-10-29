package tech.hezy.glassalarmslider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

internal class GlassAlarmRenderer(context: Context) {
    private val shader = RuntimeShader(
        context.resources.openRawResource(R.raw.alarm_glass).bufferedReader().use { it.readText() },
    )
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val shimmerMatrix = Matrix()
    private var shimmerGradient: LinearGradient? = null
    private var nativeCapture: GlassSceneCapture? = null
    private val drawSource: (Canvas) -> Unit = ::drawScene
    private lateinit var geometry: GlassControlGeometry
    private var fontScale = Float.NaN
    private var snoozeLabel = ""
    private var sliderLabel = ""
    private var motionEnabled = true
    private var snoozeTextSize = 0f
    private var sliderTextSize = 0f
    private var snoozeBaseline = 0f
    private var sliderBaseline = 0f

    private var materialSettings: GlassAlarmSettings? = null
    private var materialRevision = -1
    private var uniformGeometry: GlassControlGeometry? = null
    private var frameProgress = Float.NaN
    private var framePressScale = Float.NaN
    private var shimmer = 0f
    private var brightness = 1f
    private var orangeR = 0f
    private var orangeG = 0f
    private var orangeB = 0f
    private var orangeDimming = 0f
    private var lightAngleOffset = 0f
    private var sizeCoefficient = 4f
    private var scaleCoefficient = 1f
    private var blurRadius = 0f
    private var usesNativeCapture = false
    private var shaderDirty = false
    private val uniformValues = FloatArray(Uniform.entries.size * 4) { Float.NaN }
    private var snapshot: RenderEffect? = null
    private var layerEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun configure(
        width: Float,
        unit: Float,
        fontScale: Float,
        snoozeLabel: String,
        sliderLabel: String,
        motionEnabled: Boolean,
    ): GlassControlGeometry {
        val geometryChanged = !::geometry.isInitialized || geometry.width != width || geometry.unit != unit
        if (!::geometry.isInitialized || geometry.unit != unit) {
            val halfBand = 24f * unit
            shimmerGradient = LinearGradient(
                -halfBand, 0f, halfBand, 0f,
                intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.32f, 0.68f, 1f), Shader.TileMode.CLAMP,
            )
        }
        if (geometryChanged) geometry = GlassControlGeometry(width, unit)
        if (geometryChanged || this.fontScale != fontScale || this.snoozeLabel != snoozeLabel ||
            this.sliderLabel != sliderLabel) {
            this.fontScale = fontScale
            this.snoozeLabel = snoozeLabel
            this.sliderLabel = sliderLabel
            snoozeTextSize = fitText(snoozeLabel, 30f * unit * fontScale)
            snoozeBaseline = labelBaseline(geometry.lightY)
            sliderTextSize = fitText(sliderLabel, 26f * unit * fontScale)
            sliderBaseline = labelBaseline(geometry.thumbY)
        }
        this.motionEnabled = motionEnabled
        return geometry
    }

    fun effect(settings: GlassAlarmSettings, progress: Float, pressScale: Float): androidx.compose.ui.graphics.RenderEffect? {
        update(settings, progress, pressScale)
        return layerEffect
    }

    fun draw(canvas: Canvas, settings: GlassAlarmSettings, progress: Float, pressScale: Float, shimmer: Float) {
        update(settings, progress, pressScale)
        this.shimmer = shimmer
        if (usesNativeCapture) {
            val capture = nativeCapture ?: GlassSceneCapture().also { nativeCapture = it }
            capture.draw(
                canvas, geometry.width.roundToInt(), (240f * geometry.unit).roundToInt(),
                geometry.thumbX(progress), geometry.thumbY, geometry.radius * pressScale,
                sizeCoefficient, scaleCoefficient, blurRadius * geometry.unit,
                requireNotNull(snapshot), drawSource,
            )
        } else drawScene(canvas)
    }

    private fun update(settings: GlassAlarmSettings, progress: Float, pressScale: Float) {
        val revision = settings.revision
        val materialChanged = materialSettings !== settings || materialRevision != revision
        val geometryChanged = uniformGeometry !== geometry
        val progressChanged = frameProgress != progress
        val pressChanged = framePressScale != pressScale
        if (!materialChanged && !geometryChanged && !progressChanged && !pressChanged) return

        if (materialChanged) {
            orangeR = (settings[GlassParameter.ORANGE_R] *
                settings[GlassParameter.LIGHT_R] / GlassParameter.LIGHT_R.defaultValue).coerceIn(0f, 1f)
            orangeG = (settings[GlassParameter.ORANGE_G] *
                settings[GlassParameter.LIGHT_G] / GlassParameter.LIGHT_G.defaultValue).coerceIn(0f, 1f)
            orangeB = (settings[GlassParameter.ORANGE_B] *
                settings[GlassParameter.LIGHT_B] / GlassParameter.LIGHT_B.defaultValue).coerceIn(0f, 1f)
            orangeDimming = settings[GlassParameter.ORANGE_DIMMING]
            lightAngleOffset = settings[GlassParameter.LIGHT_ANGLE]
            sizeCoefficient = settings[GlassParameter.SIZE_COEFFICIENT]
            scaleCoefficient = settings[GlassParameter.SCALE_COEFFICIENT]
            blurRadius = settings[GlassParameter.BLUR_RADIUS]
            uniform(Uniform.LIGHT_ANGLE_OFFSET, lightAngleOffset)
            uniform(Uniform.REFRACTION,
                settings[GlassParameter.THICKNESS], settings[GlassParameter.IOR],
                settings[GlassParameter.CHROMATIC_ABERRATION], settings[GlassParameter.CURVATURE])
            uniform(Uniform.LENS,
                settings[GlassParameter.LENS_DEPTH], settings[GlassParameter.MAGNIFICATION],
                settings[GlassParameter.EDGE_POWER], settings[GlassParameter.RIM_WIDTH])
            uniform(Uniform.REFLECTION,
                settings[GlassParameter.RIM_INTENSITY], settings[GlassParameter.LIGHT_RADIUS],
                settings[GlassParameter.FRESNEL_F0], settings[GlassParameter.AMBIENT_ELEVATION])
            uniform(Uniform.AMBIENT,
                settings[GlassParameter.AMBIENT_RANGE], settings[GlassParameter.AMBIENT_HEIGHT],
                settings[GlassParameter.AMBIENT_STRENGTH], settings[GlassParameter.BODY_LIFT])
            uniform(Uniform.LIGHTING,
                GlassParameter.LIGHT_R.defaultValue, GlassParameter.LIGHT_G.defaultValue,
                GlassParameter.LIGHT_B.defaultValue, settings[GlassParameter.LIGHT_INTENSITY])
            val tint = settings[GlassParameter.GLASS_A]
            uniform(Uniform.GLASS_COLOR,
                if (tint == 0f) 0f else settings[GlassParameter.GLASS_R],
                if (tint == 0f) 0f else settings[GlassParameter.GLASS_G],
                if (tint == 0f) 0f else settings[GlassParameter.GLASS_B], tint)
            uniform(Uniform.CAPTURE, sizeCoefficient, scaleCoefficient, blurRadius)
            val native = scaleCoefficient < 1f || blurRadius > 0f || sizeCoefficient < 4f
            if (usesNativeCapture && !native) nativeCapture?.release()
            usesNativeCapture = native
            uniform(Uniform.OVERLAY_ONLY, if (native) 1f else 0f)
            materialSettings = settings
            materialRevision = revision
        }
        if (geometryChanged) {
            uniform(Uniform.RESOLUTION, geometry.width, 240f * geometry.unit)
            uniform(Uniform.LIGHT, geometry.width / 2f, geometry.lightY,
                geometry.controlWidth / 2f, geometry.controlHeight / 2f)
            uniform(Uniform.DENSITY, geometry.unit)
            uniformGeometry = geometry
        }
        val thumbX = geometry.thumbX(progress)
        if (materialChanged || geometryChanged || progressChanged) {
            val sceneHeight = 240f * geometry.unit
            val coefficient = sizeCoefficient / 4f
            val halfWidth = max(thumbX, geometry.width - thumbX) * coefficient
            val halfHeight = max(geometry.thumbY, sceneHeight - geometry.thumbY) * coefficient
            uniform(Uniform.CAPTURE_BOUNDS,
                (thumbX - halfWidth).coerceAtLeast(0f),
                (geometry.thumbY - halfHeight).coerceAtLeast(0f),
                (thumbX + halfWidth).coerceAtMost(geometry.width),
                (geometry.thumbY + halfHeight).coerceAtMost(sceneHeight))
            brightness = 1f - orangeDimming * ((progress - 0.32f) / 0.68f).coerceIn(0f, 1f)
            uniform(Uniform.BRIGHTNESS, brightness)
            uniform(Uniform.LIGHT_ANGLE, geometry.lightAngle(progress) + lightAngleOffset)
        }
        if (geometryChanged || progressChanged || pressChanged) {
            uniform(Uniform.THUMB, thumbX, geometry.thumbY, geometry.radius, pressScale)
        }
        frameProgress = progress
        framePressScale = pressScale
        if (shaderDirty) {
            val effect = RenderEffect.createRuntimeShaderEffect(shader, "scene")
            snapshot = effect
            layerEffect = if (usesNativeCapture) null else effect.asComposeRenderEffect()
            shaderDirty = false
        }
    }

    private enum class Uniform(val shaderName: String, val components: Int = 4) {
        LIGHT_ANGLE_OFFSET("lightAngleOffset", 1),
        REFRACTION("refraction"), LENS("lens"), REFLECTION("reflection"), AMBIENT("ambient"),
        LIGHTING("lighting"), GLASS_COLOR("glassColor"), CAPTURE("capture", 3),
        OVERLAY_ONLY("overlayOnly", 1), RESOLUTION("resolution", 2), LIGHT("light"),
        DENSITY("density", 1), CAPTURE_BOUNDS("captureBounds"), BRIGHTNESS("brightness", 1),
        LIGHT_ANGLE("lightAngle", 1), THUMB("thumb"),
    }

    private fun uniform(uniform: Uniform, x: Float, y: Float = 0f, z: Float = 0f, w: Float = 0f) {
        val slot = uniform.ordinal * 4
        if (uniformValues[slot] == x && uniformValues[slot + 1] == y &&
            uniformValues[slot + 2] == z && uniformValues[slot + 3] == w) return
        when (uniform.components) {
            1 -> shader.setFloatUniform(uniform.shaderName, x)
            2 -> shader.setFloatUniform(uniform.shaderName, x, y)
            3 -> shader.setFloatUniform(uniform.shaderName, x, y, z)
            else -> shader.setFloatUniform(uniform.shaderName, x, y, z, w)
        }
        uniformValues[slot] = x
        uniformValues[slot + 1] = y
        uniformValues[slot + 2] = z
        uniformValues[slot + 3] = w
        shaderDirty = true
    }

    private fun drawScene(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        fillPaint.color = Color.rgb(
            (255f * orangeR * brightness).roundToInt(),
            (255f * orangeG * brightness).roundToInt(),
            (255f * orangeB * brightness).roundToInt(),
        )
        canvas.drawRoundRect(
            geometry.inset, 0f, geometry.width - geometry.inset, geometry.controlHeight,
            geometry.controlHeight / 2f, geometry.controlHeight / 2f, fillPaint,
        )
        val white = (255f * brightness).roundToInt()
        textPaint.shader = null
        textPaint.color = Color.rgb(white, white, white)
        textPaint.textSize = snoozeTextSize
        textPaint.isFakeBoldText = true
        canvas.drawText(snoozeLabel, geometry.width / 2f, snoozeBaseline, textPaint)

        fillPaint.color = Color.rgb(24, 24, 24)
        canvas.drawRoundRect(
            geometry.inset, geometry.trackTop, geometry.width - geometry.inset,
            geometry.trackTop + geometry.controlHeight,
            geometry.controlHeight / 2f, geometry.controlHeight / 2f, fillPaint,
        )
        textPaint.color = Color.argb(98, 255, 255, 255)
        textPaint.isFakeBoldText = false
        textPaint.textSize = sliderTextSize
        canvas.drawText(sliderLabel, geometry.width / 2f, sliderBaseline, textPaint)
        if (motionEnabled) {
            val band = 48f * geometry.unit
            val x = geometry.inset - band + shimmer * (geometry.controlWidth + band * 2f)
            shimmerMatrix.setRotate(15f)
            shimmerMatrix.postTranslate(x, geometry.thumbY)
            val gradient = requireNotNull(shimmerGradient)
            gradient.setLocalMatrix(shimmerMatrix)
            textPaint.color = Color.WHITE
            textPaint.alpha = 235
            textPaint.shader = gradient
            canvas.drawText(sliderLabel, geometry.width / 2f, sliderBaseline, textPaint)
            textPaint.shader = null
        }
    }

    private fun fitText(label: String, size: Float): Float {
        textPaint.textSize = size
        val width = textPaint.measureText(label)
        if (width > geometry.controlWidth * 0.85f) textPaint.textSize *= geometry.controlWidth * 0.85f / width
        return textPaint.textSize
    }

    private fun labelBaseline(centerY: Float): Float =
        centerY - (textPaint.ascent() + textPaint.descent()) / 2f

    fun release() {
        nativeCapture?.release()
        nativeCapture = null
        snapshot = null
        layerEffect = null
        shaderDirty = true
        materialSettings = null
    }
}

internal class GlassControlGeometry(val width: Float, val unit: Float) {
    val inset = 16f * unit
    val controlWidth = width - inset * 2f
    val controlHeight = 104f * unit
    val trackTop = 128f * unit
    val lightY = controlHeight / 2f
    val thumbY = trackTop + controlHeight / 2f
    val radius = controlHeight * 0.46f
    private val startX = inset + controlHeight * 0.04f + radius
    val travel = controlWidth - controlHeight

    fun thumbX(progress: Float): Float = startX + travel * progress

    fun lightAngle(progress: Float): Float {
        val vertical = (thumbY - lightY).toDouble().coerceAtLeast(1.0)
        val left = (inset - thumbX(progress)).toDouble()
        val right = (width - inset - thumbX(progress)).toDouble()
        val leftSquared = left * left + vertical * vertical
        val rightSquared = right * right + vertical * vertical
        val horizontal = (1.0 / leftSquared - 1.0 / rightSquared) / 2.0
        val downward = (right / (vertical * rightSquared) - left / (vertical * leftSquared)
            + (atan(right / vertical) - atan(left / vertical)) / (vertical * vertical)) / 2.0
        return atan2(horizontal, downward).toFloat()
    }
}
