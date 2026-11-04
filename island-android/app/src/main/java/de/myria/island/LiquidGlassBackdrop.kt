package de.myria.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class GlassDockLayout(
    context: Context,
    palette: Palette,
    backdrop: LiquidGlassBackdrop,
    dock: View,
    private val reduceMotion: () -> Boolean,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).roundToInt()

    private var zoom = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val icon = GlyphIcon(context, palette.label, Glyph.SEARCH, palette, bare = true).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val lens = object : FrameLayout(context) {
        override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

        override fun setPressed(pressed: Boolean) {
            val changed = isPressed != pressed
            super.setPressed(pressed)
            if (changed) moveLens(pressed || zoom)
        }
    }.apply {
        contentDescription = "Lupe"
        isFocusable = true
        addView(icon, LayoutParams(dp(29f), dp(29f), Gravity.CENTER))
        setOnClickListener {
            zoom = !zoom
            isSelected = zoom
            moveLens(zoom || isPressed)
        }
    }

    init {
        addView(dock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(18f)
        })
        addView(lens, LayoutParams(dp(62f), dp(70f), Gravity.BOTTOM or Gravity.RIGHT).apply {
            rightMargin = dp(18f)
            bottomMargin = dp(79f)
        })
        background = backdrop.dockBackground(this, dock, lens) { progress }
    }

    private fun moveLens(expanded: Boolean) {
        val target = if (expanded) 1f else 0f
        animator?.cancel()
        animator = null
        if (progress == target) return
        if (reduceMotion() || !ValueAnimator.areAnimatorsEnabled() || !isShown) {
            drawProgress(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 290L
            interpolator = PathInterpolator(0.2f, 0.78f, 0.25f, 1f)
            addUpdateListener { drawProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun drawProgress(value: Float) {
        progress = value
        icon.translationX = lensOffsetX(density, value)
        icon.translationY = lensOffsetY(density, value)
        background?.invalidateSelf()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        drawProgress(if (zoom) 1f else 0f)
        super.onDetachedFromWindow()
    }
}

private fun lensOffsetX(density: Float, progress: Float) = -4f * density * progress
private fun lensOffsetY(density: Float, progress: Float) = 23f * density * progress

internal class LiquidGlassBackdrop(
    private val page: View,
    private val palette: Palette,
    private val reduceMotion: () -> Boolean,
) : Drawable() {
    private val density = page.resources.displayMetrics.density
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
            renderer.updateScene(bounds.width().toFloat(), bounds.height().toFloat(), phase)
            paint.shader = renderer.shader
            canvas.drawRect(bounds, paint)
        }
        unscheduleSelf(tick)
        if (!reduceMotion() && ValueAnimator.areAnimatorsEnabled()) {
            scheduleSelf(tick, SystemClock.uptimeMillis() + 33L)
        }
    }

    fun dockBackground(surface: View, dock: View, lens: View, progress: () -> Float): Drawable =
        object : Drawable() {
            private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val silhouette = Path()
            private val lensShader by lazy { createShader(lens = true) }

            override fun draw(canvas: Canvas) {
                if (bounds.isEmpty || dock.width <= 0 || lens.width <= 0 ||
                    page.width <= 0 || page.height <= 0
                ) return

                val t = progress()
                val cx = lens.left + lens.width * 0.5f + lensOffsetX(density, t)
                val cy = lens.top + lens.height * 0.5f + lensOffsetY(density, t)
                val radius = (23f + 6f * t) * density
                val renderer = if (canvas.isHardwareAccelerated) lensShader else null
                if (renderer != null) {
                    renderer.updateScene(page.width.toFloat(), page.height.toFloat(), phase)
                    renderer.updateOptics(surface, dock, cx, cy, radius, t)
                    dockPaint.color = Color.WHITE
                    dockPaint.shader = renderer.shader
                    canvas.drawRect(bounds, dockPaint)
                    dockPaint.shader = null
                    return
                }

                val dockRadius = minOf(dock.width, dock.height) * 0.5f
                silhouette.rewind()
                silhouette.addRoundRect(dock.left.toFloat(), dock.top.toFloat(),
                    dock.right.toFloat(), dock.bottom.toFloat(), dockRadius, dockRadius, Path.Direction.CW)
                silhouette.addCircle(cx, cy, radius, Path.Direction.CW)
                dockPaint.color = if (palette.dark) 0xD92B233B.toInt() else 0xD9F8F5FF.toInt()
                canvas.drawPath(silhouette, dockPaint)
            }

            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }.also { dockSurface = it }

    private fun createShader(lens: Boolean): GlassShader? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try {
            GlassShader(palette, density, lens)
        } catch (error: IllegalArgumentException) {
            android.util.Log.w("LiquidGlass", "Shader konnte nicht geladen werden", error)
            null
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    @TargetApi(33)
    private class GlassShader(palette: Palette, density: Float, lens: Boolean) {
        private val runtimeShader = RuntimeShader(SOURCE).apply {
            setColorUniform("baseColor", palette.bg)
            setFloatUniform("colorStrength", if (palette.dark) 0.34f else 0.38f)
            setFloatUniform("darkMode", if (palette.dark) 1f else 0f)
            setFloatUniform("density", density)
            setFloatUniform("useLens", if (lens) 1f else 0f)
        }

        val shader: Shader get() = runtimeShader

        fun updateScene(width: Float, height: Float, phase: Float) {
            runtimeShader.setFloatUniform("pageSize", width, height)
            runtimeShader.setFloatUniform("phase", phase)
        }

        fun updateOptics(surface: View, dock: View, cx: Float, cy: Float, radius: Float, morph: Float) {
            runtimeShader.setFloatUniform("origin", surface.left.toFloat(), surface.top.toFloat())
            runtimeShader.setFloatUniform("dockRect", dock.left + dock.width * 0.5f,
                dock.top + dock.height * 0.5f, dock.width.toFloat(), dock.height.toFloat())
            runtimeShader.setFloatUniform("loupe", cx, cy, radius)
            runtimeShader.setFloatUniform("morph", morph)
        }

        companion object {
            private const val SOURCE = """
                layout(color) uniform half4 baseColor;
                uniform float2 pageSize;
                uniform float2 origin;
                uniform float4 dockRect;
                uniform float3 loupe;
                uniform float density;
                uniform float phase;
                uniform float colorStrength;
                uniform float darkMode;
                uniform float useLens;
                uniform float morph;

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
                    float3 color = mix(float3(baseColor.rgb), row(top, center, bottom, lo.y, hi.y), colorStrength);
                    float wave = pixel.y / density + 17.0 * sin(p.x * 6.28 + phase)
                        + 8.0 * sin(p.x * 12.56 - phase);
                    float stripe = abs(fract(wave / 12.0) - 0.5) * 12.0;
                    float lines = (1.0 - smoothstep(0.1, 0.85, stripe)) * smoothstep(0.62, 0.95, p.y);
                    float3 ink = mix(float3(0.20, 0.12, 0.38), float3(0.72, 0.84, 1.0), darkMode);
                    return mix(color, ink, lines * 0.14);
                }

                float field(float2 point) {
                    float radius = dockRect.w * 0.5;
                    float span = max(dockRect.z * 0.5 - radius, 0.0);
                    float2 local = point - dockRect.xy;
                    local.x -= clamp(local.x, -span, span);
                    float a = length(local) - radius;
                    float b = length(point - loupe.xy) - loupe.z;
                    float blend = mix(5.0, 25.0, morph) * density;
                    float weight = max(0.0, blend - abs(a - b)) / blend;
                    return min(a, b) - 0.25 * blend * weight * weight;
                }

                float2 bend(float3 normal, float ior, float thickness) {
                    float eta = 1.0 / ior;
                    float factor = sqrt(1.0 - eta * eta * (1.0 - normal.z * normal.z)) - eta * normal.z;
                    return -normal.xy * factor * thickness / (eta + factor * normal.z);
                }

                half4 main(float2 pixel) {
                    if (useLens < 0.5) {
                        return half4(mesh(pixel), 1.0);
                    }
                    float d = field(pixel);
                    if (d > 20.0 * density) {
                        return half4(0.0);
                    }

                    float coverage = 1.0 - smoothstep(-0.65, 0.65, d);
                    float shadowDist = max(0.0, field(pixel - float2(0.0, 4.0 * density)) / density);
                    float shadow = 0.24 * exp(-0.025 * shadowDist * shadowDist)
                        * (1.0 - smoothstep(11.0, 15.0, shadowDist));
                    if (coverage <= 0.0) {
                        return half4(0.0, 0.0, 0.0, shadow);
                    }

                    float depth = max(0.0, -d) / density;
                    float2 dx = float2(0.75 * density, 0.0);
                    float2 dy = float2(0.0, 0.75 * density);
                    float2 delta = float2(field(pixel + dx) - field(pixel - dx),
                        field(pixel + dy) - field(pixel - dy));
                    float2 outward = delta / max(length(delta), 0.001);
                    float bevel = 1.0 - smoothstep(0.0, 17.0, depth);
                    float lensWeight = 1.0 - smoothstep(loupe.z * 0.58, loupe.z + 7.0 * density,
                        length(pixel - loupe.xy));
                    float3 normal = normalize(float3(outward * bevel * (1.05 + 0.48 * lensWeight), 1.0));
                    float thickness = (20.0 + 13.0 * lensWeight * morph) * density;
                    float2 focus = (loupe.xy - pixel) * lensWeight * (0.17 + 0.18 * morph);
                    float2 green = bend(normal, 1.52, thickness);
                    float dispersion = bevel * (3.0 + 2.0 * morph);
                    float2 sampleAt = pixel + origin + focus + green;
                    float2 red = (bend(normal, 1.50, thickness) - green) * dispersion;
                    float2 blue = (bend(normal, 1.55, thickness) - green) * dispersion;
                    float3 color = float3(mesh(sampleAt + red).r, mesh(sampleAt).g, mesh(sampleAt + blue).b);
                    float2 spread = float2(-outward.y, outward.x) * density * 1.5;
                    color = mix(color, (mesh(sampleAt - spread) + mesh(sampleAt + spread)) * 0.5, 0.20);
                    color = mix(color, float3(baseColor.rgb), mix(0.42, 0.33, darkMode));
                    color *= 1.0 - 0.16 * bevel * max(0.0, outward.y);

                    float light = max(0.0, dot(outward, float2(-0.60, -0.80)));
                    float rim = smoothstep(0.12, 0.8, depth) * (1.0 - smoothstep(0.9, 2.8, depth));
                    color = mix(color, float3(1.0), 0.27 * rim * pow(light, 14.0));
                    return half4(color * coverage, coverage + shadow * (1.0 - coverage));
                }
            """
        }
    }
}
