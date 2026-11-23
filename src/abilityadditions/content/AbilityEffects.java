package abilityadditions.content;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.entities.*;
import mindustry.graphics.*;

public class AbilityEffects{

    public static final Color graniteBlue = Color.valueOf("4a7fe5");
    public static final Color graniteCyan = Color.valueOf("9ae6ff");
    public static final Color graniteCore = Color.valueOf("e8edf2");

    public static final Effect overdriveTrigger = new Effect(28f, e -> {
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color, Color.white, fin * 0.6f);
        Lines.stroke(2.6f * e.fout());
        Lines.circle(e.x, e.y, 4f + fin * 22f);
        Angles.randLenVectors(e.id, 8, 6f + fin * 24f, (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, 3.2f * e.fout(), 8f * e.fout(), Mathf.angle(x, y));
        });
    });

    public static final Effect activeTurretSpark = new Effect(20f, e -> {
        Draw.color(e.color);
        Fill.circle(e.x, e.y, 2.3f * e.fout());
        Draw.color(Color.white);
        Fill.circle(e.x, e.y, 1.1f * e.fout());
    });

    public static final Effect meltdownSmoke = new Effect(46f, e -> {
        Draw.color(Color.darkGray, Color.gray, e.fin());
        Draw.alpha(0.65f * e.fout());
        Angles.randLenVectors(e.id, 3, 2f + e.fin() * 14f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y + e.fin() * 10f, 3.2f * e.fout() + 1.4f);
        });
    });

    public static final Effect salvoMuzzleSpark = new Effect(14f, e -> {
        float fin = Interp.pow2Out.apply(e.fin());
        Draw.color(e.color, Color.white, e.fout() * 0.45f);
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 2.5f + fin * 9f);
        for(int i = -1; i <= 1; i += 2){
            float ang = e.rotation + i * 24f;
            float dist = 3f + fin * 10f;
            Drawf.tri(
                e.x + Mathf.cosDeg(ang) * dist,
                e.y + Mathf.sinDeg(ang) * dist,
                2.6f * e.fout(),
                8f * e.fout(),
                ang
            );
        }
    });

    public static final Effect chainLightningArc = new Effect(18f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color);
        Lines.stroke(3.2f * e.fout());

        int segs = 4;
        float lx = e.x, ly = e.y;
        float dx = (end.x - e.x) / segs;
        float dy = (end.y - e.y) / segs;
        float perpX = -dy, perpY = dx;

        float[] px = new float[segs + 1];
        float[] py = new float[segs + 1];
        px[0] = e.x;
        py[0] = e.y;
        for(int i = 1; i <= segs; i++){
            float nx = e.x + dx * i;
            float ny = e.y + dy * i;
            if(i < segs){
                float jitter = Mathf.randomSeed(e.id + i * 17L, -5f, 5f) * 0.16f;
                nx += perpX * jitter;
                ny += perpY * jitter;
            }
            px[i] = nx;
            py[i] = ny;
            Lines.line(lx, ly, nx, ny);
            lx = nx;
            ly = ny;
        }
        Draw.color(Color.white);
        Lines.stroke(1.2f * e.fout());
        for(int i = 0; i < segs; i++){
            Lines.line(px[i], py[i], px[i + 1], py[i + 1]);
        }
        Fill.circle(end.x, end.y, 3.6f * e.fout());
    });

    public static final Effect lancerParalyzeArc = new Effect(18f, e -> {
        float rad = Math.max(8f, e.rotation * 0.65f);
        Draw.color(e.color, Color.white, 0.65f * e.fout());
        Lines.stroke(Math.max(1.5f, rad * 0.12f) * e.fout());
        for(int i = 0; i < 2; i++){
            float a1 = Mathf.randomSeed(e.id + i * 31L, 0f, 360f);
            float a2 = a1 + Mathf.randomSeed(e.id + i * 47L, 50f, 150f);
            float x1 = e.x + Mathf.cosDeg(a1) * rad;
            float y1 = e.y + Mathf.sinDeg(a1) * rad;
            float xm = e.x + Mathf.cosDeg((a1 + a2) * 0.5f) * (rad * 0.45f);
            float ym = e.y + Mathf.sinDeg((a1 + a2) * 0.5f) * (rad * 0.45f);
            float x2 = e.x + Mathf.cosDeg(a2) * rad;
            float y2 = e.y + Mathf.sinDeg(a2) * rad;
            Lines.line(x1, y1, xm, ym);
            Lines.line(xm, ym, x2, y2);
        }
    });

    public static final Effect seismicPullRing = new Effect(26f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 36f;
        Draw.color(e.color, Color.white, 0.35f);
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, rad * e.fout());
        Angles.randLenVectors(e.id, 10, rad * e.fout(), (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, 2.8f * e.fout(), 8f * e.fout(), Mathf.angle(-x, -y));
        });
    });

    public static final Effect carpetBombletWarning = new Effect(33f, e -> {
        Draw.color(e.color, Color.white, 0.25f);
        Lines.stroke(1.6f * e.fout());
        Lines.circle(e.x, e.y, 6f + 16f * (1f - e.fin()));
        Fill.circle(e.x, e.y, 2.4f);
    });

    public static final Effect carpetBombletExplosion = new Effect(24f, e -> {
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color);
        Lines.stroke(2.8f * e.fout());
        Lines.circle(e.x, e.y, 3f + fin * 22f);
        Angles.randLenVectors(e.id, 8, 4f + fin * 20f, (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, 3f * e.fout(), 7f * e.fout(), Mathf.angle(x, y));
        });
        Draw.color(Color.white);
        Fill.circle(e.x, e.y, 4.5f * e.fout());
    });

    public static final Effect huntersMarkPulse = new Effect(24f, e -> {
        Draw.color(e.color, Color.white, 0.45f * e.fout());
        Lines.stroke(2.2f * e.fout());
        float r = 10f + 14f * (1f - e.finpow());
        Lines.poly(e.x, e.y, 4, r, 45f);
        Lines.circle(e.x, e.y, r * 1.2f);
    });

    public static final Effect waveMonsoonPulse = new Effect(26f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 54f;
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color, Color.white, 0.35f);
        Lines.stroke(2.8f * e.fout());
        Lines.circle(e.x, e.y, 8f + rad * fin);
    });

    public static final Effect tsunamiTidalSplash = new Effect(24f, e -> {
        Draw.color(e.color, Color.white, e.fout() * 0.4f);
        Angles.randLenVectors(e.id, 6, 4f + e.fin() * 20f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 3.2f * e.fout());
        });
    });

    public static final Effect parallaxGravitySlam = new Effect(28f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 48f;
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color);
        Lines.stroke(3.2f * e.fout());
        Lines.circle(e.x, e.y, 4f + rad * fin);
        Draw.color(Color.white);
        Lines.stroke(1.4f * e.fout());
        Lines.circle(e.x, e.y, 4f + rad * fin * 0.7f);
        Angles.randLenVectors(e.id, 10, 6f + rad * 0.75f * fin, (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, 3.5f * e.fout(), 9f * e.fout(), Mathf.angle(x, y));
        });
    });

    public static final Effect segmentCounterBeam = new Effect(15f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color);
        Lines.stroke(3.2f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Draw.color(Color.white);
        Lines.stroke(1.2f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Fill.circle(end.x, end.y, 3.6f * e.fout());
    });

    public static final Effect segmentAegisPulse = new Effect(24f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 140f;
        Draw.color(e.color, Color.white, 0.45f * e.fout());
        Lines.stroke(2.6f * e.fout());
        Lines.circle(e.x, e.y, rad * (0.85f + 0.15f * e.fin()));
    });

    public static final Effect fragmentStaticZap = new Effect(16f, e -> {
        Draw.color(e.color, Color.white, 0.55f * e.fout());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 2f + e.fin() * 13f);
    });

    public static final Effect cycloneSpinImpact = new Effect(18f, e -> {
        Draw.color(e.color, Color.white, 0.45f * e.fout());
        Lines.stroke(2.2f * e.fout());
        float a = e.rotation + e.fin() * 180f;
        Lines.arc(e.x, e.y, 8f + e.fin() * 10f, 0.45f, a);
        Lines.arc(e.x, e.y, 8f + e.fin() * 10f, 0.45f, a + 180f);
    });

    // Meltdown Granite Blast: mov-style shrinking implosion rings + dual-side spiral streaks + 0->360 eruption wave
    public static final Effect graniteChargeRing = new Effect(42f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 56f;
        Draw.color(graniteBlue, graniteCore, e.fin());
        Lines.stroke(3.2f * e.fin());
        Lines.circle(e.x, e.y, rad * (1f - Interp.pow2In.apply(e.fin())));
    });

    public static final Effect graniteSpiralStreak = new Effect(40f, e -> {
        float dist = (e.data instanceof Float ? (Float)e.data : 68f) * (1f - Interp.pow2In.apply(e.fin()));
        float ang = e.rotation + e.fin() * 48f;
        float px = e.x + Mathf.cosDeg(ang) * dist;
        float py = e.y + Mathf.sinDeg(ang) * dist;
        Draw.color(graniteBlue, graniteCyan, e.fin());
        Fill.circle(px, py, 2.2f + 2.8f * e.fin());
        Draw.color(graniteCore);
        Fill.circle(px, py, 1.1f + 1.3f * e.fin());
    });

    public static final Effect graniteSnapWave = new Effect(36f, e -> {
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(graniteCyan, graniteCore, 0.4f);
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, 6f + fin * 180f);
        Draw.color(graniteBlue);
        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, 4f + fin * 110f);
        Angles.randLenVectors(e.id, 14, 10f + fin * 90f, (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, 4.5f * e.fout(), 14f * e.fout(), Mathf.angle(x, y));
        });
    });

    // Spectre PvZ2 Giant Shell Impact
    public static final Effect giantShellImpact = new Effect(34f, e -> {
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color);
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, 6f + fin * 56f);
        Draw.color(Color.white);
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, 4f + fin * 34f);
        Fill.circle(e.x, e.y, 9f * e.fout());
        Angles.randLenVectors(e.id, 14, 8f + fin * 54f, (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, 4.2f * e.fout(), 12f * e.fout(), Mathf.angle(x, y));
        });
    });

    public static final Effect foreshadowApexImpact = new Effect(36f, e -> {
        float fin = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color);
        Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, 6f + 48f * fin);
        Draw.color(Color.white);
        Lines.stroke(2.2f * e.fout());
        Lines.circle(e.x, e.y, 12f + 24f * e.fout());
        for(int i = 0; i < 4; i++){
            float a = i * 90f + 45f;
            Drawf.tri(e.x + Mathf.cosDeg(a) * 6f, e.y + Mathf.sinDeg(a) * 6f, 5f * e.fout(), 26f * e.fout(), a);
        }
    });

    public static final Effect pierceHyperTracer = new Effect(16f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color);
        Lines.stroke(3.2f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Draw.color(Color.white);
        Lines.stroke(1.2f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Draw.color(e.color, Color.white, 0.5f);
        for(int i = 0; i < 4; i++){
            Drawf.tri(end.x, end.y, 3.2f * e.fout(), 11f * e.fout(), i * 90f + 45f);
        }
    });
            }
