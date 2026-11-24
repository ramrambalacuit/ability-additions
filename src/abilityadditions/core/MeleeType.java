package abilityadditions.core;

import arc.audio.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;

public class MeleeType{

    public enum MeleeShape{
        ARC,
        THRUST,
        SLAM
    }

    public MeleeShape shape = MeleeShape.ARC;
    public float reach = 26f;
    public float arcAngle = 110f;
    public float halfWidth = 10f;
    public float damage = 32f;
    public float buildingDamageMultiplier = 1f;
    public boolean pierceArmor = false;
    public int maxTargets = 5;
    public boolean hitGround = true;
    public boolean hitAir = false;
    public float knockback = 2.5f;
    public float lungeSpeed = 0f;
    public float shake = 0f;

    public @Nullable StatusEffect hitStatus = null;
    public float hitStatusDuration = 24f;

    public Color accentColor = Color.valueOf("b58cff");
    public Effect swingEffect = Fx.none;
    public Effect hitEffect = Fx.hitBulletSmall;
    public Sound swingSound = Sounds.none;
    public Sound hitSound = Sounds.none;

    private static final Seq<Unit> tmpUnits = new Seq<>();
    private static final Seq<Building> tmpBuilds = new Seq<>();

    public int execute(Unit attacker, float originX, float originY, float angle){
        if(attacker == null || !attacker.isAdded()) return 0;

        if(swingEffect != null && swingEffect != Fx.none){
            swingEffect.at(originX, originY, angle, accentColor);
        }
        if(swingSound != null && swingSound != Sounds.none){
            swingSound.at(originX, originY, Mathf.random(0.92f, 1.08f));
        }
        if(lungeSpeed > 0f){
            attacker.vel.add(Tmp.v1.trns(angle, lungeSpeed));
        }

        tmpUnits.clear();
        tmpBuilds.clear();

        float scanRadius = reach + 18f;
        Units.nearbyEnemies(attacker.team, originX, originY, scanRadius, u -> {
            if(u == null || u.dead || !u.hittable()) return;
            if(u.isFlying() && !hitAir) return;
            if(!u.isFlying() && !hitGround) return;
            if(matchesShape(originX, originY, angle, u.x, u.y, u.hitSize * 0.5f)){
                tmpUnits.add(u);
            }
        });

        if(hitGround){
            for(Team team : Team.all){
                if(team == attacker.team || team.data().buildings.isEmpty()) continue;
                for(Building b : team.data().buildings){
                    if(b == null || !b.isAdded() || b.dead || !b.block.targetable) continue;
                    if(b.within(originX, originY, scanRadius + b.hitSize() * 0.5f)){
                        if(matchesShape(originX, originY, angle, b.x, b.y, b.hitSize() * 0.5f)){
                            tmpBuilds.add(b);
                        }
                    }
                }
            }
        }

        tmpUnits.sort(u -> attacker.dst2(u));
        tmpBuilds.sort(b -> attacker.dst2(b));

        int hits = 0;
        float dmgScale = attacker.damageMultiplier;

        for(int i = 0; i < tmpUnits.size && (maxTargets <= 0 || hits < maxTargets); i++){
            Unit target = tmpUnits.get(i);
            float dmg = damage * dmgScale;
            if(pierceArmor){
                target.damagePierce(dmg);
            }else{
                target.damage(dmg);
            }
            if(knockback > 0f){
                float pushAng = target.within(attacker.x, attacker.y, 1f) ? angle : Angles.angle(attacker.x, attacker.y, target.x, target.y);
                target.impulse(Angles.trnsx(pushAng, knockback * 26f), Angles.trnsy(pushAng, knockback * 26f));
            }
            if(hitStatus != null && hitStatus != StatusEffects.none && hitStatusDuration > 0f){
                target.apply(hitStatus, hitStatusDuration);
            }
            if(hitEffect != null && hitEffect != Fx.none){
                hitEffect.at(target.x, target.y, angle, accentColor);
            }
            hits++;
        }

        for(int i = 0; i < tmpBuilds.size && (maxTargets <= 0 || hits < maxTargets); i++){
            Building b = tmpBuilds.get(i);
            float dmg = damage * dmgScale * buildingDamageMultiplier;
            b.damage(attacker.team, dmg);
            if(hitEffect != null && hitEffect != Fx.none){
                hitEffect.at(b.x, b.y, angle, accentColor);
            }
            hits++;
        }

        if(hits > 0){
            if(hitSound != null && hitSound != Sounds.none){
                hitSound.at(originX, originY, Mathf.random(0.95f, 1.1f));
            }
            if(shake > 0f){
                Effect.shake(shake, shake, originX, originY);
            }
        }

        return hits;
    }

    private boolean matchesShape(float ox, float oy, float angle, float tx, float ty, float targetRadius){
        float dst = Mathf.dst(ox, oy, tx, ty);
        if(dst > reach + targetRadius) return false;

        if(shape == MeleeShape.SLAM){
            return true;
        }

        // Point-blank contact always connects
        if(dst <= targetRadius + 8f) return true;

        float toTarget = Angles.angle(ox, oy, tx, ty);
        float diff = Math.abs(Angles.angleDist(angle, toTarget));

        if(shape == MeleeShape.ARC){
            float edgeBonus = Mathf.clamp((targetRadius / Math.max(8f, dst)) * Mathf.radiansToDegrees, 0f, 35f);
            return diff <= arcAngle * 0.5f + edgeBonus;
        }

        // THRUST / line
        if(diff > 85f) return false;
        float perp = Mathf.sinDeg(diff) * dst;
        return perp <= halfWidth + targetRadius;
    }
            }
