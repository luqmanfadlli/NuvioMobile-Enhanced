//!DESC Nuvio-Line-Sharpen
//!HOOK MAIN
//!BIND HOOKED
#define NUVIO_SHARPEN_STRENGTH 4.0
float nuvio_luma(vec3 rgb) { return dot(rgb, vec3(0.2126, 0.7152, 0.0722)); }
float nuvio_tap(float x, float y) { return nuvio_luma(HOOKED_texOff(vec2(x, y)).rgb); }
vec4 hook() {
    vec4 center = HOOKED_texOff(vec2(0.0, 0.0));
    float c = nuvio_luma(center.rgb);
    float n = nuvio_tap(0.0, -1.0);
    float s = nuvio_tap(0.0, 1.0);
    float w = nuvio_tap(-1.0, 0.0);
    float e = nuvio_tap(1.0, 0.0);
    float nw = nuvio_tap(-1.0, -1.0);
    float ne = nuvio_tap(1.0, -1.0);
    float sw = nuvio_tap(-1.0, 1.0);
    float se = nuvio_tap(1.0, 1.0);
    float n2 = nuvio_tap(0.0, -2.0);
    float s2 = nuvio_tap(0.0, 2.0);
    float w2 = nuvio_tap(-2.0, 0.0);
    float e2 = nuvio_tap(2.0, 0.0);
    float blur = (36.0 * c + 24.0 * (n + s + w + e) + 16.0 * (nw + ne + sw + se) + 6.0 * (n2 + s2 + w2 + e2)) / 220.0;
    float lo = min(c, min(min(min(n, s), min(w, e)), min(min(nw, ne), min(sw, se))));
    float hi = max(c, max(max(max(n, s), max(w, e)), max(max(nw, ne), max(sw, se))));
    float sharpened = clamp(c + NUVIO_SHARPEN_STRENGTH * (c - blur), lo, hi);
    return vec4(clamp(center.rgb + (sharpened - c), 0.0, 1.0), center.a);
}
