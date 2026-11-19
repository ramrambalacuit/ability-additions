package abilityadditions.core;

import abilityadditions.content.AbilityEffects;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

public class AbilityManager{
    private static final AbilityManager INSTANCE = new AbilityManager();

    private final ObjectMap<Block, AbilityProfile> blockAbilities = new ObjectMap<>();
    private final IntMap<TurretState> turretStates = new IntMap<>();
    private final IntMap<EmpoweredBullet> empoweredLookup = new IntMap<>();
    private final Seq<EmpoweredBullet> empoweredList = new Seq<>();
    private final Seq<Integer> staleStateIds = new Seq<>();

    private final Stat abilityStat = new Stat("ability", StatCat.function);
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
            block.checkStats();
            block.stats.add(abilityStat, profile.statSummary());
        }
    }

    public void initEvents(){
        if(eventsRegistered) return;
        eventsRegistered = true;

        Events.on(EventType.WorldLoadEvent.class, e -> clearRuntimeState());

        Events.on(EventType.BulletCreateEvent.class, e -> {
            Bullet b = e.bullet;
            if(b == null || !(b.owner instanceof Turret.TurretBuild turret)) return;

            AbilityProfile profile = blockAbilities.get(turret.block);
            if(profile == null) return;

            if(!isPrimaryBullet(turret.block, b.type)) return;

            TurretState state = turretStates.get(turret.id);
            if(state == null){
                state = new TurretState(turret, profile);
                turretStates.put(turret.id, state);
            }

            if(state.activeTimer > 0f){
                empowerBullet(b, profile);
            }else if(profile.trigger.checkAndAdvance(state)){
                state.activeTimer = profile.durationTicks;
                turret.applyBoost(profile.fireRateMultiplier, profile.durationTicks);

                Color waveColor = resolveBulletColor(b.type);
                AbilityEffects.abilityActivate.at(turret.x, turret.y, 0f, waveColor);

                if(turret.block instanceof Turret t && t.shootSound != null){
                    t.shootSound.at(turret.x, turret.y, 1.35f, 0.85f);
                }

                empowerBullet(b, profile);
            }
        });

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
    }

    private void updateRuntime(){
        if(Vars.state == null || Vars.state.isPaused()) return;

        staleStateIds.clear();
        for(IntMap.Entry<TurretState> entry : turretStates.entries()){
            TurretState state = entry.value;
            if(state.build == null || !state.build.isValid()){
                staleStateIds.add(entry.key);
                continue;
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
                            state.build.x + Mathf.range(state.build.block.size * 3f),
                            state.build.y + Mathf.range(state.build.block.size * 3f),
                            0f,
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
    }

    public static class TurretState{
        public final Turret.TurretBuild build;
        public final AbilityProfile profile;
        public int shotCount = 0;
        public float activeTimer = 0f;
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
    } 
