package abilityadditions.core;

import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;

public class MeleeCommandAI extends CommandAI{

    public float detectThreatRange = 136f; // 17 tiles
    public float sprintStartEdgeDist = 36f;
    public float sprintStopEdgeDist = 24f;

    public float baseDesiredEdgeDist = 6f;
    public float standDesiredEdgeDist = 38f; // ~4.75 tiles standoff so Jotaro backs up cleanly behind Stand

    // Lunge Dash / Time-Skip Teleport (6s cooldown)
    public float lungeMinEdgeDist = 60f;
    public float lungeMaxEdgeDist = 148f;
    public float lungeCooldownDuration = 360f; // 6.0s
    public float lungeBurstTicks = 9f;
    public float lungeBurstSpeed = 7.4f;

    // Combat Flank Dodge Dash (3s cooldown, only near enemies in combat range)
    public float flankCombatMaxEdgeDist = 76f; // 9.5 tiles
    public float dodgeCooldownDuration = 180f; // 3.0s
    public float baseDodgeTicks = 6f;
    public float baseDodgeSpeed = 6.4f;

    public float lungeCooldown = 0f;
    public float dodgeCooldown = 0f;
    public float dashTimer = 0f;
    public float dashSpeed = 0f;
    public float dashAngle = 0f;
    public float threatAlertTimer = 0f;

    public boolean sprinting = false;
    public boolean standAwakened = false;
    public boolean standActiveOut = false;
    public boolean entrancePaused = false;
    public boolean activeFlankDash = false;
    public @Nullable Teamc flankTarget = null;
    public @Nullable Cons<Unit> onDashAudio;
    public @Nullable Cons2<Unit, Vec2> onTeleportBlink;

    public void notifyThreatened(){
        threatAlertTimer = 240f;
    }

    @Override
    public void updateTargeting(){
        if(attackTarget != null && Units.invalidateTarget(attackTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
            attackTarget = null;
        }
        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }

        if(attackTarget == null && targetPos == null && !hasStance(UnitStance.holdFire)){
            float scanRange = threatAlertTimer > 0f ? detectThreatRange * 1.35f : detectThreatRange;
            Teamc threat = Units.closestTarget(unit.team, unit.x, unit.y, scanRange,
                u -> !u.dead && !u.isFlying() && u.hittable(),
                b -> b.block.targetable && (threatAlertTimer > 0f || b.within(unit, 64f))
            );
            if(threat != null){
                attackTarget = threat;
            }
        }
    }

    @Override
    public void updateUnit(){
        if(lungeCooldown > 0f) lungeCooldown = Math.max(0f, lungeCooldown - Time.delta);
        if(dodgeCooldown > 0f) dodgeCooldown = Math.max(0f, dodgeCooldown - Time.delta);
        if(threatAlertTimer > 0f) threatAlertTimer = Math.max(0f, threatAlertTimer - Time.delta);

        if(entrancePaused){
            dashTimer = 0f;
            sprinting = false;
            activeFlankDash = false;
            unit.vel.setZero();
            stopAttacking();
            return;
        }

        super.updateUnit();

        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }
        if(attackTarget != null && Units.invalidateTarget(attackTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
            attackTarget = null;
        }

        if(dashTimer > 0f){
            dashTimer = Math.max(0f, dashTimer - Time.delta);
            if(activeFlankDash && flankTarget != null && !Units.invalidateTarget(flankTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
                float toTarget = Angles.angle(unit.x, unit.y, flankTarget.x(), flankTarget.y());
                float targetRad = flankTarget instanceof Sized s ? s.hitSize() * 0.5f : 6f;
                float dst = unit.dst(flankTarget);
                if(dst < targetRad + unit.hitSize * 0.6f + 10f){
                    dashAngle = toTarget + (Angles.forwardDistance(toTarget, dashAngle) >= 0f ? 66f : -66f);
                }
                unit.lookAt(flankTarget);
            }

            if(!isPathBlocked(dashAngle, 14f)){
                unit.vel.set(Angles.trnsx(dashAngle, dashSpeed), Angles.trnsy(dashAngle, dashSpeed));
            }else{
                dashTimer = 0f;
                activeFlankDash = false;
            }

            if(dashTimer <= 0f){
                activeFlankDash = false;
                if(attackTarget != null) unit.lookAt(attackTarget);
            }
        }

        if(dashTimer <= 0f){
            applyBulletWeaveSteering();
        }

        float punchReach = standActiveOut ? 54f : 28f;

        if(attackTarget == null){
            sprinting = false;

            if(!hasStance(UnitStance.holdFire)){
                Unit closeThreat = Units.closestEnemy(unit.team, unit.x, unit.y, punchReach, u -> !u.dead && !u.isFlying() && u.hittable());
                if(closeThreat != null){
                    unit.lookAt(closeThreat);
                    unit.aim(closeThreat);
                    unit.controlWeapons(true, true);
                    return;
                }

                // Also punch blocking walls or buildings right in reach
                Building closeBuild = findClosestEnemyBuilding(punchReach);
                if(closeBuild != null){
                    unit.lookAt(closeBuild);
                    unit.aim(closeBuild);
                    unit.controlWeapons(true, true);
                    return;
                }
            }
            stopAttacking();
            return;
        }

        float targetRad = attackTarget instanceof Sized s ? s.hitSize() * 0.5f : 0f;
        float edgeDst = unit.dst(attackTarget) - unit.hitSize * 0.5f - targetRad;
        boolean targetIsBuilding = attackTarget instanceof Building;

        float sprintStop = standActiveOut ? 44f : sprintStopEdgeDist;
        float sprintStart = standActiveOut ? 56f : sprintStartEdgeDist;

        if(!sprinting && edgeDst > sprintStart){
            sprinting = true;
        }else if(sprinting && edgeDst <= sprintStop){
            sprinting = false;
        }

        // Combat Flank Dodge Dash (units only; don't flank stationary walls)
        if(!targetIsBuilding && dodgeCooldown <= 0f && dashTimer <= 0f && edgeDst <= flankCombatMaxEdgeDist){
            boolean dangerNearby = findIncomingBullet(96f, 24f, unit.hitSize * 0.95f + 12f) != null || edgeDst <= 48f;
            if(dangerNearby){
                triggerCombatFlankDash(attackTarget, targetRad);
            }
        }

        // Lunge Dash OR Star Platinum Time-Skip Teleport (6s CD)
        if(lungeCooldown <= 0f && dashTimer <= 0f && edgeDst >= lungeMinEdgeDist && edgeDst <= lungeMaxEdgeDist){
            if(standAwakened){
                tryTimeSkipTeleport(attackTarget, targetRad);
            }else{
                float chaseAng = Angles.angle(unit.x, unit.y, attackTarget.x(), attackTarget.y());
                if(!isPathBlocked(chaseAng, 48f)){
                    dashAngle = chaseAng;
                    dashTimer = lungeBurstTicks;
                    dashSpeed = lungeBurstSpeed;
                    activeFlankDash = false;
                    lungeCooldown = lungeCooldownDuration;
                    unit.vel.set(Angles.trnsx(dashAngle, dashSpeed), Angles.trnsy(dashAngle, dashSpeed));
                    if(onDashAudio != null) onDashAudio.get(unit);
                }
            }
        }

        // Auto-backstep & standoff against enemy units (stay planted to smash walls/buildings)
        if(dashTimer <= 0f){
            float desiredEdge = (standActiveOut && !targetIsBuilding) ? standDesiredEdgeDist : baseDesiredEdgeDist;
            float desiredCenterDist = unit.hitSize * 0.5f + targetRad + desiredEdge;

            if(standActiveOut && !targetIsBuilding && edgeDst < 30f){
                float backAng = Angles.angle(attackTarget.x(), attackTarget.y(), unit.x, unit.y);
                if(!isPathBlocked(backAng, 16f)){
                    unit.moveAt(Tmp.v1.trns(backAng, unit.speed() * 1.15f));
                }
                unit.lookAt(attackTarget);
            }else if(edgeDst > desiredEdge + 4f && edgeDst <= 96f){
                moveTo(attackTarget, desiredCenterDist);
            }else if(edgeDst <= punchReach){
                unit.vel.scl(0.80f);
                unit.lookAt(attackTarget);
            }
        }

        if(!hasStance(UnitStance.holdFire) && edgeDst <= punchReach){
            unit.lookAt(attackTarget);
            unit.aim(attackTarget);
            unit.controlWeapons(true, true);
        }else{
            // If blocked by an enemy wall while walking to attackTarget, smash the blocking wall!
            if(!hasStance(UnitStance.holdFire)){
                Building blocker = findClosestEnemyBuilding(punchReach);
                if(blocker != null){
                    unit.lookAt(blocker);
                    unit.aim(blocker);
                    unit.controlWeapons(true, true);
                    return;
                }
            }
            stopAttacking();
        }
    }

    private @Nullable Building findClosestEnemyBuilding(float maxEdgeDist){
        Building best = null;
        float bestDist = maxEdgeDist;
        for(Team t : Team.all){
            if(t == unit.team || t.data().buildings.isEmpty()) continue;
            for(Building b : t.data().buildings){
                if(b == null || !b.isAdded() || b.dead || !b.block.targetable) continue;
                float edgeDst = unit.dst(b) - unit.hitSize * 0.5f - b.hitSize() * 0.5f;
                if(edgeDst <= bestDist){
                    bestDist = edgeDst;
                    best = b;
                }
            }
        }
        return best;
    }

    private void tryTimeSkipTeleport(Teamc target, float targetRad){
        float angToTarget = Angles.angle(unit.x, unit.y, target.x(), target.y());
        float standOff = targetRad + unit.hitSize * 0.5f + standDesiredEdgeDist;

        float baseDir = target instanceof Unit u ? u.rotation + 180f : angToTarget;
        float[] testAngles = {baseDir, baseDir + 45f, baseDir - 45f, angToTarget + 180f};

        for(float dir : testAngles){
            float tx = target.x() + Angles.trnsx(dir, standOff);
            float ty = target.y() + Angles.trnsy(dir, standOff);
            Tile tile = Vars.world.tileWorld(tx, ty);
            if(tile != null && !tile.solid()){
                Vec2 oldPos = new Vec2(unit.x, unit.y);
                unit.set(tx, ty);
                unit.vel.setZero();
                unit.lookAt(target);
                lungeCooldown = lungeCooldownDuration;
                if(onTeleportBlink != null) onTeleportBlink.get(unit, oldPos);
                return;
            }
        }
    }

    private void triggerCombatFlankDash(Teamc target, float targetRad){
        float toEnemy = Angles.angle(unit.x, unit.y, target.x(), target.y());
        float sideSign = Mathf.chance(0.5f) ? 1f : -1f;
        float sizeScale = Mathf.clamp(1f + (targetRad - 5.5f) * 0.055f, 0.95f, 2.25f);
        float checkLen = 26f * sizeScale;

        float[] offsets = {sideSign * 56f, -sideSign * 56f, sideSign * 78f, -sideSign * 78f, sideSign * 40f, -sideSign * 40f, sideSign * 96f};
        float chosen = Float.NaN;
        for(float off : offsets){
            float ang = toEnemy + off;
            if(!isPathBlocked(ang, checkLen)){
                chosen = ang;
                break;
            }
        }
        if(Float.isNaN(chosen)) return;

        dashAngle = chosen;
        dashTimer = baseDodgeTicks * Mathf.lerp(1f, sizeScale, 0.55f);
        dashSpeed = baseDodgeSpeed * Mathf.lerp(1f, sizeScale, 0.65f);
        dodgeCooldown = dodgeCooldownDuration;
        activeFlankDash = true;
        flankTarget = target;
        unit.vel.set(Angles.trnsx(dashAngle, dashSpeed), Angles.trnsy(dashAngle, dashSpeed));
        if(onDashAudio != null) onDashAudio.get(unit);
    }

    private void stopAttacking(){
        unit.controlWeapons(false, false);
        for(WeaponMount m : unit.mounts){
            m.shoot = false;
        }
    }

    private void applyBulletWeaveSteering(){
        Position goal = attackTarget != null ? attackTarget : targetPos;
        if(goal == null || unit.within(goal, 28f)) return;

        Bullet nearestThreat = findIncomingBullet(132f, 34f, unit.hitSize + 18f);
        if(nearestThreat == null) return;

        float goalAng = Angles.angle(unit.x, unit.y, goal.getX(), goal.getY());
        float bAng = nearestThreat.vel.angle();

        float leftWeave = goalAng + 52f;
        float rightWeave = goalAng - 52f;

        float lx = unit.x + Angles.trnsx(leftWeave, 18f);
        float ly = unit.y + Angles.trnsy(leftWeave, 18f);
        float rx = unit.x + Angles.trnsx(rightWeave, 18f);
        float ry = unit.y + Angles.trnsy(rightWeave, 18f);

        float bDirX = Mathf.cosDeg(bAng);
        float bDirY = Mathf.sinDeg(bAng);
        float leftDist = Math.abs((lx - nearestThreat.x) * bDirY - (ly - nearestThreat.y) * bDirX);
        float rightDist = Math.abs((rx - nearestThreat.x) * bDirY - (ry - nearestThreat.y) * bDirX);

        float chosen = leftDist >= rightDist ? leftWeave : rightWeave;
        if(isPathBlocked(chosen, 20f)){
            chosen = chosen == leftWeave ? rightWeave : leftWeave;
            if(isPathBlocked(chosen, 20f)) return;
        }

        unit.moveAt(Tmp.v1.trns(chosen, unit.speed()));
    }

    private @Nullable Bullet findIncomingBullet(float maxDist, float maxTicksAhead, float corridorHalfWidth){
        Bullet best = null;
        float bestTime = Float.MAX_VALUE;

        for(Bullet b : Groups.bullet){
            if(b == null || !b.isAdded() || b.team == unit.team) continue;
            if(b.damage + b.type.splashDamage < 5f) continue;

            float speed = b.vel.len();
            if(speed < 0.8f) continue;

            float dst = unit.dst(b);
            if(dst > maxDist) continue;
            if(b.type.homingPower > 0f && dst > 44f) continue;

            float vx = b.vel.x / speed;
            float vy = b.vel.y / speed;
            float dx = unit.x - b.x;
            float dy = unit.y - b.y;

            float forwardProj = dx * vx + dy * vy;
            if(forwardProj < -4f) continue;

            float timeToClosest = forwardProj / speed;
            if(timeToClosest > maxTicksAhead) continue;

            float closestX = b.x + vx * forwardProj;
            float closestY = b.y + vy * forwardProj;
            float missDist = Mathf.dst(unit.x, unit.y, closestX, closestY);

            if(missDist <= corridorHalfWidth + b.hitSize && timeToClosest < bestTime){
                bestTime = timeToClosest;
                best = b;
            }
        }
        return best;
    }

    private boolean isPathBlocked(float angle, float checkDist){
        for(float d = 10f; d <= checkDist; d += 8f){
            float wx = unit.x + Angles.trnsx(angle, d);
            float wy = unit.y + Angles.trnsy(angle, d);
            Tile tile = Vars.world.tileWorld(wx, wy);
            if(tile == null || tile.solid()){
                return true;
            }
        }
        return false;
    }
                } 
