package abilityadditions.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import mindustry.entities.Effect;

public class AbilityEffects{

    public static final Effect abilityActivate = new Effect(28f, e -> {
        Draw.color(e.color, Color.white, 0.35f * e.fout());
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, 4f + 22f * e.fin(Interp.pow3Out));

        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(1.4f * e.fout());
        Lines.circle(e.x, e.y, 2f + 13f * e.fin(Interp.pow2Out));

        Angles.randLenVectors(e.id, 10, 4f + 24f * e.fin(Interp.pow2Out), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.8f * e.fout());
        });
        Draw.color();
    });

    public static final Effect activeTurretSpark = new Effect(18f, e -> {
        Draw.color(e.color, Color.white, 0.45f);
        Angles.randLenVectors(e.id, 2, 3f + 9f * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.3f * e.fout());
        });
        Draw.color();
    });

    public static final Effect empoweredTrail = new Effect(15f, e -> {
        Draw.color(e.color, Color.white, 0.3f * e.fout());
        Fill.circle(e.x, e.y, 2.1f * e.fout());

        Lines.stroke(1.1f * e.fout());
        Lines.lineAngle(e.x, e.y, e.rotation + 180f, 4.5f * e.fout());
        Draw.color();
    });

    public static final Effect empoweredHit = new Effect(20f, e -> {
        Draw.color(e.color, Color.white, 0.4f * e.fout());
        Lines.stroke(1.8f * e.fout());
        Lines.circle(e.x, e.y, 2f + 12f * e.fin(Interp.pow3Out));

        Angles.randLenVectors(e.id, 7, 3f + 16f * e.fin(Interp.pow2Out), (x, y) -> {
            Lines.stroke(1.2f * e.fout());
            Lines.lineAngle(e.x + x, e.y + y, Angles.angle(x, y), 3.5f * e.fout());
        });
        Draw.color();
    });
        } 
