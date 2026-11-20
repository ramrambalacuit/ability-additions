package abilityadditions.core;

import abilityadditions.content.AbilityEffects;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.draw.DrawTurret;

public class AbilityManager{
    private static final AbilityManager INSTANCE = new AbilityManager();

    private final ObjectMap<Block, AbilityProfile> blockAbilities = new ObjectMap<>();
    private final ObjectMap<BulletType, MissileBulletType> missileCache = new ObjectMap<>();
    private final IntMap<TurretState> turretStates = new IntMap<>();
    private final IntMap<EmpoweredBullet> empoweredLookup = new IntMap<>();
    private final Seq<EmpoweredBullet> empoweredList = new Seq<>();
    private final Seq<Integer> staleStateIds = new Seq<>();
    private final Seq<Unit> chainCandidates = new Seq<>();

    private IntSet seenBulletIds = new IntSet();
    private IntSet nextSeenBulletIds = new IntSet();
    private boolean eventsRegistered = false;

    public static AbilityManager instance(){
        return INSTANCE;
    }

    public void register(Block block, AbilityProfile profile){
        if(block != null && profile != null){
            blockAbilities.put(block, profile);
        }
    }

    public void applyStats(){
        for(ObjectMap.Entry<Block, AbilityProfile> entry : blockAbilities.entries()){
            Block block = entry.key;
            AbilityProfile profile = entry.value;

            String abilityText = "[accent]Ability[]\n" + profile.statSummary();
            if(block.details == null || block.details.isEmpty()){
                block.details = abilityText;
            }else if(!block.details.contains(profile.abilityName)){
                block.details = block.details + "\n\n" + abilityText;
            }

            block.addBar("ability", entity -> new Bar(
                () -> {
                    TurretState state = turretStates.get(entity.id);
                    if(state != null && state.meltdownTimer > 0f){
                        return "Meltdown: " + String.format(java.util.Locale.US, "%.1fs", state.meltdownTimer / 60f);
                    }
                    if(profile.mode == AbilityProfile.Mode.HEAT_RAMP){
                        int pct = state == null ? 0 : Math.round(state.heat * 100f);
                        return "Heat: " + pct + "%";
                    }
                    if(state != null && state.activeTimer > 0f){
                        return profile.abilityName + ": " + String.format(java.util.Locale.US, "%.1fs", state.activeTimer / 60f);
                    }
                    return profile.abilityName + ": " + profile.trigger.barText(state);
                },
                () -> {
                    TurretState state = turretStates.get(entity.id);
                    if(state != null && state.meltdownTimer > 0f){
                        return Pal.remove;
                    }
                    if(profile.mode == AbilityProfile.Mode.HEAT_RAMP){
                        float h = state == null ? 0f : state.heat;
                        return h >= 0.6f ? Pal.remove : (state != null && state.lastColor != null ? state.lastColor : Pal.accent);
                    }
                    if(state != null && state.activeTimer > 0f){
                        return state.lastColor != null ? state.lastColor : Pal.accent;
                    }
                    return Pal.ammo;
                },
                () -> {
                    TurretState state = turretStates.get(entity.id);
                    if(state != null && state.meltdownTimer > 0f){
                        return Mathf.clamp(state.meltdownTimer / profile.meltdownTicks);
                    }
                    if(profile.mode == AbilityProfile.Mode.HEAT_RAMP){
                        return state == null ? 0f : Mathf.clamp(state.heat);
                    }
                    if(state != null && state.activeTimer > 0f){
                        return Mathf.clamp(state.activeTimer / profile.durationTicks);
                    }
                    return Mathf.clamp(profile.trigger.progress(state));
                }
            ));
        }
    }

    public void initEvents(){
        if(eventsRegistered) return;
        eventsRegistered = true;

        Events.on(EventType.WorldLoadEvent.class, e -> clearRuntimeState());

        Events.on(EventType.UnitDamageEvent.class, e -> {
            if(e.bullet == null || e.unit == null) return;
            EmpoweredBullet eb = empoweredLookup.get(e.bullet.id);
            if(eb == null) return;

            if(eb.profile.debuff != null && eb.applyDebuff){
                e.unit.apply(eb.profile.debuff, eb.profile.debuffDurationTicks);
            }
            AbilityEffects.empoweredHit.at(e.bullet.x, e.bullet.y, e.bullet.rotation(), eb.color);

            if(eb.profile.mode == AbilityProfile.Mode.SEISMIC_MORTAR && !eb.impactTriggered){
                eb.impactTriggered = true;
                triggerSeismicPull(e.bullet.x, e.bullet.y, e.bullet.team, eb.profile, eb.color);
            }
        });

        Events.on(EventType.BuildDamageEvent.class, e -> {
            if(e.source == null || e.build == null) return;
            EmpoweredBullet eb = empoweredLookup.get(e.source.id);
            if(eb == null) return;

            AbilityEffects.empoweredHit.at(e.source.x, e.source.y, e.source.rotation(), eb.color);

            if(eb.profile.mode == AbilityProfile.Mode.SEISMIC_MORTAR && !eb.impactTriggered){
                eb.impactTriggered = true;
                triggerSeismicPull(e.source.x, e.source.y, e.source.team, eb.profile, eb.color);
            }
        });

        Events.run(EventType.Trigger.update, this::updateRuntime);
        Events.run(EventType.Trigger.draw, this::drawRuntime);
    }

    private void onBulletSpawned(Bullet b){
        if(b == null || !(b.owner instanceof Turret.TurretBuild turret)) return;

        AbilityProfile profile = blockAbilities.get(turret.block);
        if(profile == null) return;

        if(!isPrimaryBullet(turret.block, b.type)) return;

        TurretState state = turretStates.get(turret.id);
        if(state == null){
            state = new TurretState(turret, profile);
            turretStates.put(turret.id, state);
        }

        if(state.meltdownTimer > 0f){
            b.damage = 0f;
            b.remove();
            return;
        }

        if(profile.mode == AbilityProfile.Mode.OVERDRIVE_RUSH){
            if(state.activeTimer > 0f){
                empowerBullet(b, profile, true);
            }else if(profile.trigger.checkAndAdvance(state)){
                state.activeTimer = profile.durationTicks;
                turret.applyBoost(profile.fireRateMultiplier, profile.durationTicks);

                Color waveColor = resolveBulletColor(b.type);
                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, waveColor);

                if(turret.block instanceof Turret t && t.shootSound != null){
                    t.shootSound.at(turret.x, turret.y, 1.35f, 0.85f);
                }

                empowerBullet(b, profile, true);
            }
        }else if(profile.mode == AbilityProfile.Mode.MISSILE_SWARM){
            if(state.activeTimer > 0f){
                convertToSnakeMissile(turret, b, profile, state);
            }else if(profile.trigger.checkAndAdvance(state)){
                Color waveColor = resolveBulletColor(b.type);
                state.lastColor = waveColor;
                state.activeTimer = profile.durationTicks;
                state.pendingMeltdown = profile.meltdownTicks > 0f;

                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, waveColor);
                if(turret.block instanceof Turret t && t.shootSound != null){
                    t.shootSound.at(turret.x, turret.y, 0.85f, 0.9f);
                }

                convertToSnakeMissile(turret, b, profile, state);
            }
        }else if(profile.mode == AbilityProfile.Mode.CHAIN_LIGHTNING){
            if(profile.trigger.checkAndAdvance(state)){
                Color arcColor = resolveBulletColor(b.type);
                state.lastColor = arcColor;
                b.damage *= profile.damageMultiplier;
                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, arcColor);
                triggerChainLightning(turret, b, profile, arcColor);
            }
        }else if(profile.mode == AbilityProfile.Mode.SEISMIC_MORTAR){
            if(profile.trigger.checkAndAdvance(state)){
                Color mortarColor = resolveBulletColor(b.type);
                state.lastColor = mortarColor;
                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, mortarColor);
                if(turret.block instanceof Turret t && t.shootSound != null){
                    t.shootSound.at(turret.x, turret.y, 0.8f, 0.95f);
                }
                empowerBullet(b, profile, true);
            }
        }else if(profile.mode == AbilityProfile.Mode.HEAT_RAMP){
            state.heatCoolDelay = 45f; // wait 0.75s after last shot before cooling down
            state.heat = Mathf.clamp(state.heat + profile.heatGainPerShot);

            Color flameColor = resolveBulletColor(b.type);
            state.lastColor = flameColor;

            float dmgScale = Mathf.lerp(1f, profile.damageMultiplier, state.heat);
            b.damage *= dmgScale;

            if(state.heat >= 0.35f){
                empowerBulletVisualOnly(b, profile, state.heat >= 0.6f, flameColor);
            }

            if(state.heat >= 1f){
                state.heat = 0f;
                state.meltdownTimer = profile.meltdownTicks;
                turret.reloadCounter = 0f;
                turret.applySlowdown(0.01f, profile.meltdownTicks);
                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, Pal.remove);
            }else if(state.heat > 0.2f){
                float rateBoost = Mathf.lerp(1f, profile.fireRateMultiplier, state.heat);
                turret.applyBoost(rateBoost, 24f);
            }
        }
    }

    private void triggerChainLightning(Turret.TurretBuild turret, Bullet sourceBullet, AbilityProfile profile, Color arcColor){
        float searchRange = turret.block instanceof Turret t ? t.range + 40f : 140f;
        Unit first = Units.closestEnemy(turret.team, sourceBullet.x, sourceBullet.y, searchRange, u -> u.isGrounded() && u.isValid());
        if(first == null){
            first = Units.closestEnemy(turret.team, turret.x, turret.y, searchRange, u -> u.isGrounded() && u.isValid());
        }
        if(first == null) return;

        float jumpDamage = sourceBullet.damage * profile.chainDamageRatio;
        if(profile.debuff != null){
            first.apply(profile.debuff, profile.debuffDurationTicks);
        }

        Unit prev = first;
        chainCandidates.clear();
        chainCandidates.add(first);

        for(int i = 0; i < profile.chainTargets; i++){
            Unit prevRef = prev;
            Unit next = Units.closestEnemy(
                turret.team,
                prevRef.x,
                prevRef.y,
                profile.chainRadius,
                u -> u.isGrounded() && u.isValid() && !chainCandidates.contains(u)
            );
            if(next == null) break;

            chainCandidates.add(next);
            AbilityEffects.chainArc.at(prevRef.x, prevRef.y, 0f, arcColor, next);
            next.damagePierce(jumpDamage);
            if(profile.debuff != null){
                next.apply(profile.debuff, profile.debuffDurationTicks);
            }
            AbilityEffects.empoweredHit.at(next.x, next.y, 0f, arcColor);
            prev = next;
        }
    }

    private void triggerSeismicPull(float x, float y, mindustry.game.Team team, AbilityProfile profile, Color color){
        AbilityEffects.seismicPull.at(x, y, 0f, color);
        Units.nearbyEnemies(team, x, y, profile.pullRadius, u -> {
            if(!u.isValid() || !u.isGrounded()) return;
            float angleToCenter = u.angleTo(x, y);
            float dist = u.dst(x, y);
            float force = profile.pullStrength * Mathf.clamp(dist / profile.pullRadius, 0.25f, 1f);
            u.vel.add(Angles.trnsx(angleToCenter, force), Angles.trnsy(angleToCenter, force));
            if(profile.debuff != null){
                u.apply(profile.debuff, profile.debuffDurationTicks);
            }
        });
    }

    private void convertToSnakeMissile(Turret.TurretBuild turret, Bullet triggerBullet, AbilityProfile profile, TurretState state){
        Color ammoColor = resolveBulletColor(triggerBullet.type);
        state.lastColor = ammoColor;

        MissileBulletType missile = getOrCreateSnakeMissile(triggerBullet.type, profile, ammoColor);
        missile.create(turret, turret.team, triggerBullet.x, triggerBullet.y, triggerBullet.rotation());

        triggerBullet.damage = 0f;
        triggerBullet.remove();
    }

    private MissileBulletType getOrCreateSnakeMissile(BulletType sourceAmmo, AbilityProfile profile, Color ammoColor){
        MissileBulletType existing = missileCache.get(sourceAmmo);
        if(existing != null) return existing;

        Color bright = ammoColor.cpy().lerp(Color.white, 0.45f);
        boolean airOnly = !sourceAmmo.collidesGround;

        MissileBulletType missile = new MissileBulletType(7.2f, Math.max(10f, sourceAmmo.damage * profile.damageMultiplier));
        missile.width = 10f;
        missile.height = 16f;
        missile.lifetime = 54f;
        missile.homingPower = 0.20f;
        missile.homingRange = 360f;
        missile.homingDelay = 1f;
        missile.weaveScale = 4.8f;
        missile.weaveMag = 3.6f;
        missile.collidesGround = !airOnly;
        missile.collidesAir = sourceAmmo.collidesAir;
        missile.splashDamageRadius = Math.max(18f, sourceAmmo.splashDamageRadius * 0.9f);
        missile.splashDamage = Math.max(8f, sourceAmmo.splashDamage * 0.8f);
        missile.status = profile.debuff;
        missile.statusDuration = profile.debuffDurationTicks;
        missile.frontColor = bright;
        missile.backColor = ammoColor;
        missile.trailColor = ammoColor;
        missile.lightColor = bright;
        missile.trailLength = 10;
        missile.trailWidth = 2.0f;
        missile.hitEffect = AbilityEffects.empoweredHit;
        missile.despawnEffect = AbilityEffects.empoweredHit;
        missile.init();
        if(!Vars.headless) missile.load();

        missileCache.put(sourceAmmo, missile);
        return missile;
    }

    private void updateRuntime(){
        if(Vars.state == null || Vars.state.isPaused()) return;

        nextSeenBulletIds.clear();
        for(Bullet b : Groups.bullet){
            nextSeenBulletIds.add(b.id);
            if(!seenBulletIds.contains(b.id)){
                onBulletSpawned(b);
            }
        }
        IntSet tmp = seenBulletIds;
        seenBulletIds = nextSeenBulletIds;
        nextSeenBulletIds = tmp;

        staleStateIds.clear();
        for(IntMap.Entry<TurretState> entry : turretStates.entries()){
            TurretState state = entry.value;
            if(state.build == null || !state.build.isValid()){
                staleStateIds.add(entry.key);
                continue;
            }

            if(state.triggerCooldown > 0f){
                state.triggerCooldown = Math.max(0f, state.triggerCooldown - Time.delta);
            }

            // Scorch Heat Ramp cooling & heated sparks
            if(state.profile.mode == AbilityProfile.Mode.HEAT_RAMP && state.meltdownTimer <= 0f){
                if(state.heatCoolDelay > 0f){
                    state.heatCoolDelay -= Time.delta;
                }else if(state.heat > 0f){
                    state.heat = Math.max(0f, state.heat - (state.profile.heatCoolPerSecond / 60f) * Time.delta);
                }
                if(state.heat >= 0.55f){
                    state.sparkTimer += Time.delta;
                    if(state.sparkTimer >= 10f){
                        state.sparkTimer = 0f;
                        Color sparkColor = state.lastColor != null ? state.lastColor : Pal.remove;
                        AbilityEffects.activeTurretSpark.at(
                            state.build.x + Mathf.range(state.build.block.size * 2.5f),
                            state.build.y + Mathf.range(state.build.block.size * 2.5f),
                            state.build.block.size,
                            sparkColor
                        );
                    }
                }
            }

            if(state.meltdownTimer > 0f){
                state.meltdownTimer -= Time.delta;
                state.build.reloadCounter = 0f;
                if(state.meltdownTimer <= 0f){
                    state.meltdownTimer = 0f;
                }else{
                    state.build.applySlowdown(0.01f, 2f);
                    state.sparkTimer += Time.delta;
                    if(state.sparkTimer >= 10f){
                        state.sparkTimer = 0f;
                        Color smokeTint = state.lastColor != null ? state.lastColor : Pal.remove;
                        AbilityEffects.meltdownSmoke.at(
                            state.build.x + Mathf.range(state.build.block.size * 2.5f),
                            state.build.y + Mathf.range(state.build.block.size * 2.5f),
                            state.build.block.size,
                            smokeTint
                        );
                    }
                }
            }

            if(state.activeTimer > 0f){
                state.activeTimer -= Time.delta;
                if(state.activeTimer <= 0f){
                    state.activeTimer = 0f;
                    state.shotCount = 0;
                    if(state.pendingMeltdown && state.profile.meltdownTicks > 0f){
                        state.pendingMeltdown = false;
                        state.meltdownTimer = state.profile.meltdownTicks;
                        state.build.reloadCounter = 0f;
                        state.build.applySlowdown(0.01f, state.profile.meltdownTicks);
                    }
                }else{
                    if(state.profile.mode == AbilityProfile.Mode.OVERDRIVE_RUSH){
                        state.build.applyBoost(state.profile.fireRateMultiplier, state.activeTimer);
                    }
                    state.sparkTimer += Time.delta;
                    if(state.sparkTimer >= 8f){
                        state.sparkTimer = 0f;
                        Color sparkColor = state.lastColor != null ? state.lastColor : Pal.accent;
                        AbilityEffects.activeTurretSpark.at(
                            state.build.x + Mathf.range(state.build.block.size * 2.5f),
                            state.build.y + Mathf.range(state.build.block.size * 2.5f),
                            state.build.block.size,
                            sparkColor
                        );
                    }
                }
            }
        }
        for(int i = 0; i < staleStateIds.size; i++){
            turretStates.remove(staleStateIds.get(i));
        }

        for(int i = empoweredList.size - 1; i >= 0; i--){
            EmpoweredBullet eb = empoweredList.get(i);
            if(eb.bullet == null || !eb.bullet.isAdded()){
                if(eb.bullet != null && eb.profile.mode == AbilityProfile.Mode.SEISMIC_MORTAR && !eb.impactTriggered){
                    eb.impactTriggered = true;
                    triggerSeismicPull(eb.lastX, eb.lastY, eb.team, eb.profile, eb.color);
                }
                empoweredList.remove(i);
                if(eb.bullet != null){
                    empoweredLookup.remove(eb.bullet.id);
                }
                continue;
            }

            eb.lastX = eb.bullet.x;
            eb.lastY = eb.bullet.y;
            eb.trailTimer += Time.delta;
            if(eb.trailTimer >= 2.2f){
                eb.trailTimer = 0f;
                AbilityEffects.empoweredTrail.at(eb.bullet.x, eb.bullet.y, eb.bullet.rotation(), eb.color);
            }
        }
    }

    private void drawRuntime(){
        if(Vars.headless || Vars.state == null || turretStates.size == 0) return;

        float pulse = 0.5f + 0.5f * Mathf.sin(Time.time * 0.25f);
        for(IntMap.Entry<TurretState> entry : turretStates.entries()){
            TurretState state = entry.value;
            if(state.meltdownTimer <= 0f || state.build == null || !state.build.isValid()) continue;

            Turret.TurretBuild turret = state.build;
            float darkAlpha = 0.6f + 0.16f * pulse;

            if(turret.block instanceof Turret t && t.drawer instanceof DrawTurret dt && dt.base != null && dt.base.found()){
                Draw.z(Layer.turret - 0.1f);
                Draw.color(0.06f, 0.06f, 0.08f, darkAlpha);
                Draw.rect(dt.base, turret.x, turret.y);
            }

            Draw.z(Layer.turret + 0.05f);
            if(turret.block.region != null && turret.block.region.found()){
                Draw.color(0.06f, 0.06f, 0.08f, darkAlpha);
                Draw.rect(turret.block.region, turret.x, turret.y, turret.drawrot());
            }

            Color glow = state.lastColor != null ? state.lastColor : Pal.remove;
            Draw.color(glow, 0.15f + 0.22f * pulse);
            Fill.circle(turret.x, turret.y, turret.block.size * (2.2f + 0.7f * pulse));

            Lines.stroke(1.1f);
            Draw.color(glow, 0.25f + 0.3f * pulse);
            Lines.circle(turret.x, turret.y, turret.block.size * (3.2f + 0.8f * pulse));
        }

        Draw.color();
        Draw.z();
    }

    private void empowerBullet(Bullet b, AbilityProfile profile, boolean applyDebuff){
        b.damage *= profile.damageMultiplier;

        if(profile.bulletSpeedMultiplier > 0f && profile.bulletSpeedMultiplier != 1f){
            b.vel.scl(profile.bulletSpeedMultiplier);
            b.lifetime /= profile.bulletSpeedMultiplier;
        }

        empowerBulletVisualOnly(b, profile, applyDebuff, resolveBulletColor(b.type));
    }

    private void empowerBulletVisualOnly(Bullet b, AbilityProfile profile, boolean applyDebuff, Color bulletColor){
        if(b.owner instanceof Turret.TurretBuild turret){
            TurretState state = turretStates.get(turret.id);
            if(state != null){
                state.lastColor = bulletColor;
            }
        }

        EmpoweredBullet eb = new EmpoweredBullet(b, profile, bulletColor, applyDebuff);
        empoweredList.add(eb);
        empoweredLookup.put(b.id, eb);

        AbilityEffects.empoweredTrail.at(b.x, b.y, b.rotation(), bulletColor);
    }

    private static boolean isPrimaryBullet(Block block, BulletType type){
        if(type == null) return false;

        if(block instanceof ItemTurret itemTurret){
            for(BulletType bt : itemTurret.ammoTypes.values()){
                if(bt == type) return true;
            }
            return false;
        }
        if(block instanceof LiquidTurret liquidTurret){
            for(BulletType bt : liquidTurret.ammoTypes.values()){
                if(bt == type) return true;
            }
            return false;
        }
        if(block instanceof PowerTurret powerTurret){
            return powerTurret.shootType == type;
        }
        return true;
    }

    private static Color resolveBulletColor(BulletType type){
        if(type == null) return Pal.accent;
        if(type.trailLength > 0 && type.trailColor != null){
            return type.trailColor;
        }
        if(type instanceof BasicBulletType basic && basic.backColor != null){
            return basic.backColor;
        }
        if(type instanceof LightningBulletType light && light.lightningColor != null){
            return light.lightningColor;
        }
        if(type.lightColor != null){
            return type.lightColor;
        }
        return Pal.accent;
    }

    private void clearRuntimeState(){
        turretStates.clear();
        empoweredLookup.clear();
        empoweredList.clear();
        seenBulletIds.clear();
        nextSeenBulletIds.clear();
    }

    public static class TurretState{
        public final Turret.TurretBuild build;
        public final AbilityProfile profile;
        public int shotCount = 0;
        public float triggerCooldown = 0f;
        public float activeTimer = 0f;
        public float meltdownTimer = 0f;
        public float sparkTimer = 0f;
        public float heat = 0f;
        public float heatCoolDelay = 0f;
        public boolean pendingMeltdown = false;
        public Color lastColor = null;

        public TurretState(Turret.TurretBuild build, AbilityProfile profile){
            this.build = build;
            this.profile = profile;
        }
    }

    private static class EmpoweredBullet{
        final Bullet bullet;
        final AbilityProfile profile;
        final Color color;
        final boolean applyDebuff;
        final mindustry.game.Team team;
        float lastX, lastY;
        float trailTimer = 0f;
        boolean impactTriggered = false;

        EmpoweredBullet(Bullet bullet, AbilityProfile profile, Color color, boolean applyDebuff){
            this.bullet = bullet;
            this.profile = profile;
            this.color = color;
            this.applyDebuff = applyDebuff;
            this.team = bullet.team;
            this.lastX = bullet.x;
            this.lastY = bullet.y;
        }
    }
                   }
