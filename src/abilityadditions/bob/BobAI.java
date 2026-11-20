package abilityadditions.bob;

import abilityadditions.content.AbilityEffects;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntIntMap;
import arc.struct.IntQueue;
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
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.Duct;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.Junction;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.production.Drill;

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
    private float prankSwitchTimer = 0f;
    private float prankCooldown = 0f;
    private float weavePhase = 0f;
    private float dashCooldown = 0f;
    private float smokeTimer = 0f;
    private float sweatTimer = 0f;
    private float smokeBombTimer = 0f;
    private float pathRecalcTimer = 0f;
    private float stuckTimer = 0f;
    private float lastCheckX = 0f, lastCheckY = 0f;
    private float lastHealth = -1f;
    private boolean usedSmokeBomb = false;
    private boolean sprinting = false;

    private final Vec2 moveGoal = new Vec2();
    private final Vec2 nextWaypoint = new Vec2();
    private final Vec2 escapePos = new Vec2();
    private final Vec2 dodgeVec = new Vec2();

    private final IntQueue bfsQueue = new IntQueue();
    private final IntIntMap bfsParent = new IntIntMap();

    public boolean isSmokeCloaked(){
        return smokeBombTimer > 0f;
    }

    @Override
    public void updateUnit(){
        if(unit == null || !unit.isValid()) return;

        if(lastHealth < 0f){
            lastHealth = unit.health;
            lastCheckX = unit.x;
            lastCheckY = unit.y;
            pickWanderPoint();
        }

        if(dashCooldown > 0f) dashCooldown = Math.max(0f, dashCooldown - Time.delta);
        if(prankCooldown > 0f) prankCooldown = Math.max(0f, prankCooldown - Time.delta);
        if(pathRecalcTimer > 0f) pathRecalcTimer = Math.max(0f, pathRecalcTimer - Time.delta);

        if(smokeBombTimer > 0f){
            smokeBombTimer = Math.max(0f, smokeBombTimer - Time.delta);
            lastHealth = unit.health;
        }

        boolean wasHit = smokeBombTimer <= 0f && unit.health < lastHealth - 0.1f;
        lastHealth = unit.health;

        // <50% HP Ability: Smoke Snow Bomb (10s stealth + invincibility + decoy run)
        if(!usedSmokeBomb && unit.health <= unit.maxHealth * 0.5f){
            triggerSmokeSnowBomb();
            return;
        }

        Unit nearestThreat = findNearestUnitThreat();
        boolean bulletThreat = smokeBombTimer <= 0f && computeBulletDodge();

        if(wasHit){
            if(state == State.PRANKSTER && !usedSmokeBomb){
                // Reposition to a new spot / different side when shooed away
                pickRepositionSpot(nearestThreat);
            }
            state = State.SKIDADDLE;
            stateTimer = 170f;
            sprinting = true;
            target = null;
        }

        updateStateLogic(nearestThreat, bulletThreat);
        updateParticles();
        updateWeapons();
    }

    private void triggerSmokeSnowBomb(){
        usedSmokeBomb = true;
        smokeBombTimer = 10f * 60f; // 10 seconds
        state = State.SKIDADDLE;
        stateTimer = 240f;
        sprinting = true;
        target = null;

        AbilityEffects.smokeSnowBomb.at(unit.x, unit.y);
        AbilityEffects.bobDash.at(unit.x, unit.y);

        Unit threat = findNearestUnitThreat();
        float away = threat != null ? threat.angleTo(unit) : Mathf.random(360f);
        float fakeAngle = away + Mathf.random(-70f, 70f);
        escapePos.set(unit.x + Angles.trnsx(fakeAngle, 150f), unit.y + Angles.trnsy(fakeAngle, 150f));
        unit.vel.add(Angles.trnsx(fakeAngle, 3.8f), Angles.trnsy(fakeAngle, 3.8f));
        moveGoal.set(escapePos);
        nextWaypoint.set(escapePos);
    }

    private void updateStateLogic(Unit threatUnit, boolean bulletThreat){
        float threatDist = threatUnit != null ? unit.dst(threatUnit) : Float.MAX_VALUE;

        if(state == State.NEUTRAL){
            sprinting = false;
            target = null;

            if(bulletThreat || threatDist < 36f){
                state = State.SKIDADDLE;
                stateTimer = 150f;
                sprinting = true;
                return;
            }

            // Look for a nearby base building to prank (never scared of buildings!)
            if(prankCooldown <= 0f){
                Building baseTarget = findPrankBuilding(150f);
                if(baseTarget != null){
                    state = State.CURIOUS;
                    stateTimer = 65f;
                    target = baseTarget;
                    return;
                }
            }

            if(idlePauseTimer > 0f){
                idlePauseTimer -= Time.delta;
                unit.vel.scl(0.6f);
                return;
            }

            stateTimer -= Time.delta;
            if(stateTimer <= 0f || unit.dst(moveGoal) < 16f){
                idlePauseTimer = Mathf.chance(0.45f) ? Mathf.random(50f, 110f) : 0f;
                stateTimer = Mathf.random(160f, 300f);
                pickWanderPoint();
            }

            moveSmartly(moveGoal, 0.48f, false);
            faceMovement();

        }else if(state == State.CURIOUS){
            sprinting = false;
            if(bulletThreat || threatDist < 34f){
                state = State.SKIDADDLE;
                stateTimer = 150f;
                sprinting = true;
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
                stateTimer = Mathf.random(360f, 540f); // calm 6-9s prank session
                prankSwitchTimer = 110f;
            }

        }else if(state == State.PRANKSTER){
            stateTimer -= Time.delta;
            prankSwitchTimer -= Time.delta;

            if(stateTimer <= 0f){
                prankCooldown = 360f; // 6s chill break before next prank session
                state = State.NEUTRAL;
                stateTimer = 180f;
                target = null;
                pickWanderPoint();
                return;
            }

            // Rotate between transport/drill blocks instead of camping 1 building
            if(target == null || !isTargetValid(target) || prankSwitchTimer <= 0f){
                prankSwitchTimer = Mathf.random(95f, 145f);
                Teamc nextTarget = findPrankBuilding(180f);
                if(nextTarget == null) nextTarget = threatUnit != null && threatDist < 180f ? threatUnit : null;
                target = nextTarget;
            }

            if(target == null){
                // Walk toward nearest base (player or enemy) if none nearby
                Building farBase = findPrankBuilding(700f);
                if(farBase != null){
                    moveGoal.set(farBase.x, farBase.y);
                    moveSmartly(moveGoal, 0.58f, false);
                    faceMovement();
                }else{
                    state = State.NEUTRAL;
                    stateTimer = 120f;
                }
                return;
            }

            // Back-dash if player/unit rushes Bob (2.5s cooldown)
            if(threatUnit != null && threatDist < 54f && dashCooldown <= 0f){
                dashCooldown = 150f;
                float away = threatUnit.angleTo(unit);
                unit.vel.add(Angles.trnsx(away, 3.6f), Angles.trnsy(away, 3.6f));
                AbilityEffects.bobDash.at(unit.x, unit.y);
            }

            sprinting = bulletThreat || threatDist < 48f;
            if(bulletThreat){
                escapePos.set(unit.x + dodgeVec.x * 85f, unit.y + dodgeVec.y * 85f);
                moveSmartly(escapePos, 0.92f, false);
            }else if(threatUnit != null && threatDist < 68f){
                float away = threatUnit.angleTo(unit);
                escapePos.set(unit.x + Angles.trnsx(away, 95f), unit.y + Angles.trnsy(away, 95f));
                moveSmartly(escapePos, 0.85f, false);
            }else{
                float dst = unit.dst(target);
                if(dst < 68f){
                    float away = target.angleTo(unit);
                    escapePos.set(unit.x + Angles.trnsx(away, 80f), unit.y + Angles.trnsy(away, 80f));
                    moveSmartly(escapePos, 0.62f, false);
                }else if(dst > 125f){
                    moveGoal.set(target.x(), target.y());
                    moveSmartly(moveGoal, 0.62f, false);
                }else{
                    unit.vel.scl(0.75f);
                }
            }
            faceTarget();

        }else if(state == State.SKIDADDLE){
            target = null;

            if(smokeBombTimer <= 0f && (threatDist < 68f || bulletThreat)){
                sprinting = true;
            }else if(threatDist > 130f && stateTimer < 70f){
                sprinting = false;
            }

            float awayAngle = threatUnit != null ? threatUnit.angleTo(unit) : unit.rotation;
            if(bulletThreat){
                escapePos.set(unit.x + dodgeVec.x * 110f, unit.y + dodgeVec.y * 110f);
            }else if(smokeBombTimer > 0f && unit.dst(moveGoal) > 24f){
                escapePos.set(moveGoal);
            }else{
                escapePos.set(unit.x + Angles.trnsx(awayAngle, 120f), unit.y + Angles.trnsy(awayAngle, 120f));
            }

            moveSmartly(escapePos, sprinting ? 1.0f : 0.78f, true);
            faceMovement();

            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                if(threatUnit != null){
                    unit.rotation = unit.angleTo(threatUnit);
                }
                sprinting = false;
                state = State.NEUTRAL;
                stateTimer = 150f;
                prankCooldown = 180f;
                pickWanderPoint();
            }
        }
    }

    private void moveSmartly(Vec2 goal, float speedScale, boolean preyWeave){
        // Detect stuck in U-shape walls and compute BFS waypoint around obstacles
        stuckTimer += Time.delta;
        if(stuckTimer >= 24f){
            stuckTimer = 0f;
            if(Mathf.dst(unit.x, unit.y, lastCheckX, lastCheckY) < 6f){
                computeBfsWaypoint(goal);
                pathRecalcTimer = 45f;
            }
            lastCheckX = unit.x;
            lastCheckY = unit.y;
        }

        Vec2 activeTarget = goal;
        if(pathRecalcTimer > 0f && !nextWaypoint.isZero()){
            if(unit.dst(nextWaypoint) < 12f){
                computeBfsWaypoint(goal);
            }
            activeTarget = nextWaypoint;
        }else if(!hasLineOfSight(unit.x, unit.y, goal.x, goal.y)){
            computeBfsWaypoint(goal);
            pathRecalcTimer = 40f;
            if(!nextWaypoint.isZero()) activeTarget = nextWaypoint;
        }

        float desiredAngle = unit.angleTo(activeTarget);
        if(preyWeave){
            weavePhase += Time.delta * 0.14f;
            desiredAngle += Mathf.sin(weavePhase) * 24f;
        }

        // Local wall-avoidance feelers
        float clearAngle = findClearAngle(desiredAngle);
        escapePos.set(unit.x + Angles.trnsx(clearAngle, 48f), unit.y + Angles.trnsy(clearAngle, 48f));
        moveTo(escapePos, 0f);
        unit.vel.scl(speedScale);
    }

    private float findClearAngle(float baseAngle){
        float[] offsets = {0f, 30f, -30f, 60f, -60f, 95f, -95f, 135f, -135f, 180f};
        for(float off : offsets){
            float ang = baseAngle + off;
            if(isWalkablePoint(unit.x + Angles.trnsx(ang, 14f), unit.y + Angles.trnsy(ang, 14f))
                && isWalkablePoint(unit.x + Angles.trnsx(ang, 26f), unit.y + Angles.trnsy(ang, 26f))){
                return ang;
            }
        }
        return baseAngle;
    }

    private void computeBfsWaypoint(Vec2 goal){
        nextWaypoint.set(goal);
        if(Vars.world == null) return;

        Tile start = Vars.world.tileWorld(unit.x, unit.y);
        Tile end = Vars.world.tileWorld(goal.x, goal.y);
        if(start == null || end == null) return;

        bfsQueue.clear();
        bfsParent.clear();

        int startPos = start.pos();
        bfsQueue.addLast(startPos);
        bfsParent.put(startPos, -1);

        int bestPos = -1;
        float bestDst = Float.MAX_VALUE;
        int visited = 0;
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while(!bfsQueue.isEmpty() && visited < 420){
            int cur = bfsQueue.removeFirst();
            visited++;
            Tile ct = Vars.world.tile(cur);
            if(ct == null) continue;

            float d = ct.dst(end);
            if(d < bestDst){
                bestDst = d;
                bestPos = cur;
            }
            if(cur == end.pos()) break;

            for(int i = 0; i < 4; i++){
                Tile nt = Vars.world.tile(ct.x + dx[i], ct.y + dy[i]);
                if(nt == null || nt.solid() || bfsParent.containsKey(nt.pos())) continue;
                bfsParent.put(nt.pos(), cur);
                bfsQueue.addLast(nt.pos());
            }
        }

        if(bestPos == -1 || bestPos == startPos) return;

        int step = bestPos;
        int prev = bfsParent.get(step, -1);
        int safety = 0;
        while(prev != -1 && prev != startPos && safety++ < 200){
            step = prev;
            prev = bfsParent.get(step, -1);
        }
        // Advance 2 tiles ahead along path for smoother movement
        Tile wp = Vars.world.tile(step);
        if(wp != null){
            nextWaypoint.set(wp.worldx(), wp.worldy());
        }
    }

    private boolean isWalkablePoint(float x, float y){
        if(Vars.world == null) return true;
        Tile t = Vars.world.tileWorld(x, y);
        return t != null && !t.solid();
    }

    private boolean hasLineOfSight(float x1, float y1, float x2, float y2){
        float dist = Mathf.dst(x1, y1, x2, y2);
        int steps = Math.min(18, Math.max(2, (int)(dist / 12f)));
        for(int i = 1; i < steps; i++){
            float t = (float)i / steps;
            if(!isWalkablePoint(Mathf.lerp(x1, x2, t), Mathf.lerp(y1, y2, t))){
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
            if(dst > 80f) continue;

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
        boolean isRunning = state == State.SKIDADDLE;
        if((isRunning || sprinting) && smokeBombTimer <= 0f){
            smokeTimer += Time.delta;
            float interval = sprinting ? 5f : 10f;
            if(smokeTimer >= interval){
                smokeTimer = 0f;
                float back = unit.rotation + 180f;
                AbilityEffects.bobSmoke.at(
                    unit.x + Angles.trnsx(back, 4f),
                    unit.y + Angles.trnsy(back, 4f)
                );
            }
        }

        if(sprinting && smokeBombTimer <= 0f){
            sweatTimer += Time.delta;
            if(sweatTimer >= 10f){
                sweatTimer = 0f;
                AbilityEffects.bobSweat.at(unit.x, unit.y + 4f);
            }
        }
    }

    private Unit findNearestUnitThreat(){
        Unit best = null;
        float bestDst = Float.MAX_VALUE;
        for(Player p : Groups.player){
            Unit u = p.unit();
            if(u != null && u.isValid() && u != unit){
                float d = unit.dst(u);
                if(d < bestDst){
                    bestDst = d;
                    best = u;
                }
            }
        }
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

    private Building findPrankBuilding(float range){
        Building bestPriority = null;
        float bestPriorityScore = Float.MAX_VALUE;
        Building bestAny = null;
        float bestAnyDst = range;

        for(Building b : Groups.build){
            if(b.team == unit.team || !b.isValid()) continue;
            float d = unit.dst(b);
            if(d > range) continue;

            boolean isTransportOrDrill =
                b.block instanceof Conveyor
                || b.block instanceof Duct
                || b.block instanceof Conduit
                || b.block instanceof Drill
                || b.block instanceof ItemBridge
                || b.block instanceof LiquidBridge
                || b.block instanceof Junction
                || b.block instanceof Router;

            float jitter = Mathf.random(0f, 28f);
            if(isTransportOrDrill && b != target && d + jitter < bestPriorityScore){
                bestPriorityScore = d + jitter;
                bestPriority = b;
            }
            if(b != target && d < bestAnyDst){
                bestAnyDst = d;
                bestAny = b;
            }
        }
        return bestPriority != null ? bestPriority : bestAny;
    }

    private void pickRepositionSpot(Unit threat){
        float base = threat != null ? threat.angleTo(unit) + Mathf.random(-80f, 80f) : Mathf.random(360f);
        moveGoal.set(unit.x + Angles.trnsx(base, 140f), unit.y + Angles.trnsy(base, 140f));
    }

    private void pickWanderPoint(){
        if(Vars.world == null) return;
        // Occasionally wander toward a base to keep pranks lively
        if(Mathf.chance(0.45f)){
            Building b = findPrankBuilding(550f);
            if(b != null){
                moveGoal.set(b.x + Mathf.range(45f), b.y + Mathf.range(45f));
                return;
            }
        }
        for(int i = 0; i < 14; i++){
            float rx = unit.x + Mathf.range(120f);
            float ry = unit.y + Mathf.range(120f);
            Tile tile = Vars.world.tileWorld(rx, ry);
            if(tile != null && tile.floor() == Blocks.snow && !tile.solid()){
                moveGoal.set(tile.worldx(), tile.worldy());
                return;
            }
        }
        moveGoal.set(unit.x + Mathf.range(70f), unit.y + Mathf.range(70f));
    }

    private boolean isTargetValid(Teamc t){
        if(t instanceof Unit u) return u.isValid() && !u.dead;
        if(t instanceof Building b) return b.isValid();
        return false;
    }

    @Override
    public boolean shouldShoot(){
        if(smokeBombTimer > 0f || target == null || !isTargetValid(target)) return false;
        if(state == State.PRANKSTER) return true;
        return state == State.CURIOUS && stateTimer <= 8f;
    }
            }
