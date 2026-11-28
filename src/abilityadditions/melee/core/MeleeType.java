package abilityadditions.melee.core;

import arc.audio.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;

public class MeleeType{

    public enum MeleeShape{
        ARC,
        THRUST,
        SLAM
    }

    public MeleeShape shape = MeleeShape.THRUST;
    public float reach = 24f;
    public float arcAngle = 95f;
    public float halfWidth = 7f;

    public float damage = 32f;
    public float buildingDamageMultiplier = 1.2f;
    public boolean pierceArmor = false;
    public int maxTargets = 4;

    public boolean hitGround = true;
    public boolean hitAir = false;

    public float knockback = 0f;
    public float lungeSpeed = 0f;
    public float shake = 0f;

    public @Nullable StatusEffect hitStatus;
    public float hitStatusDuration = 0f;

    public Color accentColor = Pal.accent;
    public Effect swingEffect = Fx.none;
    public Effect hitEffect = Fx.hitBulletSmall;
    public Sound swingSound = Sounds.none;
    public Sound hitSound = Sounds.none;

    private static final Seq<HitCandidate> candidates = new Seq<>();

    public int execute(Unit attacker, float originX, float originY, float angle){
        if(attacker == null || !attacker.isAdded()) return 0;

        if(swingEffect != null && swingEffect != Fx.none){
            swingEffect.at(originX, originY, angle, accentColor);
        }
        if(swingSound != null && swingSound != Sounds.none){
            swingSound.at(originX, originY, Mathf.random(0.95f, 1.05f), 0.85f);
        }
        if(lungeSpeed > 0.01f){
            attacker.impulse(Angles.trnsx(angle, lungeSpeed * 40f), Angles.trnsy(angle, lungeSpeed * 40f));
        }
        if(shake > 0.01f){
            Effect.shake(shake, shake * 4f, originX, originY);
        }

        candidates.clear();
        float scanRadius = reach + 42f;

        Units.nearbyEnemies(attacker.team, originX, originY, scanRadius, target -> {
            if(target == null || target.dead || !target.hittable()) return;
            if(target.isFlying() && !hitAir) return;
            if(!target.isFlying() && !hitGround) return;

            float rad = target.hitSize * 0.5f;
            if(matchesShape(originX, originY, angle, target.x, target.y, rad)){
                HitCandidate c = new HitCandidate();
                c.unit = target;
                c.dist2 = Mathf.dst2(originX, originY, target.x, target.y);
                candidates.add(c);
            }
        });

        if(hitGround && Vars.indexer != null){
            Vars.indexer.eachBlock(null, originX, originY, scanRadius, b -> b.team != attacker.team && b.block.targetable && !b.dead, b -> {
                float rad = b.hitSize() * 0.5f;
                if(matchesShape(originX, originY, angle, b.x, b.y, rad)){
                    HitCandidate c = new HitCandidate();
                    c.building = b;
                    c.dist2 = Mathf.dst2(originX, originY, b.x, b.y);
                    candidates.add(c);
                }
            });
        }

        if(candidates.isEmpty()) return 0;
        candidates.sort((a, b) -> Float.compare(a.dist2, b.dist2));

        int limit = maxTargets <= 0 ? candidates.size : Math.min(maxTargets, candidates.size);
        float mult = attacker.damageMultiplier;
        float unitDmg = damage * mult;
        float buildDmg = unitDmg * buildingDamageMultiplier;

        int hits = 0;
        for(int i = 0; i < limit; i++){
            HitCandidate c = candidates.get(i);
            if(c.unit != null){
                Unit target = c.unit;
                if(pierceArmor){
                    target.damagePierce(unitDmg);
                }else{
                    target.damage(unitDmg);
                }
                if(knockback > 0.01f){
                    float pushAng = target.within(originX, originY, 1f) ? angle : Angles.angle(originX, originY, target.x, target.y);
                    target.impulse(Angles.trnsx(pushAng, knockback * 45f), Angles.trnsy(pushAng, knockback * 45f));
                }
                if(hitStatus != null && hitStatusDuration > 0f){
                    target.apply(hitStatus, hitStatusDuration);
                }
                if(hitEffect != null && hitEffect != Fx.none){
                    hitEffect.at(target.x, target.y, angle, accentColor);
                }
                if(hitSound != null && hitSound != Sounds.none){
                    hitSound.at(target.x, target.y, Mathf.random(0.95f, 1.05f), 0.7f);
                }
                hits++;
            }else if(c.building != null){
                Building b = c.building;
                b.damage(attacker.team, buildDmg);
                if(hitEffect != null && hitEffect != Fx.none){
                    hitEffect.at(b.x, b.y, angle, accentColor);
                }
                if(hitSound != null && hitSound != Sounds.none){
                    hitSound.at(b.x, b.y, Mathf.random(0.95f, 1.05f), 0.7f);
                }
                hits++;
            }
        }

        return hits;
    }

    protected boolean matchesShape(float ox, float oy, float angle, float tx, float ty, float targetRadius){
        float dx = tx - ox;
        float dy = ty - oy;
        float dst2 = dx * dx + dy * dy;
        float maxR = reach + targetRadius;
        if(dst2 > maxR * maxR) return false;

        if(shape == MeleeShape.SLAM){
            return true;
        }

        if(shape == MeleeShape.ARC){
            if(dst2 <= (targetRadius + 4f) * (targetRadius + 4f)) return true;
            float targetAng = Angles.angle(ox, oy, tx, ty);
            float tol = arcAngle * 0.5f + Mathf.clamp(targetRadius / Math.max(8f, Mathf.sqrt(dst2)) * Mathf.radDeg, 0f, 35f);
            return Math.abs(Angles.angleDist(angle, targetAng)) <= tol;
        }

        float cos = Mathf.cosDeg(angle);
        float sin = Mathf.sinDeg(angle);
        float forward = dx * cos + dy * sin;
        float side = Math.abs(-dx * sin + dy * cos);
        return forward >= -targetRadius * 0.5f
            && forward <= reach + targetRadius
            && side <= halfWidth + targetRadius;
    }

    private static class HitCandidate{
        Unit unit;
        Building building;
        float dist2;
    }
}