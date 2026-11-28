package abilityadditions.content;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.graphics.*;

/** Extra visual effects for the reworked Lancer, Meltdown, Spectre and Pierce abilities. */
public class AbilityEffectsExtra{

    private static final Rand rand = new Rand();
    private static final Color graniteHitColor = Color.valueOf("9ae6ff");

    /** Draws a jagged bolt from (x + offset, topY) straight down to (x, y). Same seed = same bolt shape. */
    private static void drawBolt(float x, float y, float topY, long seed, float stroke, float jitter){
        rand.setSeed(seed);
        int segs = 11;
        float off = rand.range(30f);
        float px = x + off;
        float py = topY;
        for(int i = 1; i <= segs; i++){
            float t = i / (float)segs;
            float nx = i == segs ? x : Mathf.lerp(x + off, x, t) + rand.range(jitter * (1.1f - t));
            float ny = Mathf.lerp(topY, y, t);
            Lines.stroke(stroke);
            Lines.line(px, py, nx, ny);
            px = nx;
            py = ny;
        }
    }

    // ------------------------------------------------------------------ Lancer: Sky Strike

    /** Lightning bolt falling from above the screen onto (x, y). Effect color = bolt color. */
    public static final Effect skyBolt = new Effect(30f, 900f, e -> {
        float f = Interp.pow2Out.apply(e.fout());
        float topY = e.y + 340f;

        Draw.color(e.color);
        Draw.alpha(0.35f * f);
        drawBolt(e.x, e.y, topY, e.id, 11f * f + 2f, 14f);

        Draw.color(e.color, Color.white, 0.5f);
        Draw.alpha(0.9f * f);
        drawBolt(e.x, e.y, topY, e.id, 5f * f + 1f, 14f);

        Draw.color(Color.white);
        Draw.alpha(f);
        drawBolt(e.x, e.y, topY, e.id, 2.2f * f + 0.5f, 14f);

        // short side forks branching off the main bolt
        rand.setSeed(e.id);
        float off = rand.range(30f);
        rand.setSeed(e.id + 99L);
        Draw.color(e.color, Color.white, 0.6f);
        Draw.alpha(0.8f * f);
        Lines.stroke(1.6f * f + 0.3f);
        for(int k = 0; k < 3; k++){
            float t = rand.random(0.15f, 0.8f);
            float bx = Mathf.lerp(e.x + off, e.x, t);
            float by = Mathf.lerp(topY, e.y, t);
            Lines.line(bx, by, bx + rand.range(46f), by - rand.random(24f, 60f));
        }

        // flash on the ground
        Draw.color(Color.white, e.color, e.fin());
        Draw.alpha(0.8f * e.fout());
        Fill.circle(e.x, e.y, 22f * e.fout() + 6f);
        Drawf.light(e.x, e.y, 150f * e.fout() + 30f, e.color, 0.9f * e.fout());
    });

    /** Shockwave + sparks + embers where the bolt lands. rotation = blast radius. */
    public static final Effect skyImpact = new Effect(38f, 300f, e -> {
        float r = Math.max(e.rotation, 20f);

        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(4f * e.fout() + 0.4f);
        Lines.circle(e.x, e.y, r * (0.3f + 1.3f * e.finpow()));

        Draw.color(e.color);
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, r * (0.2f + 0.8f * e.fin()));

        Angles.randLenVectors(e.id, 10, r * 1.6f * e.finpow(), (x, y) -> {
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(1.6f * e.fout());
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 3f + e.fout() * 9f);
        });

        Angles.randLenVectors(e.id + 1L, 8, r * 1.2f * e.finpow(), (x, y) -> {
            Draw.color(Pal.lightFlame, Pal.darkFlame, e.fin());
            Fill.circle(e.x + x, e.y + y + e.fin() * 6f, 1.6f * e.fout() + 0.3f);
        });
    });

    /** Dark scorch mark with glowing embers left on the ground. rotation = radius. */
    public static final Effect skyScorch = new Effect(170f, e -> {
        float r = Math.max(e.rotation, 14f);
        float a = Mathf.clamp(e.fout() * 1.6f);

        Draw.color(0.05f, 0.04f, 0.04f, 0.55f * a);
        Fill.circle(e.x, e.y, r);

        Draw.color(e.color, 0.35f * a);
        Lines.stroke(1.5f);
        Lines.circle(e.x, e.y, r * 0.9f);

        Angles.randLenVectors(e.id, 5, r * 0.8f, (x, y) -> {
            float flick = Mathf.absin(e.time + x * 13f, 7f, 1f);
            Draw.color(Pal.lightFlame, Pal.darkFlame, flick);
            Draw.alpha(0.6f * a * flick);
            Fill.circle(e.x + x, e.y + y, 1.2f);
        });
    }).layer(Layer.debris);

    /** Small rising flame for the burning ground. */
    public static final Effect skyFlame = new Effect(26f, e -> {
        Angles.randLenVectors(e.id, 3, 2f + e.fin() * 5f, (x, y) -> {
            Draw.color(Pal.lightFlame, Pal.darkFlame, e.fin());
            Fill.circle(e.x + x, e.y + y + e.fin() * 7f, 0.6f + e.fout() * 1.9f);
        });
    });

    // ------------------------------------------------------------------ Meltdown: Granite Blast

    /** Blue-white hit sparks for the empowered Granite Blast (replaces the orange flame hit). */
    public static final Effect graniteHit = new Effect(14f, e -> {
        Draw.color(Color.white, graniteHitColor, e.fin());
        Lines.stroke(1.4f * e.fout());
        Angles.randLenVectors(e.id, 4, 2f + 14f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1.5f + e.fout() * 4f);
        });
        Fill.circle(e.x, e.y, 2.4f * e.fout());
    });

    // ------------------------------------------------------------------ Spectre: Giant Shell

    /** Big blast when the giant shell detonates. Effect color = ammo color. */
    public static final Effect giantShellBlast = new Effect(44f, 400f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Fill.circle(e.x, e.y, 26f * Interp.pow2Out.apply(e.fout()));

        Draw.color(e.color);
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, 66f * e.finpow());
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, 38f * e.finpow());

        Angles.randLenVectors(e.id, 14, 20f + 70f * e.finpow(), (x, y) -> {
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(2f * e.fout());
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 11f + 2f);
        });

        Drawf.light(e.x, e.y, 110f * e.fout(), e.color, 0.7f * e.fout());
    });

    /** Smaller explosion for each bullet of the burst ring. */
    public static final Effect shellBlast = new Effect(24f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Fill.circle(e.x, e.y, 7f * e.fout());

        Draw.color(e.color);
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, 30f * e.finpow());

        Angles.randLenVectors(e.id, 6, 6f + 22f * e.finpow(), (x, y) -> {
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(1.4f * e.fout());
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 5f + 1.5f);
        });
    });

    // ------------------------------------------------------------------ Pierce: Dead Eye

    /** X-mark stamped on a target. rotation = target hit size. */
    public static final Effect deadEyeMark = new Effect(36f, e -> {
        float r = Math.max(e.rotation * 0.7f, 8f) + 4f;
        float s = e.fout();

        Draw.color(Color.scarlet, e.color, 0.2f);
        Lines.stroke(2.2f * s + 0.4f);
        Lines.circle(e.x, e.y, r + 22f * e.fout());

        float grow = Interp.pow2Out.apply(e.fin());
        for(int i = 0; i < 4; i++){
            float a = 45f + i * 90f;
            Lines.lineAngle(
                e.x + Angles.trnsx(a, r * 0.35f),
                e.y + Angles.trnsy(a, r * 0.35f),
                a, r * 0.9f * grow
            );
        }

        Draw.color(Color.white);
        Lines.stroke(1.2f * s);
        Lines.circle(e.x, e.y, r * 0.6f);
    });

    /** Muzzle flash for a Dead Eye hypershot. rotation = shot angle. */
    public static final Effect deadEyeMuzzle = new Effect(12f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Drawf.tri(e.x, e.y, 4f * e.fout() + 1f, 26f * e.fout() + 4f, e.rotation);
        Drawf.tri(e.x, e.y, 3f * e.fout() + 0.5f, 12f * e.fout() + 2f, e.rotation + 180f);
        Drawf.tri(e.x, e.y, 2.5f * e.fout(), 10f * e.fout(), e.rotation + 90f);
        Drawf.tri(e.x, e.y, 2.5f * e.fout(), 10f * e.fout(), e.rotation - 90f);
        Lines.stroke(1.4f * e.fout());
        Lines.circle(e.x, e.y, 3f + 9f * e.fin());
    });

    /** Fast bright tracer from the muzzle to the target. data = Vec2 target. */
    public static final Effect deadEyeTracer = new Effect(18f, 1200f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 t = (Vec2)e.data;

        Draw.color(e.color);
        Draw.alpha(0.35f * e.fout());
        Lines.stroke(6f * e.fout());
        Lines.line(e.x, e.y, t.x, t.y);

        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(2f * e.fout() + 0.4f);
        Lines.line(e.x, e.y, t.x, t.y);

        float p = Mathf.clamp(e.fin() * 3f);
        Draw.color(Color.white);
        Fill.circle(Mathf.lerp(e.x, t.x, p), Mathf.lerp(e.y, t.y, p), 2.6f * e.fout() + 0.6f);
    });

    /** Impact starburst on the marked target. */
    public static final Effect deadEyeImpact = new Effect(26f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(3f * e.fout() + 0.3f);
        Lines.circle(e.x, e.y, 4f + 26f * e.finpow());

        for(int i = 0; i < 4; i++){
            float a = 45f + i * 90f;
            Drawf.tri(e.x, e.y, 3.5f * e.fout(), 6f + 18f * e.fout(), a);
        }

        Angles.randLenVectors(e.id, 6, 4f + 22f * e.finpow(), (x, y) -> {
            Draw.color(Color.scarlet, e.color, e.fin());
            Fill.circle(e.x + x, e.y + y, 1.8f * e.fout());
        });
    });
}
