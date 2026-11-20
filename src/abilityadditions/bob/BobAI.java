package abilityadditions.bob;

import abilityadditions.content.AbilityEffects;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.AIController;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.world.Tile;

public class BobAI extends AIController{
    public enum State{
        NEUTRAL,
        CURIOUS,
        PRANKSTER,
        SKIDADDLE
    }

    private State state = State.NEUTRAL;
    private float stateTimer = 0f;
    private float idlePauseTimer = 0f;
    private float prankShotsLeft = 0f;
    private int lastPrankedId = -1;
    private float dashCooldown = 0f;
    private float smokeCloakTimer = 0f;
    private float smokeTrailTimer = 0f;
    private float sweatTimer = 0f;
    private float lastHealth = -1f;
    private float fleeAngle = 0f;
    private boolean usedSmokeBomb = false;
    private boolean sprinting = false;

    private final Vec2 wanderPos = new Vec2();
    private final Vec2 movePos = new Vec2();
    private final Vec2 dodgeVec = new Vec2();

    public boolean isSmokeCloaked(){
        return smokeCloakTimer > 0f;
    }

    @Override
    public void updateUnit(){
        if(unit == null || !unit.isValid()) return;

        if(lastHealth < 0f){
            lastHealth = unit.health;
            pickWalkableWanderPoint();
        }

        if(dashCooldown > 0f){
            dashCooldown = Math.max(0f, dashCooldown - Time.delta);
        }

        if(smokeCloakTimer > 0f){
            smokeCloakTimer = Math.max(0f, smokeCloakTimer - Time.delta);
        }

        boolean wasHit = !isSmokeCloaked() && unit.health < lastHealth - 0.1f;
        lastHealth = unit.health;

        // 50% HP Ability: Smoke Snow Bomb (10s invincible + almost invisible + confusing escape)
        if(!usedSmokeBomb && unit.health <= unit.maxHealth * 0.5f){
            triggerSmokeSnowBomb();
            return;
        }

        Unit nearestUnit = findNearestOtherUnit(220f);
        Unit playerUnit = findNearestPlayerUnit();
        boolean bulletThreat = !isSmokeCloaked() && computeBulletDodge();

        if(wasHit){
            state = State.SKIDADDLE;
            stateTimer = 150f;
            sprinting = true;
            target = null;
        }

        updateStateLogic(playerUnit, nearestUnit, bulletThreat);
        updateParticles();
        updateWeapons();
    }

    private void triggerSmokeSnowBomb(){
        usedSmokeBomb = true;
        smokeCloakTimer = 600f; // 10 seconds
        state = State.SKIDADDLE;
        stateTimer = 260f;
        sprinting = true;
        target = null;

        AbilityEffects.smokeSnowBomb.at(unit.x, unit.y);

        Unit threat = findNearestPlayerUnit();
        if(threat == null) threat = findNearestOtherUnit(260f);
        float away = threat != null ? threat.angleTo(unit) : Mathf.random(360f);
        // Pick a confusing angled direction
        fleeAngle = away + (Mathf.chance(0.5f) ? 1f : -1f) * Mathf.random(50f, 115f);
        unit.vel.add(Angles.trnsx(fleeAngle, 2.8f), Angles.trnsy(fleeAngle, 2.8f));
    }

    private void updateStateLogic(Unit playerUnit, Unit nearestUnit, boolean bulletThreat){
        Unit threatUnit = playerUnit != null ? playerUnit : nearestUnit;
        float threatDist = threatUnit != null ? unit.dst(threatUnit) : Float.MAX_VALUE;

        // While cloaked from Smoke Snow Bomb, run evasively in confusing turns
        if(isSmokeCloaked()){
            target = null;
            if(Mathf.chance(0.015f)){
                fleeAngle += Mathf.range(75f);
            }
            moveAroundWalls(fleeAngle, 0.92f, true);
            faceMovement();
            return;
        }

        if(state == State.NEUTRAL){
            sprinting = false;
            target = null;

            if(bulletThreat || threatDist < 34f){
                state = State.SKIDADDLE;
                stateTimer = 140f;
                sprinting = true;
                fleeAngle = threatUnit != null ? threatUnit.angleTo(unit) : unit.rotation;
                return;
            }

            if(idlePauseTimer > 0f){
                idlePauseTimer -= Time.delta;
                unit.vel.scl(0.6f);
                return;
            }

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                if(Mathf.chance(0.35f)){
                    idlePauseTimer = Mathf.random(60f, 130f);
                }
                stateTimer = Mathf.random(160f, 280f);
                pickWalkableWanderPoint();
            }

            // Look for transport/drill buildings or nearby player to curiously check out
            Teamc prankSpot = findPriorityBaseTarget(165f);
            if(prankSpot == null && threatDist < 120f){
                prankSpot = threatUnit;
            }
            if(prankSpot != null){
                state = State.CURIOUS;
                stateTimer = 70f;
                target = prankSpot;
                return;
            }

            if(unit.dst(wanderPos) < 16f){
                idlePauseTimer = Mathf.random(45f, 100f);
                pickWalkableWanderPoint();
            }

            moveAroundWalls(unit.angleTo(wanderPos), 0.44f, false);
            faceMovement();

        }else if(state == State.CURIOUS){
            sprinting = false;
            if(bulletThreat){
                state = State.SKIDADDLE;
                stateTimer = 130f;
                sprinting = true;
                fleeAngle = dodgeVec.angle();
                return;
            }
            if(target == null || !isTargetValid(target)){
                state = State.NEUTRAL;
                stateTimer = 120f;
                return;
            }

            unit.vel.scl(0.65f);
            faceTarget();

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                state = State.PRANKSTER;
                prankShotsLeft = Mathf.random(2, 4);
                stateTimer = 260f;
            }

        }else if(state == State.PRANKSTER){
            sprinting = bulletThreat;

            if(target == null || !isTargetValid(target) || unit.dst(target) > 190f || prankShotsLeft <= 0f){
                if(target instanceof Building b){
                    lastPrankedId = b.id;
                }
                Teamc next = findPriorityBaseTarget(195f);
                if(next == null && threatDist < 175f){
                    next = threatUnit;
                }
                target = next;
                prankShotsLeft = Mathf.random(2, 4);
                if(target == null){
                    state = State.NEUTRAL;
                    stateTimer = 140f;
                    return;
                }
            }

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                // Calmly stroll somewhere else instead of panicking
                state = State.NEUTRAL;
                stateTimer = 160f;
                idlePauseTimer = 50f;
                target = null;
                pickWalkableWanderPoint();
                return;
            }

            // Back-dash ONLY when player/unit gets too close (2.5s cooldown)
            if(threatUnit != null && threatDist < 54f && dashCooldown <= 0f){
                dashCooldown = 150f;
                float away = threatUnit.angleTo(unit);
                unit.vel.add(Angles.trnsx(away, 3.3f), Angles.trnsy(away, 3.3f));
                AbilityEffects.bobDash.at(unit.x, unit.y);
            }

            if(bulletThreat){
                moveAroundWalls(dodgeVec.angle(), 0.90f, false);
            }else if(threatUnit != null && threatDist < 48f){
                moveAroundWalls(threatUnit.angleTo(unit), 0.78f, false);
            }else{
                float dst = unit.dst(target);
                if(dst > 115f){
                    moveAroundWalls(unit.angleTo(target), 0.50f, false);
                }else if(dst < 68f && target instanceof Unit){
                    moveAroundWalls(target.angleTo(unit), 0.55f, false);
                }else{
                    unit.vel.scl(0.65f);
                }
            }
            faceTarget();

        }else if(state == State.SKIDADDLE){
            target = null;

            if(threatDist < 70f || bulletThreat){
                sprinting = true;
            }else if(threatDist > 135f){
                sprinting = false;
            }

            float baseEscape = bulletThreat ? dodgeVec.angle()
                : (threatUnit != null ? threatUnit.angleTo(unit) : fleeAngle);

            moveAroundWalls(baseEscape, sprinting ? 0.96f : 0.72f, true);
            faceMovement();

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                if(threatUnit != null){
                    unit.rotation = unit.angleTo(threatUnit);
                }
                sprinting = false;
                state = State.NEUTRAL;
                stateTimer = 150f;
                idlePauseTimer = 60f;
                pickWalkableWanderPoint();
            }
        }
    }

    private void moveAroundWalls(float desiredAngle, float speedScale, boolean preyWeave){
        float targetAngle = desiredAngle;
        if(preyWeave){
            targetAngle += Mathf.sin(Time.time * 0.14f) * 24f;
        }

        float chosenAngle = pickClearAngle(targetAngle);
        movePos.set(
            unit.x + Angles.trnsx(chosenAngle, 64f),
            unit.y + Angles.trnsy(chosenAngle, 64f)
        );
        moveTo(movePos, 0f);
        unit.vel.scl(speedScale);
    }

    private float pickClearAngle(float desiredAngle){
        if(isDirectionClear(desiredAngle)) return desiredAngle;

        float[] offsets = {35f, -35f, 70f, -70f, 105f, -105f, 145f, -145f, 180f};
        for(float off : offsets){
            float test = desiredAngle + off;
            if(isDirectionClear(test)) return test;
        }
        return desiredAngle + 180f;
    }

    private boolean isDirectionClear(float angle){
        if(Vars.world == null) return true;
        for(int step = 1; step <= 2; step++){
            float checkDist = step * Vars.tilesize * 1.15f;
            Tile t = Vars.world.tileWorld(
                unit.x + Angles.trnsx(angle, checkDist),
                unit.y + Angles.trnsy(angle, checkDist)
            );
            if(t == null || t.solid()){
                return false;
            }
        }
        return true;
    }

    private boolean computeBulletDodge(){
        dodgeVec.setZero();
        boolean danger = false;

        for(Bullet b : Groups.bullet){
            if(b.team == unit.team) continue;
            float dst = unit.dst(b);
            if(dst > 75f) continue;

            float toBob = b.angleTo(unit);
            float bulletAng = b.vel.angle();
            if(Angles.within(toBob, bulletAng, 34f)){
                float side = Angles.angleDist(toBob, bulletAng) >= 0f ? 90f : -90f;
                dodgeVec.add(Angles.trnsx(bulletAng + side, 1f), Angles.trnsy(bulletAng + side, 1f));
                danger = true;
            }
        }
        if(danger && !dodgeVec.isZero()){
            dodgeVec.nor();
        }
        return danger;
    }

    private void updateParticles(){
        if(state == State.SKIDADDLE || isSmokeCloaked()){
            smokeTrailTimer += Time.delta;
            float interval = isSmokeCloaked() ? 7f : (sprinting ? 5f : 10f);
            if(smokeTrailTimer >= interval){
                smokeTrailTimer = 0f;
                float back = unit.rotation + 180f;
                AbilityEffects.bobSmoke.at(
                    unit.x + Angles.trnsx(back, 4f),
                    unit.y + Angles.trnsy(back, 4f)
                );
            }
        }

        if(sprinting && !isSmokeCloaked()){
            sweatTimer += Time.delta;
            if(sweatTimer >= 10f){
                sweatTimer = 0f;
                AbilityEffects.bobSweat.at(unit.x, unit.y + 4f);
            }
        }
    }

    private Unit findNearestPlayerUnit(){
        Unit best = null;
        float bestDst = Float.MAX_VALUE;
        for(Player p : Groups.player){
            Unit u = p.unit();
            if(u == null || !u.isValid() || u == unit) continue;
            float d = unit.dst(u);
            if(d < bestDst){
                bestDst = d;
                best = u;
            }
        }
        return best;
    }

    private Unit findNearestOtherUnit(float maxRange){
        Unit best = null;
        float bestDst = maxRange;
        for(Unit u : Groups.unit){
            if(u == unit || !u.isValid() || u.team == unit.team) continue;
            float d = unit.dst(u);
            if(d < bestDst){
                bestDst = d;
                best = u;
            }
        }
        return best;
    }

    // Prioritizes conveyors, conduits, and drills; falls back to any building
    private Teamc findPriorityBaseTarget(float range){
        Building bestPriority = null;
        float bestPriorityScore = Float.MAX_VALUE;
        Building bestAny = null;
        float bestAnyDist = range;

        for(Building b : Groups.build){
            if(b.team == unit.team || !b.isValid()) continue;
            float d = unit.dst(b);
            if(d > range) continue;

            float penalty = b.id == lastPrankedId ? 90f : 0f;
            Category cat = b.block.category;
            if(cat == Category.distribution || cat == Category.liquid || cat == Category.production){
                float score = d + penalty;
                if(score < bestPriorityScore){
                    bestPriorityScore = score;
                    bestPriority = b;
                }
            }else if(d + penalty < bestAnyDist){
                bestAnyDist = d + penalty;
                bestAny = b;
            }
        }

        return bestPriority != null ? bestPriority : bestAny;
    }

    private boolean isTargetValid(Teamc t){
        if(t instanceof Unit u) return u.isValid() && !u.dead;
        if(t instanceof Building b) return b.isValid();
        return false;
    }

    private void pickWalkableWanderPoint(){
        if(Vars.world == null) return;
        for(int i = 0; i < 16; i++){
            float ang = Mathf.random(360f);
            float dist = Mathf.random(40f, 110f);
            float rx = unit.x + Angles.trnsx(ang, dist);
            float ry = unit.y + Angles.trnsy(ang, dist);
            Tile tile = Vars.world.tileWorld(rx, ry);
            if(tile != null && !tile.solid() && (tile.floor() == Blocks.snow || i > 8)){
                wanderPos.set(tile.worldx(), tile.worldy());
                return;
            }
        }
        wanderPos.set(unit.x + Mathf.range(60f), unit.y + Mathf.range(60f));
    }

    @Override
    public void updateWeapons(){
        boolean hadReady = false;
        if(unit.mounts.length > 0){
            hadReady = unit.mounts[0].reload <= 0.1f;
        }
        super.updateWeapons();
        if(state == State.PRANKSTER && shouldShoot() && hadReady && unit.mounts.length > 0 && unit.mounts[0].reload > 10f){
            prankShotsLeft = Math.max(0f, prankShotsLeft - 1f);
        }
    }

    @Override
    public boolean shouldShoot(){
        if(isSmokeCloaked() || target == null || !isTargetValid(target)) return false;
        if(state == State.PRANKSTER) return true;
        return state == State.CURIOUS && stateTimer <= 8f;
    }
            }
