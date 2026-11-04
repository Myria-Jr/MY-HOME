package de.myria.island

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RadialGradient
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
        environment: GlassEnvironment,
        lightInk: Boolean,
        readability: Float,
        travel: Float,
        touch: Float,
        touchX: Float,
        shadow: Paint,
    ) {
        val mainRadius = radius.coerceIn(0f, min(main.width(), main.height()) * 0.5f)
        val sideRadius = bubble?.let { bubbleRadius.coerceIn(0f, min(it.width(), it.height()) * 0.5f) } ?: 0f
        val opacity = cfg.glassOpacity.coerceIn(0, 100) / 100f
        val clear = cfg.glassMaterial == IslandConfig.GLASS_CLEAR
        val bodyAlpha = when {
            clear -> {
                val midtone = (1f - kotlin.math.abs(environment.luminance - 0.19f) / 0.16f).coerceIn(0f, 1f)
                0.018f + 0.32f * opacity + readability * (0.055f + 0.16f * midtone)
            }
            lightInk -> 0.09f + 0.62f * opacity + (0.29f - 0.08f * opacity) * readability
            else -> 0.20f + 0.53f * opacity + 0.18f * readability
        }
        val base = if (lightInk) 0xFF07090D.toInt() else 0xFFF7F9FD.toInt()
        val shine = cfg.glassShine.coerceIn(0, 100) / 100f
        val tint = cfg.glassTint.coerceIn(0, 100) / 100f
        val body = Palette.mixC(base, accent, tint * if (clear) 0.08f else 0.16f)
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
            gpu.update(main, mainRadius, bubble, sideRadius, bridge, cfg, body, bodyAlpha, shine,
                environment, travel, touch, touchX)
            bounds.inset(-bridge * 0.28f - 1f, -bridge * 0.28f - 1f)
            paint.shader = gpu.shader
            paint.color = Color.WHITE
            canvas.drawRect(bounds, paint)
            paint.shader = null
        } else {
            paint.color = (body and 0xFFFFFF) or ((bodyAlpha * 255).toInt() shl 24)
            canvas.drawPath(path, paint)
            if (cfg.glassBorder && shine > 0f) {
                val light = if (cfg.glassAdaptiveRim) environment.luminance else 0.28f
                val reflected = when {
                    cfg.glassAdaptiveRim -> Palette.mixC(Color.WHITE, environment.primary, 0.30f)
                    cfg.glassRainbow -> 0xFFBFE8FF.toInt()
                    else -> Color.WHITE
                } and 0xFFFFFF
                val alpha = ((0.14f + 0.12f * touch) * (1f - 0.42f * light) * shine * 255).toInt()
                val glint = (alpha shl 24) or reflected
                val spot = (-0.64f + travel * 0.31f) * (1f - touch) + touchX * touch
                paint.color = Color.WHITE
                paint.shader = RadialGradient(main.centerX() + main.width() * 0.5f * spot, main.top,
                    (7f + cfg.glassBorderWidth.coerceIn(3, 30) * 0.24f) * density,
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
            cfg: IslandConfig, body: Int, bodyAlpha: Float, shine: Float,
            environment: GlassEnvironment, travel: Float, touch: Float, touchX: Float,
        ) {
            program.setFloatUniform("capsule", main.centerX(), main.centerY(), main.width() / 2, main.height() / 2)
            val other = bubble ?: main
            program.setFloatUniform("satellite", other.centerX(), other.centerY(), other.width() / 2, other.height() / 2)
            program.setFloatUniform("shape", radius, sideRadius, bridge, if (bubble != null) 1f else 0f)
            program.setColorUniform("bodyColor", body)
            program.setColorUniform("ambientLeft", environment.primary)
            program.setColorUniform("ambientRight", environment.secondary)
            program.setFloatUniform("ambientLevel", environment.primaryLuminance, environment.secondaryLuminance)
            program.setFloatUniform("material", bodyAlpha, shine, if (cfg.glassAdaptiveRim) 1f else 0f,
                0.7f + cfg.glassBorderWidth.coerceIn(3, 30) * 0.10f)
            program.setFloatUniform("motion", travel, touch, touchX, if (cfg.glassBorder) 1f else 0f)
            program.setFloatUniform("spectrum", if (cfg.glassRainbow) 1f else 0f)
        }

        companion object {
            private const val SOURCE = """
                uniform float4 capsule;
                uniform float4 satellite;
                uniform float4 shape;
                layout(color) uniform half4 bodyColor;
                layout(color) uniform half4 ambientLeft;
                layout(color) uniform half4 ambientRight;
                uniform float2 ambientLevel;
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
                    float axial = (pos.x - capsule.x) / max(capsule.z, 1.0);
                    float across = smoothstep(-1.0, 1.0, axial);
                    float ambient = mix(0.28, mix(ambientLevel.x, ambientLevel.y, across), material.z);
                    float contrast = smoothstep(0.12, 0.68, ambient);
                    float shade = bevel * mix(0.065, 0.24, contrast);
                    float bodyAlpha = material.x + (1.0 - material.x) * shade;
                    float3 rgb = float3(bodyColor.rgb) * material.x * (1.0 - shade);

                    if (bevel > 0.0) {
                        float2 dx = float2(density * 0.6, 0.0);
                        float2 dy = float2(0.0, density * 0.6);
                        float2 g = float2(surface(pos + dx) - surface(pos - dx),
                            surface(pos + dy) - surface(pos - dy));
                        float2 outward = g / max(length(g), 0.001);
                        float side = mix(-0.64 + motion.x * 0.31, motion.z * 0.75, motion.y);
                        float2 light = normalize(float2(side, -0.8));
                        float spot = axial - mix(-0.54 + 0.47 * motion.x, motion.z, motion.y);
                        float key = pow(max(0.0, dot(outward, light)), 13.0) * exp(-spot * spot * 1.8);
                        float fill = pow(max(0.0, dot(outward, float2(0.88, 0.47))), 18.0);
                        float edgeLight = material.y * bevel * mix(1.12, 0.68, contrast)
                            * (key * (0.24 + 0.20 * motion.y) + fill * 0.072);
                        float band = smoothstep(0.35, 1.2, depth) * bevel;
                        float colorLight = spectrum * material.y * band * (0.07 + 0.08 * energy + 0.07 * motion.y)
                            * (key + fill * 0.7);
                        float3 spectral = mix(float3(0.25, 0.70, 1.0), float3(0.98, 0.42, 0.70),
                            clamp(0.5 + 0.5 * outward.x, 0.0, 1.0));
                        rgb = mix(rgb, spectral, colorLight);
                        bodyAlpha += (1.0 - bodyAlpha) * colorLight;
                        float3 reflection = mix(float3(ambientLeft.rgb), float3(ambientRight.rgb), across);
                        float3 glint = mix(float3(0.97, 0.98, 1.0), reflection, material.z * 0.32);
                        rgb = mix(rgb, glint, edgeLight);
                        bodyAlpha += (1.0 - bodyAlpha) * edgeLight;
                    }
                    return half4(rgb * coverage, bodyAlpha * coverage);
                }
            """
        }
    }
}
