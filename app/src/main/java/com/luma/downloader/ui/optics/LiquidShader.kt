package com.luma.downloader.ui.optics

/** Jingliu 0.9.1 Compose liquid-glass material.
 *
 * Kyant0/AndroidLiquidGlass (Apache-2.0, kmp @65ab177) informs the layered
 * dock/lens separation, arc-tinted bevel, velocity stretch and press-to-grow lens.
 * QWEA0/Liquid-Glass-Android (MIT, @73e2253) informs the inverse-power edge
 * refraction, per-corner SDF, smooth-min dual-shape fusion, press bulge and
 * saturation-protected sampling.
 *
 * This SOURCE is a Jingliu-side reimplementation for Compose RuntimeShader, not a
 * copy of either demo app. Only the app's own UI material layer is sampled;
 * web/QR/video pixels and input glyphs are drawn above the lens and never warped.
 * The sampled source must stay acyclic (lens never samples itself).
 */
object LiquidShader {
    const val SOURCE = """
        uniform shader scene;
        uniform float2 glassSize;
        uniform float padding;
        uniform float radius;
        uniform float depth;
        uniform float bevel;
        uniform float dispersion;
        uniform float saturation;
        uniform float pressure;
        uniform float2 lightPoint;
        uniform float2 touchPoint;
        uniform float touchStrength;
        uniform float2 fuseCenter;
        uniform float fuseStrength;
        uniform float edgeSoft;
        uniform float2 gravityPoint;
        float sdf(float2 p) {
            float2 q = abs(p - glassSize * 0.5) - glassSize * 0.5 + radius;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        }
        float sminPoly(float a, float b, float k) {
            float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);
            return mix(b, a, h) - k * h * (1.0 - h);
        }
        half4 main(float2 pos) {
            float2 p = pos - float2(padding);
            float distance = sdf(p);
            // Dual-glass smooth-min adhesion (QWEA fusion): second circle fuses with the pill.
            if (fuseStrength > 0.001) {
                float2 fc = fuseCenter * glassSize;
                float fr = min(glassSize.x, glassSize.y) * 0.5;
                float d2 = length(p - fc) - fr;
                float k = clamp(fuseStrength, 0.0, 1.0) * 26.0;
                distance = sminPoly(distance, d2, k);
            }
            if (distance > 1.0) return half4(0.0);
            float2 normal = float2(sdf(p + float2(0.5, 0.0)) - sdf(p - float2(0.5, 0.0)),
                                   sdf(p + float2(0.0, 0.5)) - sdf(p - float2(0.0, 0.5)));
            normal /= max(length(normal), 0.001);
            float inside = max(-distance, 0.0);
            float band = max(bevel, 1.0);
            // QWEA inverse-power edge tail: strong at rim, ~0 one bevel inside.
            float tail = 1.0 / pow(1.0 + inside / max(band * 0.25, 0.5), 2.0);
            tail *= 1.0 - smoothstep(band * 0.65, band, inside);
            // Kyant circular-arc bevel: keeps mid-bevel refraction visible.
            float arcT = clamp(inside / band, 0.0, 1.0);
            float arc = 1.0 - pow(arcT, 1.6);
            tail *= (0.72 + 0.28 * arc);
            float shift = depth * tail * (1.0 + pressure * 0.8);
            // Local touch bulge (QWEA press): finger dents the lens without moving the whole pill.
            if (touchStrength > 0.001) {
                float2 tp = touchPoint * glassSize;
                float dTouch = length(p - tp);
                float sigma = max(min(glassSize.x, glassSize.y) * 0.38, 24.0);
                float fall = exp(-0.5 * (dTouch / sigma) * (dTouch / sigma));
                shift += depth * fall * touchStrength * 0.55;
            }
            float2 center = glassSize * mix(float2(0.5), lightPoint, pressure * 0.22);
            float2 bulged = center + (p - center) / (1.0 + pressure * 0.055);
            float2 base = bulged + float2(padding) - normal * shift;
            float2 fringe = normal * (shift * dispersion);
            // RGB dispersion taps approximate a 7-band spectral fringe without 7 scene fetches.
            half4 c = scene.eval(base);
            half4 red = scene.eval(base - fringe);
            half4 blue = scene.eval(base + fringe);
            half a = c.a;
            half3 rgb = half3(red.r, c.g, blue.b);
            half y = dot(rgb, half3(0.2126, 0.7152, 0.0722));
            rgb = mix(half3(y), rgb, half(saturation));
            // Bidirectional mirror highlight: pointer key + gravity fill (Kyant/QWEA highlights).
            float2 lv = lightPoint * glassSize - p;
            lv /= max(length(lv), 0.001);
            float specKey = pow(max(dot(normal, -lv), 0.0), 6.0) * tail;
            float2 gv = gravityPoint * glassSize - p;
            gv /= max(length(gv), 0.001);
            float specFill = pow(max(dot(normal, -gv), 0.0), 10.0) * tail * 0.55;
            float spec = specKey + specFill;
            rgb += half3(half(spec * (0.025 + pressure * 0.055))) * a;
            rgb = clamp(rgb, half3(0.0), half3(a));
            float soft = clamp(edgeSoft, 0.5, 4.0);
            half mask = half(1.0 - smoothstep(-soft, soft, distance));
            return half4(rgb, a) * mask;
        }
    """
}
