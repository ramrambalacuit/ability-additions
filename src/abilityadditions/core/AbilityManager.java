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
import mindustry.entities.units.*;
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
import mindustry.world.draw.*;
import mindustry.world.meta.*;

public class AbilityManager{

    private static final AbilityManager INSTANCE = new AbilityManager();

    private static final ObjectMap<Block, AbilityProfile> profiles = new ObjectMap<>();
    private static final IntMap<AbilityState> states = new IntMap<>();
    private static final IntSet processedBullets = new IntSet();
    private static final IntSet activeEmpoweredBullets = new IntSet();
    private static final IntSet giantShellBullets = new IntSet();
    private static final IntSet galeBullets = new IntSet();
    private static final IntMap<AbilityProfile> bulletOwners = new IntMap<>();
    private static final IntMap<Unit> bulletTurretTargets = new IntMap<>();
    private static final IntMap<Long> bulletShotKeys = new IntMap<>();
    private static final ObjectSet<Long> countedUnitHitShots = new ObjectSet<>();

    private static final Seq<Bullet> bulletSnapshot = new Seq<>();
    private static final Seq<DelayedShrapnelClone> delayedClones = new Seq<>();
    private static final Seq<TrackedRippleShell> trackedRippleShells = new Seq<>();
    private static final Seq<DelayedBomblet> delayedBomblets = new Seq<>();
    private static final Seq<MarkedUnit> markedUnits = new Seq<>();
    private static final Seq<ParalyzedUnit> paralyzedUnits = new Seq<>();
    private static final Seq<SpinningUnit> spinningUnits = new Seq<>();
    private static final Seq<TidalWave> tidalWaves = new Seq<>();
    private static final IntMap<TrackedEnemyBullet> trackedEnemyBullets = new IntMap<>();
    private static final ObjectMap<String, BulletType> snakeMissileCache = new ObjectMap<>();

    private static ContinuousFlameBulletType graniteBlastType;
    private static boolean statsApplied = false;
    private static boolean eventsInitialized = false;
    private static Stat abilityStat;
    private static int tickCounter = 0;

    public static AbilityManager instance(){
        return INSTANCE;
    }

    public static void register(Block block, AbilityProfile profile){
        profiles.put(block, profile);
    }

    public static void init(){
        INSTANCE.applyStats();
        INSTANCE.initEvents();
    }

    public void applyStats(){
        if(statsApplied) return;
        statsApplied = true;

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
    }

    public void initEvents(){
        // Re-apply after the game has finished initialising content (bundle text can overwrite
        // descriptions that were set earlier during loadContent). Safe to call repeatedly.
        refreshDescriptions();

        if(eventsInitialized) return;
        eventsInitialized = true;

        Events.run(Trigger.update, AbilityManager::updateRuntime);
        Events.run(Trigger.draw, AbilityManager::drawVisuals);
        Events.on(UnitDamageEvent.class, e -> onUnitDamaged(e.unit, e.bullet));
        Events.on(BuildDamageEvent.class, e -> onBuildDamaged(e.build, e.source));
        Events.on(ResetEvent.class, e -> clearAllRuntime());
    }

    private static ContinuousFlameBulletType getGraniteBlastType(AbilityProfile profile){
        if(graniteBlastType != null) return graniteBlastType;
        graniteBlastType = new ContinuousFlameBulletType(155f * profile.damageMultiplier){{
            length = 620f;
            width = 26f;
            drawSize = 700f;
            lifetime = profile.activeDuration * 60f + 10f;
            pierceArmor = true;
            laserAbsorb = true;
            status = StatusEffects.melting;
            statusDuration = 240f;
            hitColor = flareColor = AbilityEffects.graniteCyan;
            flareWidth = 11f;
            flareLength = 58f;
            flareRotSpeed = 0.45f;
            colors = new Color[]{
                AbilityEffects.graniteBlue.cpy().a(0.50f),
                AbilityEffects.graniteBlue.cpy().a(0.78f),
                Color.valueOf("7fd2ff").a(0.95f),
                AbilityEffects.graniteCyan,
                AbilityEffects.graniteCore
            };
        }};
        if(!Vars.headless){
            graniteBlastType.load();
        }
        return graniteBlastType;
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
            public void drawPlan(Block b, BuildPlan plan, Eachable<BuildPlan> list){
                originalDrawer.drawPlan(b, plan, list);
            }

            @Override
            public TextureRegion[] icons(Block b){
                return originalDrawer.icons(b);
            }
        };
    }

    private static final String DESC_MARKER = "​"; // invisible marker so we never append twice

    /** Appends the ability text to the block's description so it shows in the research tree tooltip. */
    private static void applyDescription(Block block, AbilityProfile profile){
        if(block == null || profile == null || profile.abilityName == null) return;

        String base = block.description == null ? "" : block.description;
        if(base.contains(DESC_MARKER)) return;

        StringBuilder sb = new StringBuilder(base);
        if(!base.isEmpty()) sb.append("\n\n");
        sb.append(DESC_MARKER);
        sb.append("[accent]").append(profile.abilityName).append("[]");
        if(profile.trigger != null){
            sb.append(" [lightgray](").append(formatTriggerBadge(profile)).append(")[]");
        }
        if(profile.description != null && !profile.description.isEmpty()){
            sb.append("\n[white]").append(profile.description).append("[]");
        }
        block.description = sb.toString();
        block.inlineDescription = true;
    }

    private static void refreshDescriptions(){
        for(ObjectMap.Entry<Block, AbilityProfile> entry : profiles){
            applyDescription(entry.key, entry.value);
        }
    }

    private static void addStatsAndBars(Block block, AbilityProfile profile){
        applyDescription(block, profile);

        block.stats.add(abilityStat, table -> {
            table.left();
            table.table(t -> {
                t.left().defaults().left();
                t.add("[accent]" + profile.abilityName + "[]  [lightgray](" + formatTriggerBadge(profile) + ")[]").row();
                t.add("[white]" + profile.description + "[]").wrap().width(330f).padTop(3f).row();
                String statuses = buildStatusLine(profile);
                if(!statuses.isEmpty()){
                    t.add("[lightgray]Statuses: []" + statuses).wrap().width(330f).padTop(3f).row();
                }
            }).left().padTop(2f).padBottom(2f);
        });

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
                    font.draw(text, x, y + getHeight() * 0.5f + layout.height * 0.5f, getWidth(), Align.center, false);
                    Pools.free(layout);
                    return;
                }
                super.draw();
            }
        });
    }

    private static String formatTriggerBadge(AbilityProfile p){
        switch(p.trigger.type){
            case EVERY_N_SHOTS: return "Every " + p.trigger.everyNShots + " Shots";
            case CHANCE_PER_CYCLE: return Math.round(p.trigger.chance * 100f) + "% Chance";
            case CONTINUOUS_HEAT: return (int)p.trigger.heatSeconds + "s Firing";
            case STAGE_DURATION: return (int)p.trigger.stage1Seconds + "s / +" + (int)p.trigger.stage2Seconds + "s";
            case CONFIRMED_UNIT_HITS: return p.trigger.everyNShots + " Unit Hits";
            case INTERCEPT_COUNT: return "Every " + p.trigger.everyNShots + " Intercepts";
            case TRACTOR_LOCK: return Strings.fixed(p.trigger.heatSeconds, 1) + "s Lock";
            default: return "Ability";
        }
    }

    private static String buildStatusLine(AbilityProfile p){
        StringBuilder sb = new StringBuilder();
        if(p.resolvedPrimaryStatus != null) sb.append("[accent]").append(p.resolvedPrimaryStatus.localizedName).append("[]");
        if(p.resolvedSecondaryStatus != null){
            if(sb.length() > 0) sb.append(", ");
            sb.append("[accent]").append(p.resolvedSecondaryStatus.localizedName).append("[]");
        }
        if(p.resolvedTertiaryStatus != null){
            if(sb.length() > 0) sb.append(", ");
            sb.append("[accent]").append(p.resolvedTertiaryStatus.localizedName).append("[]");
        }
        return sb.toString();
    }

    private static boolean hasCooldownLock(AbilityProfile profile){
        return profile.meltdownDuration > 0f
            && (profile.id == AbilityId.SCORCH_THERMAL_RAMP || profile.id == AbilityId.MELTDOWN_GRANITE_BLAST);
    }

    private static boolean shouldHideBarBox(Building entity, AbilityProfile profile){
        if(profile.trigger.type != TriggerType.CHANCE_PER_CYCLE) return false;
        AbilityState state = states.get(entity.id);
        return state == null || state.activeTimer <= 0f;
    }

    private static String getBarText(Building entity, AbilityProfile profile){
        AbilityState state = states.get(entity.id);
        if(state == null){
            if(profile.trigger.type == TriggerType.CHANCE_PER_CYCLE){
                return "Chance: " + Math.round(profile.trigger.chance * 100f) + "%";
            }
            return profile.abilityName;
        }

        if(state.cooldownTimer > 0f && hasCooldownLock(profile)){
            return "Cooldown " + Strings.fixed(state.cooldownTimer / 60f, 1) + "s";
        }
        if(state.chargeTimer > 0f){
            return "Charging " + Strings.fixed(state.chargeTimer / 60f, 1) + "s";
        }
        if(profile.id == AbilityId.PIERCE_DEAD_EYE && state.deadEyeRemaining > 0){
            return "Dead Eye x" + state.deadEyeRemaining;
        }

        switch(profile.trigger.type){
            case EVERY_N_SHOTS:
                if(state.activeTimer > 0f){
                    return Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                int shown = (state.empoweredShots > 0 || state.shotCounter >= profile.trigger.everyNShots)
                    ? profile.trigger.everyNShots : state.shotCounter;
                return "Charge: " + shown + "/" + profile.trigger.everyNShots;

            case CHANCE_PER_CYCLE:
                if(state.activeTimer > 0f){
                    return Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                return "Chance: " + Math.round(profile.trigger.chance * 100f) + "%";

            case CONTINUOUS_HEAT:
                if(state.activeTimer > 0f){
                    return Strings.fixed(state.activeTimer / 60f, 1) + "s";
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
                    return "Stage 3";
                }

            case CONFIRMED_UNIT_HITS:
                int hits = Math.min(profile.trigger.everyNShots, state.shotCounter);
                return "Hits: " + hits + "/" + profile.trigger.everyNShots;

            case INTERCEPT_COUNT:
                if(state.activeTimer > 0f){
                    return Strings.fixed(state.activeTimer / 60f, 1) + "s";
                }
                if(profile.secondaryThreshold > 0){
                    return "Prism: " + state.shotCounter + " (Aegis " + state.secondaryCounter + "/" + profile.secondaryThreshold + ")";
                }
                return "Intercepts: " + state.shotCounter + "/" + profile.trigger.everyNShots;

            case TRACTOR_LOCK:
                if(state.activeTimer > 0f){
                    return Strings.fixed(state.activeTimer / 60f, 1) + "s";
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

        if(state.cooldownTimer > 0f && hasCooldownLock(profile)) return Color.scarlet;
        if(state.chargeTimer > 0f){
            return profile.id == AbilityId.MELTDOWN_GRANITE_BLAST ? AbilityEffects.graniteCyan : Color.white;
        }
        if(profile.trigger.type == TriggerType.STAGE_DURATION){
            if(state.stage >= 3) return Color.orange;
            if(state.stage == 2) return Color.gold;
            return Color.lightGray;
        }
        if(entity instanceof TurretBuild){
            return getTurretAccentColor((TurretBuild)entity, profile);
        }
        return profile.accentColor;
    }

    private static float getBarFraction(Building entity, AbilityProfile profile){
        AbilityState state = states.get(entity.id);
        if(state == null) return 0f;

        if(state.cooldownTimer > 0f && hasCooldownLock(profile)){
            return Mathf.clamp(state.cooldownTimer / (profile.meltdownDuration * 60f));
        }
        if(state.chargeTimer > 0f && profile.chargeDuration > 0f){
            return Mathf.clamp(1f - (state.chargeTimer / (profile.chargeDuration * 60f)));
        }
        if(profile.id == AbilityId.PIERCE_DEAD_EYE && state.deadEyeRemaining > 0){
            return Mathf.clamp((float)state.deadEyeRemaining / profile.deadEyeShots);
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

        updatePointDefenseIntercepts();

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
                    steerTargetedBullet(b, 14f);
                }else if(owner != null && owner.id == AbilityId.PIERCE_DEAD_EYE){
                    steerTargetedBullet(b, 26f);
                }else if(giantShellBullets.contains(b.id) && tickCounter % 3 == 0){
                    AbilityEffects.activeTurretSpark.at(b.x, b.y, getBulletColor(b, owner));
                }
            }
        }

        if(processedBullets.size > 4000){
            processedBullets.clear();
            activeEmpoweredBullets.clear();
            giantShellBullets.clear();
            galeBullets.clear();
            bulletOwners.clear();
            bulletTurretTargets.clear();
            bulletShotKeys.clear();
            for(Bullet b : Groups.bullet){
                processedBullets.add(b.id);
            }
        }

        updateTrackedRippleShells();
        updateDelayedClones();
        updateDelayedBomblets();
        updateParalyzedUnits();
        updateMarkedUnits();
        updateSpinningUnits();
        updateTidalWaves();
    }

    private static void updateBuilding(Building build, AbilityProfile profile, AbilityState state){
        TurretBuild tb = build instanceof TurretBuild ? (TurretBuild)build : null;
        Turret turret = tb != null && tb.block instanceof Turret ? (Turret)tb.block : null;

        // 1. Cooldown lockdown (ONLY Scorch & Meltdown turret!)
        if(state.cooldownTimer > 0f && hasCooldownLock(profile)){
            state.cooldownTimer = Math.max(0f, state.cooldownTimer - Time.delta);
            state.lockedByAbility = true;
            build.enabled = false;
            build.efficiency = 0f;
            if(tb != null) tb.reloadCounter = 0f;
            if(state.graniteBullet != null){
                if(state.graniteBullet.isAdded()) state.graniteBullet.remove();
                state.graniteBullet = null;
            }
            if(Mathf.chanceDelta(0.28f)){
                AbilityEffects.meltdownSmoke.at(
                    build.x + Mathf.range(build.block.size * 3.2f),
                    build.y + Mathf.range(build.block.size * 3.2f)
                );
            }
            if(state.cooldownTimer <= 0f){
                state.lockedByAbility = false;
                build.enabled = true;
            }
            return;
        }else if(state.lockedByAbility){
            state.lockedByAbility = false;
            build.enabled = true;
        }

        // 2. Meltdown Granite Blast Charge (upgraded mov-style spiral + shrinking rings, NO cursed orb!)
        if(state.chargeTimer > 0f && tb != null && turret != null){
            tb.reloadCounter = 0f;
            tb.rotation = state.lockedRotation;
            state.chargeTimer = Math.max(0f, state.chargeTimer - Time.delta);

            float totalCharge = profile.chargeDuration * 60f;
            float frac = 1f - (state.chargeTimer / totalCharge);
            float mx = tb.x + Angles.trnsx(tb.rotation, turret.shootY);
            float my = tb.y + Angles.trnsy(tb.rotation, turret.shootY);

            int ringStep = Math.max(8, Math.round(Mathf.lerp(28f, 10f, frac)));
            if(tickCounter % ringStep == 0){
                AbilityEffects.graniteChargeRing.at(mx, my, Mathf.lerp(68f, 34f, frac), AbilityEffects.graniteCyan);
            }
            if(Mathf.chanceDelta(Mathf.lerp(0.45f, 0.95f, frac))){
                float spread = Mathf.lerp(55f, 18f, frac);
                float sideAngle = tb.rotation + (Mathf.chance(0.5f) ? 90f : -90f) + Mathf.range(spread);
                float startDist = Mathf.lerp(88f, 36f, frac);
                AbilityEffects.graniteSpiralStreak.at(mx, my, sideAngle, AbilityEffects.graniteBlue, startDist);
            }

            if(state.chargeTimer <= 0f){
                AbilityEffects.graniteSnapWave.at(mx, my, AbilityEffects.graniteCyan);
                state.activeTimer = profile.activeDuration * 60f;
                state.lockedRotation = tb.rotation;
                tb.reloadCounter = 0f;
                tb.target = null;
                tb.targetPos.set(
                    tb.x + Angles.trnsx(state.lockedRotation, 240f),
                    tb.y + Angles.trnsy(state.lockedRotation, 240f)
                );
                Bullet gb = getGraniteBlastType(profile).create(tb, tb.team, mx, my, state.lockedRotation);
                state.graniteBullet = gb;
                if(gb != null){
                    processedBullets.add(gb.id);
                    activeEmpoweredBullets.add(gb.id);
                    bulletOwners.put(gb.id, profile);
                }
                Sounds.beamMeltdown.at(mx, my, 0.9f);
            }
            return;
        }

        // 3. Active duration updates
        if(state.activeTimer > 0f){
            state.activeTimer = Math.max(0f, state.activeTimer - Time.delta);

            if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST && tb != null && turret != null){
                tb.reloadCounter = 0f;
                tb.rotation = state.lockedRotation;
                tb.target = null;
                tb.targetPos.set(
                    tb.x + Angles.trnsx(state.lockedRotation, 240f),
                    tb.y + Angles.trnsy(state.lockedRotation, 240f)
                );
                if(state.graniteBullet != null && state.graniteBullet.isAdded()){
                    float mx = tb.x + Angles.trnsx(state.lockedRotation, turret.shootY);
                    float my = tb.y + Angles.trnsy(state.lockedRotation, turret.shootY);
                    state.graniteBullet.set(mx, my);
                    state.graniteBullet.rotation(state.lockedRotation);
                    if(Mathf.chanceDelta(0.45f)){
                        float dist = Mathf.random(40f, 540f);
                        AbilityEffects.activeTurretSpark.at(
                            mx + Angles.trnsx(state.lockedRotation, dist) + Mathf.range(6f),
                            my + Angles.trnsy(state.lockedRotation, dist) + Mathf.range(6f),
                            AbilityEffects.graniteCyan
                        );
                    }
                }
                if(state.activeTimer <= 0f && state.graniteBullet != null){
                    if(state.graniteBullet.isAdded()) state.graniteBullet.remove();
                    state.graniteBullet = null;
                }
            }else if(profile.id == AbilityId.PARALLAX_GRAVITY_SLAM){
                updateParallaxActive(build, profile, state);
            }else if(profile.id == AbilityId.SEGMENT_AEGIS_PRISM){
                if(tickCounter % 4 == 0){
                    runSegmentAegisSweep(build, profile);
                }
            }else if(tb != null && Mathf.chanceDelta(0.28f)){
                AbilityEffects.activeTurretSpark.at(
                    tb.x + Mathf.range(tb.block.size * 3.5f),
                    tb.y + Mathf.range(tb.block.size * 3.5f),
                    getTurretAccentColor(tb, profile)
                );
            }

            if(state.activeTimer <= 0f && hasCooldownLock(profile)){
                if(state.graniteBullet != null){
                    if(state.graniteBullet.isAdded()) state.graniteBullet.remove();
                    state.graniteBullet = null;
                }
                state.cooldownTimer = profile.meltdownDuration * 60f;
                state.lockedByAbility = true;
                build.enabled = false;
                return;
            }
        }else if(state.graniteBullet != null){
            if(state.graniteBullet.isAdded()) state.graniteBullet.remove();
            state.graniteBullet = null;
        }

        // 4. Pierce RDR2 Dead Eye rapid-fire burst
        if(profile.id == AbilityId.PIERCE_DEAD_EYE && tb != null && turret != null && state.deadEyeRemaining > 0){
            updatePierceDeadEye(tb, turret, profile, state);
            return;
        }

        // 5. General TurretBuild triggers
        if(tb != null && turret != null){
            if(tb.totalShots > state.lastTotalShots){
                state.lastTotalShots = tb.totalShots;
                onTurretTriggerPull(tb, turret, profile, state);
            }

            // Meltdown (7/7 full) starts Royal Blue charge EARLY while reload < 50% so mov's orange charge never triggers!
            if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST
                && state.shotCounter >= profile.trigger.everyNShots
                && state.activeTimer <= 0f && state.chargeTimer <= 0f
                && tb.isShooting() && tb.efficiency > 0.05f
                && tb.reloadCounter < turret.reload * 0.55f){
                state.shotCounter = 0;
                state.chargeTimer = profile.chargeDuration * 60f;
                state.lockedRotation = tb.rotation;
                tb.reloadCounter = 0f;
                return;
            }

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

            if(profile.trigger.type == TriggerType.STAGE_DURATION){
                updateSalvoStages(tb, turret, profile, state);
            }

            if(state.activeTimer > 0f && profile.fireRateMultiplier > 1f){
                tb.reloadCounter += Time.delta * (profile.fireRateMultiplier - 1f) * tb.efficiency;
            }
        }

        // 6. Parallax tractor lock
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

    private static void startPierceDeadEye(TurretBuild tb, Turret turret, AbilityProfile profile, AbilityState state){
        Seq<Unit> candidates = new Seq<>();
        Units.nearbyEnemies(tb.team, tb.x, tb.y, turret.range, u -> {
            if(u != null && !u.dead) candidates.add(u);
        });
        if(candidates.size == 0) return;

        candidates.shuffle();
        state.deadEyeTargets.clear();
        for(int i = 0; i < profile.deadEyeShots; i++){
            state.deadEyeTargets.add(candidates.get(i % candidates.size));
        }
        state.deadEyeRemaining = state.deadEyeTargets.size;
        state.deadEyeFireTimer = 4f;
        AbilityEffects.overdriveTrigger.at(tb.x, tb.y, getTurretAccentColor(tb, profile));
    }

    private static void updatePierceDeadEye(TurretBuild tb, Turret turret, AbilityProfile profile, AbilityState state){
        state.deadEyeFireTimer -= Time.delta;
        if(state.deadEyeFireTimer > 0f) return;

        if(!tb.hasAmmo() || state.deadEyeTargets.size == 0){
            state.deadEyeRemaining = 0;
            state.deadEyeTargets.clear();
            state.shotCounter = 0;
            return;
        }

        Unit target = state.deadEyeTargets.remove(0);
        state.deadEyeRemaining = state.deadEyeTargets.size;
        state.deadEyeFireTimer = profile.deadEyeInterval;
        if(state.deadEyeRemaining == 0){
            state.shotCounter = 0;
        }

        if(target == null || target.dead){
            target = Units.closestEnemy(tb.team, tb.x, tb.y, turret.range, u -> !u.dead);
        }
        if(target == null) return;

        float ang = Angles.angle(tb.x, tb.y, target.x, target.y);
        tb.rotation = ang;
        BulletType ammo = tb.peekAmmo();
        if(ammo == null) return;

        float mx = tb.x + Angles.trnsx(ang, turret.shootY);
        float my = tb.y + Angles.trnsy(ang, turret.shootY);
        Color col = getAmmoColor(ammo, profile.accentColor);

        Bullet hyper = ammo.create(tb, tb.team, mx, my, ang, ammo.damage * profile.damageMultiplier, 4.2f, 0.35f, null);
        if(hyper != null){
            processedBullets.add(hyper.id);
            activeEmpoweredBullets.add(hyper.id);
            bulletOwners.put(hyper.id, profile);
            bulletTurretTargets.put(hyper.id, target);
        }
        Sounds.shootDuo.at(mx, my, 1.35f);
        AbilityEffects.pierceHyperTracer.at(mx, my, 0f, col, new Vec2(target.x, target.y));
    }

    private static void onTurretTriggerPull(TurretBuild tb, Turret turret, AbilityProfile profile, AbilityState state){
        if(profile.trigger.type == TriggerType.EVERY_N_SHOTS){
            if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST){
                if(state.activeTimer > 0f || state.chargeTimer > 0f) return;
                if(state.shotCounter < profile.trigger.everyNShots){
                    state.shotCounter++;
                }
                return;
            }
            if(state.activeTimer > 0f) return;
            state.shotCounter++;
            if(state.shotCounter >= profile.trigger.everyNShots){
                if(profile.activeDuration > 0f){
                    state.shotCounter = 0;
                    state.activeTimer = profile.activeDuration * 60f;
                    AbilityEffects.overdriveTrigger.at(tb.x, tb.y, getTurretAccentColor(tb, profile));
                }else{
                    state.shotCounter = profile.trigger.everyNShots;
                    state.empoweredShots = 1;
                }
            }
        }else if(profile.trigger.type == TriggerType.CHANCE_PER_CYCLE){
            if(profile.id == AbilityId.CYCLONE_GALE_RICOCHET) return;
            if(state.activeTimer > 0f) return;

            if(Mathf.chance(profile.trigger.chance)){
                if(profile.id == AbilityId.SCATTER_SNAKE_SWARM){
                    state.activeTimer = profile.activeDuration * 60f;
                    AbilityEffects.overdriveTrigger.at(tb.x, tb.y, getTurretAccentColor(tb, profile));
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
            AbilityEffects.overdriveTrigger.at(tb.x, tb.y, getTurretAccentColor(tb, profile));
        }else if(profile.id == AbilityId.WAVE_ALCHEMICAL_MONSOON){
            state.activeTimer = profile.activeDuration * 60f;
            Liquid liq = tb.liquids != null ? tb.liquids.current() : Liquids.water;
            Color col = liq != null ? liq.color : profile.accentColor;
            AbilityEffects.waveMonsoonPulse.at(tb.x, tb.y, 56f, col);
            triggerWaveElementalSurge(tb, profile, liq);
        }else if(profile.id == AbilityId.TSUNAMI_TIDAL_WALL){
            state.activeTimer = profile.activeDuration * 60f;
            Liquid liq = tb.liquids != null ? tb.liquids.current() : Liquids.water;
            Color col = liq != null ? liq.color : profile.accentColor;
            AbilityEffects.overdriveTrigger.at(tb.x, tb.y, col);
            spawnTsunamiWave(tb, turret, profile, liq, col);
        }
    }

    private static void triggerWaveElementalSurge(TurretBuild tb, AbilityProfile profile, Liquid liq){
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
                    ((TurretBuild)other).reloadCounter += 10f;
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

            if(t.time >= t.lifetime - 2f) continue;

            Building bestDefense = null;
            AbilityProfile bestProfile = null;
            float bestDst = Float.MAX_VALUE;

            for(Team team : Team.all){
                if(team == t.team) continue;
                for(Building build : team.data().buildings){
                    if(!(build.block instanceof PointDefenseTurret)) continue;
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

        if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST && b != state.graniteBullet
            && (state.chargeTimer > 0f || state.activeTimer > 0f || state.cooldownTimer > 0f)){
            b.remove();
            return;
        }

        bulletOwners.put(b.id, profile);
        long shotKey = (((long)tb.id) << 32) | (tb.totalShots & 0xFFFFFFFFL);
        bulletShotKeys.put(b.id, shotKey);

        if(profile.id == AbilityId.CYCLONE_GALE_RICOCHET){
            if(Mathf.chance(profile.trigger.chance)){
                galeBullets.add(b.id);
                activeEmpoweredBullets.add(b.id);
            }
            return;
        }

        if(profile.id == AbilityId.FORESHADOW_APEX_EXECUTION){
            if(state.shotCounter >= profile.trigger.everyNShots){
                state.shotCounter = 0;
                activeEmpoweredBullets.add(b.id);
                b.damage *= profile.damageMultiplier;
                if(state.apexTarget != null && !state.apexTarget.dead){
                    bulletTurretTargets.put(b.id, state.apexTarget);
                }
                AbilityEffects.overdriveTrigger.at(tb.x, tb.y, getBulletColor(b, profile));
            }
            return;
        }

        if(profile.id == AbilityId.SWARMER_HUNTERS_MARK){
            for(int i = 0; i < markedUnits.size; i++){
                MarkedUnit mu = markedUnits.get(i);
                if(mu.unit != null && !mu.unit.dead && mu.unit.within(b.x, b.y, 180f)){
                    b.damage *= profile.markBonusMultiplier;
                    break;
                }
            }
        }

        if(profile.id == AbilityId.SCATTER_SNAKE_SWARM && state.activeTimer > 0f){
            convertToSnakeMissile(tb, b, profile);
            return;
        }

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

        if(profile.id == AbilityId.SCORCH_THERMAL_RAMP && state.heatTimer > 0f){
            float frac = state.heatTimer / (profile.trigger.heatSeconds * 60f);
            b.damage *= Mathf.lerp(1f, profile.damageMultiplier, frac);
            return;
        }

        if(profile.activeDuration > 0f && state.activeTimer > 0f && profile.trigger.type == TriggerType.EVERY_N_SHOTS){
            b.damage *= profile.damageMultiplier;
            b.vel.scl(profile.bulletSpeedMultiplier);
            b.lifetime /= profile.bulletSpeedMultiplier;
            activeEmpoweredBullets.add(b.id);
            return;
        }

        boolean singleShotReady = (state.empoweredShots > 0) || (state.empoweredShotIndex == tb.totalShots);
        if(singleShotReady){
            if(state.empoweredShots > 0 && tb.totalShots != state.lastEmpoweredConsumedShot){
                state.empoweredShots--;
                state.lastEmpoweredConsumedShot = tb.totalShots;
                if(state.empoweredShots <= 0 && profile.trigger.type == TriggerType.EVERY_N_SHOTS){
                    state.shotCounter = 0;
                }
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
            giantShellBullets.add(b.id);
            AbilityEffects.overdriveTrigger.at(b.x, b.y, col);
            return;
        }

        if(profile.id == AbilityId.FUSE_ECHO_KICK){
            DelayedShrapnelClone clone = new DelayedShrapnelClone();
            clone.turret = tb;
            clone.team = tb.team;
            clone.x = b.x;
            clone.y = b.y;
            clone.angle = b.rotation();
            clone.bulletType = b.type;
            clone.damage = Math.max(8f, b.damage * profile.echoDamageFraction);
            clone.timer = profile.echoDelay * 60f;
            clone.color = col;
            clone.knockback = profile.knockbackForce;
            clone.status = profile.resolvedPrimaryStatus;
            clone.statusDuration = profile.primaryStatusDuration * 60f;
            delayedClones.add(clone);
            return;
        }

        if(profile.id == AbilityId.RIPPLE_CARPET_BOMBLETS){
            b.damage *= profile.damageMultiplier;
            TrackedRippleShell shell = new TrackedRippleShell();
            shell.bullet = b;
            shell.team = tb.team;
            shell.lastX = b.x;
            shell.lastY = b.y;
            shell.damage = Math.max(15f, (b.damage + b.type.splashDamage) * profile.bombletDamageFraction);
            shell.count = profile.bombletCount;
            shell.delay = profile.bombletDelay * 60f;
            shell.color = col;
            shell.status = profile.resolvedPrimaryStatus;
            shell.statusDuration = profile.primaryStatusDuration * 60f;
            trackedRippleShells.add(shell);
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

        if(profile.id == AbilityId.PIERCE_DEAD_EYE && bullet.owner instanceof TurretBuild){
            TurretBuild tb = (TurretBuild)bullet.owner;
            AbilityState state = states.get(tb.id);
            Long key = bulletShotKeys.get(bullet.id);
            if(state != null && key != null && state.deadEyeRemaining <= 0 && !countedUnitHitShots.contains(key)){
                if(countedUnitHitShots.size > 200) countedUnitHitShots.clear();
                countedUnitHitShots.add(key);
                state.shotCounter = Math.min(profile.trigger.everyNShots, state.shotCounter + 1);
                if(state.shotCounter >= profile.trigger.everyNShots){
                    startPierceDeadEye(tb, (Turret)tb.block, profile, state);
                }
            }
            return;
        }

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

        if(profile.id == AbilityId.CYCLONE_GALE_RICOCHET && galeBullets.contains(bullet.id)){
            galeBullets.remove(bullet.id);
            runCycloneRicochet(bullet.team, unit, bullet, profile);
            return;
        }

        if(!activeEmpoweredBullets.contains(bullet.id)) return;
        Color col = getBulletColor(bullet, profile);

        if(profile.id == AbilityId.SPECTRE_SINGULARITY_ORB && giantShellBullets.contains(bullet.id)){
            giantShellBullets.remove(bullet.id);
            activeEmpoweredBullets.remove(bullet.id);
            AbilityEffects.giantShellImpact.at(unit.x, unit.y, col);
            Damage.damage(bullet.team, unit.x, unit.y, 44f, bullet.damage * 0.4f, true, true);
            unit.impulse(Angles.trnsx(bullet.rotation(), 220f), Angles.trnsy(bullet.rotation(), 220f));
            if(profile.resolvedPrimaryStatus != null){
                unit.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
            }
            return;
        }

        if(profile.id == AbilityId.MELTDOWN_GRANITE_BLAST){
            if(profile.resolvedPrimaryStatus != null){
                unit.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
            }
            return;
        }

        if(profile.id == AbilityId.LANCER_ARC_PARALYSIS && bullet.owner instanceof TurretBuild){
            paralyzeUnit((TurretBuild)bullet.owner, unit, col);
            return;
        }

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
                    float sizeScale = Mathf.clamp(22f / Math.max(8f, other.hitSize), 0.35f, 1.5f);
                    float ang = Angles.angle(other.x, other.y, unit.x, unit.y);
                    float pull = profile.pullStrength * 32f * sizeScale;
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

        if(profile.id == AbilityId.SPECTRE_SINGULARITY_ORB && giantShellBullets.contains(bullet.id)){
            giantShellBullets.remove(bullet.id);
            activeEmpoweredBullets.remove(bullet.id);
            Color col = getBulletColor(bullet, profile);
            AbilityEffects.giantShellImpact.at(build.x, build.y, col);
            Damage.damage(bullet.team, build.x, build.y, 44f, bullet.damage * 0.4f, true, true);
            return;
        }

        if(profile.id == AbilityId.HAIL_SEISMIC_MORTAR){
            activeEmpoweredBullets.remove(bullet.id);
            Color col = getBulletColor(bullet, profile);
            AbilityEffects.seismicPullRing.at(build.x, build.y, profile.pullRadius, col);
            Units.nearbyEnemies(bullet.team, build.x, build.y, profile.pullRadius, other -> {
                if(!other.dead && !other.isFlying()){
                    float sizeScale = Mathf.clamp(22f / Math.max(8f, other.hitSize), 0.35f, 1.5f);
                    float ang = Angles.angle(other.x, other.y, build.x, build.y);
                    float pull = profile.pullStrength * 32f * sizeScale;
                    other.impulse(Angles.trnsx(ang, pull), Angles.trnsy(ang, pull));
                    if(profile.resolvedPrimaryStatus != null){
                        other.apply(profile.resolvedPrimaryStatus, profile.primaryStatusDuration * 60f);
                    }
                }
            });
        }
    }

    private static void paralyzeUnit(TurretBuild source, Unit u, Color col){
        if(PierceTurret.lancerParalyzed == null) return;
        u.apply(PierceTurret.lancerParalyzed, 600f);
        for(int i = 0; i < paralyzedUnits.size; i++){
            if(paralyzedUnits.get(i).unit == u){
                paralyzedUnits.get(i).source = source;
                paralyzedUnits.get(i).firedAtShot = source.totalShots;
                paralyzedUnits.get(i).timer = 0f;
                return;
            }
        }
        ParalyzedUnit pu = new ParalyzedUnit();
        pu.unit = u;
        pu.source = source;
        pu.firedAtShot = source.totalShots;
        pu.color = col;
        paralyzedUnits.add(pu);
        AbilityEffects.lancerParalyzeArc.at(u.x, u.y, u.hitSize, col);
    }

    private static void updateParalyzedUnits(){
        for(int i = paralyzedUnits.size - 1; i >= 0; i--){
            ParalyzedUnit pu = paralyzedUnits.get(i);
            pu.timer += Time.delta;
            if(pu.unit == null || pu.unit.dead){
                paralyzedUnits.remove(i);
                continue;
            }

            boolean sourceCanShootNext = false;
            if(pu.source == null || !pu.source.isAdded() || pu.source.dead){
                if(pu.timer >= 180f) sourceCanShootNext = true;
            }else{
                Turret t = (Turret)pu.source.block;
                if(pu.timer > 30f && (pu.source.reloadCounter >= t.reload - 2f || pu.source.totalShots > pu.firedAtShot)){
                    sourceCanShootNext = true;
                }
            }

            if(sourceCanShootNext || pu.timer >= 540f){
                if(PierceTurret.lancerParalyzed != null){
                    pu.unit.unapply(PierceTurret.lancerParalyzed);
                }
                paralyzedUnits.remove(i);
                continue;
            }

            if(PierceTurret.lancerParalyzed != null){
                pu.unit.apply(PierceTurret.lancerParalyzed, 30f);
            }
            if(tickCounter % 7 == 0){
                AbilityEffects.lancerParalyzeArc.at(pu.unit.x, pu.unit.y, pu.unit.hitSize, pu.color);
            }
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
        BulletType missileType = getOrCreateSnakeMissile(oldBullet, profile, col);
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

    private static BulletType getOrCreateSnakeMissile(Bullet base, AbilityProfile profile, Color col){
        String key = col.toString();
        BulletType cached = snakeMissileCache.get(key);
        if(cached != null) return cached;

        MissileBulletType m = new MissileBulletType(Math.max(4.5f, base.type.speed * 0.9f), Math.max(12f, base.damage * profile.damageMultiplier)){{
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
        if(!Vars.headless){
            m.load();
        }
        if(snakeMissileCache.size > 24) snakeMissileCache.clear();
        snakeMissileCache.put(key, m);
        return m;
    }

    private static void steerSnakeMissile(Bullet b){
        Unit air = Units.closestEnemy(b.team, b.x, b.y, 175f, u -> !u.dead && u.isFlying());
        if(air != null){
            float targetAng = Angles.angle(b.x, b.y, air.x, air.y);
            b.rotation(Angles.moveToward(b.rotation(), targetAng, 5.2f * Time.delta));
        }
    }

    private static void steerTargetedBullet(Bullet b, float turnSpeed){
        Unit target = bulletTurretTargets.get(b.id);
        if(target != null && !target.dead){
            float targetAng = Angles.angle(b.x, b.y, target.x, target.y);
            b.rotation(Angles.moveToward(b.rotation(), targetAng, turnSpeed * Time.delta));
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

    private static void updateTrackedRippleShells(){
        for(int i = trackedRippleShells.size - 1; i >= 0; i--){
            TrackedRippleShell s = trackedRippleShells.get(i);
            if(s.bullet != null && s.bullet.isAdded()){
                s.lastX = s.bullet.x;
                s.lastY = s.bullet.y;
                continue;
            }
            trackedRippleShells.remove(i);
            for(int k = 0; k < s.count; k++){
                DelayedBomblet bomb = new DelayedBomblet();
                bomb.team = s.team;
                float ang = k * (360f / Math.max(1, s.count)) + Mathf.range(25f);
                float off = Mathf.random(12f, 28f);
                bomb.x = s.lastX + Angles.trnsx(ang, off);
                bomb.y = s.lastY + Angles.trnsy(ang, off);
                bomb.damage = s.damage;
                bomb.timer = s.delay + k * 4f;
                bomb.color = s.color;
                bomb.status = s.status;
                bomb.statusDuration = s.statusDuration;
                delayedBomblets.add(bomb);
                AbilityEffects.carpetBombletWarning.at(bomb.x, bomb.y, s.color);
            }
        }
    }

    private static void updateDelayedClones(){
        for(int i = delayedClones.size - 1; i >= 0; i--){
            DelayedShrapnelClone c = delayedClones.get(i);
            c.timer -= Time.delta;
            if(c.timer <= 0f){
                delayedClones.remove(i);
                if(c.turret == null || !c.turret.isAdded()) continue;
                Bullet cloneBullet = c.bulletType.create(c.turret, c.team, c.x, c.y, c.angle, c.damage, 1f, 1f, null);
                if(cloneBullet != null){
                    processedBullets.add(cloneBullet.id);
                }
                float len = c.bulletType instanceof ShrapnelBulletType ? ((ShrapnelBulletType)c.bulletType).length : 120f;
                Units.nearbyEnemies(c.team, c.x + Angles.trnsx(c.angle, len * 0.5f), c.y + Angles.trnsy(c.angle, len * 0.5f), len * 0.6f, u -> {
                    if(!u.dead && Math.abs(Angles.angleDist(c.angle, Angles.angle(c.x, c.y, u.x, u.y))) <= 22f){
                        if(c.status != null) u.apply(c.status, c.statusDuration);
                        if(c.knockback > 0f){
                            u.impulse(Angles.trnsx(c.angle, c.knockback * 32f), Angles.trnsy(c.angle, c.knockback * 32f));
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
        Draw.z(Layer.turret + 0.05f);
        for(Team team : Team.all){
            for(Building build : team.data().buildings){
                AbilityProfile profile = profiles.get(build.block);
                if(profile == null || !hasCooldownLock(profile)) continue;
                AbilityState state = states.get(build.id);
                if(state != null && state.cooldownTimer > 0f && build instanceof TurretBuild){
                    TextureRegion top = Core.atlas.find(build.block.name);
                    if(top != null && top.found()){
                        TurretBuild tb = (TurretBuild)build;
                        Draw.color(0.06f, 0.04f, 0.04f, 0.72f);
                        Draw.rect(top, tb.x, tb.y, tb.drawrot());
                        Draw.color();
                    }
                }
            }
        }

        Draw.z(Layer.bullet + 0.05f);
        for(Bullet b : Groups.bullet){
            if(b == null || !b.isAdded() || !giantShellBullets.contains(b.id)) continue;
            AbilityProfile owner = bulletOwners.get(b.id);
            Color col = getBulletColor(b, owner != null ? owner : new AbilityProfile());
            float ang = b.rotation();
            Draw.color(col);
            Draw.alpha(0.55f);
            Fill.circle(b.x, b.y, 13f);
            Drawf.tri(b.x + Angles.trnsx(ang, 10f), b.y + Angles.trnsy(ang, 10f), 11f, 20f, ang);
            Drawf.tri(b.x - Angles.trnsx(ang, 6f), b.y - Angles.trnsy(ang, 6f), 9f, 16f, ang + 180f);
            Draw.color(Color.white);
            Fill.circle(b.x, b.y, 6f);
            Drawf.tri(b.x + Angles.trnsx(ang, 7f), b.y + Angles.trnsy(ang, 7f), 5.5f, 12f, ang);
            Draw.color();
        }

        Draw.z(Layer.effect);

        for(int i = 0; i < markedUnits.size; i++){
            MarkedUnit mu = markedUnits.get(i);
            if(mu.unit == null || mu.unit.dead) continue;
            float rad = mu.unit.hitSize + 8f + Mathf.absin(Time.time, 4f, 2f);
            Draw.color(mu.color, Color.white, 0.3f);
            Lines.stroke(1.8f);
            Lines.poly(mu.unit.x, mu.unit.y, 4, rad, Time.time * 1.6f);
            for(int k = 0; k < 4; k++){
                float a = k * 90f + 45f;
                float cx = mu.unit.x + Angles.trnsx(a, rad * 1.15f);
                float cy = mu.unit.y + Angles.trnsy(a, rad * 1.15f);
                Lines.lineAngle(cx, cy, a + 135f, 5f);
                Lines.lineAngle(cx, cy, a - 135f, 5f);
            }
        }

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

        for(Team team : Team.all){
            for(Building build : team.data().buildings){
                AbilityProfile profile = profiles.get(build.block);
                if(profile == null) continue;
                AbilityState state = states.get(build.id);
                if(state == null) continue;

                // Meltdown charge core glint at barrel tip (matching mov style)
                if(state.chargeTimer > 0f && build instanceof TurretBuild){
                    TurretBuild tb = (TurretBuild)build;
                    Turret turret = (Turret)tb.block;
                    float frac = 1f - (state.chargeTimer / (profile.chargeDuration * 60f));
                    float mx = tb.x + Angles.trnsx(tb.rotation, turret.shootY);
                    float my = tb.y + Angles.trnsy(tb.rotation, turret.shootY);
                    float coreR = Mathf.lerp(1f, 5.5f, frac);
                    Draw.color(AbilityEffects.graniteCyan);
                    Fill.circle(mx, my, coreR * 1.4f);
                    Draw.color(AbilityEffects.graniteCore);
                    Fill.circle(mx, my, coreR * 0.75f);
                    Draw.color();
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

                if(profile.id == AbilityId.FORESHADOW_APEX_EXECUTION && state.apexTarget != null && !state.apexTarget.dead && build instanceof TurretBuild){
                    TurretBuild tb = (TurretBuild)build;
                    Unit tgt = state.apexTarget;
                    Color col = getTurretAccentColor(tb, profile);
                    float rad = tgt.hitSize + 11f + Mathf.absin(Time.time, 3f, 2f);

                    Draw.color(col);
                    Draw.alpha(0.35f);
                    Lines.stroke(1.2f);
                    Lines.line(tb.x, tb.y, tgt.x, tgt.y);

                    Draw.alpha(0.95f);
                    Lines.stroke(2.2f);
                    Lines.arc(tgt.x, tgt.y, rad, 0.35f, Time.time * 2.2f);
                    Lines.arc(tgt.x, tgt.y, rad, 0.35f, Time.time * 2.2f + 180f);
                    Lines.stroke(1.5f);
                    Lines.circle(tgt.x, tgt.y, rad * 0.68f);
                    for(int k = 0; k < 4; k++){
                        float a = k * 90f - Time.time * 1.5f;
                        Drawf.tri(tgt.x + Angles.trnsx(a, rad + 4f), tgt.y + Angles.trnsy(a, rad + 4f), 4f, 8f, a + 180f);
                    }
                    Draw.color();
                }

                if(profile.id == AbilityId.PIERCE_DEAD_EYE && state.deadEyeTargets.size > 0 && build instanceof TurretBuild){
                    Color col = getTurretAccentColor((TurretBuild)build, profile);
                    for(int i = 0; i < state.deadEyeTargets.size; i++){
                        Unit tgt = state.deadEyeTargets.get(i);
                        if(tgt == null || tgt.dead) continue;
                        float r = Math.max(7f, tgt.hitSize * 0.7f);
                        Draw.color(Color.scarlet, col, 0.25f);
                        Lines.stroke(2.4f);
                        Lines.circle(tgt.x, tgt.y, r + 3f);
                        for(int k = 0; k < 4; k++){
                            float a = k * 90f + 45f;
                            Lines.lineAngle(tgt.x, tgt.y, a, r);
                        }
                    }
                    Draw.color();
                }
            }
        }

        Draw.reset();
    }

    private static Color getTurretAccentColor(TurretBuild tb, AbilityProfile profile){
        if(tb.hasAmmo()){
            BulletType ammo = tb.peekAmmo();
            if(ammo != null) return getAmmoColor(ammo, profile.accentColor);
        }
        if(tb.liquids != null && tb.liquids.currentAmount() > 0.01f && tb.liquids.current() != null){
            return tb.liquids.current().color;
        }
        return profile.accentColor;
    }

    private static Color getBulletColor(Bullet b, AbilityProfile profile){
        Color fallback = profile != null ? profile.accentColor : Color.white;
        if(b != null && b.type != null){
            return getAmmoColor(b.type, fallback);
        }
        return fallback;
    }

    private static Color getAmmoColor(BulletType type, Color fallback){
        if(type instanceof ShrapnelBulletType){
            Color c = ((ShrapnelBulletType)type).toColor;
            if(c != null && !c.equals(Color.white)) return c;
        }
        if(type instanceof LaserBulletType){
            Color[] cols = ((LaserBulletType)type).colors;
            if(cols != null && cols.length > 0){
                for(Color c : cols){
                    if(c != null && !c.equals(Color.white) && c.a > 0.2f) return c;
                }
            }
        }
        if(type instanceof ContinuousLaserBulletType){
            Color[] cols = ((ContinuousLaserBulletType)type).colors;
            if(cols != null && cols.length > 0){
                for(Color c : cols){
                    if(c != null && !c.equals(Color.white) && c.a > 0.2f) return c;
                }
            }
        }
        if(type instanceof LiquidBulletType && ((LiquidBulletType)type).liquid != null){
            return ((LiquidBulletType)type).liquid.color;
        }
        if(type.trailColor != null && !type.trailColor.equals(Color.white) && !type.trailColor.equals(Pal.powerLight) && type.trailColor.a > 0.1f){
            return type.trailColor;
        }
        if(type instanceof BasicBulletType){
            BasicBulletType bbt = (BasicBulletType)type;
            if(bbt.backColor != null && !bbt.backColor.equals(Color.white) && !bbt.backColor.equals(Pal.powerLight) && bbt.backColor.a > 0.1f){
                return bbt.backColor;
            }
            if(bbt.frontColor != null && !bbt.frontColor.equals(Color.white) && !bbt.frontColor.equals(Pal.powerLight) && bbt.frontColor.a > 0.1f){
                return bbt.frontColor;
            }
        }
        if(type.hitColor != null && !type.hitColor.equals(Color.white) && !type.hitColor.equals(Pal.powerLight) && type.hitColor.a > 0.1f){
            return type.hitColor;
        }
        if(type.lightColor != null && !type.lightColor.equals(Pal.powerLight) && type.lightColor.a > 0.1f){
            return type.lightColor;
        }
        return fallback;
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
        giantShellBullets.clear();
        galeBullets.clear();
        bulletOwners.clear();
        bulletTurretTargets.clear();
        bulletShotKeys.clear();
        countedUnitHitShots.clear();
        delayedClones.clear();
        trackedRippleShells.clear();
        delayedBomblets.clear();
        markedUnits.clear();
        paralyzedUnits.clear();
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

        Bullet graniteBullet = null;
        Unit apexTarget = null;
        Unit tetherMain = null;
        final Seq<Unit> tetherExtras = new Seq<>();

        int deadEyeRemaining = 0;
        float deadEyeFireTimer = 0f;
        final Seq<Unit> deadEyeTargets = new Seq<>();
    }

    private static class DelayedShrapnelClone{
        TurretBuild turret;
        Team team;
        BulletType bulletType;
        float x, y, angle, damage, timer, knockback, statusDuration;
        Color color;
        StatusEffect status;
    }

    private static class TrackedRippleShell{
        Bullet bullet;
        Team team;
        float lastX, lastY, damage, delay, statusDuration;
        int count;
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

    private static class ParalyzedUnit{
        Unit unit;
        TurretBuild source;
        int firedAtShot;
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
