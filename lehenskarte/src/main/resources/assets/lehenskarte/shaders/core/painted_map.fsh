#version 330

// Sampler0 = gemalte Karte von karte.ottonien.com, Sampler1 = Kopie des Bildschirms mit Xaeros Kacheln.
// Wo Xaeros Karte schwarz ist (unerkundet), erscheint die gemalte Karte; am Übergang weich überblendet.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

float explored(vec2 uv) {
    vec3 c = texture(Sampler1, uv).rgb;
    return step(0.025, max(c.r, max(c.g, c.b)));
}

void main() {
    vec2 px = 1.0 / vec2(textureSize(Sampler1, 0));
    vec2 uv = gl_FragCoord.xy * px;
    float self = explored(uv);
    float sum = 0.0;
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            sum += explored(uv + vec2(dx, dy) * 3.0 * px);
        }
    }
    float unexplored = 1.0 - sum / 9.0;
    if (self > 0.5 && unexplored < 0.01) discard;
    float alpha = self > 0.5 ? unexplored * 0.6 : 1.0;
    vec3 map = texture(Sampler0, texCoord0).rgb * vertexColor.rgb;
    fragColor = vec4(map, alpha);
}
