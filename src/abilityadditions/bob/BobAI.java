package abilityadditions.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import mindustry.entities.Effect;

public class AbilityEffects{

    public static final Effect abilityActivate = new Effect(22f, e -> {
        float size = Math.max(1f, e.rotation);
        float maxRad = 4.5f * size;

        Draw.color(e.color, Color.white, 0.35f * e.fout());
        Lines.stroke((1.3f + 0.35f * size) * e.fout());
        Lines.circle(e.x, e.y, 1.5f + maxRad * e.fin(Interp.pow3Out));

        int sparks = Math.round(4 + 2 * size);
        Angles.randLenVectors(e.id, sparks, 2f + maxRad * 1.15f * e.fin(Interp.pow2Out), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, (1.0f + 0.2f * size) * e.fout());
        });
        Draw.color();
    });

    public static final Effect activeTurretSpark = new Effect(18f, e -> {
        float size = Math.max(1f, e.rotation);
        Draw.color(e.color, Color.white, 0.45f);
        Angles.randLenVectors(e.id, 2, 2f + (5f * size) * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, (1.1f + 0.15f * size) * e.fout());
        });
        Draw.color();
    });

    public static final Effect meltdownSmoke = new Effect(30f, e -> {
        float size = Math.max(1f, e.rotation);
        Draw.color(Color.darkGray, e.color, 0.25f * e.fout());
        Draw.alpha(0.65f * e.fout());
        Angles.randLenVectors(e.id, 2, 2f + (6f * size) * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y + 4f * e.fin(), (1.6f + 0.4f * size) * e.fout());
        });
        Draw.color();
    });

    public static final Effect empoweredTrail = new Effect(14f, e -> {
        Draw.color(e.color, Color.white, 0.3f * e.fout());
        Fill.circle(e.x, e.y, 1.8f * e.fout());

        Lines.stroke(1.0f * e.fout());
        Lines.lineAngle(e.x, e.y, e.rotation + 180f, 4f * e.fout());
        Draw.color();
    });

    public static final Effect empoweredHit = new Effect(18f, e -> {
        Draw.color(e.color, Color.white, 0.4f * e.fout());
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, 2f + 10f * e.fin(Interp.pow3Out));

        Angles.randLenVectors(e.id, 6, 2f + 13f * e.fin(Interp.pow2Out), (x, y) -> {
            Lines.stroke(1.1f * e.fout());
            Lines.lineAngle(e.x + x, e.y + y, Angles.angle(x, y), 3f * e.fout());
        });
        Draw.color();
    });

    public static final Effect bobSmoke = new Effect(24f, e -> {
        Draw.color(Color.lightGray, Color.white, 0.5f);
        Draw.alpha(0.5f * e.fout());
        Angles.randLenVectors(e.id, 2, 2f + 6f * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 2.2f * e.fout());
        });
        Draw.color();
    });

    public static final Effect bobSweat = new Effect(22f, e -> {
        Draw.color(Color.valueOf("6fd6ff"), Color.white, 0.35f);
        Angles.randLenVectors(e.id, 2, 3f + 9f * e.fin(Interp.pow2Out), (x, y) -> {
            Fill.circle(e.x + x, e.y + Math.abs(y) + 3f * e.fin() - 5f * e.fin(Interp.pow2In), 1.4f * e.fout());
        });
        Draw.color();
    });

    public static final Effect bobDash = new Effect(18f, e -> {
        Draw.color(Color.white, Color.valueOf("b8ecff"), e.fin());
        Lines.stroke(1.6f * e.fout());
        Lines.circle(e.x, e.y, 2f + 11f * e.fin(Interp.pow2Out));
        Angles.randLenVectors(e.id, 6, 3f + 12f * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.8f * e.fout());
        });
        Draw.color();
    });

    public static final Effect snowballHit = new Effect(16f, e -> {
        Draw.color(Color.white, Color.valueOf("cfefff"), e.fin());
        Angles.randLenVectors(e.id, 6, 2f + 10f * e.fin(Interp.pow2Out), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.7f * e.fout());
        });
        Draw.color();
    });

    // Big blinding Smoke Snow Bomb cloud!
    public static final Effect smokeSnowBomb = new Effect(130f, 160f, e -> {
        Draw.color(Color.valueOf("dce6ee"), Color.white, 0.35f * e.fout());
        Draw.alpha(0.88f * e.fout(Interp.pow2In));
        Angles.randLenVectors(e.id, 26, 8f + 56f * e.fin(Interp.pow3Out), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 16f * e.fout(Interp.pow2In) + 5f);
        });

        Draw.color(Color.white, Color.valueOf("bfe8ff"), e.fin());
        Draw.alpha(0.7f * e.fout());
        Angles.randLenVectors(e.id + 1, 14, 6f + 48f * e.fin(Interp.pow2Out), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 3.2f * e.fout());
        });
        Draw.color();
    });
} 
