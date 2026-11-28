package abilityadditions.core;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.graphics.*;

public class TimeStopWarpShader{

    private static final int MAX_ZONES = 2;

    private static final String FRAG_SHADER =
        "#ifdef GL_ES\n" +
        "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
        "precision highp float;\n" +
        "#else\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "#endif\n" +
        "\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_campos;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform int u_zone_count;\n" +
        "uniform vec4 u_zones[2];\n" +
        "uniform vec4 u_zone_params[2];\n" +
        "\n" +
        "varying vec2 v_texCoords;\n" +
        "\n" +
        "void main(){\n" +
        "    vec2 uv = v_texCoords;\n" +
        "    vec2 worldPos = u_campos + uv * u_resolution;\n" +
        "\n" +
        "    vec2 totalUvOffset = vec2(0.0);\n" +
        "    float chromaAmount = 0.0;\n" +
        "    float domeFactor = 0.0;\n" +
        "    float invertFactor = 0.0;\n" +
        "    float ringHighlight = 0.0;\n" +
        "\n" +
        "    for(int i = 0; i < 2; i++){\n" +
        "        if(i >= u_zone_count) break;\n" +
        "\n" +
        "        vec4 z = u_zones[i];\n" +
        "        vec4 p = u_zone_params[i];\n" +
        "\n" +
        "        vec2 delta = worldPos - z.xy;\n" +
        "        float dist = length(delta);\n" +
        "        vec2 dir = dist > 0.001 ? (delta / dist) : vec2(0.0);\n" +
        "\n" +
        "        float maxR = max(1.0, z.z);\n" +
        "        float waveR = z.w;\n" +
        "        float warpStrength = p.x;\n" +
        "        float invStrength = p.y;\n" +
        "        float domeR = p.z;\n" +
        "        float fade = p.w;\n" +
        "\n" +
        "        if(warpStrength > 0.001 && waveR > 1.0){\n" +
        "            float bandWidth = max(24.0, maxR * 0.22);\n" +
        "            float dWave = dist - waveR;\n" +
        "            float band = exp(-(dWave * dWave) / (bandWidth * bandWidth));\n" +
        "            float ripple = sin(clamp(dWave / bandWidth, -1.5, 1.5) * 2.1);\n" +
        "            float pushWorld = (ripple * 18.0 - band * 10.0) * warpStrength * fade;\n" +
        "            totalUvOffset += (dir * pushWorld) / u_resolution;\n" +
        "            chromaAmount = max(chromaAmount, band * warpStrength * fade);\n" +
        "            ringHighlight = max(ringHighlight, band * warpStrength * fade);\n" +
        "        }\n" +
        "\n" +
        "        if(domeR > 1.0 && dist < domeR){\n" +
        "            float edgeSoft = clamp((domeR - dist) / 14.0, 0.0, 1.0) * fade;\n" +
        "            domeFactor = max(domeFactor, edgeSoft);\n" +
        "            invertFactor = max(invertFactor, edgeSoft * invStrength);\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    vec2 sampleUv = clamp(uv + totalUvOffset, vec2(0.001), vec2(0.999));\n" +
        "    vec4 col = texture2D(u_texture, sampleUv);\n" +
        "\n" +
        "    if(chromaAmount > 0.02){\n" +
        "        vec2 split = totalUvOffset * (0.45 * chromaAmount) + vec2(0.0022 * chromaAmount, 0.0);\n" +
        "        float r = texture2D(u_texture, clamp(sampleUv + split, vec2(0.001), vec2(0.999))).r;\n" +
        "        float b = texture2D(u_texture, clamp(sampleUv - split, vec2(0.001), vec2(0.999))).b;\n" +
        "        col.r = mix(col.r, r, 0.75);\n" +
        "        col.b = mix(col.b, b, 0.75);\n" +
        "    }\n" +
        "\n" +
        "    if(domeFactor > 0.001){\n" +
        "        float luma = dot(col.rgb, vec3(0.299, 0.587, 0.114));\n" +
        "        vec3 frozenTone = mix(vec3(luma), col.rgb, 0.35) * vec3(0.84, 0.74, 1.15);\n" +
        "        vec3 inverted = vec3(1.0) - col.rgb;\n" +
        "        vec3 jjbaInvert = mix(inverted, vec3(0.65, 0.32, 0.98) * (0.4 + 0.8 * luma), 0.25);\n" +
        "        vec3 targetCol = mix(frozenTone, jjbaInvert, clamp(invertFactor, 0.0, 1.0));\n" +
        "        col.rgb = mix(col.rgb, targetCol, domeFactor);\n" +
        "    }\n" +
        "\n" +
        "    if(ringHighlight > 0.05){\n" +
        "        col.rgb += vec3(0.22, 0.38, 0.55) * ringHighlight * 0.35;\n" +
        "    }\n" +
        "\n" +
        "    gl_FragColor = col;\n" +
        "}\n";

    private static final Seq<WarpZone> zones = new Seq<>(MAX_ZONES);
    private static final float[] zoneVecs = new float[MAX_ZONES * 4];
    private static final float[] zoneParams = new float[MAX_ZONES * 4];

    private static @Nullable Shader shader;
    private static @Nullable FrameBuffer buffer;
    private static boolean initialized = false;
    private static boolean disabledByError = false;
    private static boolean hadAny = false;

    public static void init(){
        if(initialized || Vars.headless) return;
        initialized = true;

        Core.settings.defaults("aa-timestop-warp", true);

        try{
            String vert = Shaders.getShaderFi("screenspace.vert").readString();
            shader = new Shader(vert, FRAG_SHADER){
                @Override
                public void apply(){
                    int count = Math.min(MAX_ZONES, zones.size);
                    setUniformi("u_zone_count", count);
                    if(count > 0){
                        setUniformf("u_resolution", Core.camera.width, Core.camera.height);
                        setUniformf("u_campos", Core.camera.position.x - Core.camera.width / 2f, Core.camera.position.y - Core.camera.height / 2f);
                        for(int i = 0; i < count; i++){
                            WarpZone z = zones.get(i);
                            int off = i * 4;
                            zoneVecs[off] = z.x;
                            zoneVecs[off + 1] = z.y;
                            zoneVecs[off + 2] = z.radius;
                            zoneVecs[off + 3] = z.waveRadius;

                            zoneParams[off] = z.warpStrength;
                            zoneParams[off + 1] = z.invertStrength;
                            zoneParams[off + 2] = z.domeRadius;
                            zoneParams[off + 3] = z.fade;
                        }
                        setUniform4fv("u_zones", zoneVecs, 0, count * 4);
                        setUniform4fv("u_zone_params", zoneParams, 0, count * 4);
                    }
                }
            };
            buffer = new FrameBuffer();
        }catch(Throwable t){
            disabledByError = true;
            shader = null;
            Log.err("[AbilityAdditions] Failed to compile TimeStopWarpShader; falling back to ring effects.", t);
            return;
        }

        Events.run(Trigger.preDraw, () -> {
            if(!Vars.state.isGame()){
                zones.clear();
                hadAny = false;
                return;
            }
            boolean enabled = Core.settings.getBool("aa-timestop-warp", true);
            boolean pixelating = Vars.renderer != null && Vars.renderer.pixelate;
            int w = Core.graphics.getWidth();
            int h = Core.graphics.getHeight();

            hadAny = enabled && !disabledByError && !pixelating && shader != null && buffer != null && zones.size > 0 && w > 0 && h > 0;
            if(hadAny){
                try{
                    buffer.resize(w, h);
                    buffer.begin(Color.clear);
                }catch(Throwable t){
                    hadAny = false;
                    disabledByError = true;
                    Log.err("[AbilityAdditions] TimeStopWarpShader preDraw failed.", t);
                }
            }
        });

        Events.run(Trigger.postDraw, () -> {
            if(hadAny){
                hadAny = false;
                try{
                    Draw.flush();
                    buffer.end();
                    Draw.blend(Blending.disabled);
                    buffer.blit(shader);
                    Draw.blend();
                }catch(Throwable t){
                    disabledByError = true;
                    try{
                        Draw.blend();
                    }catch(Throwable ignored){}
                    Log.err("[AbilityAdditions] TimeStopWarpShader postDraw failed.", t);
                }
            }
        });
    }

    public static void clear(){
        zones.clear();
    }

    public static void beginSync(){
        zones.clear();
    }

    public static void addZone(float x, float y, float radius, float timer, float totalDuration, float introExpand, float introShrink){
        if(Vars.headless || disabledByError || zones.size >= MAX_ZONES) return;

        float elapsed = Math.max(0f, totalDuration - timer);
        float tailFade = timer < 18f ? Mathf.clamp(timer / 18f) : 1f;

        float domeScale = elapsed <= introExpand
            ? Interp.pow3Out.apply(elapsed / introExpand)
            : 1f;
        float domeRadius = radius * domeScale;

        float waveRadius = 0f;
        float warpStrength = 0f;
        float invertStrength = 0f;

        if(elapsed <= introExpand){
            float f = elapsed / introExpand;
            waveRadius = radius * Interp.pow3Out.apply(f);
            warpStrength = 1.05f * (1f - f * 0.25f);
            invertStrength = f < 0.18f ? 0.92f : Mathf.lerp(0.65f, 0.25f, (f - 0.18f) / 0.82f);
        }else if(elapsed <= introShrink){
            float f = (elapsed - introExpand) / Math.max(1f, introShrink - introExpand);
            waveRadius = radius * Interp.pow4In.apply(Math.max(0f, 1f - f));
            warpStrength = 1.35f * (1f - f * 0.4f);
            invertStrength = 0.65f * (1f - f);
        }

        WarpZone wz = new WarpZone();
        wz.x = x;
        wz.y = y;
        wz.radius = radius;
        wz.waveRadius = waveRadius;
        wz.warpStrength = warpStrength;
        wz.invertStrength = invertStrength;
        wz.domeRadius = domeRadius;
        wz.fade = tailFade;
        zones.add(wz);
    }

    private static class WarpZone{
        float x, y, radius;
        float waveRadius, warpStrength, invertStrength, domeRadius, fade;
    }
                              }
