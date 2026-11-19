package abilityadditions.bob;

import abilityadditions.content.AbilityEffects;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
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
    private float neutralRunTimer = 0f;
    private float weaveTimer = 0f;
    private float weaveAngle = 0f;
    private float dashCooldown = 0f;
    private float smokeTimer = 0f;
    private float sweatTimer = 0f;
    private float lastHealth = -1f;
    private boolean usedBarrage = false;
    private boolean sprinting = false;

    private final Vec2 wanderPos = new Vec2();
    private final Vec2 escapePos = new Vec2();
    private final Vec2 dodgeVec = new Vec2();

    @Override
    public void updateUnit(){
        if(unit == null || !unit.isValid()) return;

        if(lastHealth < 0f){
            lastHealth = unit.health;
            pickRandomSnowPoint();
        }

        if(dashCooldown > 0f){
            dashCooldown = Math.max(0f, dashCooldown - Time.delta);
        }

        // Check if damaged by a stray bullet
        boolean wasHit = unit.health < lastHealth - 0.1f;
        lastHealth = unit.health;

        // 50% HP Ability: Cancel Prankster mode -> crazy knockback snowball barrage -> sprint away
        if(!usedBarrage && unit.health <= unit.maxHealth * 0.5f){
            triggerDesperationBarrage();
            return;
        }

        Unit nearestPlayerUnit = findNearestPlayerUnit();
        boolean bulletThreat = computeBulletDodge();

        if(wasHit){
            state = State.SKIDADDLE;
            stateTimer = 240f;
            sprinting = true;
            target = null;
        }

        updateStateLogic(nearestPlayerUnit, bulletThreat);
        updateParticles();
        updateWeapons();
    }

    private void triggerDesperationBarrage(){
        usedBarrage = true;
        state = State.SKIDADDLE;
        stateTimer = 300f;
        sprinting = true;

        Teamc threat = findNearestPlayerUnit();
        if(threat == null){
            threat = findNearestPrankTarget(260f);
        }

        float baseAngle = threat != null ? unit.angleTo(threat) : unit.rotation;
        for(int i = 0; i < 6; i++){
            float offset = Mathf.lerp(-28f, 28f, i / 5f) + Mathf.range(4f);
            BobUnit.barrageSnowball.create(unit, unit.team, unit.x, unit.y, baseAngle + offset);
        }

        AbilityEffects.bobDash.at(unit.x, unit.y);
        float awayAngle = baseAngle + 180f;
        unit.vel.add(Angles.trnsx(awayAngle, 3.2f), Angles.trnsy(awayAngle, 3.2f));
        target = null;
    }

    private void updateStateLogic(Unit playerUnit, boolean bulletThreat){
        float playerDist = playerUnit != null ? unit.dst(playerUnit) : Float.MAX_VALUE;

        if(state == State.NEUTRAL){
            sprinting = false;
            target = null;

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                neutralRunTimer = Mathf.chance(0.35f) ? Mathf.random(70f, 130f) : 0f;
                stateTimer = Mathf.random(120f, 240f);
                pickRandomSnowPoint();
            }
            if(neutralRunTimer > 0f){
                neutralRunTimer -= Time.delta;
            }

            if(bulletThreat || playerDist < 48f){
                state = State.SKIDADDLE;
                stateTimer = 180f;
                sprinting = true;
                return;
            }

            // Spot player, unit, or building nearby -> Silent Curious stare!
            Teamc nearby = playerDist < 135f ? playerUnit : findNearestPrankTarget(115f);
            if(nearby != null){
                state = State.CURIOUS;
                stateTimer = 75f; // ~1.25s silent stare
                target = nearby;
                return;
            }

            if(unit.dst(wanderPos) < 16f){
                pickRandomSnowPoint();
            }

            boolean running = neutralRunTimer > 0f;
            moveWithPreyWeave(wanderPos, running ? 0.85f : 0.52f, running);
            faceMovement();

        }else if(state == State.CURIOUS){
            sprinting = false;
            if(target == null || !isTargetValid(target) || bulletThreat){
                state = State.SKIDADDLE;
                stateTimer = 150f;
                sprinting = bulletThreat;
                return;
            }

            unit.vel.scl(0.7f);
            faceTarget();

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                // Fire 1 snowball, then either go Prankster mode or Skidaddle
                if(Mathf.chance(0.65f)){
                    state = State.PRANKSTER;
                    stateTimer = Mathf.random(240f, 420f);
                }else{
                    state = State.SKIDADDLE;
                    stateTimer = Mathf.random(150f, 240f);
                    sprinting = false;
                }
            }

        }else if(state == State.PRANKSTER){
            if(target == null || !isTargetValid(target) || unit.dst(target) > 220f){
                target = findNearestPrankTarget(185f);
            }
            if(target == null){
                state = State.NEUTRAL;
                stateTimer = 120f;
                return;
            }

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                state = State.SKIDADDLE;
                stateTimer = 160f;
                sprinting = false;
                return;
            }

            // Back-dash if player gets too close (2.5s cooldown)
            if(playerUnit != null && playerDist < 62f && dashCooldown <= 0f){
                dashCooldown = 150f; // 2.5 seconds
                float away = playerUnit.angleTo(unit);
                unit.vel.add(Angles.trnsx(away, 3.6f), Angles.trnsy(away, 3.6f));
                AbilityEffects.bobDash.at(unit.x, unit.y);
            }

            // Smoothly dodge incoming bullets while keeping comfortable distance!
            sprinting = bulletThreat || playerDist < 52f;
            if(bulletThreat){
                escapePos.set(unit.x + dodgeVec.x * 90f, unit.y + dodgeVec.y * 90f);
                moveTo(escapePos, 0f);
            }else{
                float dst = unit.dst(target);
                if(dst < 80f){
                    float away = target.angleTo(unit);
                    escapePos.set(unit.x + Angles.trnsx(away, 85f), unit.y + Angles.trnsy(away, 85f));
                    moveTo(escapePos, 0f);
                    unit.vel.scl(0.85f);
                }else if(dst > 145f){
                    moveTo(target, 110f);
                    unit.vel.scl(0.7f);
                }else{
                    circle(target, 110f);
                    unit.vel.scl(0.65f);
                }
            }
            faceTarget();

        }else if(state == State.SKIDADDLE){
            target = null;

            // Escalate to Sweaty Sprint if player chases closely or bullet threatens
            if(playerDist < 105f || bulletThreat){
                sprinting = true;
                stateTimer = Math.max(stateTimer, 150f);
            }else if(playerDist > 175f && stateTimer < 60f){
                sprinting = false;
            }

            Teamc chaser = playerUnit != null ? playerUnit : findNearestPrankTarget(160f);
            float awayAngle = chaser != null ? chaser.angleTo(unit) : unit.rotation;

            if(bulletThreat){
                escapePos.set(unit.x + dodgeVec.x * 110f, unit.y + dodgeVec.y * 110f);
            }else{
                escapePos.set(unit.x + Angles.trnsx(awayAngle, 120f), unit.y + Angles.trnsy(awayAngle, 120f));
            }

            moveWithPreyWeave(escapePos, sprinting ? 1.0f : 0.85f, true);
            faceMovement();

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                // Glance back briefly when calm
                if(chaser != null){
                    unit.rotation = unit.angleTo(chaser);
                }
                sprinting = false;
                state = State.NEUTRAL;
                stateTimer = 140f;
                pickRandomSnowPoint();
            }
        }
    }

    private void moveWithPreyWeave(Vec2 goal, float speedScale, boolean weave){
        if(weave){
            weaveTimer -= Time.delta;
            if(weaveTimer <= 0f){
                weaveTimer = Mathf.random(12f, 26f);
                weaveAngle = Mathf.range(42f);
            }
            float baseAngle = unit.angleTo(goal);
            escapePos.set(
                unit.x + Angles.trnsx(baseAngle + weaveAngle, 80f),
                unit.y + Angles.trnsy(baseAngle + weaveAngle, 80f)
            );
            moveTo(escapePos, 0f);
        }else{
            moveTo(goal, 0f);
        }
        unit.vel.scl(speedScale);
    }

    private boolean computeBulletDodge(){
        dodgeVec.setZero();
        boolean danger = false;

        for(Bullet b : Groups.bullet){
            if(b.team == unit.team) continue;
            float dst = unit.dst(b);
            if(dst > 85f) continue;

            float toBob = b.angleTo(unit);
            float bulletAng = b.vel.angle();
            if(Angles.within(toBob, bulletAng, 38f)){
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
        boolean isRunning = state == State.SKIDADDLE || (state == State.NEUTRAL && neutralRunTimer > 0f);
        if(isRunning || sprinting){
            smokeTimer += Time.delta;
            float interval = sprinting ? 4f : 8f;
            if(smokeTimer >= interval){
                smokeTimer = 0f;
                float back = unit.rotation + 180f;
                AbilityEffects.bobSmoke.at(
                    unit.x + Angles.trnsx(back, 4f),
                    unit.y + Angles.trnsy(back, 4f)
                );
            }
        }

        if(sprinting){
            sweatTimer += Time.delta;
            if(sweatTimer >= 9f){
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

    private Teamc findNearestPrankTarget(float range){
        Teamc best = null;
        float bestDst = range;

        Unit playerUnit = findNearestPlayerUnit();
        if(playerUnit != null && unit.dst(playerUnit) < bestDst){
            best = playerUnit;
            bestDst = unit.dst(playerUnit);
        }

        for(Unit u : Groups.unit){
            if(u == unit || !u.isValid() || u.team == unit.team) continue;
            float d = unit.dst(u);
            if(d < bestDst){
                bestDst = d;
                best = u;
            }
        }

        if(best == null){
            for(Building b : Groups.build){
                if(b.team == unit.team || !b.isValid()) continue;
                float d = unit.dst(b);
                if(d < bestDst){
                    bestDst = d;
                    best = b;
                }
            }
        }

        return best;
    }

    private boolean isTargetValid(Teamc t){
        if(t instanceof Unit u) return u.isValid() && !u.dead;
        if(t instanceof Building b) return b.isValid();
        return false;
    }

    private void pickRandomSnowPoint(){
        if(Vars.world == null) return;
        for(int i = 0; i < 16; i++){
            float rx = unit.x + Mathf.range(110f);
            float ry = unit.y + Mathf.range(110f);
            Tile tile = Vars.world.tileWorld(rx, ry);
            if(tile != null && tile.floor() == Blocks.snow && !tile.solid()){
                wanderPos.set(tile.worldx(), tile.worldy());
                return;
            }
        }
        wanderPos.set(unit.x + Mathf.range(70f), unit.y + Mathf.range(70f));
    }

    @Override
    public boolean shouldShoot(){
        if(target == null || !isTargetValid(target)) return false;
        if(state == State.PRANKSTER) return true;
        return state == State.CURIOUS && stateTimer <= 8f;
    }
              }
