package abilityadditions.content;

import abilityadditions.core.*;
import abilityadditions.core.MeleeType.*;
import arc.*;
import arc.assets.loaders.SoundLoader.*;
import arc.audio.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;

public class JotaroContent{

    public static UnitType jotaro;
    public static StatusEffect punchSlow;
    public static StatusEffect heavyStun;
    public static StatusEffect sprintBuff;
    public static StatusEffect resolveBuff;
    public static BulletType rockProjectile;

    public static Sound punchSound = Sounds.none;
    public static Sound heavyPunchSound = Sounds.none;
    public static Sound oraRushSound = Sounds.none;
    public static Sound dashSound = Sounds.none;
    public static Sound yareYareSound = Sounds.none;

    private static final IntMap<JotaroRuntime> runtimes = new IntMap<>();
    private static boolean eventsHooked = false;
    private static boolean soundsLoaded = false;

    private static final Color JOTARO_PURPLE = Color.valueOf("9c6bff");
    private static final Color JOTARO_GOLD = Color.valueOf("ffcf5c");

    private static final float ORA_DURATION_TICKS = 315f; // 5.25s flurry + finisher right at 5.5s (matches ora-rush.ogg)
    private static final float ORA_COOLDOWN_TICKS = 600f; // 10.0s cooldown after ORA ends
    private static final float COMBO_RECOVERY_TICKS = 54f; // 0.90s pause after 4th Heavy hit

    private static Effect punchArcEffect;
    private static Effect heavyPunchEffect;
    private static Effect dashAfterimageEffect;
    private static Effect sprintDustEffect;
    private static Effect oraFistEffect;
    private static Effect rockThrowMuzzle;

    public static void load(){
        loadEffectsAndStatuses();
        loadRockBullet();

        MeleeType normalPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 26f;
            arcAngle = 115f;
            damage = 42f;
            buildingDamageMultiplier = 0.9f;
            maxTargets = 5;
            hitGround = true;
            hitAir = false;
            knockback = 2.0f;
            lungeSpeed = 0.6f;
            hitStatus = punchSlow;
            hitStatusDuration = 12f; // 0.20s light stagger (does NOT disarm)
            accentColor = JOTARO_PURPLE;
            swingEffect = punchArcEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType heavyFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 32f;
            arcAngle = 145f;
            damage = 88f;
            buildingDamageMultiplier = 1.25f;
            pierceArmor = true;
            maxTargets = 7;
            hitGround = true;
            hitAir = false;
            knockback = 7.2f;
            lungeSpeed = 1.5f;
            shake = 1.6f;
            hitStatus = heavyStun;
            hitStatusDuration = 30f; // 0.50s disarm stun (gives enemies fair fighting window!)
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeType oraFlurryPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 30f;
            arcAngle = 125f;
            damage = 16f;
            buildingDamageMultiplier = 0.9f;
            maxTargets = 5;
            hitGround = true;
            hitAir = false;
            knockback = 0.85f;
            hitStatus = punchSlow;
            hitStatusDuration = 10f; // stagger only during flurry so enemies can still fire back
            accentColor = JOTARO_PURPLE;
            swingEffect = oraFistEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeWeapon jotaroFist = new MeleeWeapon("", normalPunch){{
            x = 4.5f;
            y = 1.5f;
            shootX = 0f;
            shootY = 5f;
            reload = 33f; // Mirrored + alternate = 0.55s per punch
            mirror = true;
            alternate = true;
            rotate = false;
            recoil = 2.2f;
            shootCone = 40f;
            shootSound = Sounds.none;

            strikeHandler = (unit, mount, ox, oy, angle, weapon) -> {
                JotaroRuntime rt = getRuntime(unit);

                // Block M1s during ORA Rush or during post-Heavy combo recovery
                if(rt.oraActive || rt.comboRecoveryTimer > 0f){
                    return;
                }

                rt.comboIdleTimer = 70f;
                rt.comboStep = (rt.comboStep + 1) % 4;

                int hits;
                if(rt.comboStep == 0){
                    // 4th hit: Heavy Finisher + 0.90s recovery pause
                    hits = heavyFinisher.execute(unit, ox, oy, angle);
                    playSound(heavyPunchSound, ox, oy, 1f, 0.9f);
                    rt.comboRecoveryTimer = COMBO_RECOVERY_TICKS;
                }else{
                    // Hits 1-3: Normal M1
                    hits = normalPunch.execute(unit, ox, oy, angle);
                    playSound(punchSound, ox, oy, Mathf.random(0.95f, 1.05f), 0.72f);
                }

                if(hits > 0 && rt.oraCooldown <= 0f && !rt.oraActive){
                    rt.oraCharge = Math.min(6, rt.oraCharge + 1);
                }
            };
        }};

        jotaro = new UnitType("jotaro-kujo"){
            {
                localizedName = "Jotaro Kujo";
                description = "Melee brawler who sprints and weaves through enemy fire, stuns on heavy combo finishers, and pelts air targets with rocks.";
                constructor = MechUnit::create;
                aiController = MeleeCommandAI::new;
                controller = u -> new MeleeCommandAI();

                flying = false;
                speed = 0.95f;
                rotateSpeed = 6.5f;
                baseRotateSpeed = 6.5f;
                hitSize = 11f;
                health = 3500f;
                armor = 10f;
                drag = 0.35f;
                accel = 0.55f;

                stepSound = UnitTypes.dagger != null ? UnitTypes.dagger.stepSound : Sounds.mechStepSmall;
                stepSoundVolume = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundVolume : 0.5f;
                stepSoundPitch = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitch : 1f;
                stepSoundPitchRange = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitchRange : 0.1f;

                targetAir = false;
                targetGround = true;
                autoFindTarget = false;
                faceTarget = true;
                omniMovement = true;
                useUnitCap = true;

                // Secret Sandbox unit: Payload Source only
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
                if(rt.comboRecoveryTimer > 0f){
                    rt.comboRecoveryTimer = Math.max(0f, rt.comboRecoveryTimer - Time.delta);
                }
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
        resolveModSounds();
        if(eventsHooked) return;
        eventsHooked = true;

        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit != null && e.unit.type == jotaro && e.unit.controller() instanceof MeleeCommandAI ai){
                ai.notifyThreatened();
            }
        });

        Events.on(ResetEvent.class, e -> runtimes.clear());
    }

    private static void resolveModSounds(){
        if(soundsLoaded || Vars.headless) return;
        soundsLoaded = true;

        punchSound = loadSoundSmart("punch");
        heavyPunchSound = loadSoundSmart("heavy-punch");
        oraRushSound = loadSoundSmart("ora-rush");
        dashSound = loadSoundSmart("dash");
        yareYareSound = loadSoundSmart("yare-yare");
    }

    private static Sound loadSoundSmart(String baseName){
        try{
            // 1. Check Core.assets first (just like Mindustry ContentParser)
            String[] assetKeys = {
                baseName + ".ogg",
                baseName + ".mp3",
                "sounds/" + baseName + ".ogg",
                "sounds/" + baseName + ".mp3",
                "assets/sounds/" + baseName + ".ogg",
                "assets/sounds/" + baseName + ".mp3"
            };
            for(String key : assetKeys){
                Sound existing = Core.assets.getOrNull(key, Sound.class);
                if(existing != null){
                    Log.info("[AbilityAdditions] Sound loaded from Core.assets: @", key);
                    return existing;
                }
            }

            // 2. Check Vars.tree
            if(Vars.tree.get("sounds/" + baseName + ".ogg").exists() || Vars.tree.get("sounds/" + baseName + ".mp3").exists()){
                Sound s = Vars.tree.loadSound(baseName);
                Log.info("[AbilityAdditions] Sound queued from Vars.tree: sounds/@", baseName);
                return s;
            }

            // 3. Fallback directly inside mod root (handles into("assets") nesting if needed)
            Mods.LoadedMod mod = Vars.mods.locateMod("ability-additions");
            if(mod != null && mod.root != null){
                String[] relPaths = {
                    "sounds/" + baseName + ".ogg",
                    "sounds/" + baseName + ".mp3",
                    "assets/sounds/" + baseName + ".ogg",
                    "assets/sounds/" + baseName + ".mp3"
                };
                for(String rel : relPaths){
                    Fi fi = mod.root.child(rel);
                    if(fi.exists()){
                        Sound s = new Sound();
                        Core.assets.load(rel, Sound.class, new SoundParameter(s));
                        Log.info("[AbilityAdditions] Sound loaded from mod root: @", rel);
                        return s;
                    }
                }
            }

            Log.warn("[AbilityAdditions] Sound not found: @ (.ogg/.mp3) in assets/sounds/", baseName);
            return Sounds.none;
        }catch(Throwable t){
            Log.err("[AbilityAdditions] Failed loading sound " + baseName, t);
            return Sounds.none;
        }
    }

    private static void playSound(Sound sound, float x, float y, float pitch, float volume){
        if(Vars.headless || sound == null || sound == Sounds.none) return;
        sound.at(x, y, pitch, volume);
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
        punchSlow = new StatusEffect("jotaro-punch-slow"){{
            disarm = false;
            speedMultiplier = 0.45f;
            reloadMultiplier = 0.78f;
            show = false;
        }};

        heavyStun = new StatusEffect("jotaro-heavy-stun"){{
            disarm = true;
            speedMultiplier = 0.12f;
            reloadMultiplier = 0.15f;
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
            Draw.alpha(0.6f * e.fout());
            Lines.stroke(2.5f * e.fout());
            Lines.circle(e.x, e.y, 7f + e.fin() * 10f);
            Drawf.tri(e.x, e.y, 6.5f * e.fout(), 15f, e.rotation);
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
                Draw.color();
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

    // Ability 1: Smart Dash, Bullet Weave Dodge & Sprint
    public static class SprintDashAbility extends Ability{
        @Override
        public String localized(){
            return "Delinquent Dash & Weave";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Dashes to close M1 gaps, weaves diagonally through incoming bullets while charging, and sprints until reaching close range.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;
            bars.add(new Bar(
                () -> ai.dashCooldown <= 0f ? "Dash Ready" : "Dash: " + Strings.fixed(ai.dashCooldown / 60f, 1) + "s",
                () -> ai.dashCooldown <= 0f ? JOTARO_PURPLE : Color.gray,
                () -> 1f - Mathf.clamp(ai.dashCooldown / ai.dashCooldownDuration)
            )).row();
        }

        @Override
        public void update(Unit unit){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;

            if(ai.dashTriggeredThisTick){
                dashAfterimageEffect.at(unit.x, unit.y, ai.dashAngle);
                playSound(dashSound, unit.x, unit.y, Mathf.random(0.95f, 1.05f), 0.85f);
            }
            if(ai.dashTimer > 0f && Mathf.chanceDelta(0.75f)){
                dashAfterimageEffect.at(unit.x, unit.y, ai.dashAngle);
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

    // Ability 2: Independent Pinpoint Anti-Air Rock Throw
    public static class RockThrowAbility extends Ability{
        public float range = 185f;
        public float cooldown = 55f;

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
            rt.rockCooldown = cooldown;
        }
    }

    // Ability 3: ORA ORA Rush (aligned to 5.5s ora-rush.ogg + 10s cooldown)
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
            t.add("[lightgray]6 confirmed M1 hits charge a 5.25s ORA punch flurry ending in a Heavy Finisher, then enters a 10s cooldown.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            JotaroRuntime rt = getRuntime(unit);
            bars.add(new Bar(
                () -> rt.oraActive ? "ORA ORA RUSH! " + Strings.fixed(rt.oraTimer / 60f, 1) + "s"
                    : rt.oraCooldown > 0f ? "ORA Cooldown: " + Strings.fixed(rt.oraCooldown / 60f, 1) + "s"
                    : "ORA Charge: " + rt.oraCharge + "/6",
                () -> rt.oraActive ? JOTARO_GOLD : (rt.oraCooldown > 0f ? Color.scarlet : JOTARO_PURPLE),
                () -> rt.oraActive ? Mathf.clamp(rt.oraTimer / ORA_DURATION_TICKS)
                    : rt.oraCooldown > 0f ? Mathf.clamp(rt.oraCooldown / ORA_COOLDOWN_TICKS)
                    : Mathf.clamp(rt.oraCharge / 6f)
            )).row();
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);

            if(rt.oraCooldown > 0f){
                rt.oraCooldown = Math.max(0f, rt.oraCooldown - Time.delta);
            }

            if(!rt.oraActive){
                if(rt.oraCooldown <= 0f && rt.oraCharge >= 6){
                    Unit nearby = Units.closestEnemy(unit.team, unit.x, unit.y, 44f, u -> !u.dead && !u.isFlying());
                    if(nearby != null){
                        rt.oraCharge = 0;
                        rt.oraActive = true;
                        rt.oraTimer = ORA_DURATION_TICKS;
                        rt.oraTickTimer = 0f;
                        Effect.shake(1.5f, 1.5f, unit);
                        playSound(oraRushSound, unit.x, unit.y, 1f, 0.95f);
                    }
                }
                return;
            }

            rt.oraTimer -= Time.delta;
            rt.oraTickTimer -= Time.delta;

            if(rt.oraTickTimer <= 0f){
                rt.oraTickTimer = 10f;
                float ox = unit.x + Angles.trnsx(unit.rotation, 6f);
                float oy = unit.y + Angles.trnsy(unit.rotation, 6f);
                flurryType.execute(unit, ox, oy, unit.rotation);
            }

            if(rt.oraTimer <= 0f){
                rt.oraActive = false;
                rt.oraCooldown = ORA_COOLDOWN_TICKS;
                float ox = unit.x + Angles.trnsx(unit.rotation, 8f);
                float oy = unit.y + Angles.trnsy(unit.rotation, 8f);
                finisherType.execute(unit, ox, oy, unit.rotation);
                playSound(heavyPunchSound, ox, oy, 0.95f, 0.95f);
            }
        }
    }

    // Ability 4: Yare Yare Daze (aligned to 2s yare-yare.ogg)
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
                playSound(yareYareSound, unit.x, unit.y, 1f, 0.95f);
            }
        }
    }

    private static class JotaroRuntime{
        int comboStep = 0;
        float comboIdleTimer = 0f;
        float comboRecoveryTimer = 0f;
        float rockCooldown = 0f;

        int oraCharge = 0;
        boolean oraActive = false;
        float oraTimer = 0f;
        float oraTickTimer = 0f;
        float oraCooldown = 0f;

        float resolveCooldown = 0f;
    }
                                                                  }
