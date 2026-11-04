package de.myria.island

import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View

internal class LiquidGlassBackdrop(
    private val page: View,
    private val palette: Palette,
    private val reduceMotion: () -> Boolean,
) : Drawable() {
    private val fallback = MeshDrawable(palette)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meshShader by lazy { createShader(lens = false) }
    private var phase = 0f
    private var dockSurface: Drawable? = null
    private val tick = Runnable {
        if (page.isShown && page.windowVisibility == View.VISIBLE && !reduceMotion() &&
            ValueAnimator.areAnimatorsEnabled()
        ) {
            phase = (SystemClock.uptimeMillis() % 16_000L).toFloat() * (2f * Math.PI.toFloat() / 16_000f)
            invalidateSelf()
            dockSurface?.invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val renderer = if (canvas.isHardwareAccelerated) meshShader else null
        if (renderer == null) {
            fallback.setBounds(bounds)
            fallback.phase = phase
            fallback.draw(canvas)
        } else {
            renderer.update(bounds.width().toFloat(), bounds.height().toFloat(), 0f, 0f, 1f, 1f, phase)
            paint.shader = renderer.shader
            canvas.drawRect(bounds, paint)
        }
        unscheduleSelf(tick)
        if (!reduceMotion() && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(tick, SystemClock.uptimeMillis() + 33L)
        }
    }

    fun dockBackground(dock: View): Drawable = object : Drawable() {
        private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lensShader by lazy { createShader(lens = true) }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val renderer = if (canvas.isHardwareAccelerated && page.width > 0 && page.height > 0) lensShader else null
            if (renderer == null) {
                dockPaint.color = if (palette.dark) 0xB52B233B.toInt() else 0xBAF8F5FF.toInt()
            } else {
                renderer.update(page.width.toFloat(), page.height.toFloat(),
                    dock.left.toFloat(), dock.top.toFloat(), w, h, phase)
                dockPaint.color = Color.WHITE
                dockPaint.shader = renderer.shader
            }
            val radius = minOf(w, h) / 2f
            canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), radius, radius, dockPaint)
            dockPaint.shader = null
        }

        override fun getOutline(outline: Outline) {
            outline.setRoundRect(bounds, minOf(bounds.width(), bounds.height()) / 2f)
            outline.alpha = 0.32f
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }.also { dockSurface = it }

    private fun createShader(lens: Boolean): GlassShader? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try {
            GlassShader(palette, lens)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    @TargetApi(33)
    private class GlassShader(palette: Palette, lens: Boolean) {
        private val runtimeShader = RuntimeShader(SOURCE).apply {
            setColorUniform("baseColor", palette.bg)
            setFloatUniform("colorStrength", if (palette.dark) 0.34f else 0.38f)
            setFloatUniform("useLens", if (lens) 1f else 0f)
        }

        val shader: Shader get() = runtimeShader

        fun update(pageWidth: Float, pageHeight: Float, left: Float, top: Float,
                   width: Float, height: Float, phase: Float) {
            runtimeShader.setFloatUniform("pageSize", pageWidth, pageHeight)
            runtimeShader.setFloatUniform("origin", left, top)
            runtimeShader.setFloatUniform("lensSize", width, height)
            runtimeShader.setFloatUniform("phase", phase)
        }

        companion object {
            private const val SOURCE = """
                layout(color) uniform half4 baseColor;
                uniform float2 pageSize;
                uniform float2 origin;
                uniform float2 lensSize;
                uniform float phase;
                uniform float colorStrength;
                uniform float useLens;

                float3 row(float3 a, float3 b, float3 c, float left, float right) {
                    return mix(mix(a, b, left), c, right);
                }

                float3 mesh(float2 pixel) {
                    float2 p = clamp(pixel / pageSize, 0.0, 1.0);
                    float2 middle = float2(0.5 + 0.14 * sin(phase), 0.5 + 0.12 * cos(phase));
                    float2 lo = smoothstep(float2(0.0), middle, p);
                    float2 hi = smoothstep(middle, float2(1.0), p);
                    float3 top = row(float3(0.45, 0.17, 0.78), float3(0.26, 0.29, 0.90),
                        float3(0.16, 0.55, 0.98), lo.x, hi.x);
                    float3 center = row(float3(0.96, 0.29, 0.63), float3(0.05, 0.78, 0.84),
                        float3(0.53, 0.24, 0.90), lo.x, hi.x);
                    float3 bottom = row(float3(0.98, 0.55, 0.29), float3(0.85, 0.27, 0.65),
                        float3(0.31, 0.22, 0.77), lo.x, hi.x);
                    return mix(float3(baseColor.rgb), row(top, center, bottom, lo.y, hi.y), colorStrength);
                }

                half4 main(float2 pixel) {
                    if (useLens < 0.5) {
                        return half4(mesh(pixel), 1.0);
                    }

                    float radius = lensSize.y * 0.5;
                    float span = max(lensSize.x * 0.5 - radius, 0.0);
                    float2 local = pixel - lensSize * 0.5;
                    float2 normal = local - float2(clamp(local.x, -span, span), 0.0);
                    float edgeLength = length(normal);
                    float edge = clamp(edgeLength / radius, 0.0, 1.0);
                    float2 direction = normal / max(edgeLength, 1.0);
                    float2 sampleAt = pixel + origin - direction * radius * 0.42 *
                        smoothstep(0.2, 1.0, edge);
                    float2 spread = direction * radius * 0.12;
                    float3 color = mix(mesh(sampleAt),
                        (mesh(sampleAt - spread) + mesh(sampleAt + spread)) * 0.5, 0.38);
                    color = mix(color, float3(baseColor.rgb), 0.18);
                    color *= 1.0 - 0.13 * smoothstep(0.84, 1.0, edge);
                    float light = max(dot(direction, float2(-0.45, -0.89)), 0.0);
                    float glint = pow(light, 8.0) * smoothstep(0.76, 1.0, edge);
                    return half4(mix(color, float3(1.0), 0.15 * glint), 1.0);
                }
            """
        }
    }
}
