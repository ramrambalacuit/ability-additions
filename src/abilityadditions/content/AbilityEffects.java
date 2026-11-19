package abilityadditions.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import mindustry.entities.Effect;

public class AbilityEffects{

    // Pass block.size in rotation; scales automatically for 1x1, 2x2, 3x3, etc.
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

    // Top sparks while Overdrive Rush is active; pass block.size in rotation
    public static final Effect activeTurretSpark = new Effect(18f, e -> {
        float size = Math.max(1f, e.rotation);
        Draw.color(e.color, Color.white, 0.45f);
        Angles.randLenVectors(e.id, 2, 2f + (5f * size) * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, (1.1f + 0.15f * size) * e.fout());
        });
        Draw.color();
    });

    // Overheat smoke puffs during Meltdown; pass block.size in rotation
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
}
