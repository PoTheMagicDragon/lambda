#version 330
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    float u_GlowRadius = TextureMat[0][0]; // in pixels
    float u_GlowIntensity = TextureMat[0][1];
    vec4 u_GlowColor = ColorModulator;
    
    vec2 texelSize = 1.0 / textureSize(Sampler0, 0);
    vec4 center = texture(Sampler0, v_TexCoord);
    
    // Draw the original UI if there's any data
    if (center.a > 0.0) {
        fragColor = center;
        return;
    }
    
    int maxRadiusInt = int(ceil(u_GlowRadius));
    if (maxRadiusInt <= 0) {
        discard;
    }
    
    float minDist = u_GlowRadius + 1.0;
    int step = max(1, maxRadiusInt / 12);
    int bestDx = 0, bestDy = 0;
    
    // Coarse search
    for (int dy = -maxRadiusInt; dy <= maxRadiusInt; dy += step) {
        for (int dx = -maxRadiusInt; dx <= maxRadiusInt; dx += step) {
            float dist = length(vec2(float(dx), float(dy)));
            if (dist > u_GlowRadius || dist >= minDist) continue;
            
            vec2 sampleCoord = v_TexCoord + vec2(float(dx), float(dy)) * texelSize;
            if (texture(Sampler0, sampleCoord).a > 0.0) {
                minDist = dist;
                bestDx = dx;
                bestDy = dy;
            }
        }
    }
    
    // Fine search around the best coarse result
    if (step > 1 && minDist <= u_GlowRadius + 1.0) {
        int rMin = step;
        for (int dy = bestDy - rMin; dy <= bestDy + rMin; dy++) {
            for (int dx = bestDx - rMin; dx <= bestDx + rMin; dx++) {
                float dist = length(vec2(float(dx), float(dy)));
                if (dist > u_GlowRadius || dist >= minDist) continue;
                
                vec2 sampleCoord = v_TexCoord + vec2(float(dx), float(dy)) * texelSize;
                if (texture(Sampler0, sampleCoord).a > 0.0) {
                    minDist = dist;
                }
            }
        }
    }
    
    if (minDist <= u_GlowRadius) {
        float glowFactor = 1.0 - (minDist / u_GlowRadius);
        
        // Apply an exponential curve for a very smooth, long tail falloff
        glowFactor = pow(glowFactor, 2.5);
        
        // Create an animated gradient between Blue and Pink
        vec3 colorBlue = vec3(0.0, 0.5, 1.0);
        vec3 colorPink = vec3(1.0, 0.2, 0.6);
        
        // Animate based on time (ModelOffset.x) and screen coordinates
        float time = ModelOffset.x;
        float gradientWave = sin(time * 2.0 + v_TexCoord.x * 5.0 + v_TexCoord.y * 3.0);
        float mixFactor = (gradientWave + 1.0) * 0.5;
        
        vec3 gradientColor = mix(colorBlue, colorPink, mixFactor);
        
        // Clamp alpha to exactly 0.0-1.0 to prevent OpenGL blend mode math corruption
        float alpha = clamp(glowFactor * u_GlowIntensity, 0.0, 1.0);
        
        // Pre-multiply RGB by alpha to prevent sRGB desaturation
        fragColor = vec4(gradientColor * alpha, alpha);
    } else {
        discard;
    }
}
