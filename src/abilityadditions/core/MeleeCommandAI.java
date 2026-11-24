package abilityadditions.core;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.world.*;

public class MeleeCommandAI extends CommandAI{

    public float detectThreatRange = 136f; // 17 tiles (independent of melee reach)
    public float sprintStartEdgeDist = 36f;
    public float sprintStopEdgeDist = 24f;

    public float dashMinEdgeDist = 42f;
    public float dashMaxEdgeDist = 112f;
    public float dashCooldownDuration = 210f; // 3.5s
    public float dashBurstTicks = 7f;
    public float dashBurstSpeed = 8.4f;

    public float dashCooldown = 0f;
    public float dashTimer = 0f;
    public float dashAngle = 0f;
    public float threatAlertTimer = 0f;

    public boolean sprinting = false;
    public boolean dashTriggeredThisTick = false;

    public void notifyThreatened(){
        threatAlertTimer = 240f; // 4s alert when attacked
    }

    @Override
    public void updateTargeting(){
        if(attackTarget != null && Units.invalidateTarget(attackTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
            attackTarget = null;
        }
        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }

        // Acquire ground threats within detectThreatRange (or when hurt) if not given a manual move order
        if(attackTarget == null && targetPos == null && !hasStance(UnitStance.holdFire)){
            float scanRange = threatAlertTimer > 0f ? detectThreatRange * 1.35f : detectThreatRange;
            Teamc threat = Units.closestTarget(unit.team, unit.x, unit.y, scanRange,
                u -> !u.dead && !u.isFlying() && u.hittable(),
                b -> b.block.targetable && threatAlertTimer > 0f
            );
            if(threat != null){
                attackTarget = threat;
            }
        }
    }

    @Override
    public void updateUnit(){
        dashTriggeredThisTick = false;
        if(dashCooldown > 0f) dashCooldown = Math.max(0f, dashCooldown - Time.delta);
        if(threatAlertTimer > 0f) threatAlertTimer = Math.max(0f, threatAlertTimer - Time.delta);

        // Active dash burst velocity (overcomes ground mech drag cleanly)
        if(dashTimer > 0f){
            dashTimer = Math.max(0f, dashTimer - Time.delta);
            if(!isPathBlocked(dashAngle, 14f)){
                unit.vel.set(Angles.trnsx(dashAngle, dashBurstSpeed), Angles.trnsy(dashAngle, dashBurstSpeed));
            }else{
                dashTimer = 0f;
            }
        }

        super.updateUnit();

        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }

        // Auto-weave dodge incoming bullets (even when idle or mid-chase!)
        if(dashCooldown <= 0f && dashTimer <= 0f){
            tryWeaveBulletDodge();
        }

        if(attackTarget == null || Units.invalidateTarget(attackTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
            sprinting = false;

            // Even while walking to a targetPos, punch back any ground threat right in his face
            if(!hasStance(UnitStance.holdFire)){
                Unit closeThreat = Units.closestEnemy(unit.team, unit.x, unit.y, 34f, u -> !u.dead && !u.isFlying());
                if(closeThreat != null){
                    unit.lookAt(closeThreat);
                    unit.aim(closeThreat);
                    unit.controlWeapons(true, true);
                }
            }
            return;
        }

        float targetRad = attackTarget instanceof Sized s ? s.hitSize() * 0.5f : 0f;
        float edgeDst = unit.dst(attackTarget) - unit.hitSize * 0.5f - targetRad;

        // Sprint when chasing from far; stop sprinting smoothly once close
        if(!sprinting && edgeDst > sprintStartEdgeDist){
            sprinting = true;
        }else if(sprinting && edgeDst <= sprintStopEdgeDist){
            sprinting = false;
        }

        // Offensive gap-close dash for M1 (only when clear path & outside punch reach)
        if(dashCooldown <= 0f && dashTimer <= 0f && edgeDst >= dashMinEdgeDist && edgeDst <= dashMaxEdgeDist){
            float chaseAng = Angles.angle(unit.x, unit.y, attackTarget.x(), attackTarget.y());
            if(!isPathBlocked(chaseAng, 34f)){
                triggerDash(chaseAng);
            }
        }

        // Move tightly up to edge-to-edge melee distance
        float desiredCenterDist = unit.hitSize * 0.5f + targetRad + 5f;
        if(edgeDst > 10f && edgeDst <= 64f && dashTimer <= 0f){
            moveTo(attackTarget, desiredCenterDist);
        }

        if(!hasStance(UnitStance.holdFire) && edgeDst <= Math.max(unit.range(), 26f)){
            unit.lookAt(attackTarget);
            unit.aim(attackTarget);
            unit.controlWeapons(true, true);
        }
    }

    private void tryWeaveBulletDodge(){
        Bullet threatBullet = null;
        float bestUrgency = Float.MAX_VALUE;

        for(Bullet b : Groups.bullet){
            if(b == null || !b.isAdded() || b.team == unit.team) continue;
            if(b.damage + b.type.splashDamage < 5f) continue;

            float speed = b.vel.len();
            if(speed < 0.8f) continue;

            float dst = unit.dst(b);
            if(dst > 104f) continue;

            // Ignore homing bullets until close so dash isn't wasted early
            if(b.type.homingPower > 0f && dst > 44f) continue;

            // Vector from bullet to unit
            float dx = unit.x - b.x;
            float dy = unit.y - b.y;
            float vx = b.vel.x / speed;
            float vy = b.vel.y / speed;

            float forwardProj = dx * vx + dy * vy;
            if(forwardProj < -4f) continue; // already passed

            float timeToClosest = forwardProj / speed;
            if(timeToClosest > 24f) continue; // more than 0.4s away

            float closestX = b.x + vx * forwardProj;
            float closestY = b.y + vy * forwardProj;
            float missDist = Mathf.dst(unit.x, unit.y, closestX, closestY);
            float dangerRadius = unit.hitSize * 0.85f + b.hitSize + 6f;

            if(missDist <= dangerRadius && timeToClosest < bestUrgency){
                bestUrgency = timeToClosest;
                threatBullet = b;
            }
        }

        if(threatBullet == null) return;

        float bAng = threatBullet.vel.angle();
        Position goal = attackTarget != null ? attackTarget : targetPos;

        float leftPerp = bAng + 90f;
        float rightPerp = bAng - 90f;
        float candidateA = leftPerp;
        float candidateB = rightPerp;

        // If moving/chasing toward a goal, blend dodge diagonally forward so he weaves smoothly toward enemy!
        if(goal != null && !unit.within(goal, 28f)){
            float goalAng = Angles.angle(unit.x, unit.y, goal.getX(), goal.getY());
            // Pick +/-55 deg weave offset around goal or blend perpendicular toward goal
            candidateA = blendAngles(leftPerp, goalAng, 0.42f);
            candidateB = blendAngles(rightPerp, goalAng, 0.42f);

            // Prefer the weave direction closer to the goal
            if(Math.abs(Angles.angleDist(candidateB, goalAng)) < Math.abs(Angles.angleDist(candidateA, goalAng))){
                float tmp = candidateA;
                candidateA = candidateB;
                candidateB = tmp;
            }
        }

        if(!isPathBlocked(candidateA, 34f)){
            triggerDash(candidateA);
        }else if(!isPathBlocked(candidateB, 34f)){
            triggerDash(candidateB);
        }
    }

    private void triggerDash(float angle){
        dashAngle = angle;
        dashTimer = dashBurstTicks;
        dashCooldown = dashCooldownDuration;
        dashTriggeredThisTick = true;
    }

    private float blendAngles(float a, float b, float t){
        float vx = Mathf.cosDeg(a) * (1f - t) + Mathf.cosDeg(b) * t;
        float vy = Mathf.sinDeg(a) * (1f - t) + Mathf.sinDeg(b) * t;
        return Angles.angle(0f, 0f, vx, vy);
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
