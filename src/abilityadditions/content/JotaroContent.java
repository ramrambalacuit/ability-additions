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
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;

public class JotaroContent{

    public static UnitType jotaro;
    public static StatusEffect punchSlow;
    public static StatusEffect heavyStun;
    public static StatusEffect oraSlow;
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

    private static final float ORA_DURATION_TICKS = 330f; // Exact 5.50s (matches ora-rush.ogg)
    private static final float ORA_COOLDOWN_TICKS = 600f; // 10.0s cooldown
    private static final float COMBO_RECOVERY_TICKS = 54f; // 0.90s post-Heavy pause
    private static final float YARE_INVULN_TICKS = 120f; // Exact 2.00s untargetable/passthrough (matches yare-yare.ogg)

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
            reach = 28f;
            arcAngle = 120f;
            damage = 90f;
            buildingDamageMultiplier = 0.95f;
            maxTargets = 5;
            hitGround = true;
            hitAir = false;
            knockback = 2.4f;
            lungeSpeed = 0.65f;
            hitStatus = punchSlow;
            hitStatusDuration = 14f;
            accentColor = JOTARO_PURPLE;
            swingEffect = punchArcEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType heavyFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 34f;
            arcAngle = 145f;
            damage = 240f;
            buildingDamageMultiplier = 1.3f;
            pierceArmor = true;
            maxTargets = 7;
            hitGround = true;
            hitAir = false;
            knockback = 7.6f;
            lungeSpeed = 1.6f;
            shake = 2.0f;
            hitStatus = heavyStun;
            hitStatusDuration = 30f; // 0.50s disarm stun
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeType oraFlurryPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 32f;
            arcAngle = 130f;
            damage = 38f;
            buildingDamageMultiplier = 1.0f;
            pierceArmor = true;
            maxTargets = 6;
            hitGround = true;
            hitAir = false;
            knockback = 0.7f;
            hitStatus = oraSlow;
            hitStatusDuration = 16f;
            accentColor = JOTARO_PURPLE;
            swingEffect = oraFistEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType oraFinalFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 36f;
            arcAngle = 155f;
            damage = 280f;
            buildingDamageMultiplier = 1.4f;
            pierceArmor = true;
            maxTargets = 8;
            hitGround = true;
            hitAir = false;
            knockback = 8.5f;
            lungeSpeed = 1.8f;
            shake = 2.4f;
            hitStatus = heavyStun;
            hitStatusDuration = 30f; // 0.50s disarm stun
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeWeapon jotaroFist = new MeleeWeapon("", normalPunch){{
            x = 4.5f;
            y = 1.5f;
            shootX = 0f;
            shootY = 5f;
            reload = 33f; // 0.55s per punch
            mirror = true;
            alternate = true;
            rotate = false;
            recoil = 2.2f;
            shootCone = 40f;
            shootSound = Sounds.none;

            strikeHandler = (unit, mount, ox, oy, angle, weapon) -> {
                JotaroRuntime rt = getRuntime(unit);
                if(rt.oraActive || rt.comboRecoveryTimer > 0f){
                    return;
                }

                rt.comboIdleTimer = 70f;
                rt.comboStep = (rt.comboStep + 1) % 4;

                int hits;
                if(rt.comboStep == 0){
                    hits = heavyFinisher.execute(unit, ox, oy, angle);
                    playSound(heavyPunchSound, ox, oy, 1f, 0.9f);
                    rt.comboRecoveryTimer = COMBO_RECOVERY_TICKS;
                }else{
                    hits = normalPunch.execute(unit, ox, oy, angle);
                    playSound(punchSound, ox, oy, Mathf.random(0.95f, 1.05f), 0.78f);
                }

                if(hits > 0 && rt.oraCooldown <= 0f && !rt.oraActive){
                    rt.oraCharge = Math.min(6, rt.oraCharge + 1);
                }
            };
        }};

        jotaro = new UnitType("jotaro-kujo"){
            {
                localizedName = "Jotaro Kujo";
                description = "Melee brawler with dash lunges, automatic bullet weaving, a heavy stun combo, ORA ORA Rush, and anti-air rock throws.";
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
                mechStepParticles = false;

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
                abilities.addAll(
                    new LungeDashAbility(),
                    new WeaveDodgeAbility(),
                    new RockThrowAbility(),
                    new OraRushAbility(oraFlurryPunch, oraFinalFinisher),
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
            public boolean targetable(Unit unit, Team targeter){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && rt.invulnTimer > 0f) return false;
                return super.targetable(unit, targeter);
            }

            @Override
            public boolean hittable(Unit unit){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && rt.invulnTimer > 0f) return false;
                return super.hittable(unit);
            }

            @Override
            public boolean killable(Unit unit){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && rt.invulnTimer > 0f) return false;
                return super.killable(unit);
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
                if(rt.invulnTimer > 0f){
                    rt.invulnTimer = Math.max(0f, rt.invulnTimer - Time.delta);
                    unit.dead = false;
                    if(unit.health < 1f) unit.health = 1f;
                }
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

            @Override
            public void draw(Unit unit){
                super.draw(unit);
                if(Vars.headless) return;

                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && rt.invulnTimer > 0f){
                    float z = Draw.z();
                    Draw.z(Layer.effect);
                    Draw.color(JOTARO_GOLD, JOTARO_PURPLE, Mathf.absin(Time.time, 3f, 1f));
                    Lines.stroke(2.2f);
                    Lines.circle(unit.x, unit.y, unit.hitSize + 4f + Mathf.absin(Time.time, 2.5f, 1.5f));
                    Draw.color();
                    Draw.z(z);
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

            if(Vars.tree.get("sounds/" + baseName + ".ogg").exists() || Vars.tree.get("sounds/" + baseName + ".mp3").exists()){
                Sound s = Vars.tree.loadSound(baseName);
                Log.info("[AbilityAdditions] Sound queued from Vars.tree: sounds/@", baseName);
                return s;
            }

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
            speedMultiplier = 0.42f;
            reloadMultiplier = 0.78f;
            show = false;
        }};

        oraSlow = new StatusEffect("jotaro-ora-slow"){{
            disarm = false;
            speedMultiplier = 0.25f;
            reloadMultiplier = 0.65f;
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
            healthMultiplier = 1.35f;
            reloadMultiplier = 1.35f;
            speedMultiplier = 1.15f;
            color = JOTARO_GOLD;
            show = true;
        }};

        // Upgraded M1 visual: layered purple + white crescent arc, knuckle burst, and speed streaks
        punchArcEffect = new Effect(16f, e -> {
            float fx = e.x + Angles.trnsx(e.rotation, 8f + e.fin() * 10f);
            float fy = e.y + Angles.trnsy(e.rotation, 8f + e.fin() * 10f);

            Draw.color(JOTARO_PURPLE);
            Lines.stroke(4.2f * e.fout());
            Lines.arc(e.x, e.y, 14f + e.fin() * 13f, 0.36f, e.rotation - 64f);

            Draw.color(Color.white);
            Lines.stroke(1.8f * e.fout());
            Lines.arc(e.x, e.y, 16f + e.fin() * 13f, 0.28f, e.rotation - 50f);

            Draw.color(JOTARO_PURPLE, Color.white, 0.45f);
            Drawf.tri(fx, fy, 6.5f * e.fout(), 14f, e.rotation);

            for(int i = -1; i <= 1; i++){
                float sideAng = e.rotation + i * 20f;
                float sx = e.x + Angles.trnsx(sideAng, 9f);
                float sy = e.y + Angles.trnsy(sideAng, 9f);
                Lines.stroke(1.6f * e.fout());
                Lines.lineAngle(sx, sy, sideAng, 8f + e.fin() * 8f);
            }
        });

        // Upgraded Heavy Finisher visual: gold & violet double shockwaves + directional burst cone
        heavyPunchEffect = new Effect(24f, e -> {
            float ix = e.x + Angles.trnsx(e.rotation, 14f + e.fin() * 8f);
            float iy = e.y + Angles.trnsy(e.rotation, 14f + e.fin() * 8f);

            Draw.color(JOTARO_GOLD, Color.white, 0.25f);
            Lines.stroke(5.2f * e.fout());
            Lines.arc(e.x, e.y, 17f + e.fin() * 18f, 0.44f, e.rotation - 79f);

            Draw.color(JOTARO_PURPLE);
            Lines.stroke(3.0f * e.fout());
            Lines.arc(e.x, e.y, 13f + e.fin() * 14f, 0.36f, e.rotation - 64f);

            Draw.color(JOTARO_GOLD, Color.white, 0.4f);
            Drawf.tri(ix, iy, 10f * e.fout(), 22f, e.rotation);
            Lines.stroke(2.4f * e.fout());
            Lines.circle(ix, iy, 6f + e.fin() * 18f);

            Draw.color(Color.white);
            Lines.stroke(1.6f * e.fout());
            for(int k = 0; k < 5; k++){
                float a = e.rotation + (k - 2) * 18f;
                Lines.lineAngle(ix, iy, a, 7f + e.fin() * 13f);
            }
        });

        // Drawn UNDER unit sprite so it trails cleanly behind
        dashAfterimageEffect = new Effect(18f, e -> {
            Draw.color(JOTARO_PURPLE, Color.white, 0.3f);
            Draw.alpha(0.55f * e.fout());
            Lines.stroke(2.2f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin() * 8f);
            Drawf.tri(e.x, e.y, 6f * e.fout(), 14f, e.rotation);
        }).layer(Layer.groundUnit - 0.1f);

        // Drawn on debris layer behind feet, never over Jotaro's sprite
        sprintDustEffect = new Effect(16f, e -> {
            Draw.color(Color.lightGray);
            Draw.alpha(0.38f * e.fout());
            Fill.circle(e.x, e.y, 1.8f * e.fout() + 0.6f);
        }).layer(Layer.debris);

        // Upgraded ORA ORA flurry fists: multi-angle streaking punches + wind rings
        oraFistEffect = new Effect(12f, e -> {
            float off = Mathf.range(9f);
            float spread = Mathf.range(16f);
            float ang = e.rotation + spread;
            float fx = e.x + Angles.trnsx(e.rotation + 90f, off) + Angles.trnsx(ang, 9f + e.fin() * 20f);
            float fy = e.y + Angles.trnsy(e.rotation + 90f, off) + Angles.trnsy(ang, 9f + e.fin() * 20f);

            Draw.color(JOTARO_PURPLE, JOTARO_GOLD, Mathf.random(0.45f));
            Draw.alpha(0.9f * e.fout());
            Drawf.tri(fx, fy, 5.5f * e.fout(), 13f, ang);
            Fill.circle(fx, fy, 3.0f * e.fout());
            Lines.stroke(1.4f * e.fout());
            Lines.lineAngle(fx, fy, ang + 180f, 9f);
        });

        rockThrowMuzzle = new Effect(12f, e -> {
            Draw.color(Color.gray);
            Draw.alpha(0.45f * e.fout());
            Lines.stroke(1.4f * e.fout());
            Lines.circle(e.x, e.y, 4f + e.fin() * 7f);
        }).layer(Layer.groundUnit - 0.05f);
    }

    private static void loadRockBullet(){
        rockProjectile = new BasicBulletType(8.2f, 65f){{
            lifetime = 32f;
            damage = 65f;
            splashDamage = 22f;
            splashDamageRadius = 15f;
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

    // Ability 1A: Long Lunge Dash (6s cooldown) + Sprint
    public static class LungeDashAbility extends Ability{
        @Override
        public String localized(){
            return "Delinquent Lunge & Sprint";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Sprints when chasing distant ground targets, stops sprinting at close range, and lunges forward across 8-17 tile gaps (6s CD).[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;
            bars.add(new Bar(
                () -> ai.lungeCooldown <= 0f ? "Lunge Ready" : "Lunge: " + Strings.fixed(ai.lungeCooldown / 60f, 1) + "s",
                () -> ai.lungeCooldown <= 0f ? JOTARO_GOLD : Color.gray,
                () -> 1f - Mathf.clamp(ai.lungeCooldown / ai.lungeCooldownDuration)
            )).row();
        }

        @Override
        public void update(Unit unit){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;

            if(ai.lungeTriggeredThisTick){
                dashAfterimageEffect.at(unit.x, unit.y, ai.dashAngle);
                playSound(dashSound, unit.x, unit.y, 0.9f, 0.9f);
            }
            if(ai.dashTimer > 0f && Mathf.chanceDelta(0.75f)){
                dashAfterimageEffect.at(unit.x - Angles.trnsx(ai.dashAngle, 5f), unit.y - Angles.trnsy(ai.dashAngle, 5f), ai.dashAngle);
            }

            if(ai.sprinting){
                unit.apply(sprintBuff, 10f);
                if(Mathf.chanceDelta(0.35f)){
                    float bx = unit.x - Angles.trnsx(unit.rotation, 6f) + Mathf.range(2.5f);
                    float by = unit.y - Angles.trnsy(unit.rotation, 6f) + Mathf.range(2.5f);
                    sprintDustEffect.at(bx, by);
                }
            }else if(unit.hasEffect(sprintBuff)){
                unit.unapply(sprintBuff);
            }
        }
    }

    // Ability 1B: Combat Bullet Weave & Dodge Dash (3s cooldown)
    public static class WeaveDodgeAbility extends Ability{
        @Override
        public String localized(){
            return "Bullet Weave & Dodge Dash";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Automatically weaves around incoming bullets while advancing and snaps a short sidestep dash out of danger (3s CD).[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;
            bars.add(new Bar(
                () -> ai.dodgeCooldown <= 0f ? "Dodge Ready" : "Dodge: " + Strings.fixed(ai.dodgeCooldown / 60f, 1) + "s",
                () -> ai.dodgeCooldown <= 0f ? JOTARO_PURPLE : Color.gray,
                () -> 1f - Mathf.clamp(ai.dodgeCooldown / ai.dodgeCooldownDuration)
            )).row();
        }

        @Override
        public void update(Unit unit){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;

            if(ai.dodgeTriggeredThisTick){
                dashAfterimageEffect.at(unit.x, unit.y, ai.dashAngle);
                playSound(dashSound, unit.x, unit.y, 1.15f, 0.85f);
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

            float mx = unit.x + Angles.trnsx(ang, 6f);
            float my = unit.y + Angles.trnsy(ang, 6f);
            rockProjectile.create(unit, unit.team, mx, my, ang);
            rockThrowMuzzle.at(mx, my, ang);
            rt.rockCooldown = cooldown;
        }
    }

    // Ability 3: ORA ORA Rush (Exact 5.50s = 330 ticks aligned to ora-rush.ogg + 10s cooldown)
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
            t.add("[lightgray]6 confirmed M1 hits charge a 5.5s ORA punch flurry ending in a 280-damage Heavy Finisher (10s CD).[]").wrap().width(320f);
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
                    Unit nearby = Units.closestEnemy(unit.team, unit.x, unit.y, 44f, u -> !u.dead && !u.isFlying() && u.hittable());
                    if(nearby != null){
                        rt.oraCharge = 0;
                        rt.oraActive = true;
                        rt.oraTimer = ORA_DURATION_TICKS;
                        rt.oraTickTimer = 0f;
                        Effect.shake(1.8f, 1.8f, unit);
                        playSound(oraRushSound, unit.x, unit.y, 1f, 0.95f);
                    }
                }
                return;
            }

            rt.oraTimer -= Time.delta;
            rt.oraTickTimer -= Time.delta;

            if(rt.oraTickTimer <= 0f){
                rt.oraTickTimer = 6f;
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

    // Ability 4: Yare Yare Daze (Permanent Resolve while <= 45% HP + 2s untargetable/passthrough burst)
    public static class YareResolveAbility extends Ability{
        @Override
        public String localized(){
            return "Yare Yare Daze";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Dropping to 45% HP triggers 2s of untargetable bullet-passthrough invulnerability (20s CD) and permanent Resolve until healed above 45% HP.[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);
            if(rt.resolveCooldown > 0f){
                rt.resolveCooldown = Math.max(0f, rt.resolveCooldown - Time.delta);
            }

            if(unit.healthf() <= 0.45f){
                // Permanent Resolve while <= 45% HP
                unit.apply(resolveBuff, 10f);

                // Trigger 2.0s untargetable/passthrough + debuff cleanse on cooldown
                if(!rt.resolveTriggeredBelow && rt.resolveCooldown <= 0f){
                    rt.resolveTriggeredBelow = true;
                    rt.resolveCooldown = 1200f; // 20s cooldown
                    rt.invulnTimer = YARE_INVULN_TICKS;

                    unit.unapply(StatusEffects.slow);
                    unit.unapply(StatusEffects.freezing);
                    unit.unapply(StatusEffects.tarred);
                    unit.unapply(StatusEffects.burning);
                    unit.unapply(StatusEffects.melting);
                    unit.unapply(StatusEffects.shocked);
                    unit.unapply(StatusEffects.sapped);

                    Fx.healWave.at(unit.x, unit.y, JOTARO_GOLD);
                    playSound(yareYareSound, unit.x, unit.y, 1f, 0.95f);
                }
            }else{
                // Cancelled immediately when healed above 45% HP
                rt.resolveTriggeredBelow = false;
                if(unit.hasEffect(resolveBuff)){
                    unit.unapply(resolveBuff);
                }
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
        float invulnTimer = 0f;
        boolean resolveTriggeredBelow = false;
    }
                }
