package tech.hezy.glassalarmslider

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class GlassSceneCapture {
    private val source = RenderNode("glass source")
    private val sharp = RenderNode("glass sharp capture").apply {
        setUseCompositingLayer(true, null)
    }
    private val blurred = RenderNode("glass native blur")
    private val atlas = RenderNode("glass sharp/blur atlas")
    private var blurEffect: RenderEffect? = null
    private var blurX = Float.NaN
    private var blurY = Float.NaN

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        thumbX: Float,
        thumbY: Float,
        thumbRadius: Float,
        sizeCoefficient: Float,
        scaleCoefficient: Float,
        blurRadiusPx: Float,
        effect: RenderEffect,
        drawSource: (Canvas) -> Unit,
    ) {
        require(canvas.isHardwareAccelerated) { "GlassSceneCapture requires a hardware canvas" }
        if (width <= 0 || height <= 0) return

        source.record(width, height, drawSource)
        canvas.drawRenderNode(source)

        val halfWidth = max(thumbX, width - thumbX) * (sizeCoefficient / 4f)
        val halfHeight = max(thumbY, height - thumbY) * (sizeCoefficient / 4f)
        val left = max(0f, thumbX - halfWidth)
        val top = max(0f, thumbY - halfHeight)
        val right = min(width.toFloat(), thumbX + halfWidth)
        val bottom = min(height.toFloat(), thumbY + halfHeight)
        val captureWidth = ceil((right - left) * scaleCoefficient).toInt().coerceAtLeast(1)
        val captureHeight = ceil((bottom - top) * scaleCoefficient).toInt().coerceAtLeast(1)
        val scaleX = captureWidth / (right - left)
        val scaleY = captureHeight / (bottom - top)

        sharp.record(captureWidth, captureHeight) {
            it.drawColor(Color.BLACK)
            it.scale(scaleX, scaleY)
            it.translate(-left, -top)
            it.drawRenderNode(source)
        }

        var padX = 0
        var padY = 0
        if (blurRadiusPx > 0f) {
            val radiusX = blurRadiusPx * scaleX
            val radiusY = blurRadiusPx * scaleY
            if (radiusX != blurX || radiusY != blurY) {
                blurEffect = RenderEffect.createBlurEffect(radiusX, radiusY, Shader.TileMode.DECAL)
                blurX = radiusX
                blurY = radiusY
                blurred.setRenderEffect(blurEffect)
            }
            padX = ceil(radiusX * 3f + 2f).toInt()
            padY = ceil(radiusY * 3f + 2f).toInt()
            blurred.record(captureWidth + padX * 2, captureHeight + padY * 2) {
                it.drawColor(Color.BLACK)
                it.translate(padX.toFloat(), padY.toFloat())
                it.drawRenderNode(sharp)
            }
        } else {
            blurred.discardDisplayList()
        }

        atlas.setRenderEffect(effect)
        atlas.record(width, if (blurRadiusPx > 0f) height * 2 else height) {
            it.drawColor(Color.BLACK)
            val sharpSave = it.save()
            try {
                it.clipRect(0, 0, width, height)
                it.translate(left, top)
                it.scale(1f / scaleX, 1f / scaleY)
                it.drawRenderNode(sharp)
            } finally {
                it.restoreToCount(sharpSave)
            }
            if (blurRadiusPx > 0f) {
                val blurSave = it.save()
                try {
                    it.clipRect(0, height, width, height * 2)
                    it.translate(left - padX / scaleX, height + top - padY / scaleY)
                    it.scale(1f / scaleX, 1f / scaleY)
                    it.drawRenderNode(blurred)
                } finally {
                    it.restoreToCount(blurSave)
                }
            }
        }

        val save = canvas.save()
        try {
            val outputRadius = thumbRadius + 2f
            canvas.clipRect(max(0f, thumbX - outputRadius), max(0f, thumbY - outputRadius),
                min(width.toFloat(), thumbX + outputRadius), min(height.toFloat(), thumbY + outputRadius))
            canvas.drawRenderNode(atlas)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    fun release() {
        source.discardDisplayList()
        sharp.discardDisplayList()
        blurred.discardDisplayList()
        atlas.discardDisplayList()
        blurred.setRenderEffect(null)
        atlas.setRenderEffect(null)
        blurEffect = null
        blurX = Float.NaN
        blurY = Float.NaN
    }

    private inline fun RenderNode.record(width: Int, height: Int, draw: (Canvas) -> Unit) {
        setPosition(0, 0, width, height)
        setClipToBounds(true)
        val recording = beginRecording(width, height)
        try {
            draw(recording)
        } finally {
            endRecording()
        }
    }
}
