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
    private float lastHealth = -1f;
    private boolean usedSmokeBomb = false;
    private boolean sprinting = false;

    private final Vec2 moveGoal = new Vec2();
    private final Vec2 pathOut = new Vec2();
    private final Vec2 baseCenter = new Vec2();
    private final Vec2 escapePos = new Vec2();
    private final Vec2 dodgeVec = new Vec2();

    public boolean isSmokeCloaked(){
        return smokeBombTimer > 0f;
    }

    @Override
    public void updateTargeting(){
    }

    @Override
    public void updateMovement(){
        if(unit == null || !unit.isValid()) return;

        if(lastHealth < 0f){
            lastHealth = unit.health;
            pickWanderPoint();
        }

        if(dashCooldown > 0f) dashCooldown = Math.max(0f, dashCooldown - Time.delta);
        if(prankCooldown > 0f) prankCooldown = Math.max(0f, prankCooldown - Time.delta);

        if(smokeBombTimer > 0f){
            smokeBombTimer = Math.max(0f, smokeBombTimer - Time.delta);
            lastHealth = unit.health;
        }

        boolean wasHit = smokeBombTimer <= 0f && unit.health < lastHealth - 0.1f;
        lastHealth = unit.health;

        if(!usedSmokeBomb && unit.health <= unit.maxHealth * 0.5f){
            triggerSmokeSnowBomb();
            return;
        }

        Unit nearestThreat = findNearestUnitThreat();
        boolean bulletThreat = smokeBombTimer <= 0f && computeBulletDodge();

        if(wasHit){
            state = State.SKIDADDLE;
            stateTimer = 190f;
            sprinting = true;
            target = null;
            pickEscapeAwayFromBase(nearestThreat);
        }

        updateStateLogic(nearestThreat, bulletThreat);
        updateParticles();
    }

    private void triggerSmokeSnowBomb(){
        usedSmokeBomb = true;
        smokeBombTimer = 10f * 60f;
        state = State.SKIDADDLE;
        stateTimer = 260f;
        sprinting = true;
        target = null;

        AbilityEffects.smokeSnowBomb.at(unit.x, unit.y);
        AbilityEffects.bobDash.at(unit.x, unit.y);

        Unit threat = findNearestUnitThreat();
        pickEscapeAwayFromBase(threat);
        float away = unit.angleTo(moveGoal) + Mathf.random(-35f, 35f);
        unit.vel.add(Angles.trnsx(away, 3.6f), Angles.trnsy(away, 3.6f));
    }

    private void updateStateLogic(Unit threatUnit, boolean bulletThreat){
        float threatDist = threatUnit != null ? unit.dst(threatUnit) : Float.MAX_VALUE;

        if(state == State.NEUTRAL){
            sprinting = false;
            target = null;

            if(bulletThreat || threatDist < 36f){
                state = State.SKIDADDLE;
                stateTimer = 160f;
                sprinting = true;
                pickEscapeAwayFromBase(threatUnit);
                return;
            }

            if(prankCooldown <= 0f){
                Building baseTarget = findPrankBuilding(145f);
                if(baseTarget != null){
                    state = State.CURIOUS;
                    stateTimer = 50f;
                    target = baseTarget;
                    return;
                }
            }

            if(idlePauseTimer > 0f){
                idlePauseTimer -= Time.delta;
                return;
            }

            stateTimer -= Time.delta;
            if(stateTimer <= 0f || unit.dst(moveGoal) < 18f){
                idlePauseTimer = Mathf.chance(0.45f) ? Mathf.random(45f, 100f) : 0f;
                stateTimer = Mathf.random(160f, 300f);
                pickWanderPoint();
            }

            navigateTo(moveGoal, false, 0f);
            faceMovement();

        }else if(state == State.CURIOUS){
            sprinting = false;
            if(bulletThreat || threatDist < 34f){
                state = State.SKIDADDLE;
                stateTimer = 160f;
                sprinting = true;
                pickEscapeAwayFromBase(threatUnit);
                return;
            }

            if(target == null || !isTargetValid(target)){
                state = State.NEUTRAL;
                stateTimer = 120f;
                return;
            }

            faceTarget();
            stateTimer -= Time.delta;
            if(stateTimer <= 0f){
                state = State.PRANKSTER;
                stateTimer = Mathf.random(360f, 540f);
                prankSwitchTimer = 110f;
            }

        }else if(state == State.PRANKSTER){
            stateTimer -= Time.delta;
            prankSwitchTimer -= Time.delta;

            if(stateTimer <= 0f){
                prankCooldown = 360f;
                state = State.NEUTRAL;
                stateTimer = 180f;
                target = null;
                pickWanderPoint();
                return;
            }

            if(target == null || !isTargetValid(target) || prankSwitchTimer <= 0f){
                prankSwitchTimer = Mathf.random(95f, 145f);
                Teamc nextTarget = findPrankBuilding(175f);
                if(nextTarget == null && threatUnit != null && threatDist < 175f){
                    nextTarget = threatUnit;
                }
                target = nextTarget;
            }

            if(target == null){
                Building farBase = findPrankBuilding(700f);
                if(farBase != null){
                    snapToWalkable(farBase.x, farBase.y, moveGoal);
                    navigateTo(moveGoal, false, 0f);
                    faceMovement();
                }else{
                    state = State.NEUTRAL;
                    stateTimer = 120f;
                }
                return;
            }

            if(threatUnit != null && threatDist < 54f && dashCooldown <= 0f){
                dashCooldown = 150f;
                float away = threatUnit.angleTo(unit);
                unit.vel.add(Angles.trnsx(away, 3.6f), Angles.trnsy(away, 3.6f));
                AbilityEffects.bobDash.at(unit.x, unit.y);
            }

            sprinting = bulletThreat || threatDist < 48f;
            if(bulletThreat){
                snapToWalkable(unit.x + dodgeVec.x * 80f, unit.y + dodgeVec.y * 80f, escapePos);
                navigateTo(escapePos, false, 0.22f);
            }else if(threatUnit != null && threatDist < 68f){
                float away = threatUnit.angleTo(unit);
                snapToWalkable(unit.x + Angles.trnsx(away, 95f), unit.y + Angles.trnsy(away, 95f), escapePos);
                navigateTo(escapePos, false, 0.16f);
            }else{
                float dst = unit.dst(target);
                if(dst < 64f){
                    float away = target.angleTo(unit);
                    snapToWalkable(unit.x + Angles.trnsx(away, 85f), unit.y + Angles.trnsy(away, 85f), escapePos);
                    navigateTo(escapePos, false, 0.08f);
                }else if(dst > 140f){
                    snapToWalkable(target.x(), target.y(), moveGoal);
                    navigateTo(moveGoal, false, 0.06f);
                }
            }
            faceTarget();

        }else if(state == State.SKIDADDLE){
            target = null;

            if(smokeBombTimer <= 0f && (threatDist < 68f || bulletThreat)){
                sprinting = true;
            }else if(threatDist > 140f && stateTimer < 70f){
                sprinting = false;
            }

            if(bulletThreat){
                snapToWalkable(unit.x + dodgeVec.x * 95f, unit.y + dodgeVec.y * 95f, escapePos);
                navigateTo(escapePos, false, 0.25f);
            }else{
                if(unit.dst(moveGoal) < 24f){
                    pickEscapeAwayFromBase(threatUnit);
                }
                navigateTo(moveGoal, true, sprinting ? 0.25f : 0.12f);
            }
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

    private void navigateTo(Vec2 goal, boolean preyWeave, float extraPush){
        Vec2 stepGoal = goal;
        if(Vars.controlPath != null && Vars.controlPath.getPathPosition(unit, goal, pathOut, null)){
            stepGoal = pathOut;
        }

        float ang = unit.angleTo(stepGoal);
        if(preyWeave){
            weavePhase += Time.delta * 0.12f;
            float weaved = ang + Mathf.sin(weavePhase) * 16f;
            escapePos.set(unit.x + Angles.trnsx(weaved, 36f), unit.y + Angles.trnsy(weaved, 36f));
            moveTo(escapePos, 0f);
        }else{
            moveTo(stepGoal, 0f);
        }

        if(extraPush > 0f){
            unit.vel.add(Angles.trnsx(ang, extraPush * Time.delta), Angles.trnsy(ang, extraPush * Time.delta));
        }
    }

    private void pickEscapeAwayFromBase(Unit threat){
        float awayAngle;
        if(computeBaseCenter(baseCenter)){
            awayAngle = baseCenter.angleTo(unit) + Mathf.random(-35f, 35f);
        }else if(threat != null){
            awayAngle = threat.angleTo(unit) + Mathf.random(-35f, 35f);
        }else{
            awayAngle = Mathf.random(360f);
        }
        snapToWalkable(
            unit.x + Angles.trnsx(awayAngle, 220f),
            unit.y + Angles.trnsy(awayAngle, 220f),
            moveGoal
        );
    }

    private boolean computeBaseCenter(Vec2 out){
        float sumX = 0f, sumY = 0f;
        int count = 0;
        for(Building b : Groups.build){
            if(b.team == unit.team || !b.isValid()) continue;
            if(unit.dst(b) > 320f) continue;
            sumX += b.x;
            sumY += b.y;
            count++;
        }
        if(count == 0) return false;
        out.set(sumX / count, sumY / count);
        return true;
    }

    private void snapToWalkable(float wx, float wy, Vec2 out){
        if(Vars.world == null){
            out.set(wx, wy);
            return;
        }
        Tile t = Vars.world.tileWorld(wx, wy);
        if(t != null && !t.solid()){
            out.set(t.worldx(), t.worldy());
            return;
        }
        int cx = Mathf.clamp(Vars.world.toTile(wx), 0, Vars.world.width() - 1);
        int cy = Mathf.clamp(Vars.world.toTile(wy), 0, Vars.world.height() - 1);
        for(int r = 1; r <= 6; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    Tile nt = Vars.world.tile(cx + dx, cy + dy);
                    if(nt != null && !nt.solid()){
                        out.set(nt.worldx(), nt.worldy());
                        return;
                    }
                }
            }
        }
        out.set(unit.x, unit.y);
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

    private void pickWanderPoint(){
        if(Vars.world == null) return;
        if(Mathf.chance(0.45f)){
            Building b = findPrankBuilding(550f);
            if(b != null){
                snapToWalkable(b.x + Mathf.range(45f), b.y + Mathf.range(45f), moveGoal);
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
        snapToWalkable(unit.x + Mathf.range(70f), unit.y + Mathf.range(70f), moveGoal);
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
