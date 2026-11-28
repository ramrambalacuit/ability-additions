package abilityadditions.melee.core;

import arc.func.*;
import arc.math.*;
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

    public float viewRange = 160f;
    public float meleeEdgeReach = 24f;
    public float desiredEdgeDist = 16f;
    public float tooCloseEdgeDist = 10f;

    public float walkStartEdgeDist = 80f;
    public float sprintResumeEdgeDist = 94f;

    public float lungeMinEdgeDist = 30f;
    public float lungeMaxEdgeDist = 62f;
    public float lungeCooldownDuration = 240f;
    public float lungeBurstTicks = 8f;
    public float lungeBurstSpeed = 4.2f;

    public float zigzagSpeed = 0.92f;

    public boolean canSprint = true;
    public boolean canLunge = true;
    public boolean canZigzag = true;
    public boolean marchEnemyCore = true;

    public boolean sprinting = false;
    public float lungeCooldown = 0f;
    public float dashTimer = 0f;
    public float dashSpeed = 0f;
    public float dashAngle = 0f;

    protected float threatAlertTimer = 0f;
    protected int zigzagDir = 1;
    protected float zigzagSwitchTimer = 0f;
    protected float zigzagRotOffset = 0f;
    protected float zigzagRotTimer = 0f;

    public @Nullable Cons<Unit> onLungeStart;

    public MeleeCommandAI(){
        zigzagDir = Mathf.chance(0.5f) ? 1 : -1;
        zigzagSwitchTimer = Mathf.random(55f, 105f);
        zigzagRotOffset = Mathf.range(22f);
        zigzagRotTimer = Mathf.random(18f, 36f);
    }

    public void notifyThreatened(){
        threatAlertTimer = 240f;
    }

    @Override
    public void updateTargeting(){
        if(targetPos != null && !unit.within(targetPos, 10f)){
            attackTarget = null;
            return;
        }

        if(attackTarget != null){
            if(!attackTarget.isAdded()
                || (attackTarget instanceof Healthc h && h.dead())
                || (attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir)){
                attackTarget = null;
            }
        }

        if(attackTarget == null && targetPos == null && !hasStance(UnitStance.holdFire)){
            float scanRange = threatAlertTimer > 0f ? viewRange * 1.25f : viewRange;
            Unit closest = Units.closestEnemy(unit.team, unit.x, unit.y, scanRange,
                u -> !u.dead && (unit.type.targetAir || !u.isFlying()) && u.hittable());
            if(closest != null){
                attackTarget = closest;
                return;
            }

            Building build = findClosestEnemyBuilding(scanRange);
            if(build != null){
                attackTarget = build;
            }
        }
    }

    @Override
    public void updateUnit(){
        lungeCooldown = Math.max(0f, lungeCooldown - Time.delta);
        threatAlertTimer = Math.max(0f, threatAlertTimer - Time.delta);

        boolean holdFire = hasStance(UnitStance.holdFire);
        boolean aiMarching = marchEnemyCore && !unit.isPlayer() && unit.team != Vars.state.rules.defaultTeam;

        if(targetPos != null && !unit.within(targetPos, 10f)){
            attackTarget = null;
        }

        super.updateUnit();

        if(targetPos != null && !unit.within(targetPos, 10f)){
            attackTarget = null;
            dashTimer = 0f;
            sprinting = canSprint;
            stopAttacking();
            return;
        }

        if(dashTimer > 0f){
            dashTimer = Math.max(0f, dashTimer - Time.delta);
            if(isPathBlocked(dashAngle, 12f)){
                dashTimer = 0f;
                unit.vel.setZero();
            }else{
                unit.vel.trns(dashAngle, dashSpeed);
                unit.lookAt(dashAngle);
            }
            attackWithinReach(holdFire);
            return;
        }

        if(attackTarget == null){
            if(aiMarching && targetPos == null){
                Building core = unit.closestEnemyCore();
                if(core != null){
                    float coreEdge = Math.max(0f, unit.dst(core) - unit.hitSize * 0.5f - core.hitSize() * 0.5f);
                    if(coreEdge <= viewRange){
                        attackTarget = core;
                    }else{
                        pathfind(Pathfinder.fieldCore);
                        faceMovement();
                    }
                }else{
                    pathfind(Pathfinder.fieldCore);
                    faceMovement();
                }
            }

            boolean hasMoveGoal = (targetPos != null && !unit.within(targetPos, 12f)) || aiMarching;
            sprinting = canSprint && (hasMoveGoal || unit.vel.len() > unit.type.speed * 0.45f);

            if(!holdFire){
                Unit closeUnit = Units.closestEnemy(unit.team, unit.x, unit.y, meleeEdgeReach + unit.hitSize * 0.5f + 12f,
                    u -> !u.dead && (unit.type.targetAir || !u.isFlying()) && u.hittable()
                        && (unit.dst(u) - unit.hitSize * 0.5f - u.hitSize * 0.5f) <= meleeEdgeReach);
                if(closeUnit != null){
                    aimAndStrike(closeUnit);
                    return;
                }
                Building closeBuild = findClosestEnemyBuilding(meleeEdgeReach);
                if(closeBuild != null){
                    aimAndStrike(closeBuild);
                    return;
                }
            }
            stopAttacking();
            return;
        }

        float targetRad = attackTarget instanceof Sized s ? s.hitSize() * 0.5f : 4f;
        float centerDst = unit.dst(attackTarget);
        float edgeDst = Math.max(0f, centerDst - unit.hitSize * 0.5f - targetRad);
        float aimAngle = unit.angleTo(attackTarget);

        if(canSprint){
            if(sprinting){
                if(edgeDst <= walkStartEdgeDist){
                    sprinting = false;
                }
            }else if(edgeDst >= sprintResumeEdgeDist){
                sprinting = true;
            }
        }else{
            sprinting = false;
        }

        if(canLunge && lungeCooldown <= 0f && edgeDst >= lungeMinEdgeDist && edgeDst <= lungeMaxEdgeDist){
            if(!isPathBlocked(aimAngle, Math.min(edgeDst + 10f, lungeBurstSpeed * lungeBurstTicks))){
                dashAngle = aimAngle;
                dashSpeed = lungeBurstSpeed;
                dashTimer = lungeBurstTicks;
                lungeCooldown = lungeCooldownDuration;
                sprinting = false;
                unit.vel.trns(dashAngle, dashSpeed);
                unit.lookAt(dashAngle);
                if(onLungeStart != null) onLungeStart.get(unit);
                attackWithinReach(holdFire);
                return;
            }
        }

        float holdDist = Math.max(4f, desiredEdgeDist);
        float minDist = Math.min(holdDist * 0.7f, tooCloseEdgeDist);

        if(edgeDst <= meleeEdgeReach + 14f){
            if(attackTarget instanceof Unit && canZigzag){
                zigzagSwitchTimer -= Time.delta;
                if(zigzagSwitchTimer <= 0f){
                    zigzagDir = -zigzagDir;
                    zigzagSwitchTimer = Mathf.random(55f, 105f);
                }

                zigzagRotTimer -= Time.delta;
                if(zigzagRotTimer <= 0f){
                    zigzagRotOffset = Mathf.range(24f);
                    zigzagRotTimer = Mathf.random(18f, 38f);
                }

                float tangentAng = aimAngle + 90f * zigzagDir;
                if(isPathBlocked(tangentAng, 16f)){
                    zigzagDir = -zigzagDir;
                    tangentAng = aimAngle + 90f * zigzagDir;
                }

                float radialPull = 0f;
                if(edgeDst < minDist){
                    radialPull = -0.65f;
                }else if(edgeDst > holdDist + 2f){
                    radialPull = 0.55f;
                }

                Tmp.v1.trns(tangentAng, zigzagSpeed * unit.type.speed);
                if(Math.abs(radialPull) > 0.01f){
                    Tmp.v2.trns(aimAngle, radialPull * unit.type.speed);
                    Tmp.v1.add(Tmp.v2);
                }
                if(!isPathBlocked(Tmp.v1.angle(), 10f)){
                    unit.moveAt(Tmp.v1);
                }else{
                    unit.vel.scl(0.75f);
                }

                unit.lookAt(aimAngle + zigzagDir * 14f + zigzagRotOffset);
            }else{
                unit.lookAt(aimAngle);
                if(edgeDst < minDist){
                    float backAng = aimAngle + 180f;
                    if(!isPathBlocked(backAng, 10f)){
                        unit.moveAt(Tmp.v1.trns(backAng, unit.type.speed * 0.65f));
                    }
                }else if(edgeDst <= holdDist + 2f){
                    unit.vel.scl(0.65f);
                }
            }
        }else if(edgeDst > holdDist + 2f && unit.vel.len() < unit.type.speed * 0.35f && !isPathBlocked(aimAngle, 12f)){
            unit.moveAt(Tmp.v1.trns(aimAngle, unit.type.speed));
        }

        if(!holdFire && edgeDst <= meleeEdgeReach){
            aimAndStrike(attackTarget);
        }else if(!holdFire){
            Building blocker = findClosestEnemyBuilding(meleeEdgeReach);
            if(blocker != null){
                aimAndStrike(blocker);
            }else{
                stopAttacking();
            }
        }else{
            stopAttacking();
        }
    }

    protected void attackWithinReach(boolean holdFire){
        if(holdFire){
            stopAttacking();
            return;
        }
        if(attackTarget != null){
            float targetRad = attackTarget instanceof Sized s ? s.hitSize() * 0.5f : 4f;
            float edgeDst = Math.max(0f, unit.dst(attackTarget) - unit.hitSize * 0.5f - targetRad);
            if(edgeDst <= meleeEdgeReach){
                aimAndStrike(attackTarget);
                return;
            }
        }
        stopAttacking();
    }

    protected void aimAndStrike(Teamc target){
        unit.lookAt(target);
        unit.aim(target.getX(), target.getY());
        unit.controlWeapons(true, true);
        if(unit.mounts != null){
            for(WeaponMount m : unit.mounts){
                m.aimX = target.getX();
                m.aimY = target.getY();
                m.shoot = true;
            }
        }
    }

    protected void stopAttacking(){
        unit.controlWeapons(false, false);
        if(unit.mounts != null){
            for(WeaponMount m : unit.mounts){
                m.shoot = false;
            }
        }
    }

    protected @Nullable Building findClosestEnemyBuilding(float maxEdgeDist){
        Building best = null;
        float bestDst = maxEdgeDist;
        for(Team t : Team.all){
            if(t == unit.team || t.data().buildings.isEmpty()) continue;
            for(Building b : t.data().buildings){
                if(b == null || !b.isAdded() || b.dead || !b.block.targetable) continue;
                float edgeDst = Math.max(0f, unit.dst(b) - unit.hitSize * 0.5f - b.hitSize() * 0.5f);
                if(edgeDst <= bestDst){
                    bestDst = edgeDst;
                    best = b;
                }
            }
        }
        return best;
    }

    protected boolean isPathBlocked(float angle, float checkDist){
        for(float d = 8f; d <= checkDist; d += 8f){
            Tile t = Vars.world.tileWorld(unit.x + Angles.trnsx(angle, d), unit.y + Angles.trnsy(angle, d));
            if(t == null || t.solid()) return true;
        }
        return false;
    }
    }
