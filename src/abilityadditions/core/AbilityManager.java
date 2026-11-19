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
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
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
    private final ObjectMap<BulletType, MissilePair> missileCache = new ObjectMap<>();
    private final IntMap<TurretState> turretStates = new IntMap<>();
    private final IntMap<EmpoweredBullet> empoweredLookup = new IntMap<>();
    private final Seq<EmpoweredBullet> empoweredList = new Seq<>();
    private final Seq<Integer> staleStateIds = new Seq<>();

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

            if(eb.profile.debuff != null){
                e.unit.apply(eb.profile.debuff, eb.profile.debuffDurationTicks);
            }
            AbilityEffects.empoweredHit.at(e.bullet.x, e.bullet.y, e.bullet.rotation(), eb.color);
        });

        Events.on(EventType.BuildDamageEvent.class, e -> {
            if(e.source == null || e.build == null) return;
            EmpoweredBullet eb = empoweredLookup.get(e.source.id);
            if(eb == null) return;

            AbilityEffects.empoweredHit.at(e.source.x, e.source.y, e.source.rotation(), eb.color);
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
                empowerBullet(b, profile);
            }else if(profile.trigger.checkAndAdvance(state)){
                state.activeTimer = profile.durationTicks;
                turret.applyBoost(profile.fireRateMultiplier, profile.durationTicks);

                Color waveColor = resolveBulletColor(b.type);
                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, waveColor);

                if(turret.block instanceof Turret t && t.shootSound != null){
                    t.shootSound.at(turret.x, turret.y, 1.35f, 0.85f);
                }

                empowerBullet(b, profile);
            }
        }else if(profile.mode == AbilityProfile.Mode.MISSILE_SWARM){
            if(profile.trigger.checkAndAdvance(state)){
                Color waveColor = resolveBulletColor(b.type);
                state.lastColor = waveColor;
                state.meltdownTimer = profile.meltdownTicks;

                AbilityEffects.abilityActivate.at(turret.x, turret.y, turret.block.size, waveColor);
                if(turret.block instanceof Turret t && t.shootSound != null){
                    t.shootSound.at(turret.x, turret.y, 0.78f, 1.0f);
                }

                spawnMissileSwarm(turret, b.type, profile, waveColor);

                turret.reloadCounter = 0f;
                turret.applySlowdown(0.01f, profile.meltdownTicks);
            }
        }
    }

    private void spawnMissileSwarm(Turret.TurretBuild turret, BulletType sourceAmmo, AbilityProfile profile, Color ammoColor){
        MissilePair pair = getOrCreateMissilePair(sourceAmmo, profile, ammoColor);
        float muzzleOffset = turret.block.size * Vars.tilesize * 0.45f;
        float mx = turret.x + Angles.trnsx(turret.rotation, muzzleOffset);
        float my = turret.y + Angles.trnsy(turret.rotation, muzzleOffset);

        int count = Math.max(1, profile.parentMissiles);
        float spread = 24f;
        for(int i = 0; i < count; i++){
            float offsetAngle = count == 1 ? 0f : Mathf.lerp(-spread * 0.5f, spread * 0.5f, (float)i / (count - 1));
            pair.parent.create(turret, turret.team, mx, my, turret.rotation + offsetAngle);
        }
    }

    private MissilePair getOrCreateMissilePair(BulletType sourceAmmo, AbilityProfile profile, Color ammoColor){
        MissilePair existing = missileCache.get(sourceAmmo);
        if(existing != null) return existing;

        Color bright = ammoColor.cpy().lerp(Color.white, 0.45f);
        boolean airOnly = !sourceAmmo.collidesGround;

        MissileBulletType mini = new MissileBulletType(4.6f, Math.max(4f, sourceAmmo.damage * profile.miniDamageMultiplier));
        mini.width = 6f;
        mini.height = 10f;
        mini.lifetime = 48f;
        mini.homingPower = 0.09f;
        mini.homingRange = 140f;
        mini.homingDelay = 3f;
        mini.weaveScale = 4.5f;
        mini.weaveMag = 3.8f;
        mini.collidesGround = !airOnly;
        mini.collidesAir = sourceAmmo.collidesAir;
        mini.splashDamageRadius = Math.max(12f, sourceAmmo.splashDamageRadius * 0.65f);
        mini.splashDamage = Math.max(5f, sourceAmmo.splashDamage * 0.45f);
        mini.status = profile.debuff;
        mini.statusDuration = profile.debuffDurationTicks;
        mini.frontColor = bright;
        mini.backColor = ammoColor;
        mini.trailColor = ammoColor;
        mini.lightColor = bright;
        mini.trailLength = 7;
        mini.trailWidth = 1.4f;
        mini.hitEffect = AbilityEffects.empoweredHit;
        mini.despawnEffect = Fx.none;
        mini.init();
        if(!Vars.headless) mini.load();

        MissileBulletType parent = new MissileBulletType(6.2f, Math.max(10f, sourceAmmo.damage * profile.damageMultiplier));
        parent.width = 9f;
        parent.height = 14f;
        parent.lifetime = 22f;
        parent.homingPower = 0.05f;
        parent.homingRange = 160f;
        parent.weaveScale = 3.2f;
        parent.weaveMag = 1.8f;
        parent.collidesGround = !airOnly;
        parent.collidesAir = sourceAmmo.collidesAir;
        parent.splashDamageRadius = Math.max(16f, sourceAmmo.splashDamageRadius * 0.85f);
        parent.splashDamage = Math.max(8f, sourceAmmo.splashDamage * 0.7f);
        parent.status = profile.debuff;
        parent.statusDuration = profile.debuffDurationTicks;
        parent.fragBullets = profile.miniMissilesEach;
        parent.fragBullet = mini;
        parent.fragSpread = 18f;
        parent.fragRandomSpread = 45f;
        parent.fragVelocityMin = 0.75f;
        parent.fragVelocityMax = 1.25f;
        parent.frontColor = bright;
        parent.backColor = ammoColor;
        parent.trailColor = ammoColor;
        parent.lightColor = bright;
        parent.trailLength = 9;
        parent.trailWidth = 2f;
        parent.hitEffect = AbilityEffects.empoweredHit;
        parent.despawnEffect = AbilityEffects.empoweredHit;
        parent.init();
        if(!Vars.headless) parent.load();

        MissilePair pair = new MissilePair(parent, mini);
        missileCache.put(sourceAmmo, pair);
        return pair;
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

            if(state.meltdownTimer > 0f){
                state.meltdownTimer -= Time.delta;
                state.build.reloadCounter = 0f;
                if(state.meltdownTimer <= 0f){
                    state.meltdownTimer = 0f;
                }else{
                    state.build.applySlowdown(0.01f, 2f);
                    state.sparkTimer += Time.delta;
                    if(state.sparkTimer >= 12f){
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
                }else{
                    state.build.applyBoost(state.profile.fireRateMultiplier, state.activeTimer);
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
                empoweredList.remove(i);
                if(eb.bullet != null){
                    empoweredLookup.remove(eb.bullet.id);
                }
                continue;
            }

            eb.trailTimer += Time.delta;
            if(eb.trailTimer >= 2.2f){
                eb.trailTimer = 0f;
                AbilityEffects.empoweredTrail.at(eb.bullet.x, eb.bullet.y, eb.bullet.rotation(), eb.color);
            }
        }
    }

    private void drawRuntime(){
        if(Vars.headless || Vars.state == null || turretStates.size == 0) return;

        float pulse = 0.5f + 0.5f * Mathf.sin(Time.time * 0.22f);
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

    private void empowerBullet(Bullet b, AbilityProfile profile){
        b.damage *= profile.damageMultiplier;

        if(profile.bulletSpeedMultiplier > 0f && profile.bulletSpeedMultiplier != 1f){
            b.vel.scl(profile.bulletSpeedMultiplier);
            b.lifetime /= profile.bulletSpeedMultiplier;
        }

        Color bulletColor = resolveBulletColor(b.type);
        if(b.owner instanceof Turret.TurretBuild turret){
            TurretState state = turretStates.get(turret.id);
            if(state != null){
                state.lastColor = bulletColor;
            }
        }

        EmpoweredBullet eb = new EmpoweredBullet(b, profile, bulletColor);
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
        float trailTimer = 0f;

        EmpoweredBullet(Bullet bullet, AbilityProfile profile, Color color){
            this.bullet = bullet;
            this.profile = profile;
            this.color = color;
        }
    }

    private static class MissilePair{
        final MissileBulletType parent;
        final MissileBulletType mini;

        MissilePair(MissileBulletType parent, MissileBulletType mini){
            this.parent = parent;
            this.mini = mini;
        }
    }
                }
