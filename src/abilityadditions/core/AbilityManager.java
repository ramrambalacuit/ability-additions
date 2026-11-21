package abilityadditions.core;

import abilityadditions.content.*;
import abilityadditions.core.AbilityProfile.*;
import abilityadditions.core.AbilityTrigger.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.part.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.TractorBeamTurret.*;
import mindustry.world.blocks.defense.turrets.Turret.*;
import mindustry.world.blocks.units.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;

public class AbilityManager{

    private static final ObjectMap<Block, AbilityProfile> profiles = new ObjectMap<>();
    private static final IntMap<AbilityState> states = new IntMap<>();
    private static final IntSet processedBullets = new IntSet();
    private static final IntSet activeEmpoweredBullets = new IntSet();
    private static final IntSet galeBullets = new IntSet();
    private static final IntMap<AbilityProfile> bulletOwners = new IntMap<>();
    private static final IntMap<Unit> bulletTurretTargets = new IntMap<>();
    private static final IntMap<Long> bulletShotKeys = new IntMap<>();
    private static final ObjectSet<Long> countedUnitHitShots = new ObjectSet<>();

    private static final Seq<Bullet> bulletSnapshot = new Seq<>();
    private static final Seq<DelayedEcho> delayedEchoes = new Seq<>();
    private static final Seq<DelayedBomblet> delayedBomblets = new Seq<>();
    private static final Seq<MarkedUnit> markedUnits = new Seq<>();
    private static final Seq<SpinningUnit> spinningUnits = new Seq<>();
    private static final Seq<TidalWave> tidalWaves = new Seq<>();
    private static final IntMap<TrackedEnemyBullet> trackedEnemyBullets = new IntMap<>();

    private static final BulletType[] snakeMissileCache = new BulletType[4];
    private static boolean initialized = false;
    private static Stat abilityStat;
    private static int tickCounter = 0;

    public static void register(Block block, AbilityProfile profile){
        profiles.put(block, profile);
    }

    public static void init(){
        if(initialized) return;
        initialized = true;

        abilityStat = Stat.all.find(s -> s.name.equalsIgnoreCase("ability"));
        if(abilityStat == null){
            abilityStat = new Stat("ability", StatCat.function);
        }

        for(ObjectMap.Entry<Block, AbilityProfile> entry : profiles){
            Block block = entry.key;
            AbilityProfile profile = entry.value;

            resolveStatuses(profile);
            addStatsAndBars(block, profile);
            wrapSalvoDrawerIfNeeded(block, profile);
        }

        Events.run(Trigger.update, AbilityManager::updateRuntime);
        Events.run(Trigger.draw, AbilityManager::drawVisuals);
        Events.on(UnitDamageEvent.class, e -> onUnitDamaged(e.unit, e.bullet));
        Events.on(BuildDamageEvent.class, e -> onBuildDamaged(e.build, e.source));
        Events.on(ResetEvent.class, e -> clearAllRuntime());
    }

    private static void resolveStatuses(AbilityProfile profile){
        profile.resolvedPrimaryStatus = findStatus(profile.primaryStatusEffect);
        profile.resolvedSecondaryStatus = findStatus(profile.secondaryStatusEffect);
        profile.resolvedTertiaryStatus = findStatus(profile.tertiaryStatusEffect);
    }

    private static StatusEffect findStatus(String name){
        if(name == null || name.isEmpty()) return null;
        StatusEffect found = Vars.content.statusEffect(name);
        if(found != null) return found;
        return Vars.content.statusEffect("mov-" + name);
    }

    private static void wrapSalvoDrawerIfNeeded(Block block, AbilityProfile profile){
        if(profile.id != AbilityId.SALVO_ESCALATING_BARREL || !(block instanceof Turret)) return;
        Turret turret = (Turret)block;
        if(!(turret.drawer instanceof DrawTurret)) return;

        final DrawTurret drawTurret = (DrawTurret)turret.drawer;
        if(drawTurret.parts.size == 0 || !(drawTurret.parts.get(0) instanceof RegionPart)) return;

        final RegionPart sidePart = (RegionPart)drawTurret.parts.get(0);
        final float origMoveX = sidePart.moveX;
        final float origMoveRot = sidePart.moveRot;
        final Color origHeatColor = sidePart.heatColor;
        final DrawBlock originalDrawer = turret.drawer;

        turret.drawer = new DrawBlock(){
            @Override
            public void draw(Building build){
                AbilityState s = states.get(build.id);
                int stage = s == null ? 1 : s.stage;
                if(stage == 2){
                    sidePart.moveX = 0.1f;
                    sidePart.moveRot = -4f;
                    sidePart.heatColor = Color.gold;
                }else if(stage >= 3){
                    sidePart.moveX = -0.55f;
                    sidePart.moveRot = 6f;
                    sidePart.heatColor = Color.orange;
                }else{
                    sidePart.moveX = origMoveX;
                    sidePart.moveRot = origMoveRot;
                    sidePart.heatColor = origHeatColor;
                }
                originalDrawer.draw(build);
                sidePart.moveX = origMoveX;
                sidePart.moveRot = origMoveRot;
                sidePart.heatColor = origHeatColor;
            }

            @Override
            public void load(Block b){
                originalDrawer.load(b);
            }

            @Override
            public void drawPlan(Block b, mindustry.entities.units.BuildPlan plan, arc.func.Eachable<mindustry.entities.units.BuildPlan> list){
                originalDrawer.drawPlan(b, plan, list);
            }

            @Override
            public TextureRegion[] icons(Block b){
                return originalDrawer.icons(b);
            }
        };
    }

    private static void addStatsAndBars(Block block, AbilityProfile profile){
        block.stats.add(abilityStat, "[accent]" + profile.abilityName + "[] - " + profile.description);

        block.addBar("ability", entity -> new Bar(
            () -> getBarText(entity, profile),
            () -> getBarColor(entity, profile),
            () -> getBarFraction(entity, profile)
        ){
            @Override
            public void draw(){
                if(shouldHideBarBox(entity, profile)){
                    String text = getBarText(entity, profile);
                    if(text == null || text.isEmpty()) return;
                    Font font = Fonts.outline;
                    GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
                    layout.setText(font, text);
                    font.setColor(Color.white);
                    font.draw(text, getX(), getY() + getHeight() * 0.5f + layout.height * 0.5f, getWidth(), Align.center, false);
                    Pools.free(layout);
                    return;
                }
                super.draw();
            }
        });
    }

    private static boolean shouldHideBarBox(Building entity, AbilityProfile profile){
        if(profile.trigger.type != TriggerType.CHANCE_PER_CYCLE) return false;
        AbilityState state = states.get(entity.id);
        return state == null || (state.activeTimer <= 0f && state.cooldownTimer <= 0f);
    }

    private static String getBarText(Building entity, AbilityProfile profile){
        AbilityState state = states.get(entity.id);
        if(state == null){
            if(profile.trigger.type == TriggerType.CHANCE_PER_CYCLE){
                return "Chance: " + Math.round(profile.trigger.chance * 100f) + "%";
            }
            return profile.abilityName;
        }

        if(state.cooldownTimer > 0f && profile.meltdownDuration > 0f){
            return "Meltdown " + Strings.fixed(state.cooldownTimer / 60f, 1) + "s";
        }
        if(state.chargeTimer > 0f){
            return "Charging " + Strings.fixed(state.chargeTimer / 60f, 1) + "s";
        }

        switch(profile.trigger.type){
            case EVERY_N_SHOTS:
                if(state.activeTimer > 0f){
                    return profile.abilityName + " " + Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                if(state.empoweredShots > 0){
                    return profile.abilityName + " READY!";
                }
                return "Charge: " + state.shotCounter + "/" + profile.trigger.everyNShots;

            case CHANCE_PER_CYCLE:
                if(state.activeTimer > 0f){
                    return profile.abilityName + " " + Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                return "Chance: " + Math.round(profile.trigger.chance * 100f) + "%";

            case CONTINUOUS_HEAT:
                if(state.activeTimer > 0f){
                    return profile.abilityName + " " + Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                int pct = Mathf.clamp(Math.round((state.heatTimer / (profile.trigger.heatSeconds * 60f)) * 100f), 0, 100);
                return (profile.id == AbilityId.SCORCH_THERMAL_RAMP ? "Heat: " : "Pressure: ") + pct + "%";

            case STAGE_DURATION:
                if(state.stage == 1){
                    int s1 = Math.round((state.stageProgress / (profile.trigger.stage1Seconds * 60f)) * 100f);
                    return "Stage 1 (" + Mathf.clamp(s1, 0, 100) + "%)";
                }else if(state.stage == 2){
                    int s2 = Math.round((state.stageProgress / (profile.trigger.stage2Seconds * 60f)) * 100f);
                    return "Stage 2 (" + Mathf.clamp(s2, 0, 100) + "%)";
                }else{
                    if(state.stage3HoldTimer > 0f && state.stageGraceTimer <= 0f){
                        return "Stage 3 Hold " + Strings.fixed(state.stage3HoldTimer / 60f, 1) + "s";
                    }
                    return "Stage 3 MAX";
                }

            case CONFIRMED_UNIT_HITS:
                if(state.shotCounter >= profile.trigger.everyNShots){
                    return "Apex Lock READY!";
                }
                return "Hits: " + state.shotCounter + "/" + profile.trigger.everyNShots;

            case INTERCEPT_COUNT:
                if(state.activeTimer > 0f){
                    return "Aegis Sweep " + Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                if(profile.secondaryThreshold > 0){
                    return "Prism: " + state.shotCounter + " (Aegis " + state.secondaryCounter + "/" + profile.secondaryThreshold + ")";
                }
                return "Intercepts: " + state.shotCounter + "/" + profile.trigger.everyNShots;

            case TRACTOR_LOCK:
                if(state.activeTimer > 0f){
                    return "Tether Slam " + Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                int lockPct = Mathf.clamp(Math.round((state.heatTimer / (profile.trigger.heatSeconds * 60f)) * 100f), 0, 100);
                return "Tether Lock: " + lockPct + "%";

            default:
                return profile.abilityName;
        }
    }

    private static Color getBarColor(Building entity, AbilityProfile profile){
        AbilityState state = states.get(entity.id);
        if(state == null) return profile.accentColor;

        if(state.cooldownTimer > 0f && profile.meltdownDuration > 0f){
            return Color.scarlet;
        }
        if(state.chargeTimer > 0f){
            return Color.white;
        }
        if(profile.trigger.type == TriggerType.STAGE_DURATION){
            if(state.stage >= 3) return Color.orange;
            if(state.stage == 2) return Color.gold;
            return Color.lightGray;
        }
        if(state.activeTimer > 0f || state.empoweredShots > 0 ||
            (profile.trigger.type == TriggerType.CONFIRMED_UNIT_HITS && state.shotCounter >= profile.trigger.everyNShots)){
            return profile.accentColor;
        }
        return profile.accentColor.cpy().mul(0.75f);
    }

    private static float getBarFraction(Building entity, AbilityProfile profile){
        AbilityState state = states.get(entity.id);
        if(state == null) return 0f;

        if(state.cooldownTimer > 0f && profile.meltdownDuration > 0f){
            return Mathf.clamp(state.cooldownTimer / (profile.meltdownDuration * 60f));
        }
        if(state.chargeTimer > 0f && profile.chargeDuration > 0f){
            return Mathf.clamp(1f - (state.chargeTimer / (profile.chargeDuration * 60f)));
        }

        switch(profile.trigger.type){
            case EVERY_N_SHOTS:
                if(state.activeTimer > 0f && profile.activeDuration > 0f){
                    return Mathf.clamp(state.activeTimer / (profile.activeDuration * 60f));
                }
                if(state.empoweredShots > 0) return 1f;
                return Mathf.clamp((float)state.shotCounter / profile.trigger.everyNShots);

            case CHANCE_PER_CYCLE:
                if(state.activeTimer > 0f && profile.activeDuration > 0f){
                    return Mathf.clamp(state.activeTimer / (profile.activeDuration * 60f));
                }
                return 0f;

            case CONTINUOUS_HEAT:
                if(state.activeTimer > 0f && profile.activeDuration > 0f){
                    return Mathf.clamp(state.activeTimer / (profile.activeDuration * 60f));
                }
                return Mathf.clamp(state.heatTimer / (profile.trigger.heatSeconds * 60f));

            case STAGE_DURATION:
                if(state.stage == 1) return Mathf.clamp(state.stageProgress / (profile.trigger.stage1Seconds * 60f));
                if(state.stage == 2) return Mathf.clamp(state.stageProgress / (profile.trigger.stage2Seconds * 60f));
                if(state.stage3HoldTimer > 0f && state.stageGraceTimer <= 0f){
                    return Mathf.clamp(state.stage3HoldTimer / (profile.stage3HoldDuration * 60f));
                }
                return 1f;

            case CONFIRMED_UNIT_HITS:
                return Mathf.clamp((float)state.shotCounter / profile.trigger.everyNShots);

            case INTERCEPT_COUNT:
                if(state.activeTimer > 0f && profile.activeDuration > 0f){
                    return Mathf.clamp(state.activeTimer / (profile.activeDuration * 60f));
                }
                return Mathf.clamp((float)state.shotCounter / profile.trigger.everyNShots);

            case TRACTOR_LOCK:
                if(state.activeTimer > 0f && profile.activeDuration > 0f){
                    return Mathf.clamp(state.activeTimer / (profile.activeDuration * 60f));
                }
                return Mathf.clamp(state.heatTimer / (profile.trigger.heatSeconds * 60f));

            default:
                return 0f;
        }
    }

    private static void updateRuntime(){
        if(Vars.state.isPaused()) return;
        tickCounter++;

        // 1. Update all registered turret buildings
        for(Team team : Team.all){
            for(Building build : team.data().buildings){
                AbilityProfile profile = profiles.get(build.block);
                if(profile == null) continue;
                AbilityState state = states.get(build.id);
                if(state == null){
                    state = new AbilityState();
                    if(build instanceof TurretBuild){
                        state.lastTotalShots = ((TurretBuild)build).totalShots;
                    }
                    states.put(build.id, state);
                }
                updateBuilding(build, profile, state);
            }
        }

        // 2. Track Segment / Fragment projectile intercepts safely
        updatePointDefenseIntercepts();

        // 3. Snapshot Groups.bullet so removing/creating bullets never skips simultaneous shots!
        bulletSnapshot.clear();
        for(Bullet b : Groups.bullet){
            bulletSnapshot.add(b);
        }

        for(int i = 0; i < bulletSnapshot.size; i++){
            Bullet b = bulletSnapshot.get(i);
            if(b == null || !b.isAdded()) continue;

            if(!processedBullets.contains(b.id)){
                processedBullets.add(b.id);
                onBulletSpawn(b);
            }else if(activeEmpoweredBullets.contains(b.id)){
                AbilityProfile owner = bulletOwners.get(b.id);
                if(owner != null && owner.id == AbilityId.SCATTER_SNAKE_SWARM){
                    steerSnakeMissile(b);
                }else if(owner != null && owner.id == AbilityId.FORESHADOW_APEX_EXECUTION){
                    steerApexBullet(b);
                }
            }
        }

        if(processedBullets.size > 4000){
            processedBullets.clear();
            activeEmpoweredBullets.clear();
            galeBullets.clear();
            bulletOwners.clear();
            bulletTurretTargets.clear();
            bulletShotKeys.clear();
            for(Bullet b : Groups.bullet){
                processedBullets.add(b.id);
            }
        }

        updateDelayedEchoes();
        updateDelayedBomblets();
        updateMarkedUnits();
        updateSpinningUnits();
        updateTidalWaves();
                    } 
private static void updateBuilding(Building build, AbilityProfile profile, AbilityState state){
        TurretBuild tb = build instanceof TurretBuild ? (TurretBuild)build : null;
        Turret turret = tb != null && tb.block instanceof Turret ? (Turret)tb.block : null;

        // 1. Meltdown lockdown (cuts off firing + ammo/power/liquid intake)
        if(state.cooldownTimer > 0f){
            state.cooldownTimer = Math.max(0f, state.cooldownTimer - Time.delta);
            if(profile.meltdownDuration > 0f){
                state.lockedByAbility = true;
                build.enabled = false;
                build.efficiency = 0f;
                if(tb != null) tb.reloadCounter = 0f;
                if(Mathf.chanceDelta(0.18f)){
                    AbilityEffects.activeTurretSpark.at(
                        build.x + Mathf.range(build.block.size * 3.5f),
                        build.y + Mathf.range(build.block.size * 3.5f),
                        Color.scarlet
                    );
                }
            }
            if(state.cooldownTimer <= 0f && state.lockedByAbility){
                state.lockedByAbility = false;
                build.enabled = true;
            }
            return;
        }else if(state.lockedByAbility){
            state.lockedByAbility = false;
            build.enabled = true;
        }

        // 2. Charging phase (Spectre 3s orb, Meltdown 7s orb)
        if(state.chargeTimer > 0f && tb != null && turret != null){
            tb.reloadCounter = 0f;
            if(!profile.canTurnWhileCharging){
                tb.rotation = state.lockedRotation;
            }
            state.chargeTimer = Math.max(0f, state.chargeTimer - Time.delta);

            Color orbCol = getTurretAccentColor(tb, profile);
            float muzzleX = tb.x + Angles.trnsx(tb.rotation, turret.shootY);
            float muzzleY = tb.y + Angles.trnsy(tb.rotation, turret.shootY);
            if(Mathf.chanceDelta(0.65f)){
                float streakRad = profile.id == AbilityId.MELTDOWN_GRANITE_BLAST ? 68f : 44f;
                AbilityEffects.singularityAbsorbStreak.at(muzzleX, muzzleY, Mathf.random(360f), orbCol, streakRad);
            }

            if(state.chargeTimer <= 0f){
                float blastRad = profile.id == AbilityId.MELTDOWN_GRANITE_BLAST ? 74f : 48f;
                AbilityEffects.singularityReleaseBlast.at(muzzleX, muzzleY, blastRad, orbCol);
                if(profile.id == AbilityId.SPECTRE_SINGULARITY_ORB){
                    state.empoweredShots = 1;
                    tb.reloadCounter = turret.reload + 1f;
                }else if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST){
                    state.activeTimer = profile.activeDuration * 60f;
                    state.lockedRotation = tb.rotation;
                    tb.reloadCounter = turret.reload + 1f;
                }
            }
            return;
        }

        // 3. Active duration updates
        if(state.activeTimer > 0f){
            state.activeTimer = Math.max(0f, state.activeTimer - Time.delta);

            if(tb != null && profile.lockRotationWhileActive){
                tb.rotation = state.lockedRotation;
            }

            if(profile.id == AbilityId.PARALLAX_GRAVITY_SLAM){
                updateParallaxActive(build, profile, state);
            }else if(profile.id == AbilityId.SEGMENT_AEGIS_PRISM){
                if(tickCounter % 4 == 0){
                    runSegmentAegisSweep(build, profile);
                }
            }else if(tb != null && Math.chanceDelta(0.28f)){
                AbilityEffects.activeTurretSpark.at(
                    tb.x + Mathf.range(tb.block.size * 3.5f),
                    tb.y + Mathf.range(tb.block.size * 3.5f),
                    getTurretAccentColor(tb, profile)
                );
            }

            if(state.activeTimer <= 0f && profile.meltdownDuration > 0f){
                state.cooldownTimer = profile.meltdownDuration * 60f;
                state.lockedByAbility = true;
                build.enabled = false;
                return;
            }
        }

        // 4. Trigger-specific updates
        if(tb != null && turret != null){
            // Shot-count delta (counts trigger pulls, NOT individual shotgun pellets!)
            if(tb.totalShots > state.lastTotalShots){
                state.lastTotalShots = tb.totalShots;
                onTurretTriggerPull(tb, turret, profile, state);
            }

            // Spectre / Meltdown pre-fire charge detection on the final threshold shot
            if((profile.id == AbilityId.SPECTRE_SINGULARITY_ORB || profile.id == AbilityId.MELTDOWN_GRANITE_BLAST)
                && state.shotCounter >= profile.trigger.everyNShots - 1
                && state.empoweredShots <= 0 && state.activeTimer <= 0f
                && tb.isShooting() && tb.hasAmmo() && tb.efficiency > 0.05f
                && tb.reloadCounter >= turret.reload * 0.92f){
                state.shotCounter = 0;
                state.chargeTimer = profile.chargeDuration * 60f;
                state.lockedRotation = tb.rotation;
                tb.reloadCounter = 0f;
                return;
            }

            // Foreshadow Apex target lock (highest HP enemy in range)
            if(profile.id == AbilityId.FORESHADOW_APEX_EXECUTION && state.shotCounter >= profile.trigger.everyNShots){
                Unit best = findHighestHpEnemy(tb.team, tb.x, tb.y, turret.range);
                state.apexTarget = best;
                if(best != null && !best.dead){
                    tb.target = best;
                    tb.targetPos.set(best.x, best.y);
                }
            }else{
                state.apexTarget = null;
            }

            // Continuous heat / pressure (Scorch, Wave, Tsunami)
            if(profile.trigger.type == TriggerType.CONTINUOUS_HEAT && state.activeTimer <= 0f){
                boolean firing = tb.isShooting() && tb.hasAmmo() && tb.efficiency > 0.05f;
                if(firing){
                    state.heatTimer = Math.min(profile.trigger.heatSeconds * 60f, state.heatTimer + Time.delta * tb.efficiency);
                    if(profile.id == AbilityId.SCORCH_THERMAL_RAMP){
                        float heatFrac = state.heatTimer / (profile.trigger.heatSeconds * 60f);
                        float bonusReload = Mathf.lerp(0f, profile.fireRateMultiplier - 1f, heatFrac);
                        tb.reloadCounter += Time.delta * bonusReload * tb.efficiency;
                    }
                    if(state.heatTimer >= profile.trigger.heatSeconds * 60f){
                        state.heatTimer = 0f;
                        triggerHeatAbility(tb, turret, profile, state);
                    }
                }else{
                    state.heatTimer = Math.max(0f, state.heatTimer - Time.delta * 0.45f);
                }
            }

            // Stage duration (Salvo)
            if(profile.trigger.type == TriggerType.STAGE_DURATION){
                updateSalvoStages(tb, turret, profile, state);
            }

            // Active fire rate boost (Duo Overdrive, Scorch Overheated)
            if(state.activeTimer > 0f && profile.fireRateMultiplier > 1f){
                tb.reloadCounter += Time.delta * (profile.fireRateMultiplier - 1f) * tb.efficiency;
            }
        }

        // 5. Parallax tractor lock buildup
        if(profile.id == AbilityId.PARALLAX_GRAVITY_SLAM && build instanceof TractorBeamBuild && state.activeTimer <= 0f){
            TractorBeamBuild tbb = (TractorBeamBuild)build;
            if(tbb.target != null && !tbb.target.dead && build.efficiency > 0.05f){
                state.heatTimer += Time.delta * build.efficiency;
                if(state.heatTimer >= profile.trigger.heatSeconds * 60f){
                    state.heatTimer = 0f;
                    startParallaxTether(tbb, profile, state);
                }
            }else{
                state.heatTimer = Math.max(0f, state.heatTimer - Time.delta * 0.6f);
            }
        }
    }

    private static void onTurretTriggerPull(TurretBuild tb, Turret turret, AbilityProfile profile, AbilityState state){
        if(profile.trigger.type == TriggerType.EVERY_N_SHOTS){
            if(profile.id == AbilityId.SPECTRE_SINGULARITY_ORB || profile.id == AbilityId.MELTDOWN_GRANITE_BLAST){
                if(state.empoweredShots > 0 || state.activeTimer > 0f){
                    return;
                }
                state.shotCounter++;
                return;
            }
            if(state.activeTimer > 0f) return;
            state.shotCounter++;
            if(state.shotCounter >= profile.trigger.everyNShots){
                state.shotCounter = 0;
                if(profile.activeDuration > 0f){
                    state.activeTimer = profile.activeDuration * 60f;
                    AbilityEffects.overdriveTrigger.at(tb.x, tb.y, profile.accentColor);
                }else{
                    state.empoweredShots = 1;
                }
            }
        }else if(profile.trigger.type == TriggerType.CHANCE_PER_CYCLE){
            // Cyclone rolls 10% per individual bullet inside onBulletSpawn instead of per burst
            if(profile.id == AbilityId.CYCLONE_GALE_RICOCHET) return;
            if(state.activeTimer > 0f) return;

            if(Mathf.chance(profile.trigger.chance)){
                if(profile.id == AbilityId.SCATTER_SNAKE_SWARM){
                    state.activeTimer = profile.activeDuration * 60f;
                    AbilityEffects.overdriveTrigger.at(tb.x, tb.y, profile.accentColor);
                }else{
                    state.empoweredShotIndex = tb.totalShots;
                }
            }
        }
    }

    private static void updateSalvoStages(TurretBuild tb, Turret turret, AbilityProfile profile, AbilityState state){
        boolean firing = tb.isShooting() && tb.hasAmmo() && tb.efficiency > 0.05f;
        if(firing){
            state.stageGraceTimer = profile.trigger.decayDelaySeconds * 60f;
            state.stage3HoldTimer = profile.stage3HoldDuration * 60f;

            if(state.stage == 1){
                state.stageProgress += Time.delta * tb.efficiency;
                if(state.stageProgress >= profile.trigger.stage1Seconds * 60f){
                    state.stage = 2;
                    state.stageProgress = 0f;
                    AbilityEffects.overdriveTrigger.at(tb.x, tb.y, Color.gold);
                }
            }else if(state.stage == 2){
                state.stageProgress += Time.delta * tb.efficiency;
                if(state.stageProgress >= profile.trigger.stage2Seconds * 60f){
                    state.stage = 3;
                    state.stageProgress = 0f;
                    AbilityEffects.overdriveTrigger.at(tb.x, tb.y, Color.orange);
                }
            }
        }else{
            if(state.stage == 3){
                if(state.stage3HoldTimer > 0f){
                    state.stage3HoldTimer = Math.max(0f, state.stage3HoldTimer - Time.delta);
                }else{
                    state.stage = 2;
                    state.stageProgress = profile.trigger.stage2Seconds * 60f * 0.65f;
                    state.stageGraceTimer = profile.trigger.decayDelaySeconds * 60f;
                }
            }else{
                if(state.stageGraceTimer > 0f){
                    state.stageGraceTimer = Math.max(0f, state.stageGraceTimer - Time.delta);
                }else{
                    state.stageProgress -= Time.delta * 1.3f;
                    if(state.stageProgress <= 0f){
                        if(state.stage == 2){
                            state.stage = 1;
                            state.stageProgress = profile.trigger.stage1Seconds * 60f * 0.7f;
                        }else{
                            state.stageProgress = 0f;
                        }
                    }
                }
            }
        }

        if(state.stage == 2){
            tb.reloadCounter += Time.delta * (profile.stage2FireRateMult - 1f) * tb.efficiency;
            if(Mathf.chanceDelta(0.22f)){
                AbilityEffects.activeTurretSpark.at(
                    tb.x + Mathf.range(tb.block.size * 3.5f),
                    tb.y + Mathf.range(tb.block.size * 3.5f),
                    Color.gold
                );
            }
        }else if(state.stage >= 3){
            tb.reloadCounter += Time.delta * (profile.stage3FireRateMult - 1f) * tb.efficiency;
            if(Mathf.chanceDelta(0.36f)){
                AbilityEffects.activeTurretSpark.at(
                    tb.x + Mathf.range(tb.block.size * 3.5f),
                    tb.y + Mathf.range(tb.block.size * 3.5f),
                    Color.orange
                );
            }
        }
    }

    private static void triggerHeatAbility(TurretBuild tb, Turret turret, AbilityProfile profile, AbilityState state){
        if(profile.id == AbilityId.SCORCH_THERMAL_RAMP){
            state.activeTimer = profile.activeDuration * 60f;
            AbilityEffects.overdriveTrigger.at(tb.x, tb.y, profile.accentColor);
        }else if(profile.id == AbilityId.WAVE_ALCHEMICAL_MONSOON){
            state.activeTimer = profile.activeDuration * 60f;
            Liquid liq = tb.liquids != null ? tb.liquids.current() : Liquids.water;
            Color col = liq != null ? liq.color : profile.accentColor;
            AbilityEffects.waveMonsoonPulse.at(tb.x, tb.y, 56f, col);
            triggerWaveElementalSurge(tb, profile, liq, col);
        }else if(profile.id == AbilityId.TSUNAMI_TIDAL_WALL){
            state.activeTimer = profile.activeDuration * 60f;
            Liquid liq = tb.liquids != null ? tb.liquids.current() : Liquids.water;
            Color col = liq != null ? liq.color : profile.accentColor;
            AbilityEffects.overdriveTrigger.at(tb.x, tb.y, col);
            spawnTsunamiWave(tb, turret, profile, liq, col);
        }
    }

    private static void triggerWaveElementalSurge(TurretBuild tb, AbilityProfile profile, Liquid liq, Color col){
        float rad = 60f;
        if(liq == Liquids.water){
            Units.nearby(tb.team, tb.x, tb.y, rad, u -> {
                u.unapply(StatusEffects.burning);
                u.unapply(StatusEffects.melting);
            });
            Units.nearbyEnemies(tb.team, tb.x, tb.y, rad, u -> {
                u.apply(StatusEffects.wet, 240f);
                if(profile.resolvedPrimaryStatus != null) u.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
                pushUnitAway(tb.x, tb.y, u, 4.5f);
            });
        }else if(liq == Liquids.cryofluid){
            Units.nearbyEnemies(tb.team, tb.x, tb.y, rad, u -> u.apply(StatusEffects.freezing, 180f));
            for(Building other : tb.team.data().buildings){
                if(other != tb && other instanceof TurretBuild && other.within(tb, 52f)){
                    if(profile.resolvedSecondaryStatus != null){
                        // Cool nearby allied turrets by nudging reload slightly
                        ((TurretBuild)other).reloadCounter += 10f;
                    }
                    AbilityEffects.activeTurretSpark.at(other.x, other.y, Liquids.cryofluid.color);
                }
            }
        }else if(liq == Liquids.slag){
            Units.nearbyEnemies(tb.team, tb.x, tb.y, rad, u -> {
                u.apply(StatusEffects.burning, 240f);
                if(profile.resolvedTertiaryStatus != null) u.apply(profile.resolvedTertiaryStatus, profile.primaryStatusDuration * 60f);
            });
        }else if(liq == Liquids.oil){
            StatusEffect slowReload = findStatus("mov-slow-reloading-tier-1");
            Units.nearbyEnemies(tb.team, tb.x, tb.y, rad, u -> {
                u.apply(StatusEffects.tarred, 240f);
                if(slowReload != null) u.apply(slowReload, 150f);
            });
        }else{
            Units.nearbyEnemies(tb.team, tb.x, tb.y, rad, u -> {
                if(profile.resolvedPrimaryStatus != null) u.apply(profile.resolvedPrimaryStatus, 150f);
            });
        }
    }

    private static void spawnTsunamiWave(TurretBuild tb, Turret turret, AbilityProfile profile, Liquid liq, Color col){
        TidalWave wave = new TidalWave();
        wave.team = tb.team;
        wave.x = tb.x + Angles.trnsx(tb.rotation, turret.shootY + 6f);
        wave.y = tb.y + Angles.trnsy(tb.rotation, turret.shootY + 6f);
        wave.angle = tb.rotation;
        wave.speed = 2.6f;
        wave.width = 108f;
        wave.duration = profile.activeDuration * 60f;
        wave.timer = wave.duration;
        wave.liquid = liq != null ? liq : Liquids.water;
        wave.color = col;
        wave.profile = profile;
        tidalWaves.add(wave);
    }

    private static void updateTidalWaves(){
        for(int i = tidalWaves.size - 1; i >= 0; i--){
            TidalWave w = tidalWaves.get(i);
            w.timer -= Time.delta;
            if(w.timer <= 0f){
                tidalWaves.remove(i);
                continue;
            }

            w.x += Angles.trnsx(w.angle, w.speed * Time.delta);
            w.y += Angles.trnsy(w.angle, w.speed * Time.delta);

            if(tickCounter % 5 == 0){
                AbilityEffects.tsunamiTidalSplash.at(w.x, w.y, w.angle, w.color);
                for(int k = -1; k <= 1; k++){
                    float px = w.x + Angles.trnsx(w.angle + 90f, k * (w.width * 0.3f));
                    float py = w.y + Angles.trnsy(w.angle + 90f, k * (w.width * 0.3f));
                    Tile tile = Vars.world.tileWorld(px, py);
                    if(tile != null && w.liquid != null){
                        Puddles.deposit(tile, w.liquid, 8f);
                    }
                }
            }

            float hitRad = w.width * 0.52f;
            if(w.liquid == Liquids.water){
                Units.nearby(w.team, w.x, w.y, hitRad, u -> {
                    u.unapply(StatusEffects.burning);
                    u.unapply(StatusEffects.melting);
                });
            }

            Units.nearbyEnemies(w.team, w.x, w.y, hitRad, u -> {
                if(u.isFlying()) return;
                pushUnitAlong(w.angle, u, w.profile.knockbackForce * 0.16f * Time.delta);
                if(!w.hitUnitIds.contains(u.id)){
                    w.hitUnitIds.add(u.id);
                    if(w.liquid == Liquids.water){
                        u.apply(StatusEffects.wet, 300f);
                        if(w.profile.resolvedPrimaryStatus != null) u.apply(w.profile.resolvedPrimaryStatus, w.profile.primaryStatusDuration * 60f);
                    }else if(w.liquid == Liquids.cryofluid){
                        u.apply(StatusEffects.freezing, 240f);
                        if(w.profile.resolvedSecondaryStatus != null) u.apply(w.profile.resolvedSecondaryStatus, w.profile.primaryStatusDuration * 60f);
                    }else if(w.liquid == Liquids.slag){
                        u.apply(StatusEffects.melting, 240f);
                        if(w.profile.resolvedTertiaryStatus != null) u.apply(w.profile.resolvedTertiaryStatus, w.profile.primaryStatusDuration * 60f);
                        StatusEffect ap2 = findStatus("mov-armor-piercing-tier-2");
                        if(ap2 != null) u.apply(ap2, w.profile.primaryStatusDuration * 60f);
                    }else if(w.liquid == Liquids.oil){
                        u.apply(StatusEffects.tarred, 300f);
                        if(w.profile.resolvedSecondaryStatus != null) u.apply(w.profile.resolvedSecondaryStatus, w.profile.primaryStatusDuration * 60f);
                        if(u.hasEffect(StatusEffects.burning)){
                            Damage.damage(w.team, u.x, u.y, 28f, 45f, false, true);
                            Fx.fireHit.at(u.x, u.y);
                        }
                    }else if(w.profile.resolvedPrimaryStatus != null){
                        u.apply(w.profile.resolvedPrimaryStatus, w.profile.primaryStatusDuration * 60f);
                    }
                }
            });
        }
    }

    private static void startParallaxTether(TractorBeamBuild tbb, AbilityProfile profile, AbilityState state){
        Unit main = tbb.target;
        if(main == null || main.dead) return;
        state.tetherMain = main;
        state.tetherExtras.clear();

        Units.nearbyEnemies(tbb.team, main.x, main.y, profile.pullRadius, u -> {
            if(u != main && !u.dead && u.isFlying() && state.tetherExtras.size < profile.chainTargets){
                state.tetherExtras.add(u);
            }
        });
        state.activeTimer = profile.activeDuration * 60f;
        AbilityEffects.overdriveTrigger.at(tbb.x, tbb.y, profile.accentColor);
    }

    private static void updateParallaxActive(Building build, AbilityProfile profile, AbilityState state){
        if(state.tetherMain == null || state.tetherMain.dead){
            state.activeTimer = 0f;
            state.tetherExtras.clear();
            return;
        }
        Unit main = state.tetherMain;
        for(int i = state.tetherExtras.size - 1; i >= 0; i--){
            Unit u = state.tetherExtras.get(i);
            if(u == null || u.dead){
                state.tetherExtras.remove(i);
                continue;
            }
            float ang = Angles.angle(u.x, u.y, main.x, main.y);
            float dist = Mathf.dst(u.x, u.y, main.x, main.y);
            if(dist > 14f){
                float pull = Math.min(profile.pullStrength, dist * 0.08f) * Time.delta * 0.55f;
                u.impulse(Angles.trnsx(ang, pull), Angles.trnsy(ang, pull));
            }
        }

        if(state.activeTimer <= 0f){
            // Polarity Sky-Slam finale!
            AbilityEffects.parallaxGravitySlam.at(main.x, main.y, 52f, profile.accentColor);
            float burst = 45f + state.tetherExtras.size * 30f;
            applyParallaxSlamUnit(build.team, main, profile, burst, main.x, main.y);
            for(int i = 0; i < state.tetherExtras.size; i++){
                Unit u = state.tetherExtras.get(i);
                if(u != null && !u.dead){
                    applyParallaxSlamUnit(build.team, u, profile, burst, main.x, main.y);
                }
            }
            state.tetherMain = null;
            state.tetherExtras.clear();
        }
    }

    private static void applyParallaxSlamUnit(Team team, Unit u, AbilityProfile profile, float dmg, float cx, float cy){
        Damage.damage(team, u.x, u.y, 14f, dmg, true, false);
        if(profile.resolvedPrimaryStatus != null) u.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
        if(profile.resolvedSecondaryStatus != null) u.apply(profile.resolvedSecondaryStatus, profile.primaryStatusDuration * 60f);
        if(profile.resolvedTertiaryStatus != null) u.apply(profile.resolvedTertiaryStatus, profile.primaryStatusDuration * 60f);
        if(u != null && !u.dead){
            float ang = u.within(cx, cy, 4f) ? Mathf.random(360f) : Angles.angle(cx, cy, u.x, u.y);
            u.impulse(Angles.trnsx(ang, 180f), Angles.trnsy(ang, 180f));
        }
    }

    private static void updatePointDefenseIntercepts(){
        // Track enemy bullets near any active Segment or Fragment
        for(Bullet b : Groups.bullet){
            if(b == null || !b.isAdded() || !b.type.hittable) continue;
            if(b.type instanceof ContinuousBulletType || b.type instanceof LaserBulletType || b.type instanceof PointLaserBulletType) continue;

            TrackedEnemyBullet tracked = trackedEnemyBullets.get(b.id);
            if(tracked == null){
                tracked = new TrackedEnemyBullet();
                trackedEnemyBullets.put(b.id, tracked);
            }
            tracked.x = b.x;
            tracked.y = b.y;
            tracked.team = b.team;
            tracked.damage = b.damage + b.type.splashDamage * 0.5f;
            tracked.time = b.time;
            tracked.lifetime = b.lifetime;
            tracked.ownerId = b.owner instanceof Unit ? ((Unit)b.owner).id : -1;
            tracked.lastSeenTick = tickCounter;
        }

        IntSeq toRemove = new IntSeq();
        for(IntMap.Entry<TrackedEnemyBullet> entry : trackedEnemyBullets){
            TrackedEnemyBullet t = entry.value;
            if(t.lastSeenTick == tickCounter) continue;
            toRemove.add(entry.key);

            // Bullet disappeared before natural expiration -> check if a Segment/Fragment was aiming near it
            if(t.time >= t.lifetime - 2f) continue;

            Building bestDefense = null;
            AbilityProfile bestProfile = null;
            float bestDst = Float.MAX_VALUE;

            for(Team team : Team.all){
                if(team == t.team) continue;
                for(Building build : team.data().buildings){
                    AbilityProfile prof = profiles.get(build.block);
                    if(prof == null) continue;
                    if(prof.id != AbilityId.SEGMENT_AEGIS_PRISM && prof.id != AbilityId.FRAGMENT_STATIC_INTERCEPTOR) continue;
                    if(build.efficiency <= 0.1f) continue;
                    float range = ((PointDefenseTurret)build.block).range;
                    float dst = Mathf.dst(build.x, build.y, t.x, t.y);
                    if(dst <= range + 8f && dst < bestDst){
                        bestDst = dst;
                        bestDefense = build;
                        bestProfile = prof;
                    }
                }
            }

            if(bestDefense != null && bestProfile != null){
                onProjectileIntercepted(bestDefense, bestProfile, t);
            }
        }
        for(int i = 0; i < toRemove.size; i++){
            trackedEnemyBullets.remove(toRemove.get(i));
        }
    }

    private static void onProjectileIntercepted(Building build, AbilityProfile profile, TrackedEnemyBullet t){
        AbilityState state = states.get(build.id);
        if(state == null) return;

        state.shotCounter++;
        if(profile.secondaryThreshold > 0 && state.activeTimer <= 0f){
            state.secondaryCounter++;
            if(state.secondaryCounter >= profile.secondaryThreshold){
                state.secondaryCounter = 0;
                state.activeTimer = profile.activeDuration * 60f;
                AbilityEffects.segmentAegisPulse.at(build.x, build.y, 140f, profile.accentColor);
            }
        }

        if(state.shotCounter >= profile.trigger.everyNShots){
            state.shotCounter = 0;
            if(profile.id == AbilityId.FRAGMENT_STATIC_INTERCEPTOR){
                Unit target = Units.closestEnemy(build.team, t.x, t.y, profile.chainRange, u -> !u.dead);
                if(target != null){
                    AbilityEffects.chainLightningArc.at(t.x, t.y, 0f, profile.accentColor, new Vec2(target.x, target.y));
                    AbilityEffects.fragmentStaticZap.at(target.x, target.y, profile.accentColor);
                    Damage.damage(build.team, target.x, target.y, 8f, 18f, true, true);
                    target.apply(StatusEffects.shocked, 90f);
                }
            }else if(profile.id == AbilityId.SEGMENT_AEGIS_PRISM){
                Unit shooter = t.ownerId >= 0 ? Groups.unit.getByID(t.ownerId) : null;
                if(shooter == null || shooter.dead){
                    shooter = Units.closestEnemy(build.team, build.x, build.y, 260f, u -> !u.dead);
                }
                if(shooter != null && !shooter.dead){
                    float reflectDmg = Math.max(25f, t.damage * profile.damageMultiplier);
                    AbilityEffects.segmentCounterBeam.at(build.x, build.y, 0f, profile.accentColor, new Vec2(shooter.x, shooter.y));
                    Damage.damage(build.team, shooter.x, shooter.y, 10f, reflectDmg, true, true);
                    if(profile.resolvedPrimaryStatus != null){
                        shooter.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
                    }
                }
            }
        }
    }

    private static void runSegmentAegisSweep(Building build, AbilityProfile profile){
        float rad = 140f;
        int zapped = 0;
        for(int i = 0; i < bulletSnapshot.size && zapped < 4; i++){
            Bullet b = bulletSnapshot.get(i);
            if(b == null || !b.isAdded() || b.team == build.team || !b.type.hittable) continue;
            if(b.type instanceof ContinuousBulletType || b.type instanceof LaserBulletType || b.type instanceof PointLaserBulletType) continue;
            if(b.damage > 250f) continue;
            if(b.within(build, rad)){
                AbilityEffects.segmentCounterBeam.at(build.x, build.y, 0f, profile.accentColor, new Vec2(b.x, b.y));
                Fx.pointHit.at(b.x, b.y);
                b.remove();
                zapped++;
            }
        }
        if(tickCounter % 20 == 0){
            AbilityEffects.segmentAegisPulse.at(build.x, build.y, rad, profile.accentColor);
        }
    }

    private static void onBulletSpawn(Bullet b){
        if(!(b.owner instanceof TurretBuild)) return;
        TurretBuild tb = (TurretBuild)b.owner;
        AbilityProfile profile = profiles.get(tb.block);
        if(profile == null) return;

        AbilityState state = states.get(tb.id);
        if(state == null) return;

        bulletOwners.put(b.id, profile);
        long shotKey = (((long)tb.id) << 32) | (tb.totalShots & 0xFFFFFFFFL);
        bulletShotKeys.put(b.id, shotKey);

        // 1. Cyclone rolls 10% per individual bullet
        if(profile.id == AbilityId.CYCLONE_GALE_RICOCHET){
            if(Mathf.chance(profile.trigger.chance)){
                galeBullets.add(b.id);
                activeEmpoweredBullets.add(b.id);
            }
            return;
        }

        // 2. Foreshadow Apex Execution shot check
        if(profile.id == AbilityId.FORESHADOW_APEX_EXECUTION){
            if(state.shotCounter >= profile.trigger.everyNShots){
                state.shotCounter = 0;
                activeEmpoweredBullets.add(b.id);
                b.damage *= profile.damageMultiplier;
                if(state.apexTarget != null && !state.apexTarget.dead){
                    bulletTurretTargets.put(b.id, state.apexTarget);
                }
                AbilityEffects.overdriveTrigger.at(tb.x, tb.y, profile.accentColor);
            }
            return;
        }

        // 3. Swarmer bonus against Hunter-Marked air units
        if(profile.id == AbilityId.SWARMER_HUNTERS_MARK){
            for(int i = 0; i < markedUnits.size; i++){
                MarkedUnit mu = markedUnits.get(i);
                if(mu.unit != null && !mu.unit.dead && mu.unit.within(b.x, b.y, 180f)){
                    b.damage *= profile.markBonusMultiplier;
                    break;
                }
            }
        }

        // 4. Scatter active Snake Swarm swaps ALL flak bullets in the volley cleanly
        if(profile.id == AbilityId.SCATTER_SNAKE_SWARM && state.activeTimer > 0f){
            convertToSnakeMissile(tb, b, profile);
            return;
        }

        // 5. Salvo stage scaling + clean muzzle spark
        if(profile.id == AbilityId.SALVO_ESCALATING_BARREL){
            if(state.stage == 2){
                b.vel.scl(profile.stage2SpeedMult);
                b.lifetime /= profile.stage2SpeedMult;
                applyAimTightening(tb, b, profile.stage2InaccuracyMult);
                activeEmpoweredBullets.add(b.id);
                spawnSalvoMuzzle(tb, Color.gold);
            }else if(state.stage >= 3){
                b.vel.scl(profile.stage3SpeedMult * profile.stage3RangeMult);
                b.lifetime /= profile.stage3SpeedMult;
                applyAimTightening(tb, b, profile.stage3InaccuracyMult);
                activeEmpoweredBullets.add(b.id);
                spawnSalvoMuzzle(tb, Color.orange);
            }
            return;
        }

        // 6. Continuous heat active buffs (Scorch, Wave)
        if(profile.trigger.type == TriggerType.CONTINUOUS_HEAT && state.activeTimer > 0f){
            if(profile.id == AbilityId.SCORCH_THERMAL_RAMP){
                b.damage *= profile.damageMultiplier;
                b.vel.scl(profile.bulletSpeedMultiplier);
                activeEmpoweredBullets.add(b.id);
            }else if(profile.id == AbilityId.WAVE_ALCHEMICAL_MONSOON){
                b.vel.scl(profile.bulletSpeedMultiplier);
                b.lifetime *= (profile.rangeMultiplier / profile.bulletSpeedMultiplier);
                activeEmpoweredBullets.add(b.id);
            }
            return;
        }

        // 7. Continuous heat passive Scorch ramp
        if(profile.id == AbilityId.SCORCH_THERMAL_RAMP && state.heatTimer > 0f){
            float frac = state.heatTimer / (profile.trigger.heatSeconds * 60f);
            b.damage *= Mathf.lerp(1f, profile.damageMultiplier, frac);
            return;
        }

        // 8. Meltdown Granite Blast active laser
        if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST && state.activeTimer > 0f){
            b.damage *= profile.damageMultiplier;
            activeEmpoweredBullets.add(b.id);
            return;
        }

        // 9. Duration active buff (Duo Overdrive Rush)
        if(profile.activeDuration > 0f && state.activeTimer > 0f && profile.trigger.type == TriggerType.EVERY_N_SHOTS){
            b.damage *= profile.damageMultiplier;
            b.vel.scl(profile.bulletSpeedMultiplier);
            b.lifetime /= profile.bulletSpeedMultiplier;
            activeEmpoweredBullets.add(b.id);
            return;
        }

        // 10. Single-shot empowered triggers (Spectre 5x, Hail, Lancer, Arc, Ripple, Fuse, Swarmer)
        boolean singleShotReady = (state.empoweredShots > 0) || (state.empoweredShotIndex == tb.totalShots);
        if(singleShotReady){
            if(state.empoweredShots > 0 && (profile.id != AbilityId.SPECTRE_SINGULARITY_ORB || tb.totalShots != state.lastEmpoweredConsumedShot)){
                state.empoweredShots--;
                state.lastEmpoweredConsumedShot = tb.totalShots;
            }
            if(state.empoweredShotIndex == tb.totalShots){
                state.empoweredShotIndex = -1;
            }
            applySingleEmpoweredEffect(tb, b, profile, state);
        }
    }

    private static void applySingleEmpoweredEffect(TurretBuild tb, Bullet b, AbilityProfile profile, AbilityState state){
        activeEmpoweredBullets.add(b.id);
        Color col = getBulletColor(b, profile);

        if(profile.id == AbilityId.SPECTRE_SINGULARITY_ORB){
            b.damage *= profile.damageMultiplier;
            if(b.type.splashDamage > 0f){
                b.type.splashDamage *= 1.5f;
            }
            state.cooldownTimer = profile.meltdownDuration * 60f;
            state.lockedByAbility = true;
            tb.enabled = false;
            return;
        }

        if(profile.id == AbilityId.LANCER_RAIL_ECHO){
            DelayedEcho echo = new DelayedEcho();
            echo.team = tb.team;
            echo.x = b.x;
            echo.y = b.y;
            echo.angle = tb.rotation;
            echo.length = b.type instanceof LaserBulletType ? ((LaserBulletType)b.type).length : 185f;
            echo.damage = Math.max(12f, b.damage * profile.echoDamageFraction);
            echo.timer = profile.echoDelay * 60f;
            echo.color = col;
            echo.status = profile.resolvedPrimaryStatus;
            echo.statusDuration = profile.primaryStatusDuration * 60f;
            delayedEchoes.add(echo);
            return;
        }

        if(profile.id == AbilityId.FUSE_ECHO_KICK){
            DelayedEcho echo = new DelayedEcho();
            echo.team = tb.team;
            echo.x = b.x;
            echo.y = b.y;
            echo.angle = b.rotation();
            echo.length = 95f;
            echo.damage = Math.max(8f, b.damage * profile.echoDamageFraction);
            echo.timer = profile.echoDelay * 60f;
            echo.color = col;
            echo.knockback = profile.knockbackForce;
            echo.status = profile.resolvedPrimaryStatus;
            echo.statusDuration = profile.primaryStatusDuration * 60f;
            delayedEchoes.add(echo);
            return;
        }

        if(profile.id == AbilityId.RIPPLE_CARPET_BOMBLETS){
            b.damage *= profile.damageMultiplier;
            for(int i = 0; i < profile.bombletCount; i++){
                DelayedBomblet bomb = new DelayedBomblet();
                bomb.team = tb.team;
                float spreadAng = b.rotation() + (i - 1) * 22f + Mathf.range(8f);
                float dist = Mathf.dst(tb.x, tb.y, tb.targetPos.x, tb.targetPos.y);
                if(dist < 40f) dist = 160f;
                bomb.x = tb.x + Angles.trnsx(spreadAng, dist + Mathf.range(24f));
                bomb.y = tb.y + Angles.trnsy(spreadAng, dist + Mathf.range(24f));
                bomb.damage = Math.max(15f, (b.damage + b.type.splashDamage) * profile.bombletDamageFraction);
                bomb.timer = profile.bombletDelay * 60f + i * 5f;
                bomb.color = col;
                bomb.status = profile.resolvedPrimaryStatus;
                bomb.statusDuration = profile.primaryStatusDuration * 60f;
                delayedBomblets.add(bomb);
                AbilityEffects.carpetBombletLaunch.at(b.x, b.y, col);
            }
            return;
        }

        if(profile.id == AbilityId.HAIL_SEISMIC_MORTAR || profile.id == AbilityId.ARC_STATIC_CHAIN){
            b.damage *= profile.damageMultiplier;
        }
    }

    private static void onUnitDamaged(Unit unit, Bullet bullet){
        if(unit == null || unit.dead || bullet == null) return;
        AbilityProfile profile = bulletOwners.get(bullet.id);
        if(profile == null) return;

        // 1. Foreshadow confirmed unit hit charging (max +1 per shot, only when hitting a unit!)
        if(profile.id == AbilityId.FORESHADOW_APEX_EXECUTION && bullet.owner instanceof TurretBuild){
            TurretBuild tb = (TurretBuild)bullet.owner;
            AbilityState state = states.get(tb.id);
            Long key = bulletShotKeys.get(bullet.id);
            if(state != null && key != null && !activeEmpoweredBullets.contains(bullet.id) && !countedUnitHitShots.contains(key)){
                if(countedUnitHitShots.size > 200) countedUnitHitShots.clear();
                countedUnitHitShots.add(key);
                state.shotCounter = Math.min(profile.trigger.everyNShots, state.shotCounter + 1);
            }
            if(activeEmpoweredBullets.contains(bullet.id)){
                activeEmpoweredBullets.remove(bullet.id);
                float bonus = Math.min(profile.maxHpBonusCap, unit.maxHealth * profile.maxHpBonusPercent);
                if(bonus > 0f){
                    unit.damagePierce(bonus);
                }
                if(profile.resolvedPrimaryStatus != null) unit.apply(profile.resolvedPrimaryStatus, profile.secondaryStatusDuration * 60f);
                if(profile.resolvedSecondaryStatus != null) unit.apply(profile.resolvedSecondaryStatus, profile.secondaryStatusDuration * 60f);
                if(profile.resolvedTertiaryStatus != null) unit.apply(profile.resolvedTertiaryStatus, profile.secondaryStatusDuration * 60f);
                AbilityEffects.foreshadowApexImpact.at(unit.x, unit.y, getBulletColor(bullet, profile));
            }
            return;
        }

        // 2. Cyclone Gale Ricochet (10% bullet -> bounces to 2 units, only ricochet gets bonus dmg + knockback + spin)
        if(profile.id == AbilityId.CYCLONE_GALE_RICOCHET && galeBullets.contains(bullet.id)){
            galeBullets.remove(bullet.id);
            runCycloneRicochet(bullet.team, unit, bullet, profile);
            return;
        }

        if(!activeEmpoweredBullets.contains(bullet.id)) return;

        Color col = getBulletColor(bullet, profile);

        if(profile.id == AbilityId.ARC_STATIC_CHAIN){
            activeEmpoweredBullets.remove(bullet.id);
            unit.apply(StatusEffects.shocked, 120f);
            Seq<Unit> hitChain = new Seq<>();
            hitChain.add(unit);
            Unit current = unit;
            for(int i = 0; i < profile.chainTargets; i++){
                Unit next = Units.closestEnemy(bullet.team, current.x, current.y, profile.chainRange,
                    u -> !u.dead && !u.isFlying() && !hitChain.contains(u));
                if(next == null) break;
                hitChain.add(next);
                AbilityEffects.chainLightningArc.at(current.x, current.y, 0f, col, new Vec2(next.x, next.y));
                Damage.damage(bullet.team, next.x, next.y, 6f, bullet.damage * profile.chainDamageFraction, true, false);
                next.apply(StatusEffects.shocked, 120f);
                current = next;
            }
            return;
        }

        if(profile.id == AbilityId.HAIL_SEISMIC_MORTAR){
            activeEmpoweredBullets.remove(bullet.id);
            AbilityEffects.seismicPullRing.at(unit.x, unit.y, profile.pullRadius, col);
            Units.nearbyEnemies(bullet.team, unit.x, unit.y, profile.pullRadius, other -> {
                if(!other.dead && !other.isFlying()){
                    float ang = Angles.angle(other.x, other.y, unit.x, unit.y);
                    float pull = profile.pullStrength * 28f;
                    other.impulse(Angles.trnsx(ang, pull), Angles.trnsy(ang, pull));
                    if(profile.resolvedPrimaryStatus != null){
                        other.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
                    }
                }
            });
            return;
        }

        if(profile.id == AbilityId.SWARMER_HUNTERS_MARK && (!profile.airOnlyMark || unit.isFlying())){
            activeEmpoweredBullets.remove(bullet.id);
            markUnit(unit, profile.markDuration * 60f, col);
            if(profile.resolvedPrimaryStatus != null){
                unit.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
            }
            return;
        }

        if(profile.id == AbilityId.SCORCH_THERMAL_RAMP){
            unit.apply(StatusEffects.melting, 150f);
            return;
        }

        if(profile.resolvedPrimaryStatus != null){
            unit.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
        }
        if(profile.id == AbilityId.SALVO_ESCALATING_BARREL && bullet.owner instanceof TurretBuild){
            AbilityState st = states.get(((TurretBuild)bullet.owner).id);
            if(st != null && st.stage >= 3 && profile.resolvedSecondaryStatus != null){
                unit.apply(profile.resolvedSecondaryStatus, profile.primaryStatusDuration * 60f);
            }
        }
    }

    private static void onBuildDamaged(Building build, Bullet bullet){
        if(build == null || bullet == null || !activeEmpoweredBullets.contains(bullet.id)) return;
        AbilityProfile profile = bulletOwners.get(bullet.id);
        if(profile == null) return;

        if(profile.id == AbilityId.HAIL_SEISMIC_MORTAR){
            activeEmpoweredBullets.remove(bullet.id);
            Color col = getBulletColor(bullet, profile);
            AbilityEffects.seismicPullRing.at(build.x, build.y, profile.pullRadius, col);
            Units.nearbyEnemies(bullet.team, build.x, build.y, profile.pullRadius, other -> {
                if(!other.dead && !other.isFlying()){
                    float ang = Angles.angle(other.x, other.y, build.x, build.y);
                    float pull = profile.pullStrength * 28f;
                    other.impulse(Angles.trnsx(ang, pull), Angles.trnsy(ang, pull));
                    if(profile.resolvedPrimaryStatus != null){
                        other.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
                    }
                }
            });
        }
    }

    private static void runCycloneRicochet(Team team, Unit firstHit, Bullet bullet, AbilityProfile profile){
        Color col = getBulletColor(bullet, profile);
        Seq<Unit> hitList = new Seq<>();
        hitList.add(firstHit);
        Unit current = firstHit;

        for(int i = 0; i < profile.ricochetBounces; i++){
            Unit next = Units.closestEnemy(team, current.x, current.y, profile.ricochetRange,
                u -> !u.dead && !hitList.contains(u));
            if(next == null) break;
            hitList.add(next);

            float ricDmg = Math.max(15f, bullet.damage * profile.ricochetDamageMult);
            float bounceAng = Angles.angle(current.x, current.y, next.x, next.y);
            AbilityEffects.segmentCounterBeam.at(current.x, current.y, 0f, col, new Vec2(next.x, next.y));
            AbilityEffects.cycloneSpinImpact.at(next.x, next.y, bounceAng, col);

            Damage.damage(team, next.x, next.y, 8f, ricDmg, true, true);
            next.impulse(Angles.trnsx(bounceAng, profile.ricochetKnockback * 26f), Angles.trnsy(bounceAng, profile.ricochetKnockback * 26f));
            if(profile.resolvedPrimaryStatus != null){
                next.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
            }
            addSpinningUnit(next, 48f);
            current = next;
        }
    }

    private static void convertToSnakeMissile(TurretBuild tb, Bullet oldBullet, AbilityProfile profile){
        Color col = getBulletColor(oldBullet, profile);
        int slot = tb.block.size % snakeMissileCache.length;
        BulletType missileType = getOrCreateSnakeMissile(slot, oldBullet, profile, col);
        Bullet missile = missileType.create(
            tb, tb.team, oldBullet.x, oldBullet.y, oldBullet.rotation(),
            oldBullet.damage * profile.damageMultiplier,
            1f, 1f, null
        );
        if(missile != null){
            processedBullets.add(missile.id);
            activeEmpoweredBullets.add(missile.id);
            bulletOwners.put(missile.id, profile);
        }
        oldBullet.remove();
    }

    private static BulletType getOrCreateSnakeMissile(int slot, Bullet base, AbilityProfile profile, Color col){
        MissileBulletType m = new MissileBulletType(Math.max(4.5f, base.type.speed * 0.9f), base.damage * profile.damageMultiplier){{
            width = 7f;
            height = 11f;
            lifetime = Math.max(48f, base.lifetime * 1.15f);
            homingPower = 0.14f;
            homingRange = 170f;
            homingDelay = 2f;
            weaveScale = 4f;
            weaveMag = 2.6f;
            collidesGround = false;
            collidesAir = true;
            splashDamage = Math.max(14f, base.type.splashDamage * 1.25f);
            splashDamageRadius = Math.max(22f, base.type.splashDamageRadius);
            frontColor = Color.white;
            backColor = trailColor = hitColor = col;
            trailLength = 9;
            trailWidth = 1.8f;
            status = profile.resolvedPrimaryStatus != null ? profile.resolvedPrimaryStatus : StatusEffects.none;
            statusDuration = profile.primaryStatusDuration * 60f;
        }};
        snakeMissileCache[slot] = m;
        return m;
    }

    private static void steerSnakeMissile(Bullet b){
        Unit air = Units.closestEnemy(b.team, b.x, b.y, 175f, u -> !u.dead && u.isFlying());
        if(air != null){
            float targetAng = Angles.angle(b.x, b.y, air.x, air.y);
            b.rotation(Angles.moveToward(b.rotation(), targetAng, 5.2f * Time.delta));
        }
    }

    private static void steerApexBullet(Bullet b){
        Unit target = bulletTurretTargets.get(b.id);
        if(target != null && !target.dead){
            float targetAng = Angles.angle(b.x, b.y, target.x, target.y);
            b.rotation(Angles.moveToward(b.rotation(), targetAng, 14f * Time.delta));
        }
    }

    private static void applyAimTightening(TurretBuild tb, Bullet b, float inaccuracyMult){
        float aimAng = tb.rotation;
        float diff = Angles.angleDist(aimAng, b.rotation());
        float sign = Angles.forwardDistance(aimAng, b.rotation()) >= 0f ? 1f : -1f;
        b.rotation(aimAng + sign * diff * inaccuracyMult);
    }

    private static void spawnSalvoMuzzle(TurretBuild tb, Color col){
        float dist = 7.5f;
        float mx = tb.x + Angles.trnsx(tb.rotation, dist);
        float my = tb.y + Angles.trnsy(tb.rotation, dist);
        AbilityEffects.salvoMuzzleSpark.at(mx, my, tb.rotation, col);
    }

    private static Unit findHighestHpEnemy(Team team, float x, float y, float range){
        final Unit[] best = {null};
        final float[] bestHp = {-1f};
        Units.nearbyEnemies(team, x, y, range, u -> {
            if(u != null && !u.dead && u.health > bestHp[0]){
                bestHp[0] = u.health;
                best[0] = u;
            }
        });
        return best[0];
    }

    private static void addSpinningUnit(Unit u, float duration){
        for(int i = 0; i < spinningUnits.size; i++){
            if(spinningUnits.get(i).unit == u){
                spinningUnits.get(i).timer = duration;
                return;
            }
        }
        SpinningUnit su = new SpinningUnit();
        su.unit = u;
        su.timer = duration;
        spinningUnits.add(su);
    }

    private static void updateSpinningUnits(){
        for(int i = spinningUnits.size - 1; i >= 0; i--){
            SpinningUnit su = spinningUnits.get(i);
            su.timer -= Time.delta;
            if(su.timer <= 0f || su.unit == null || su.unit.dead){
                spinningUnits.remove(i);
                continue;
            }
            su.unit.rotation += 14f * Time.delta;
        }
    }

    private static void markUnit(Unit u, float duration, Color col){
        for(int i = 0; i < markedUnits.size; i++){
            if(markedUnits.get(i).unit == u){
                markedUnits.get(i).timer = duration;
                return;
            }
        }
        MarkedUnit mu = new MarkedUnit();
        mu.unit = u;
        mu.timer = duration;
        mu.color = col;
        markedUnits.add(mu);
        AbilityEffects.huntersMarkPulse.at(u.x, u.y, col);
    }

    private static void updateMarkedUnits(){
        for(int i = markedUnits.size - 1; i >= 0; i--){
            MarkedUnit mu = markedUnits.get(i);
            mu.timer -= Time.delta;
            if(mu.timer <= 0f || mu.unit == null || mu.unit.dead){
                markedUnits.remove(i);
            }
        }
    }

    private static void updateDelayedEchoes(){
        for(int i = delayedEchoes.size - 1; i >= 0; i--){
            DelayedEcho e = delayedEchoes.get(i);
            e.timer -= Time.delta;
            if(e.timer <= 0f){
                delayedEchoes.remove(i);
                float endX = e.x + Angles.trnsx(e.angle, e.length);
                float endY = e.y + Angles.trnsy(e.angle, e.length);
                AbilityEffects.railEchoBeam.at(e.x, e.y, 0f, e.color, new Vec2(endX, endY));

                Damage.collideLine(
                    new Bullet(), e.team, Fx.hitLancer,
                    e.x, e.y, e.angle, e.length, true, false
                );
                Units.nearbyEnemies(e.team, (e.x + endX) * 0.5f, (e.y + endY) * 0.5f, e.length * 0.6f, u -> {
                    if(!u.dead && Intersector.distanceSegmentPoint(e.x, e.y, endX, endY, u.x, u.y) <= u.hitSize + 10f){
                        Damage.damage(e.team, u.x, u.y, 6f, e.damage, true, false);
                        if(e.status != null) u.apply(e.status, e.statusDuration);
                        if(e.knockback > 0f){
                            u.impulse(Angles.trnsx(e.angle, e.knockback * 32f), Angles.trnsy(e.angle, e.knockback * 32f));
                        }
                    }
                });
            }
        }
    }

    private static void updateDelayedBomblets(){
        for(int i = delayedBomblets.size - 1; i >= 0; i--){
            DelayedBomblet b = delayedBomblets.get(i);
            b.timer -= Time.delta;
            if(b.timer <= 0f){
                delayedBomblets.remove(i);
                AbilityEffects.carpetBombletExplosion.at(b.x, b.y, b.color);
                Damage.damage(b.team, b.x, b.y, 26f, b.damage, false, true);
                if(b.status != null){
                    Units.nearbyEnemies(b.team, b.x, b.y, 26f, u -> {
                        if(!u.dead && !u.isFlying()){
                            u.apply(b.status, b.statusDuration);
                        }
                    });
                }
            }
        }
    }

    private static void drawVisuals(){
        Draw.z(Layer.effect);

        // Marked air units (Swarmer Hunter's Mark)
        for(int i = 0; i < markedUnits.size; i++){
            MarkedUnit mu = markedUnits.get(i);
            if(mu.unit == null || mu.unit.dead) continue;
            Draw.color(mu.color, Color.white, 0.25f);
            Lines.stroke(1.6f);
            float rad = mu.unit.hitSize + 6f + Mathf.absin(Time.time, 4f, 2f);
            Lines.circle(mu.unit.x, mu.unit.y, rad);
        }

        // Tsunami Rolling Tidal Waves
        for(int i = 0; i < tidalWaves.size; i++){
            TidalWave w = tidalWaves.get(i);
            float alpha = Mathf.clamp(w.timer / (w.duration * 0.25f), 0.15f, 0.85f);
            Draw.color(w.color, Color.white, 0.2f);
            Draw.alpha(alpha);
            Lines.stroke(6f);
            Lines.arc(w.x - Angles.trnsx(w.angle, 18f), w.y - Angles.trnsy(w.angle, 18f), w.width * 0.5f, 0.38f, w.angle - 68f);
            Lines.stroke(2.5f);
            Draw.color(Color.white);
            Draw.alpha(alpha * 0.7f);
            Lines.arc(w.x - Angles.trnsx(w.angle, 15f), w.y - Angles.trnsy(w.angle, 15f), w.width * 0.52f, 0.34f, w.angle - 61f);
        }

        // Turret charging orbs, Parallax tethers, Foreshadow Apex lock reticle
        for(Team team : Team.all){
            for(Building build : team.data().buildings){
                AbilityProfile profile = profiles.get(build.block);
                if(profile == null) continue;
                AbilityState state = states.get(build.id);
                if(state == null) continue;

                if(state.chargeTimer > 0f && build instanceof TurretBuild){
                    TurretBuild tb = (TurretBuild)build;
                    Turret turret = (Turret)tb.block;
                    float frac = 1f - (state.chargeTimer / (profile.chargeDuration * 60f));
                    float mx = tb.x + Angles.trnsx(tb.rotation, turret.shootY);
                    float my = tb.y + Angles.trnsy(tb.rotation, turret.shootY);
                    Color col = getTurretAccentColor(tb, profile);
                    float maxR = profile.id == AbilityId.MELTDOWN_GRANITE_BLAST ? 16f : 10f;
                    float r = Mathf.lerp(2f, maxR, frac) + Mathf.absin(Time.time, 2f, 1.4f);

                    Draw.color(col);
                    Fill.circle(mx, my, r);
                    Draw.color(Color.white);
                    Fill.circle(mx, my, r * 0.45f);
                    Lines.stroke(1.8f);
                    Draw.color(col);
                    Lines.circle(mx, my, r + 5f * (1f - frac));
                }

                if(profile.id == AbilityId.PARALLAX_GRAVITY_SLAM && state.activeTimer > 0f && state.tetherMain != null && !state.tetherMain.dead){
                    Draw.color(profile.accentColor, Color.white, 0.3f);
                    Lines.stroke(2.2f);
                    for(int i = 0; i < state.tetherExtras.size; i++){
                        Unit u = state.tetherExtras.get(i);
                        if(u != null && !u.dead){
                            Lines.line(state.tetherMain.x, state.tetherMain.y, u.x, u.y);
                        }
                    }
                    Lines.circle(state.tetherMain.x, state.tetherMain.y, state.tetherMain.hitSize + 6f);
                }

                if(profile.id == AbilityId.FORESHADOW_APEX_EXECUTION && state.apexTarget != null && !state.apexTarget.dead){
                    Unit tgt = state.apexTarget;
                    float rad = tgt.hitSize + 10f + Mathf.absin(Time.time, 3f, 2f);
                    Draw.color(profile.accentColor, Color.white, 0.35f);
                    Lines.stroke(2f);
                    Lines.circle(tgt.x, tgt.y, rad);
                    for(int k = 0; k < 4; k++){
                        float a = k * 90f + Time.time * 2f;
                        Lines.lineAngle(tgt.x + Angles.trnsx(a, rad), tgt.y + Angles.trnsy(a, rad), a, 6f);
                    }
                }
            }
        }

        Draw.reset();
    }

    private static Color getTurretAccentColor(TurretBuild tb, AbilityProfile profile){
        if(tb.hasAmmo()){
            BulletType ammo = tb.peekAmmo();
            if(ammo != null && ammo.lightColor != null && ammo.lightColor.a > 0.1f){
                return ammo.lightColor;
            }
        }
        if(tb.liquids != null && tb.liquids.currentAmount() > 0.01f && tb.liquids.current() != null){
            return tb.liquids.current().color;
        }
        return profile.accentColor;
    }

    private static Color getBulletColor(Bullet b, AbilityProfile profile){
        if(b != null && b.type != null){
            if(b.type.lightColor != null && b.type.lightColor.a > 0.1f) return b.type.lightColor;
        }
        return profile.accentColor;
    }

    private static void pushUnitAway(float ox, float oy, Unit u, float force){
        float ang = Angles.angle(ox, oy, u.x, u.y);
        u.impulse(Angles.trnsx(ang, force * 24f), Angles.trnsy(ang, force * 24f));
    }

    private static void pushUnitAlong(float ang, Unit u, float force){
        u.impulse(Angles.trnsx(ang, force * 24f), Angles.trnsy(ang, force * 24f));
    }

    private static void clearAllRuntime(){
        states.clear();
        processedBullets.clear();
        activeEmpoweredBullets.clear();
        galeBullets.clear();
        bulletOwners.clear();
        bulletTurretTargets.clear();
        bulletShotKeys.clear();
        countedUnitHitShots.clear();
        delayedEchoes.clear();
        delayedBomblets.clear();
        markedUnits.clear();
        spinningUnits.clear();
        tidalWaves.clear();
        trackedEnemyBullets.clear();
    }

    private static class AbilityState{
        int shotCounter = 0;
        int secondaryCounter = 0;
        int lastTotalShots = 0;
        int empoweredShots = 0;
        int empoweredShotIndex = -1;
        int lastEmpoweredConsumedShot = -1;
        float activeTimer = 0f;
        float chargeTimer = 0f;
        float cooldownTimer = 0f;
        float heatTimer = 0f;
        float lockedRotation = 0f;
        boolean lockedByAbility = false;

        int stage = 1;
        float stageProgress = 0f;
        float stageGraceTimer = 0f;
        float stage3HoldTimer = 0f;

        Unit apexTarget = null;
        Unit tetherMain = null;
        final Seq<Unit> tetherExtras = new Seq<>();
    }

    private static class DelayedEcho{
        Team team;
        float x, y, angle, length, damage, timer, knockback, statusDuration;
        Color color;
        StatusEffect status;
    }

    private static class DelayedBomblet{
        Team team;
        float x, y, damage, timer, statusDuration;
        Color color;
        StatusEffect status;
    }

    private static class MarkedUnit{
        Unit unit;
        float timer;
        Color color;
    }

    private static class SpinningUnit{
        Unit unit;
        float timer;
    }

    private static class TidalWave{
        Team team;
        float x, y, angle, speed, width, timer, duration;
        Liquid liquid;
        Color color;
        Liquid liquidType;
        AbilityProfile profile;
        final IntSet hitUnitIds = new IntSet();
    }

    private static class TrackedEnemyBullet{
        float x, y, damage, time, lifetime;
        Team team;
        int ownerId;
        int lastSeenTick;
    }
                    }
