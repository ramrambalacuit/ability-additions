package abilityadditions.content;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.entities.*;
import mindustry.graphics.*;

public class AbilityEffects{

    public static final Effect overdriveTrigger = new Effect(28f, e -> {
        Draw.color(e.color, Color.white, e.fin());
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, 4f + e.fin() * 20f);
        Angles.randLenVectors(e.id, 8, 6f + e.fin() * 22f, (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 4f * e.fout());
        });
    });

    public static final Effect activeTurretSpark = new Effect(22f, e -> {
        Draw.color(e.color, Color.white, e.fin() * 0.6f);
        Fill.circle(e.x, e.y, 1.8f * e.fout());
    });

    public static final Effect salvoMuzzleSpark = new Effect(14f, e -> {
        Draw.color(e.color, Color.white, e.fout() * 0.45f);
        Lines.stroke(1.8f * e.fout());
        Lines.circle(e.x, e.y, 2.5f + e.fin() * 8f);
        for(int i = -1; i <= 1; i += 2){
            float ang = e.rotation + i * 24f;
            float dist = 3f + e.fin() * 9f;
            Lines.lineAngle(
                e.x + Mathf.cosDeg(ang) * dist,
                e.y + Mathf.sinDeg(ang) * dist,
                ang,
                5f * e.fout()
            );
        }
    });

    public static final Effect chainLightningArc = new Effect(18f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color, Color.white, 0.55f);
        Lines.stroke(2.4f * e.fout());

        int segs = 4;
        float lx = e.x, ly = e.y;
        float dx = (end.x - e.x) / segs;
        float dy = (end.y - e.y) / segs;
        float perpX = -dy, perpY = dx;

        for(int i = 1; i <= segs; i++){
            float nx = e.x + dx * i;
            float ny = e.y + dy * i;
            if(i < segs){
                float jitter = Mathf.randomSeed(e.id + i * 17L, -5f, 5f) * 0.16f;
                nx += perpX * jitter;
                ny += perpY * jitter;
            }
            Lines.line(lx, ly, nx, ny);
            lx = nx;
            ly = ny;
        }
        Fill.circle(end.x, end.y, 3.2f * e.fout());
    });

    public static final Effect seismicPullRing = new Effect(26f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 36f;
        Draw.color(e.color, Color.white, 0.35f);
        Lines.stroke(2.2f * e.fout());
        Lines.circle(e.x, e.y, rad * e.fout());
        Angles.randLenVectors(e.id, 10, rad * e.fout(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(-x, -y), 5f * e.fout());
        });
    });

    public static final Effect carpetBombletLaunch = new Effect(18f, e -> {
        Draw.color(e.color, Color.white, e.fin());
        Fill.circle(e.x, e.y, 3.2f * e.fout());
    });

    public static final Effect carpetBombletExplosion = new Effect(22f, e -> {
        Draw.color(e.color, Color.orange, e.fin());
        Lines.stroke(2.2f * e.fout());
        Lines.circle(e.x, e.y, 3f + e.fin() * 18f);
        Angles.randLenVectors(e.id, 7, 4f + e.fin() * 16f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 2.2f * e.fout());
        });
    });

    public static final Effect huntersMarkPulse = new Effect(20f, e -> {
        Draw.color(e.color, Color.white, e.fin() * 0.4f);
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 12f + e.fin() * 10f);
    });

    public static final Effect railEchoBeam = new Effect(18f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color, Color.white, e.fout() * 0.5f);
        Lines.stroke(4.5f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Draw.color(Color.white);
        Lines.stroke(1.6f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
    });

    public static final Effect waveMonsoonPulse = new Effect(26f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 54f;
        Draw.color(e.color, Color.white, 0.3f);
        Lines.stroke(2.6f * e.fout());
        Lines.circle(e.x, e.y, 8f + rad * e.fin());
    });

    public static final Effect tsunamiTidalSplash = new Effect(24f, e -> {
        Draw.color(e.color, Color.white, e.fout() * 0.35f);
        Angles.randLenVectors(e.id, 6, 4f + e.fin() * 20f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 3.2f * e.fout());
        });
    });

    public static final Effect parallaxGravitySlam = new Effect(28f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 48f;
        Draw.color(e.color, Color.white, 0.5f * e.fout());
        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, 4f + rad * e.fin());
        Angles.randLenVectors(e.id, 10, 6f + rad * 0.7f * e.fin(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 6f * e.fout());
        });
    });

    public static final Effect segmentCounterBeam = new Effect(15f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color, Color.white, 0.4f * e.fout());
        Lines.stroke(3f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Draw.color(Color.white);
        Lines.stroke(1.1f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Fill.circle(end.x, end.y, 3.5f * e.fout());
    });

    public static final Effect segmentAegisPulse = new Effect(24f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 140f;
        Draw.color(e.color, Color.white, 0.4f * e.fout());
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, rad * (0.85f + 0.15f * e.fin()));
    });

    public static final Effect fragmentStaticZap = new Effect(16f, e -> {
        Draw.color(e.color, Color.white, 0.5f * e.fout());
        Lines.stroke(1.8f * e.fout());
        Lines.circle(e.x, e.y, 2f + e.fin() * 12f);
    });

    public static final Effect cycloneSpinImpact = new Effect(18f, e -> {
        Draw.color(e.color, Color.white, 0.4f * e.fout());
        Lines.stroke(2f * e.fout());
        float a = e.rotation + e.fin() * 180f;
        Lines.arc(e.x, e.y, 8f + e.fin() * 10f, 0.45f, a);
        Lines.arc(e.x, e.y, 8f + e.fin() * 10f, 0.45f, a + 180f);
    });

    public static final Effect singularityAbsorbStreak = new Effect(22f, e -> {
        float ang = e.rotation;
        float dist = (e.data instanceof Float ? (Float)e.data : 42f) * (1f - e.finpow());
        float px = e.x + Mathf.cosDeg(ang) * dist;
        float py = e.y + Mathf.sinDeg(ang) * dist;
        Draw.color(e.color, Color.white, e.fin() * 0.7f);
        Lines.stroke(1.6f * (0.4f + 0.6f * e.fin()));
        Lines.lineAngle(px, py, ang + 180f, 6f * e.fout() + 2f);
    });

    public static final Effect singularityReleaseBlast = new Effect(32f, e -> {
        float rad = e.rotation > 0f ? e.rotation : 48f;
        Draw.color(e.color, Color.white, 0.55f * e.fout());
        Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, 4f + rad * e.finpow());
        Angles.randLenVectors(e.id, 14, 8f + rad * e.fin(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 8f * e.fout());
        });
    });

    public static final Effect foreshadowApexImpact = new Effect(34f, e -> {
        Draw.color(e.color, Color.white, 0.6f * e.fout());
        Lines.stroke(3.6f * e.fout());
        Lines.circle(e.x, e.y, 6f + 38f * e.finpow());
        for(int i = 0; i < 4; i++){
            Lines.lineAngle(e.x, e.y, i * 90f + 45f, 22f * e.fout());
        }
    });
}
