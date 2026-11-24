package abilityadditions.content;

import abilityadditions.core.*;
import abilityadditions.core.MeleeType.*;
import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.meta.*;

public class JotaroContent{

    public static UnitType jotaro;
    public static StatusEffect punchStun;
    public static StatusEffect sprintBuff;
    public static StatusEffect resolveBuff;
    public static BulletType rockProjectile;
    public static Sound rockThrowSound = Sounds.none;

    private static final IntMap<JotaroRuntime> runtimes = new IntMap<>();
    private static boolean eventsHooked = false;

    private static final Color JOTARO_PURPLE = Color.valueOf("9c6bff");
    private static final Color JOTARO_GOLD = Color.valueOf("ffcf5c");

    private static Effect punchArcEffect;
    private static Effect heavyPunchEffect;
    private static Effect dashAfterimageEffect;
    private static Effect sprintDustEffect;
    private static Effect oraFistEffect;
    private static Effect rockThrowMuzzle;

    public static void load(){
        loadEffectsAndStatuses();
        rockThrowSound = loadModSound("rock-throw");
        loadRockBullet();

        MeleeType normalPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 26f;
            arcAngle = 115f;
            damage = 38f;
            buildingDamageMultiplier = 0.85f;
            maxTargets = 5;
            hitGround = true;
            hitAir = false;
            knockback = 2.2f;
            lungeSpeed = 0.7f;
            hitStatus = punchStun;
            hitStatusDuration = 26f; // ~0.43s disarm + stun
            accentColor = JOTARO_PURPLE;
            swingEffect = punchArcEffect;
            hitEffect = Fx.hitBulletColor;
            swingSound = Sounds.none;
            hitSound = Sounds.none;
        }};

        MeleeType heavyFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 32f;
            arcAngle = 145f;
            damage = 74f;
            buildingDamageMultiplier = 1.15f;
            pierceArmor = true;
            maxTargets = 7;
            hitGround = true;
            hitAir = false;
            knockback = 6.8f;
            lungeSpeed = 1.6f;
            shake = 1.6f;
            hitStatus = punchStun;
            hitStatusDuration = 48f; // 0.8s disarm + stun
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
            swingSound = Sounds.none;
            hitSound = Sounds.none;
        }};

        MeleeType oraFlurryPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 30f;
            arcAngle = 120f;
            damage = 28f;
            buildingDamageMultiplier = 0.9f;
            maxTargets = 5;
            hitGround = true;
            hitAir = false;
            knockback = 1.1f;
            hitStatus = punchStun;
            hitStatusDuration = 20f;
            accentColor = JOTARO_PURPLE;
            swingEffect = oraFistEffect;
            hitEffect = Fx.hitBulletColor;
            swingSound = Sounds.none;
            hitSound = Sounds.none;
        }};

        MeleeWeapon jotaroFist = new MeleeWeapon("", normalPunch){{
            x = 4.5f;
            y = 1.5f;
            shootX = 0f;
            shootY = 5f;
            reload = 18f;
            mirror = true;
            alternate = true;
            rotate = false;
            recoil = 2.2f;
            shootCone = 40f;
            shootSound = Sounds.none;

            strikeHandler = (unit, mount, ox, oy, angle, weapon) -> {
                JotaroRuntime rt = getRuntime(unit);
                rt.comboIdleTimer = 110f;
                rt.comboStep = (rt.comboStep + 1) % 4;

                int hits;
                if(rt.comboStep == 0){
                    // 4th strike: Heavy Finisher!
                    hits = heavyFinisher.execute(unit, ox, oy, angle);
                }else{
                    // 1st-3rd strikes: Normal M1
                    hits = normalPunch.execute(unit, ox, oy, angle);
                }

                if(hits > 0 && !rt.oraActive){
                    rt.oraCharge = Math.min(6, rt.oraCharge + 1);
                }
            };
        }};

        jotaro = new UnitType("jotaro-kujo"){
            {
                constructor = MechUnit::create;
                aiController = MeleeCommandAI::new;
                controller = u -> new MeleeCommandAI();

                flying = false;
                speed = 0.95f;
                rotateSpeed = 6.5f;
                baseRotateSpeed = 6.5f;
                hitSize = 11f;
                health = 950f;
                armor = 6f;
                drag = 0.35f;
                accel = 0.55f;

                // Copy Dagger walking sound
                stepSound = UnitTypes.dagger != null ? UnitTypes.dagger.stepSound : Sounds.mechStepSmall;
                stepSoundVolume = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundVolume : 0.5f;
                stepSoundPitch = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitch : 1f;
                stepSoundPitchRange = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitchRange : 0.1f;

                // Strictly ground-focused so air units never hijack melee movement
                targetAir = false;
                targetGround = true;
                faceTarget = true;
                omniMovement = true;
                useUnitCap = true;

                // Secret Sandbox unit: Payload Source only (no factory, no tech tree)
                hidden = false;
                allowedInPayloads = true;
                outlines = false;

                weapons.add(jotaroFist);
                abilities.add(
                    new SprintDashAbility(),
                    new RockThrowAbility(),
                    new OraRushAbility(oraFlurryPunch, heavyFinisher),
                    new YareResolveAbility()
                );
            }

            @Override
            public void createIcons(MultiPacker packer){
                // Skip generating separate icons while reusing Dagger's sprites
            }

            @Override
            public void load(){
                super.load();
                UnitType d = UnitTypes.dagger;
                if(d != null){
                    if(d.region == null || !d.region.found()) d.load();
                    region = d.region;
                    previewRegion = d.previewRegion;
                    legRegion = d.legRegion;
                    legBaseRegion = d.legBaseRegion;
                    baseRegion = d.baseRegion;
                    cellRegion = d.cellRegion;
                    outlineRegion = d.outlineRegion;
                    shadowRegion = d.shadowRegion;
                    softShadowRegion = d.softShadowRegion;
                    uiIcon = d.uiIcon;
                    fullIcon = d.fullIcon;
                    stepSound = d.stepSound;
                    stepSoundVolume = d.stepSoundVolume;
                    stepSoundPitch = d.stepSoundPitch;
                    stepSoundPitchRange = d.stepSoundPitchRange;
                }
            }

            @Override
            public void update(Unit unit){
                super.update(unit);

                // Protect against save/load resetting controller back to plain CommandAI
                if(!(unit.controller() instanceof MeleeCommandAI) && !unit.isPlayer()){
                    UnitController prev = unit.controller();
                    MeleeCommandAI next = new MeleeCommandAI();
                    if(prev instanceof CommandAI old){
                        next.command = old.command;
                        next.targetPos = old.targetPos;
                        if(!(old.attackTarget instanceof Unit u && u.isFlying())){
                            next.attackTarget = old.attackTarget;
                        }
                    }
                    unit.controller(next);
                }

                JotaroRuntime rt = getRuntime(unit);
                if(rt.comboIdleTimer > 0f){
                    rt.comboIdleTimer = Math.max(0f, rt.comboIdleTimer - Time.delta);
                    if(rt.comboIdleTimer <= 0f){
                        rt.comboStep = 0;
                    }
                }
            }
        };
    }

    public static void init(){
        if(eventsHooked) return;
        eventsHooked = true;
        Events.on(ResetEvent.class, e -> runtimes.clear());
    }

    private static Sound loadModSound(String name){
        if(Vars.headless) return Sounds.none;
        try{
            Sound loaded = Vars.tree.loadSound(name);
            return loaded != null ? loaded : Sounds.none;
        }catch(Throwable ignored){
            return Sounds.none;
        }
    }

    private static JotaroRuntime getRuntime(Unit unit){
        JotaroRuntime rt = runtimes.get(unit.id);
        if(rt == null){
            rt = new JotaroRuntime();
            runtimes.put(unit.id, rt);
        }
        return rt;
    }

    private static void loadEffectsAndStatuses(){
        punchStun = new StatusEffect("jotaro-punch-stun"){{
            disarm = true;
            speedMultiplier = 0.22f;
            reloadMultiplier = 0.25f;
            show = false;
        }};

        sprintBuff = new StatusEffect("jotaro-sprint"){{
            speedMultiplier = 1.65f;
            show = false;
        }};

        resolveBuff = new StatusEffect("jotaro-resolve"){{
            healthMultiplier = 1.45f;
            reloadMultiplier = 1.35f;
            speedMultiplier = 1.15f;
            color = JOTARO_GOLD;
            show = true;
        }};

        punchArcEffect = new Effect(14f, e -> {
            Draw.color(e.color, Color.white, 0.25f);
            Lines.stroke(3f * e.fout());
            Lines.arc(e.x, e.y, 16f + e.fin() * 10f, 0.34f, e.rotation - 61f);
        });

        heavyPunchEffect = new Effect(22f, e -> {
            Draw.color(JOTARO_GOLD, Color.white, 0.3f);
            Lines.stroke(4.5f * e.fout());
            Lines.arc(e.x, e.y, 18f + e.fin() * 16f, 0.42f, e.rotation - 75f);
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x + Angles.trnsx(e.rotation, 12f), e.y + Angles.trnsy(e.rotation, 12f), 6f + e.fin() * 14f);
        });

        dashAfterimageEffect = new Effect(18f, e -> {
            Draw.color(JOTARO_PURPLE, Color.white, 0.3f);
            Draw.alpha(0.55f * e.fout());
            Lines.stroke(2.5f * e.fout());
            Lines.circle(e.x, e.y, 7f + e.fin() * 9f);
            Drawf.tri(e.x, e.y, 6f * e.fout(), 14f, e.rotation);
        });

        sprintDustEffect = new Effect(16f, e -> {
            Draw.color(Color.lightGray);
            Draw.alpha(0.45f * e.fout());
            Fill.circle(e.x, e.y, 2.2f * e.fout() + 0.8f);
        });

        oraFistEffect = new Effect(12f, e -> {
            float off = Mathf.range(6f);
            float fx = e.x + Angles.trnsx(e.rotation + 90f, off) + Angles.trnsx(e.rotation, 8f + e.fin() * 18f);
            float fy = e.y + Angles.trnsy(e.rotation + 90f, off) + Angles.trnsy(e.rotation, 8f + e.fin() * 18f);
            Draw.color(JOTARO_PURPLE, JOTARO_GOLD, Mathf.random(0.35f));
            Draw.alpha(0.85f * e.fout());
            Drawf.tri(fx, fy, 4.5f * e.fout(), 11f, e.rotation);
            Fill.circle(fx, fy, 2.8f * e.fout());
        });

        rockThrowMuzzle = new Effect(12f, e -> {
            Draw.color(Color.gray);
            Draw.alpha(0.5f * e.fout());
            Lines.stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, 4f + e.fin() * 8f);
        });
    }

    private static void loadRockBullet(){
        // Non-glowing vanilla stone boulder projectile
        rockProjectile = new BasicBulletType(8.2f, 52f){{
            lifetime = 32f;
            damage = 52f;
            splashDamage = 18f;
            splashDamageRadius = 14f;
            collidesGround = false;
            collidesAir = true;
            homingPower = 0.16f;
            homingRange = 120f;
            homingDelay = 0f;
            hitSize = 5f;
            trailLength = 0;
            lightOpacity = 0f;
            lightRadius = 0f;
            shootEffect = rockThrowMuzzle;
            smokeEffect = Fx.none;
            hitEffect = Fx.pulverizeSmall;
            despawnEffect = Fx.pulverizeSmall;
            hitSound = Sounds.none;
            hitColor = Color.gray;
        }

            @Override
            public void draw(Bullet b){
                TextureRegion rockTex = null;
                if(Blocks.boulder != null){
                    if(Blocks.boulder.variantRegions != null && Blocks.boulder.variantRegions.length > 0){
                        rockTex = Blocks.boulder.variantRegions[Math.abs(b.id) % Blocks.boulder.variantRegions.length];
                    }else{
                        rockTex = Blocks.boulder.region;
                    }
                }
                if(rockTex == null || !rockTex.found()){
                    rockTex = Core.atlas.find("boulder1");
                }

                float oldZ = Draw.z();
                Draw.z(Layer.flyingUnit + 0.2f);
                Draw.color(); // Natural stone color with zero glow
                if(rockTex != null && rockTex.found()){
                    Draw.rect(rockTex, b.x, b.y, 9f, 9f, b.id * 45f + b.time * 16f);
                }else{
                    Draw.color(Color.gray);
                    Fill.poly(b.x, b.y, 6, 4.5f, b.time * 16f);
                }
                Draw.color();
                Draw.z(oldZ);
            }
        };
        if(!Vars.headless){
            rockProjectile.load();
        }
    }

    // Ability 1: Dash + Sprint when chasing distant enemies (silent)
    public static class SprintDashAbility extends Ability{
        @Override
        public String localized(){
            return "Delinquent Dash & Sprint";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Sprints toward far ground targets and dashes into melee range, stopping sprint once close.[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;

            if(ai.dashTriggeredThisTick){
                dashAfterimageEffect.at(unit.x, unit.y, unit.rotation);
            }

            if(ai.sprinting){
                unit.apply(sprintBuff, 10f);
                if(Mathf.chanceDelta(0.45f)){
                    sprintDustEffect.at(unit.x + Mathf.range(3f), unit.y + Mathf.range(3f));
                }
            }else if(unit.hasEffect(sprintBuff)){
                unit.unapply(sprintBuff);
            }
        }
    }

    // Ability 2: Independent Pinpoint Anti-Air Rock Throw (uses custom rock-throw sound)
    public static class RockThrowAbility extends Ability{
        public float range = 185f;
        public float cooldown = 55f; // ~0.9s between throws

        @Override
        public String localized(){
            return "Pinpoint Rock Throw";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Throws non-glowing stone rocks with pinpoint accuracy at air units without stopping ground movement.[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);
            if(rt.rockCooldown > 0f){
                rt.rockCooldown = Math.max(0f, rt.rockCooldown - Time.delta);
                return;
            }

            Unit air = Units.closestEnemy(unit.team, unit.x, unit.y, range, u -> !u.dead && u.isFlying() && u.hittable());
            if(air == null) return;

            Vec2 aim = Predict.intercept(unit, air, rockProjectile.speed);
            float ang = Angles.angle(unit.x, unit.y, aim.x, aim.y);

            rockProjectile.create(unit, unit.team, unit.x, unit.y, ang);
            rockThrowMuzzle.at(unit.x, unit.y, ang);
            if(rockThrowSound != null && rockThrowSound != Sounds.none){
                rockThrowSound.at(unit.x, unit.y, Mathf.random(0.95f, 1.05f));
            }
            rt.rockCooldown = cooldown;
        }
    }

    // Ability 3: ORA ORA Rush (charged by landing M1s, silent)
    public static class OraRushAbility extends Ability{
        private final MeleeType flurryType;
        private final MeleeType finisherType;

        public OraRushAbility(MeleeType flurryType, MeleeType finisherType){
            this.flurryType = flurryType;
            this.finisherType = finisherType;
        }

        @Override
        public String localized(){
            return "ORA ORA Rush";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Every 6 M1 strikes triggers a 1.8s rapid punch flurry ending in a heavy knockback finisher.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            JotaroRuntime rt = getRuntime(unit);
            bars.add(new Bar(
                () -> rt.oraActive ? "ORA ORA RUSH!" : "ORA Charge: " + rt.oraCharge + "/6",
                () -> rt.oraActive ? JOTARO_GOLD : JOTARO_PURPLE,
                () -> rt.oraActive ? Mathf.clamp(rt.oraTimer / 108f) : Mathf.clamp(rt.oraCharge / 6f)
            )).row();
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);

            if(!rt.oraActive){
                if(rt.oraCharge >= 6){
                    Unit nearby = Units.closestEnemy(unit.team, unit.x, unit.y, 44f, u -> !u.dead && !u.isFlying());
                    if(nearby != null){
                        rt.oraCharge = 0;
                        rt.oraActive = true;
                        rt.oraTimer = 108f; // 1.8s
                        rt.oraTickTimer = 0f;
                        Effect.shake(1.5f, 1.5f, unit);
                    }
                }
                return;
            }

            rt.oraTimer -= Time.delta;
            rt.oraTickTimer -= Time.delta;

            if(rt.oraTickTimer <= 0f){
                rt.oraTickTimer = 7f;
                float ox = unit.x + Angles.trnsx(unit.rotation, 6f);
                float oy = unit.y + Angles.trnsy(unit.rotation, 6f);
                flurryType.execute(unit, ox, oy, unit.rotation);
            }

            if(rt.oraTimer <= 0f){
                rt.oraActive = false;
                float ox = unit.x + Angles.trnsx(unit.rotation, 8f);
                float oy = unit.y + Angles.trnsy(unit.rotation, 8f);
                finisherType.execute(unit, ox, oy, unit.rotation);
            }
        }
    }

    // Ability 4: Yare Yare Daze (Resolve under 45% HP, silent)
    public static class YareResolveAbility extends Ability{
        @Override
        public String localized(){
            return "Yare Yare Daze";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Dropping below 45% HP cleanses debuffs and grants 3s of Resolve (+45% durability, +35% attack speed).[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);
            if(rt.resolveCooldown > 0f){
                rt.resolveCooldown = Math.max(0f, rt.resolveCooldown - Time.delta);
                return;
            }

            if(unit.healthf() <= 0.45f){
                rt.resolveCooldown = 1200f; // 20s
                unit.unapply(StatusEffects.slow);
                unit.unapply(StatusEffects.freezing);
                unit.unapply(StatusEffects.tarred);
                unit.unapply(StatusEffects.burning);
                unit.apply(resolveBuff, 180f);
                Fx.healWave.at(unit.x, unit.y, JOTARO_GOLD);
            }
        }
    }

    private static class JotaroRuntime{
        int comboStep = 0;
        float comboIdleTimer = 0f;
        float rockCooldown = 0f;

        int oraCharge = 0;
        boolean oraActive = false;
        float oraTimer = 0f;
        float oraTickTimer = 0f;

        float resolveCooldown = 0f;
    }
              }
