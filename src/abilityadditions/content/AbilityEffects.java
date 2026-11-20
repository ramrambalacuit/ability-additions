package abilityadditions.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Position;
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

    // Arc Static Chain lightning line (data = target Position)
    public static final Effect chainArc = new Effect(14f, e -> {
        if(!(e.data instanceof Position target)) return;

        float x1 = e.x, y1 = e.y;
        float x2 = target.getX(), y2 = target.getY();
        int segments = 5;

        Draw.color(e.color, Color.white, 0.55f * e.fout());
        Lines.stroke(2.0f * e.fout());

        float prevX = x1, prevY = y1;
        for(int i = 1; i <= segments; i++){
            float t = (float)i / segments;
            float nx = Mathf.lerp(x1, x2, t);
            float ny = Mathf.lerp(y1, y2, t);
            if(i < segments){
                float jitter = Mathf.sin(e.id * 17f + i * 9f) * 5.5f;
                float perp = Angles.angle(x1, y1, x2, y2) + 90f;
                nx += Angles.trnsx(perp, jitter);
                ny += Angles.trnsy(perp, jitter);
            }
            Lines.line(prevX, prevY, nx, ny);
            prevX = nx;
            prevY = ny;
        }
        Fill.circle(x2, y2, 3.2f * e.fout());
        Draw.color();
    });

    // Hail Seismic Mortar inward shockwave
    public static final Effect seismicPull = new Effect(26f, e -> {
        Draw.color(e.color, Color.white, 0.4f * e.fout());
        Lines.stroke(2.2f * e.fout());
        Lines.circle(e.x, e.y, 4f + 50f * (1f - e.fin(Interp.pow2Out)));

        Lines.stroke(1.2f * e.fout());
        Lines.circle(e.x, e.y, 3f + 36f * e.fin(Interp.pow3Out));

        Angles.randLenVectors(e.id, 10, 46f * (1f - e.fin(Interp.pow2Out)), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 1.8f * e.fout());
        });
        Draw.color();
    });

    // Bob effects
    public static final Effect bobSmoke = new Effect(24f, e -> {
        Draw.color(Color.lightGray, Color.white, 0.5f);
        Draw.alpha(0.55f * e.fout());
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

    public static final Effect smokeSnowBomb = new Effect(110f, e -> {
        Draw.color(Color.white, Color.valueOf("cfefff"), 0.35f * e.fin());
        Lines.stroke(2.4f * e.fout());
        Lines.circle(e.x, e.y, 4f + 36f * e.fin(Interp.pow3Out));

        Angles.randLenVectors(e.id, 26, 6f + 38f * e.fin(Interp.pow2Out), (x, y) -> {
            Draw.color(Color.white, Color.lightGray, 0.45f * e.fin());
            Draw.alpha(0.85f * e.fout());
            Fill.circle(e.x + x, e.y + y, (6f + 6f * e.fin()) * e.fout());
        });
        Draw.color();
    });
                     }
