package de.myria.island

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import kotlin.math.max
import kotlin.math.min

internal class IslandGlass(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val bounds = RectF()
    private val renderer by lazy {
        if (Build.VERSION.SDK_INT >= 33) try {
            Renderer(density)
        } catch (e: IllegalArgumentException) {
            Log.w("IslandGlass", "Using Canvas glass", e)
            null
        } else null
    }

    fun draw(
        canvas: Canvas,
        main: RectF,
        radius: Float,
        bubble: RectF?,
        bubbleRadius: Float,
        bond: Float,
        cfg: IslandConfig,
        accent: Int,
        readability: Float,
        travel: Float,
        touch: Float,
        touchX: Float,
        shadow: Paint,
    ) {
        val mainRadius = radius.coerceIn(0f, min(main.width(), main.height()) * 0.5f)
        val sideRadius = bubble?.let { bubbleRadius.coerceIn(0f, min(it.width(), it.height()) * 0.5f) } ?: 0f
        val opacity = cfg.glassOpacity.coerceIn(0, 100) / 100f
        val idleDarkness = 0.09f + 0.62f * opacity
        val contentDarkness = 0.38f + 0.54f * opacity
        val darkness = idleDarkness + (contentDarkness - idleDarkness) * readability
        val shine = cfg.glassShine.coerceIn(0, 100) / 100f
        val tint = cfg.glassTint.coerceIn(0, 100) / 100f
        val gpu = if (canvas.isHardwareAccelerated) renderer else null
        val bridge = (2f + 12f * bond) * density

        path.rewind()
        path.addRoundRect(main, mainRadius, mainRadius, Path.Direction.CW)
        bounds.set(main)
        if (bubble != null) {
            path.addRoundRect(bubble, sideRadius, sideRadius, Path.Direction.CW)
            if (bubble.left > main.right && bubble.left - main.right < bridge * 0.45f) {
                val half = min(bridge * 0.38f, bubble.height() * 0.25f)
                path.addRoundRect(main.right - half, main.centerY() - half,
                    bubble.left + half, main.centerY() + half, half, half, Path.Direction.CW)
            }
            bounds.union(bubble)
        }
        shadow.color = Color.TRANSPARENT
        canvas.drawPath(path, shadow)
        shadow.clearShadowLayer()

        if (gpu != null) {
            gpu.update(main, mainRadius, bubble, sideRadius, bridge, cfg, accent, darkness, shine, tint,
                travel, touch, touchX)
            bounds.inset(-bridge * 0.28f - 1f, -bridge * 0.28f - 1f)
            paint.shader = gpu.shader
            paint.color = Color.WHITE
            canvas.drawRect(bounds, paint)
            paint.shader = null
        } else {
            val body = Color.argb((darkness * 255).toInt(), (Color.red(accent) * tint * 0.16f).toInt(),
                (Color.green(accent) * tint * 0.16f).toInt(), (Color.blue(accent) * tint * 0.16f).toInt())
            paint.color = body
            canvas.drawPath(path, paint)
            if (cfg.glassBorder && shine > 0f) {
                val reflected = if (cfg.glassRainbow) 0xBFE8FF else 0xFFFFFF
                val alpha = ((0.08f + 0.12f * touch + 0.05f * kotlin.math.abs(travel)) * shine * 255).toInt()
                val glint = (alpha shl 24) or reflected
                paint.color = Color.WHITE
                paint.shader = LinearGradient(main.left, main.top, main.left + 8f * density,
                    main.top + (1.8f + cfg.glassBorderWidth.coerceIn(3, 30) * 0.08f) * density,
                    glint, reflected, Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint)
                paint.shader = null
            }
        }
    }

    @TargetApi(33)
    private class Renderer(density: Float) {
        private val program = RuntimeShader(SOURCE).apply { setFloatUniform("density", density) }
        val shader: Shader get() = program

        fun update(
            main: RectF, radius: Float, bubble: RectF?, sideRadius: Float, bridge: Float,
            cfg: IslandConfig, accent: Int, darkness: Float, shine: Float, tint: Float,
            travel: Float, touch: Float, touchX: Float,
        ) {
            program.setFloatUniform("capsule", main.centerX(), main.centerY(), main.width() / 2, main.height() / 2)
            val other = bubble ?: main
            program.setFloatUniform("satellite", other.centerX(), other.centerY(), other.width() / 2, other.height() / 2)
            program.setFloatUniform("shape", radius, sideRadius, bridge, if (bubble != null) 1f else 0f)
            program.setColorUniform("accent", accent)
            program.setFloatUniform("material", darkness, shine, tint,
                0.7f + cfg.glassBorderWidth.coerceIn(3, 30) * 0.10f)
            program.setFloatUniform("motion", travel, touch, touchX, if (cfg.glassBorder) 1f else 0f)
            program.setFloatUniform("spectrum", if (cfg.glassRainbow) 1f else 0f)
        }

        companion object {
            private const val SOURCE = """
                uniform float4 capsule;
                uniform float4 satellite;
                uniform float4 shape;
                layout(color) uniform half4 accent;
                uniform float4 material;
                uniform float4 motion;
                uniform float spectrum;
                uniform float density;

                float distanceToBox(float2 pos, float4 box, float radius) {
                    float2 q = abs(pos - box.xy) - box.zw + radius;
                    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
                }

                float surface(float2 pos) {
                    float a = distanceToBox(pos, capsule, shape.x);
                    if (shape.w < 0.5) return a;
                    float b = distanceToBox(pos, satellite, shape.y);
                    float blend = max(0.0, shape.z - abs(a - b)) / shape.z;
                    return min(a, b) - 0.25 * shape.z * blend * blend;
                }

                half4 main(float2 pos) {
                    float signedDist = surface(pos);
                    float coverage = 1.0 - smoothstep(-0.65, 0.65, signedDist);
                    if (coverage <= 0.0) return half4(0.0);

                    float depth = max(0.0, -signedDist) / density;
                    float energy = abs(motion.x);
                    float bevel = (1.0 - smoothstep(0.4, material.w + 1.8 + energy * 1.2, depth)) * motion.w;
                    float bodyAlpha = clamp(material.x + bevel * (0.11 + 0.04 * energy), 0.0, 0.94);
                    float3 body = float3(accent.rgb) * material.z * 0.17;
                    float3 rgb = body * bodyAlpha;

                    if (bevel > 0.0) {
                        float2 dx = float2(density * 0.6, 0.0);
                        float2 dy = float2(0.0, density * 0.6);
                        float2 g = float2(surface(pos + dx) - surface(pos - dx),
                            surface(pos + dy) - surface(pos - dy));
                        float2 outward = g / max(length(g), 0.001);
                        float side = mix(-0.64 + motion.x * 0.31, motion.z * 0.75, motion.y);
                        float2 light = normalize(float2(side, -0.8));
                        float axial = (pos.x - capsule.x) / max(capsule.z, 1.0);
                        float spot = axial - mix(-0.54 + 0.47 * motion.x, motion.z, motion.y);
                        float key = pow(max(0.0, dot(outward, light)), 13.0) * exp(-spot * spot * 1.8);
                        float fill = pow(max(0.0, dot(outward, float2(0.88, 0.47))), 18.0);
                        float edgeLight = material.y * bevel * (key * (0.24 + 0.20 * motion.y)
                            + fill * 0.072);
                        float band = smoothstep(0.35, 1.2, depth) * bevel;
                        float colorLight = spectrum * material.y * band * (0.07 + 0.08 * energy + 0.07 * motion.y)
                            * (key + fill * 0.7);
                        float3 spectral = mix(float3(0.25, 0.70, 1.0), float3(0.98, 0.42, 0.70),
                            clamp(0.5 + 0.5 * outward.x, 0.0, 1.0));
                        rgb = mix(rgb, spectral, colorLight);
                        bodyAlpha += (1.0 - bodyAlpha) * colorLight;
                        rgb = mix(rgb, float3(0.97, 0.98, 1.0), edgeLight);
                        bodyAlpha += (1.0 - bodyAlpha) * edgeLight;
                    }
                    return half4(rgb * coverage, bodyAlpha * coverage);
                }
            """
        }
    }
}
