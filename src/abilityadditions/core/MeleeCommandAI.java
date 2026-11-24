package abilityadditions.core;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;

public class MeleeCommandAI extends CommandAI{

    public float detectThreatRange = 136f; // 17 tiles
    public float sprintStartEdgeDist = 36f;
    public float sprintStopEdgeDist = 24f;

    // Ability 1A: Long Lunge Dash (Gap Closer)
    public float lungeMinEdgeDist = 64f;
    public float lungeMaxEdgeDist = 136f;
    public float lungeCooldownDuration = 360f; // 6.0s
    public float lungeBurstTicks = 9f;
    public float lungeBurstSpeed = 7.4f;

    // Ability 1B: Short Dodge Dash + Continuous Bullet Weave
    public float dodgeCooldownDuration = 180f; // 3.0s
    public float dodgeBurstTicks = 6f;
    public float dodgeBurstSpeed = 6.2f;

    public float lungeCooldown = 0f;
    public float dodgeCooldown = 0f;
    public float dashTimer = 0f;
    public float dashSpeed = 0f;
    public float dashAngle = 0f;
    public float threatAlertTimer = 0f;

    public boolean sprinting = false;
    public boolean lungeTriggeredThisTick = false;
    public boolean dodgeTriggeredThisTick = false;

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
                b -> b.block.targetable && threatAlertTimer > 0f
            );
            if(threat != null){
                attackTarget = threat;
            }
        }
    }

    @Override
    public void updateUnit(){
        lungeTriggeredThisTick = false;
        dodgeTriggeredThisTick = false;

        if(lungeCooldown > 0f) lungeCooldown = Math.max(0f, lungeCooldown - Time.delta);
        if(dodgeCooldown > 0f) dodgeCooldown = Math.max(0f, dodgeCooldown - Time.delta);
        if(threatAlertTimer > 0f) threatAlertTimer = Math.max(0f, threatAlertTimer - Time.delta);

        // Run base CommandAI first so our weave & dash velocity never get overwritten on the same tick
        super.updateUnit();

        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }
        if(attackTarget != null && Units.invalidateTarget(attackTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
            attackTarget = null;
        }

        // 1. Active dash burst overrides velocity AFTER super.updateUnit()
        if(dashTimer > 0f){
            dashTimer = Math.max(0f, dashTimer - Time.delta);
            if(!isPathBlocked(dashAngle, 14f)){
                unit.vel.set(Angles.trnsx(dashAngle, dashSpeed), Angles.trnsy(dashAngle, dashSpeed));
            }else{
                dashTimer = 0f;
            }
        }

        // 2. Smooth continuous bullet weaving while moving/chasing toward a goal
        if(dashTimer <= 0f){
            applyBulletWeaveSteering();
        }

        // 3. Snap Dodge Dash (3s cooldown) when a bullet is about to hit
        if(dodgeCooldown <= 0f && dashTimer <= 0f){
            tryDodgeDash();
        }

        // 4. Target handling + clean weapon stop after kill
        if(attackTarget == null){
            sprinting = false;

            Unit closeThreat = !hasStance(UnitStance.holdFire)
                ? Units.closestEnemy(unit.team, unit.x, unit.y, 32f, u -> !u.dead && !u.isFlying() && u.hittable())
                : null;

            if(closeThreat != null){
                unit.lookAt(closeThreat);
                unit.aim(closeThreat);
                unit.controlWeapons(true, true);
            }else{
                stopAttacking();
            }
            return;
        }

        float targetRad = attackTarget instanceof Sized s ? s.hitSize() * 0.5f : 0f;
        float edgeDst = unit.dst(attackTarget) - unit.hitSize * 0.5f - targetRad;

        if(!sprinting && edgeDst > sprintStartEdgeDist){
            sprinting = true;
        }else if(sprinting && edgeDst <= sprintStopEdgeDist){
            sprinting = false;
        }

        // 5. Offensive Lunge Dash (6s cooldown) to close medium/long gap
        if(lungeCooldown <= 0f && dashTimer <= 0f && edgeDst >= lungeMinEdgeDist && edgeDst <= lungeMaxEdgeDist){
            float chaseAng = Angles.angle(unit.x, unit.y, attackTarget.x(), attackTarget.y());
            if(!isPathBlocked(chaseAng, 48f)){
                dashAngle = chaseAng;
                dashTimer = lungeBurstTicks;
                dashSpeed = lungeBurstSpeed;
                lungeCooldown = lungeCooldownDuration;
                lungeTriggeredThisTick = true;
                unit.vel.set(Angles.trnsx(dashAngle, dashSpeed), Angles.trnsy(dashAngle, dashSpeed));
            }
        }

        // Tight edge-to-edge approach (only when not mid-dash)
        float desiredCenterDist = unit.hitSize * 0.5f + targetRad + 5f;
        if(edgeDst > 10f && edgeDst <= 64f && dashTimer <= 0f){
            moveTo(attackTarget, desiredCenterDist);
        }

        float punchReach = Math.max(unit.range(), 26f);
        if(!hasStance(UnitStance.holdFire) && edgeDst <= punchReach){
            unit.lookAt(attackTarget);
            unit.aim(attackTarget);
            unit.controlWeapons(true, true);
        }else{
            stopAttacking();
        }
    }

    private void stopAttacking(){
        unit.controlWeapons(false, false);
        for(WeaponMount m : unit.mounts){
            m.shoot = false;
        }
    }

    private void applyBulletWeaveSteering(){
        Position goal = attackTarget != null ? attackTarget : targetPos;
        if(goal == null || unit.within(goal, 26f)) return;

        Bullet nearestThreat = findIncomingBullet(132f, 34f, unit.hitSize + 18f);
        if(nearestThreat == null) return;

        float goalAng = Angles.angle(unit.x, unit.y, goal.getX(), goal.getY());
        float bAng = nearestThreat.vel.angle();

        // Pick whichever weave side (+52 deg or -52 deg) steers away from the bullet line
        float leftWeave = goalAng + 52f;
        float rightWeave = goalAng - 52f;

        float lx = unit.x + Angles.trnsx(leftWeave, 18f);
        float ly = unit.y + Angles.trnsy(leftWeave, 18f);
        float rx = unit.x + Angles.trnsx(rightWeave, 18f);
        float ry = unit.y + Angles.trnsy(rightWeave, 18f);

        float bDirX = Mathf.cosDeg(bAng);
        float bDirY = Mathf.sinDeg(bAng);
        float leftDistFromLine = Math.abs((lx - nearestThreat.x) * bDirY - (ly - nearestThreat.y) * bDirX);
        float rightDistFromLine = Math.abs((rx - nearestThreat.x) * bDirY - (ry - nearestThreat.y) * bDirX);

        float chosen = leftDistFromLine >= rightDistFromLine ? leftWeave : rightWeave;
        if(isPathBlocked(chosen, 20f)){
            chosen = chosen == leftWeave ? rightWeave : leftWeave;
            if(isPathBlocked(chosen, 20f)) return;
        }

        unit.moveAt(Tmp.v1.trns(chosen, unit.speed()));
    }

    private void tryDodgeDash(){
        Bullet threatBullet = findIncomingBullet(96f, 20f, unit.hitSize * 0.85f + 8f);
        if(threatBullet == null) return;

        float bAng = threatBullet.vel.angle();
        Position goal = attackTarget != null ? attackTarget : targetPos;

        float leftPerp = bAng + 90f;
        float rightPerp = bAng - 90f;
        float candidateA = leftPerp;
        float candidateB = rightPerp;

        // Blend diagonally forward while running toward a goal so dodge flows smoothly with movement
        if(goal != null && !unit.within(goal, 28f)){
            float goalAng = Angles.angle(unit.x, unit.y, goal.getX(), goal.getY());
            candidateA = blendAngles(leftPerp, goalAng, 0.40f);
            candidateB = blendAngles(rightPerp, goalAng, 0.40f);

            if(Math.abs(Angles.angleDist(candidateB, goalAng)) < Math.abs(Angles.angleDist(candidateA, goalAng))){
                float tmp = candidateA;
                candidateA = candidateB;
                candidateB = tmp;
            }
        }

        if(!isPathBlocked(candidateA, 26f)){
            triggerDodge(candidateA);
        }else if(!isPathBlocked(candidateB, 26f)){
            triggerDodge(candidateB);
        }
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

    private void triggerDodge(float angle){
        dashAngle = angle;
        dashTimer = dodgeBurstTicks;
        dashSpeed = dodgeBurstSpeed;
        dodgeCooldown = dodgeCooldownDuration;
        dodgeTriggeredThisTick = true;
        unit.vel.set(Angles.trnsx(dashAngle, dashSpeed), Angles.trnsy(dashAngle, dashSpeed));
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
