package com.luma.downloader.ui.optics

/** Original rounded-rectangle SDF lens. Concepts: Kyant Backdrop and QWEA0 LiquidGlass.
 * No copied platform shaders. This is an approximation, not Apple's optical engine.
 * Only the backing texture is filtered; text, icons and editable content are drawn afterwards.
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

        float sdf(float2 p) {
            float2 q = abs(p - glassSize * 0.5) - glassSize * 0.5 + radius;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
        }
        half4 main(float2 pos) {
            float2 p = pos - float2(padding);
            float distance = sdf(p);
            if (distance > 1.0) return half4(0.0);
            float2 gradient = float2(sdf(p + float2(0.5, 0.0)) - sdf(p - float2(0.5, 0.0)),
                                     sdf(p + float2(0.0, 0.5)) - sdf(p - float2(0.0, 0.5)));
            gradient /= max(length(gradient), 0.001);
            float t = clamp(1.0 + distance / max(bevel, 1.0), 0.0, 1.0);
            float smoothT = t * t * (3.0 - 2.0 * t);
            float shift = depth * smoothT * smoothT;
            float2 base = pos - gradient * shift;
            float2 fringe = gradient * (shift * dispersion);
            half4 c = scene.eval(base);
            half4 red = scene.eval(base - fringe);
            half4 blue = scene.eval(base + fringe);
            half a = c.a;
            half3 rgb = half3(red.r, c.g, blue.b);
            half y = dot(rgb, half3(0.2126, 0.7152, 0.0722));
            rgb = clamp(mix(half3(y), rgb, half(saturation)), half3(0.0), half3(a));
            half mask = half(1.0 - smoothstep(-0.5, 0.5, distance));
            return half4(rgb, a) * mask;
        }
    """
}
