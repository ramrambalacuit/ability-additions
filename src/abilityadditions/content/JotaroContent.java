package abilityadditions.content;

import abilityadditions.core.*;
import abilityadditions.core.MeleeType.*;
import arc.*;
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
    public static StatusEffect standHeavyStun;
    public static StatusEffect oraStun;
    public static StatusEffect entranceStun;
    public static StatusEffect sprintBuff;
    public static StatusEffect resolveBuff;
    public static StatusEffect timeStopFreeze;
    public static BulletType rockProjectile;

    public static TextureRegion starPlatinumRegion;

    public static Sound punchSound = Sounds.none;
    public static Sound heavyPunchSound = Sounds.none;
    public static Sound oraRushSound = Sounds.none;
    public static Sound dashSound = Sounds.none;
    public static Sound yareYareSound = Sounds.none;
    public static Sound standSummonSound = Sounds.none;
    public static Sound teleportSound = Sounds.none;
    public static Sound timeStopSound = Sounds.none;

    private static final IntMap<JotaroRuntime> runtimes = new IntMap<>();
    private static final Seq<TimeStopZone> timeStopZones = new Seq<>();
    private static final IntMap<FrozenBullet> frozenBullets = new IntMap<>();
    private static boolean eventsHooked = false;
    private static boolean soundsLoaded = false;

    private static final Color JOTARO_PURPLE = Color.valueOf("9c6bff");
    private static final Color JOTARO_GOLD = Color.valueOf("ffcf5c");
    private static final Color STAND_CYAN = Color.valueOf("7cf7ff");

    private static final float STAND_FORWARD_DIST = 20f;
    private static final float ORA_DURATION_TICKS = 210f; // Exact 3.50s (matches ora-rush.ogg)
    private static final float ORA_COOLDOWN_TICKS = 600f; // 10.0s cooldown
    private static final float COMBO_RECOVERY_TICKS = 48f; // 0.80s post-6th-hit recovery
    private static final float YARE_INVULN_TICKS = 120f; // 2.00s untargetable/passthrough
    private static final float ENTRANCE_ROAR_TICKS = 120f; // 2.00s untargetable Stand roar
    private static final float ENTRANCE_STUN_TICKS = 300f; // 5.00s stun on nearby enemies
    private static final float ENTRANCE_STUN_RADIUS = 128f; // 16 tiles
    private static final float TIME_STOP_DURATION = 720f; // 12.0s The World
    private static final float TIME_STOP_COOLDOWN = 1800f; // 30.0s cooldown
    private static final float TIME_STOP_RADIUS = 176f; // 22 tiles
    private static final float CLOCK_VISUAL_TICKS = 150f; // 2.50s visible expanding clock

    private static Effect punchArcEffect;
    private static Effect heavyPunchEffect;
    private static Effect dashAfterimageEffect;
    private static Effect sprintDustEffect;
    private static Effect oraFistEffect;
    private static Effect rockThrowMuzzle;
    private static Effect standAwakenEffect;
    private static Effect standWindEffect;
    private static Effect bulletDeflectEffect;
    private static Effect teleportBlinkEffect;

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

        // Stand punches originate from Star Platinum's forward position
        MeleeType standNormalPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 30f;
            arcAngle = 135f;
            damage = 115f;
            buildingDamageMultiplier = 1.05f;
            maxTargets = 6;
            hitGround = true;
            hitAir = false;
            knockback = 2.8f;
            lungeSpeed = 0f;
            hitStatus = punchSlow;
            hitStatusDuration = 18f;
            accentColor = STAND_CYAN;
            swingEffect = punchArcEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType heavyFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 34f;
            arcAngle = 145f;
            damage = 250f;
            buildingDamageMultiplier = 1.3f;
            pierceArmor = true;
            maxTargets = 7;
            hitGround = true;
            hitAir = false;
            knockback = 7.6f;
            lungeSpeed = 1.6f;
            shake = 2.0f;
            hitStatus = heavyStun;
            hitStatusDuration = 30f;
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeType standHeavyFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 34f;
            arcAngle = 155f;
            damage = 310f;
            buildingDamageMultiplier = 1.45f;
            pierceArmor = true;
            maxTargets = 8;
            hitGround = true;
            hitAir = false;
            knockback = 9.0f;
            lungeSpeed = 0f;
            shake = 2.5f;
            hitStatus = standHeavyStun;
            hitStatusDuration = 42f;
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        // ORA Rush stuns enemies until the 3.50s flurry finishes (~52 hits * 40 + 300 = ~2380 dmg)
        MeleeType oraFlurryPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 32f;
            arcAngle = 140f;
            damage = 40f;
            buildingDamageMultiplier = 1.05f;
            pierceArmor = true;
            maxTargets = 6;
            hitGround = true;
            hitAir = false;
            knockback = 0.6f;
            hitStatus = oraStun;
            hitStatusDuration = 14f; // Refreshed every 4 ticks so targets stay stunned until ORA ends
            accentColor = JOTARO_PURPLE;
            swingEffect = oraFistEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType oraFinalFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 36f;
            arcAngle = 160f;
            damage = 300f;
            buildingDamageMultiplier = 1.45f;
            pierceArmor = true;
            maxTargets = 8;
            hitGround = true;
            hitAir = false;
            knockback = 9.2f;
            lungeSpeed = 0f;
            shake = 2.6f;
            hitStatus = standHeavyStun;
            hitStatusDuration = 42f;
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeWeapon jotaroFist = new MeleeWeapon("", normalPunch){{
            x = 4.5f;
            y = 1.5f;
            shootX = 0f;
            shootY = 5f;
            reload = 22f; // 0.37s cadence
            mirror = true;
            alternate = true;
            rotate = false;
            recoil = 2.2f;
            shootCone = 45f;
            shootSound = Sounds.none;

            strikeHandler = (unit, mount, ox, oy, angle, weapon) -> {
                JotaroRuntime rt = getRuntime(unit);
                if(rt.entranceTimer > 0f || rt.oraActive || rt.comboRecoveryTimer > 0f){
                    return;
                }

                // 6-hit combo: Hits 1-5 normal, Hit 6 Heavy Punch
                rt.comboIdleTimer = 70f;
                rt.comboStep = (rt.comboStep + 1) % 6;

                boolean standOut = rt.standAwakened || rt.oraActive;
                float strikeX = ox;
                float strikeY = oy;
                if(standOut){
                    Vec2 spPos = getStandWorldPos(unit, rt, true);
                    strikeX = spPos.x;
                    strikeY = spPos.y;
                    rt.standPunchBob = 1f;
                }

                int hits;
                if(rt.comboStep == 0){
                    MeleeType finisher = standOut ? standHeavyFinisher : heavyFinisher;
                    hits = finisher.execute(unit, strikeX, strikeY, angle);
                    playSound(heavyPunchSound, strikeX, strikeY, 1f, 0.95f);
                    rt.comboRecoveryTimer = COMBO_RECOVERY_TICKS;
                }else{
                    MeleeType light = standOut ? standNormalPunch : normalPunch;
                    hits = light.execute(unit, strikeX, strikeY, angle);
                    playSound(punchSound, strikeX, strikeY, Mathf.random(0.95f, 1.06f), 0.8f);
                }

                if(hits > 0 && rt.oraCooldown <= 0f && !rt.oraActive){
                    rt.oraCharge = Math.min(6, rt.oraCharge + 1);
                }
            };
        }};

        jotaro = new UnitType("jotaro-kujo"){
            {
                localizedName = "Jotaro Kujo";
                description = "Melee Stand user. Summons Star Platinum temporarily for ORA Rush, awakens at <=85% HP with a 5s stun roar, holds standoff range while Star Platinum punches, deflects bullets, time-skip teleports, and stops time for 12s.";
                constructor = MechUnit::create;
                aiController = () -> createJotaroController();
                controller = u -> createJotaroController();

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

                hidden = false;
                allowedInPayloads = true;
                outlines = false;

                weapons.add(jotaroFist);
                abilities.addAll(
                    new LungeOrTeleportAbility(),
                    new CombatFlankDodgeAbility(),
                    new RockThrowAbility(),
                    new StandOraRushAbility(oraFlurryPunch, oraFinalFinisher),
                    new StarPlatinumAbility(),
                    new TheWorldAbility(),
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

                starPlatinumRegion = Core.atlas.find("ability-additions-star-platinum");
                if(!starPlatinumRegion.found()){
                    starPlatinumRegion = Core.atlas.find("star-platinum");
                }
            }

            @Override
            public boolean targetable(Unit unit, Team targeter){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && (rt.invulnTimer > 0f || rt.entranceTimer > 0f)) return false;
                return super.targetable(unit, targeter);
            }

            @Override
            public boolean hittable(Unit unit){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && (rt.invulnTimer > 0f || rt.entranceTimer > 0f)) return false;
                return super.hittable(unit);
            }

            @Override
            public boolean killable(Unit unit){
                JotaroRuntime rt = runtimes.get(unit.id);
                // ONLY unkillable during active invulnerability windows (never block taking damage down to 45%!)
                if(rt != null && (rt.invulnTimer > 0f || rt.entranceTimer > 0f)) return false;
                return super.killable(unit);
            }

            @Override
            public void update(Unit unit){
                super.update(unit);

                if(!(unit.controller() instanceof MeleeCommandAI) && !unit.isPlayer()){
                    UnitController prev = unit.controller();
                    MeleeCommandAI next = createJotaroController();
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

                if(rt.entranceTimer > 0f){
                    rt.entranceTimer = Math.max(0f, rt.entranceTimer - Time.delta);
                }
                if(unit.controller() instanceof MeleeCommandAI ai){
                    ai.standAwakened = rt.standAwakened;
                    ai.entrancePaused = rt.entranceTimer > 0f;
                }

                if(rt.invulnTimer > 0f){
                    rt.invulnTimer = Math.max(0f, rt.invulnTimer - Time.delta);
                    unit.dead = false;
                    if(unit.health < 1f) unit.health = 1f;
                }
                if(rt.standPunchBob > 0f){
                    rt.standPunchBob = Math.max(0f, rt.standPunchBob - Time.delta * 0.14f);
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
                if(rt == null) return;

                drawTimeStopClockVisual(unit, rt);
                drawStandManifestation(unit, rt);

                if(rt.invulnTimer > 0f || rt.entranceTimer > 0f){
                    float z = Draw.z();
                    Draw.z(Layer.effect);
                    Draw.color(rt.entranceTimer > 0f ? STAND_CYAN : JOTARO_GOLD, JOTARO_PURPLE, Mathf.absin(Time.time, 3f, 1f));
                    Lines.stroke(2.4f);
                    Lines.circle(unit.x, unit.y, unit.hitSize + 5f + Mathf.absin(Time.time, 2.5f, 1.5f));
                    Draw.color();
                    Draw.z(z);
                }
            }
        };
    }

    private static MeleeCommandAI createJotaroController(){
        MeleeCommandAI ai = new MeleeCommandAI();
        // Boosted dash gain so quieter dash.ogg cuts through combat cleanly
        ai.onDashAudio = u -> playSound(dashSound, u.x, u.y, Mathf.random(0.95f, 1.1f), 1.45f);
        ai.onTeleportBlink = (u, oldPos) -> {
            teleportBlinkEffect.at(oldPos.getX(), oldPos.getY(), u.rotation, STAND_CYAN);
            teleportBlinkEffect.at(u.x, u.y, u.rotation, JOTARO_PURPLE);
            playSound(teleportSound != Sounds.none ? teleportSound : dashSound, u.x, u.y, 1.2f, 1.45f);
        };
        return ai;
    }

    private static Vec2 getStandWorldPos(Unit unit, JotaroRuntime rt, boolean combatForward){
        float bob = Mathf.sin(Time.time, 7f, 1.4f);
        float forward = combatForward ? (STAND_FORWARD_DIST + rt.standPunchBob * 5f) : -6f;
        float side = combatForward ? 0f : 7f;
        float sx = unit.x + Angles.trnsx(unit.rotation, forward) + Angles.trnsx(unit.rotation - 90f, side);
        float sy = unit.y + Angles.trnsy(unit.rotation, forward) + Angles.trnsy(unit.rotation - 90f, side) + bob;
        return Tmp.v2.set(sx, sy);
    }

    private static void drawStandManifestation(Unit unit, JotaroRuntime rt){
        boolean showStand = rt.standAwakened || rt.oraActive;
        if(!showStand) return;

        boolean fighting = rt.oraActive || rt.standPunchBob > 0.01f
            || (unit.controller() instanceof MeleeCommandAI ai && ai.attackTarget != null);

        float appear = rt.standAwakened ? Mathf.clamp(rt.standEntranceAnim) : 1f;
        Vec2 spPos = getStandWorldPos(unit, rt, fighting);
        float sx = spPos.x;
        float sy = spPos.y;

        float z = Draw.z();
        Draw.z(Layer.groundUnit + 0.08f);

        Draw.color(JOTARO_PURPLE, STAND_CYAN, Mathf.absin(Time.time, 6f, 0.5f));
        Draw.alpha(0.32f * appear);
        Fill.circle(sx, sy, 11f * appear);

        if(starPlatinumRegion != null && starPlatinumRegion.found()){
            float scl = 0.72f + 0.28f * appear;
            float w = starPlatinumRegion.width * starPlatinumRegion.scl() * scl;
            float h = starPlatinumRegion.height * starPlatinumRegion.scl() * scl;
            Draw.color(Color.white, JOTARO_PURPLE, 0.18f);
            Draw.alpha(0.90f * appear);
            Draw.rect(starPlatinumRegion, sx, sy, w, h, unit.rotation - 90f);
        }else{
            Draw.color(JOTARO_PURPLE, STAND_CYAN, 0.4f);
            Draw.alpha(0.75f * appear);
            Drawf.tri(sx, sy, 13f * appear, 20f * appear, unit.rotation);
            Fill.circle(sx, sy, 7f * appear);
        }

        Draw.color();
        Draw.z(z);
    }

    private static void drawTimeStopClockVisual(Unit unit, JotaroRuntime rt){
        if(rt.clockVisualTimer <= 0f) return;

        float frac = 1f - (rt.clockVisualTimer / CLOCK_VISUAL_TICKS);
        float alpha = Mathf.clamp(1f - frac * frac);
        float radius = Mathf.lerp(18f, 132f, Interp.pow2Out.apply(frac));

        float z = Draw.z();
        Draw.z(Layer.flyingUnit + 0.5f); // Drawn above dome & ground so clearly visible

        Draw.color(JOTARO_GOLD, Color.white, 0.25f);
        Draw.alpha(alpha * 0.92f);
        Lines.stroke(4.2f * alpha + 0.8f);
        Lines.circle(rt.clockOriginX, rt.clockOriginY, radius);

        Draw.color(JOTARO_PURPLE, JOTARO_GOLD, 0.4f);
        Draw.alpha(alpha * 0.85f);
        Lines.stroke(2.6f * alpha + 0.6f);
        Lines.circle(rt.clockOriginX, rt.clockOriginY, radius * 0.82f);

        for(int i = 0; i < 12; i++){
            float a = i * 30f;
            float ix = rt.clockOriginX + Angles.trnsx(a, radius * 0.82f);
            float iy = rt.clockOriginY + Angles.trnsy(a, radius * 0.82f);
            Lines.lineAngle(ix, iy, a, radius * 0.17f);
        }

        float minuteAng = 90f - Time.time * 5.2f;
        float hourAng = 90f - Time.time * 0.9f;
        Draw.color(Color.white, JOTARO_GOLD, 0.5f);
        Draw.alpha(alpha * 0.95f);
        Lines.stroke(3.2f * alpha + 0.8f);
        Lines.lineAngle(rt.clockOriginX, rt.clockOriginY, minuteAng, radius * 0.72f);
        Lines.stroke(4.0f * alpha + 1.0f);
        Lines.lineAngle(rt.clockOriginX, rt.clockOriginY, hourAng, radius * 0.48f);

        Draw.color();
        Draw.z(z);
    }

    private static void triggerYareYare(Unit unit, JotaroRuntime rt){
        if(rt.resolveTriggeredBelow || rt.resolveCooldown > 0f) return;
        rt.resolveTriggeredBelow = true;
        rt.resolveCooldown = 1200f; // 20s cooldown
        rt.invulnTimer = YARE_INVULN_TICKS;

        unit.apply(resolveBuff, 12f);
        unit.unapply(StatusEffects.slow);
        unit.unapply(StatusEffects.freezing);
        unit.unapply(StatusEffects.tarred);
        unit.unapply(StatusEffects.burning);
        unit.unapply(StatusEffects.melting);
        unit.unapply(StatusEffects.shocked);
        unit.unapply(StatusEffects.sapped);

        Fx.healWave.at(unit.x, unit.y, JOTARO_GOLD);
        playSound(yareYareSound, unit.x, unit.y, 1f, 1.35f);
    }

    public static void init(){
        resolveModSounds();
        if(eventsHooked) return;
        eventsHooked = true;

        Events.run(Trigger.update, JotaroContent::updateTimeStopWorld);
        Events.run(Trigger.draw, JotaroContent::drawTimeStopDomes);

        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit == null) return;
            if(e.unit.type == jotaro){
                if(e.unit.controller() instanceof MeleeCommandAI ai){
                    ai.notifyThreatened();
                }
                if(e.unit.healthf() <= 0.45f){
                    JotaroRuntime rt = getRuntime(e.unit);
                    triggerYareYare(e.unit, rt);
                }
            }

            if(e.unit.hasEffect(timeStopFreeze) && e.bullet != null){
                for(int i = 0; i < timeStopZones.size; i++){
                    TimeStopZone zone = timeStopZones.get(i);
                    if(e.unit.team != zone.team && e.unit.within(zone.x, zone.y, zone.radius + 16f)){
                        zone.queuedDamage.put(e.unit.id, zone.queuedDamage.get(e.unit.id, 0f) + Math.max(25f, e.bullet.damage * 0.65f));
                        break;
                    }
                }
            }
        });

        Events.on(ResetEvent.class, e -> {
            runtimes.clear();
            timeStopZones.clear();
            frozenBullets.clear();
        });
    }

    private static void updateTimeStopWorld(){
        if(Vars.state.isPaused() || timeStopZones.size == 0){
            if(frozenBullets.size > 0 && timeStopZones.size == 0){
                releaseAllFrozenBullets();
            }
            return;
        }

        for(int i = timeStopZones.size - 1; i >= 0; i--){
            TimeStopZone zone = timeStopZones.get(i);
            zone.timer -= Time.delta;
            // Anchored in place where cast (does NOT follow Jotaro!)

            Units.nearbyEnemies(zone.team, zone.x, zone.y, zone.radius, u -> {
                if(u != null && !u.dead){
                    u.apply(timeStopFreeze, 12f);
                    u.vel.setZero();
                }
            });

            for(Bullet b : Groups.bullet){
                if(b == null || !b.isAdded() || b.team == zone.team) continue;
                if(b.within(zone.x, zone.y, zone.radius)){
                    FrozenBullet fb = frozenBullets.get(b.id);
                    if(fb == null){
                        fb = new FrozenBullet();
                        fb.bullet = b;
                        fb.savedVel.set(b.vel);
                        fb.savedTime = b.time;
                        frozenBullets.put(b.id, fb);
                    }
                    b.vel.setZero();
                    b.time = fb.savedTime;
                }
            }

            if(zone.timer <= 0f){
                IntFloatMap.Entries entries = zone.queuedDamage.entries();
                while(entries.hasNext()){
                    IntFloatMap.Entry entry = entries.next();
                    Unit tgt = Groups.unit.getByID(entry.key);
                    if(tgt != null && !tgt.dead){
                        tgt.unapply(timeStopFreeze);
                        tgt.damagePierce(entry.value);
                        float pushAng = Angles.angle(zone.x, zone.y, tgt.x, tgt.y);
                        tgt.impulse(Angles.trnsx(pushAng, 160f), Angles.trnsy(pushAng, 160f));
                        heavyPunchEffect.at(tgt.x, tgt.y, pushAng, JOTARO_GOLD);
                    }
                }
                Fx.shockwave.at(zone.x, zone.y, JOTARO_PURPLE);
                timeStopZones.remove(i);
            }
        }

        if(timeStopZones.size == 0){
            releaseAllFrozenBullets();
        }
    }

    private static void releaseAllFrozenBullets(){
        for(IntMap.Entry<FrozenBullet> entry : frozenBullets){
            FrozenBullet fb = entry.value;
            if(fb.bullet != null && fb.bullet.isAdded()){
                fb.bullet.vel.set(fb.savedVel);
            }
        }
        frozenBullets.clear();
    }

    private static void drawTimeStopDomes(){
        if(Vars.headless || timeStopZones.size == 0) return;

        float oldZ = Draw.z();
        Draw.z(Layer.effect - 0.1f);
        for(int i = 0; i < timeStopZones.size; i++){
            TimeStopZone zone = timeStopZones.get(i);
            float expand = Mathf.clamp((TIME_STOP_DURATION - zone.timer) / 14f);
            float r = zone.radius * Interp.pow2Out.apply(expand);

            Draw.color(JOTARO_PURPLE);
            Draw.alpha(0.14f);
            Fill.circle(zone.x, zone.y, r);

            Draw.color(JOTARO_GOLD, JOTARO_PURPLE, Mathf.absin(Time.time, 5f, 1f));
            Draw.alpha(0.75f);
            Lines.stroke(2.5f);
            Lines.circle(zone.x, zone.y, r);
        }
        Draw.color();
        Draw.z(oldZ);
    }

    private static void resolveModSounds(){
        if(soundsLoaded || Vars.headless) return;
        soundsLoaded = true;

        punchSound = loadSoundSync("punch", true);
        heavyPunchSound = loadSoundSync("heavy-punch", true);
        oraRushSound = loadSoundSync("ora-rush", true);
        dashSound = loadSoundSync("dash", true);
        yareYareSound = loadSoundSync("yare-yare", true);

        standSummonSound = loadSoundSync("stand-summon", false);
        teleportSound = loadSoundSync("teleport", false);
        timeStopSound = loadSoundSync("time-stop", false);
    }

    private static Sound loadSoundSync(String baseName, boolean warnIfMissing){
        try{
            String[] assetKeys = {
                baseName + ".ogg",
                baseName + ".mp3",
                "sounds/" + baseName + ".ogg",
                "sounds/" + baseName + ".mp3"
            };
            for(String key : assetKeys){
                Sound existing = Core.assets.getOrNull(key, Sound.class);
                if(existing != null){
                    Log.info("[AbilityAdditions] Sound cached: @", key);
                    return existing;
                }
            }

            Fi targetFi = null;
            if(Vars.tree.get("sounds/" + baseName + ".ogg").exists()){
                targetFi = Vars.tree.get("sounds/" + baseName + ".ogg");
            }else if(Vars.tree.get("sounds/" + baseName + ".mp3").exists()){
                targetFi = Vars.tree.get("sounds/" + baseName + ".mp3");
            }else{
                Mods.LoadedMod mod = Vars.mods.locateMod("ability-additions");
                if(mod != null && mod.root != null){
                    String[] rels = {
                        "sounds/" + baseName + ".ogg",
                        "sounds/" + baseName + ".mp3",
                        "assets/sounds/" + baseName + ".ogg",
                        "assets/sounds/" + baseName + ".mp3"
                    };
                    for(String rel : rels){
                        Fi fi = mod.root.child(rel);
                        if(fi.exists()){
                            targetFi = fi;
                            break;
                        }
                    }
                }
            }

            if(targetFi != null && targetFi.exists()){
                Sound s = new Sound(targetFi);
                Log.info("[AbilityAdditions] Sound decoded OK: @", targetFi.path());
                return s;
            }

            if(warnIfMissing){
                Log.warn("[AbilityAdditions] Missing sound: @ (.ogg/.mp3)", baseName);
            }
            return Sounds.none;
        }catch(Throwable t){
            Log.err("[AbilityAdditions] Sound DECODE FAILED for " + baseName, t);
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
            speedMultiplier = 0.40f;
            reloadMultiplier = 0.75f;
            show = false;
        }};

        oraStun = new StatusEffect("jotaro-ora-stun"){{
            disarm = true;
            speedMultiplier = 0.05f;
            reloadMultiplier = 0.05f;
            show = false;
        }};

        entranceStun = new StatusEffect("jotaro-entrance-stun"){{
            disarm = true;
            speedMultiplier = 0f;
            reloadMultiplier = 0f;
            color = STAND_CYAN;
            show = true;
        }};

        heavyStun = new StatusEffect("jotaro-heavy-stun"){{
            disarm = true;
            speedMultiplier = 0.12f;
            reloadMultiplier = 0.15f;
            show = false;
        }};

        standHeavyStun = new StatusEffect("jotaro-stand-heavy-stun"){{
            disarm = true;
            speedMultiplier = 0.05f;
            reloadMultiplier = 0.08f;
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

        timeStopFreeze = new StatusEffect("jotaro-timestop-freeze"){{
            disarm = true;
            speedMultiplier = 0f;
            reloadMultiplier = 0f;
            dragMultiplier = 10f;
            color = JOTARO_PURPLE;
            show = true;
        }};

        punchArcEffect = new Effect(16f, e -> {
            float fx = e.x + Angles.trnsx(e.rotation, 8f + e.fin() * 10f);
            float fy = e.y + Angles.trnsy(e.rotation, 8f + e.fin() * 10f);

            Draw.color(e.color);
            Lines.stroke(4.2f * e.fout());
            Lines.arc(e.x, e.y, 14f + e.fin() * 13f, 0.36f, e.rotation - 64f);

            Draw.color(Color.white);
            Lines.stroke(1.8f * e.fout());
            Lines.arc(e.x, e.y, 16f + e.fin() * 13f, 0.28f, e.rotation - 50f);

            Draw.color(e.color, Color.white, 0.45f);
            Drawf.tri(fx, fy, 6.5f * e.fout(), 14f, e.rotation);

            for(int i = -1; i <= 1; i++){
                float sideAng = e.rotation + i * 20f;
                float sx = e.x + Angles.trnsx(sideAng, 9f);
                float sy = e.y + Angles.trnsy(sideAng, 9f);
                Lines.stroke(1.6f * e.fout());
                Lines.lineAngle(sx, sy, sideAng, 8f + e.fin() * 8f);
            }
        });

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

        dashAfterimageEffect = new Effect(18f, e -> {
            Draw.color(JOTARO_PURPLE, Color.white, 0.3f);
            Draw.alpha(0.55f * e.fout());
            Lines.stroke(2.2f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin() * 8f);
            Drawf.tri(e.x, e.y, 6f * e.fout(), 14f, e.rotation);
        }).layer(Layer.groundUnit - 0.1f);

        sprintDustEffect = new Effect(16f, e -> {
            Draw.color(Color.lightGray);
            Draw.alpha(0.38f * e.fout());
            Fill.circle(e.x, e.y, 1.8f * e.fout() + 0.6f);
        }).layer(Layer.debris);

        oraFistEffect = new Effect(12f, e -> {
            float off = Mathf.range(9f);
            float spread = Mathf.range(16f);
            float ang = e.rotation + spread;
            float fx = e.x + Angles.trnsx(e.rotation + 90f, off) + Angles.trnsx(ang, 8f + e.fin() * 20f);
            float fy = e.y + Angles.trnsy(e.rotation + 90f, off) + Angles.trnsy(ang, 8f + e.fin() * 20f);

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

        standAwakenEffect = new Effect(46f, e -> {
            Draw.color(JOTARO_PURPLE, STAND_CYAN, e.fin());
            Lines.stroke(4.8f * e.fout());
            Lines.circle(e.x, e.y, 10f + e.fin() * 118f);
            Draw.color(JOTARO_GOLD, Color.white, 0.35f);
            Lines.stroke(2.8f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin() * 64f);

            for(int i = 0; i < 12; i++){
                float a = i * 30f + e.fin() * 95f;
                float wx = e.x + Angles.trnsx(a, 12f + e.fin() * 92f);
                float wy = e.y + Angles.trnsy(a, 12f + e.fin() * 92f);
                Lines.lineAngle(wx, wy, a + 45f, 14f * e.fout());
            }
        });

        standWindEffect = new Effect(24f, e -> {
            float a = e.rotation + e.fin() * 70f;
            float r = 10f + e.fin() * 26f;
            float wx = e.x + Angles.trnsx(a, r);
            float wy = e.y + Angles.trnsy(a, r);
            Draw.color(STAND_CYAN, JOTARO_PURPLE, e.fin());
            Draw.alpha(0.58f * e.fout());
            Lines.stroke(1.9f * e.fout());
            Lines.lineAngle(wx, wy, a + 75f, 10f * e.fout());
        }).layer(Layer.groundUnit - 0.06f);

        bulletDeflectEffect = new Effect(16f, e -> {
            Draw.color(JOTARO_GOLD, STAND_CYAN, 0.5f);
            Lines.stroke(2.5f * e.fout());
            Lines.arc(e.x, e.y, 6f + e.fin() * 10f, 0.45f, e.rotation - 80f);
            Draw.color(Color.white);
            Fill.circle(e.x, e.y, 2.2f * e.fout());
        });

        teleportBlinkEffect = new Effect(20f, e -> {
            Draw.color(e.color, Color.white, 0.35f);
            Lines.stroke(2.4f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin() * 14f);
            for(int i = 0; i < 4; i++){
                float a = i * 90f + e.rotation;
                Lines.lineAngle(e.x, e.y, a, 12f * e.fout());
            }
        });
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

    // Ability 1A: Lunge Dash (Base) -> Time-Skip Teleport (Stand Awakened) + Sprint
    public static class LungeOrTeleportAbility extends Ability{
        @Override
        public String localized(){
            return "Lunge / Time-Skip Teleport";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Sprints toward far enemies and lunges across gaps (6s CD). With Star Platinum awakened, lunges become instant DIO-style Time-Skip Teleports.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;
            bars.add(new Bar(
                () -> (ai.standAwakened ? "Time-Skip: " : "Lunge: ") + (ai.lungeCooldown <= 0f ? "Ready" : Strings.fixed(ai.lungeCooldown / 60f, 1) + "s"),
                () -> ai.lungeCooldown <= 0f ? (ai.standAwakened ? STAND_CYAN : JOTARO_GOLD) : Color.gray,
                () -> 1f - Mathf.clamp(ai.lungeCooldown / ai.lungeCooldownDuration)
            )).row();
        }

        @Override
        public void update(Unit unit){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;

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

    // Ability 1B: Combat Flank Dodge Dash (3s CD, wraps behind nearby enemies scaled by target size)
    public static class CombatFlankDodgeAbility extends Ability{
        @Override
        public String localized(){
            return "Combat Flank Dodge & Weave";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Weaves around bullets while advancing and dashes behind nearby combat enemies (3s CD), scaling dash speed with target size.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            if(!(unit.controller() instanceof MeleeCommandAI ai)) return;
            bars.add(new Bar(
                () -> ai.dodgeCooldown <= 0f ? "Flank Dodge Ready" : "Flank Dodge: " + Strings.fixed(ai.dodgeCooldown / 60f, 1) + "s",
                () -> ai.dodgeCooldown <= 0f ? JOTARO_PURPLE : Color.gray,
                () -> 1f - Mathf.clamp(ai.dodgeCooldown / ai.dodgeCooldownDuration)
            )).row();
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
            if(rt.entranceTimer > 0f) return;

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

    // Ability 3: Stand ORA ORA Rush (stuns enemies until 3.50s finishes!)
    public static class StandOraRushAbility extends Ability{
        private final MeleeType flurryType;
        private final MeleeType finisherType;

        public StandOraRushAbility(MeleeType flurryType, MeleeType finisherType){
            this.flurryType = flurryType;
            this.finisherType = finisherType;
        }

        @Override
        public String localized(){
            return "Star Platinum: ORA ORA Rush";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]6 confirmed M1 hits unleash a 3.5s Stand ORA flurry that stuns targets until finished, ending in a 300-damage Heavy Finisher (10s CD).[]").wrap().width(320f);
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
            if(rt.entranceTimer > 0f) return;

            if(rt.oraCooldown > 0f){
                rt.oraCooldown = Math.max(0f, rt.oraCooldown - Time.delta);
            }

            if(!rt.oraActive){
                if(rt.oraCooldown <= 0f && rt.oraCharge >= 6){
                    Unit nearby = Units.closestEnemy(unit.team, unit.x, unit.y, 58f, u -> !u.dead && !u.isFlying() && u.hittable());
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
                rt.oraTickTimer = 4f; // ~52 hits across 3.50s
                Vec2 spPos = getStandWorldPos(unit, rt, true);
                rt.standPunchBob = 0.8f;
                flurryType.execute(unit, spPos.x, spPos.y, unit.rotation);
            }

            if(rt.oraTimer <= 0f){
                rt.oraActive = false;
                rt.oraCooldown = ORA_COOLDOWN_TICKS;
                Vec2 spPos = getStandWorldPos(unit, rt, true);
                rt.standPunchBob = 1f;
                finisherType.execute(unit, spPos.x, spPos.y, unit.rotation);
                playSound(heavyPunchSound, spPos.x, spPos.y, 0.95f, 0.95f);
            }
        }
    }

    // Ability 4: Star Platinum Awakening (2s untargetable roar + 5s enemy stun) & Bullet Deflect
    public static class StarPlatinumAbility extends Ability{
        @Override
        public String localized(){
            return "Star Platinum (Stand Awakening)";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]At <=85% HP, pauses untargetable for 2s to roar and stun nearby enemies for 5s. Punches ahead while Jotaro keeps standoff distance and deflects ~88% of incoming projectiles back as friendly bullets.[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);

            if(!rt.standAwakened && unit.healthf() <= 0.85f){
                rt.standAwakened = true;
                rt.standEntranceAnim = 0f;
                rt.entranceTimer = ENTRANCE_ROAR_TICKS;

                standAwakenEffect.at(unit.x, unit.y, unit.rotation);
                Effect.shake(6.5f, 30f, unit);
                playSound(standSummonSound, unit.x, unit.y, 1f, 1.25f);

                // Roar stuns all nearby enemies for 5.0 seconds
                Units.nearbyEnemies(unit.team, unit.x, unit.y, ENTRANCE_STUN_RADIUS, enemy -> {
                    if(enemy != null && !enemy.dead){
                        enemy.apply(entranceStun, ENTRANCE_STUN_TICKS);
                    }
                });
            }

            if(!rt.standAwakened) return;

            if(rt.standEntranceAnim < 1f){
                rt.standEntranceAnim = Math.min(1f, rt.standEntranceAnim + Time.delta / 28f);
            }

            if(Mathf.chanceDelta(rt.entranceTimer > 0f ? 0.65f : 0.28f)){
                standWindEffect.at(unit.x, unit.y, Mathf.random(360f));
            }

            // Passive Bullet Deflect: flip enemy projectiles onto Jotaro's team and reflect back!
            if(rt.bulletCatchGrace > 0f){
                rt.bulletCatchGrace = Math.max(0f, rt.bulletCatchGrace - Time.delta);
                return;
            }

            for(Bullet b : Groups.bullet){
                if(b == null || !b.isAdded() || b.team == unit.team || !b.type.hittable) continue;
                if(b.type instanceof ContinuousBulletType || b.type instanceof LaserBulletType || b.type instanceof PointLaserBulletType) continue;
                if(b.within(unit, 30f)){
                    if(Mathf.chance(0.88f)){
                        Entityc shooter = b.owner;
                        b.team = unit.team;
                        b.owner = unit;
                        b.time = 0f;

                        float reflectAng = b.vel.angle() + 180f;
                        if(shooter instanceof Position p && !(shooter instanceof Healthc h && h.dead())){
                            reflectAng = Angles.angle(b.x, b.y, p.getX(), p.getY());
                        }else{
                            Unit nearest = Units.closestEnemy(unit.team, b.x, b.y, 200f, u -> !u.dead && u.hittable());
                            if(nearest != null){
                                reflectAng = Angles.angle(b.x, b.y, nearest.x, nearest.y);
                            }
                        }
                        b.vel.setAngle(reflectAng);
                        bulletDeflectEffect.at(b.x, b.y, reflectAng);
                        rt.bulletCatchGrace = 2f;
                    }else{
                        rt.bulletCatchGrace = 8f;
                    }
                    break;
                }
            }
        }
    }

    // Ability 5: Star Platinum: The World! (12.0s anchored Time Stop + 2.5s visible expanding clock)
    public static class TheWorldAbility extends Ability{
        @Override
        public String localized(){
            return "Star Platinum: The World";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Once awakened, stops time for 12.0s in an anchored 22-tile dome (30s CD), freezing enemy units and midair bullets and releasing stacked damage when time resumes.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            JotaroRuntime rt = getRuntime(unit);
            if(!rt.standAwakened) return;
            bars.add(new Bar(
                () -> rt.timeStopActiveTimer > 0f ? "THE WORLD! " + Strings.fixed(rt.timeStopActiveTimer / 60f, 1) + "s"
                    : rt.timeStopCooldown > 0f ? "Time Stop: " + Strings.fixed(rt.timeStopCooldown / 60f, 1) + "s"
                    : "The World Ready",
                () -> rt.timeStopActiveTimer > 0f ? STAND_CYAN : (rt.timeStopCooldown > 0f ? Color.gray : JOTARO_GOLD),
                () -> rt.timeStopActiveTimer > 0f ? Mathf.clamp(rt.timeStopActiveTimer / TIME_STOP_DURATION)
                    : 1f - Mathf.clamp(rt.timeStopCooldown / TIME_STOP_COOLDOWN)
            )).row();
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);

            if(rt.clockVisualTimer > 0f){
                rt.clockVisualTimer = Math.max(0f, rt.clockVisualTimer - Time.delta);
            }
            if(rt.timeStopActiveTimer > 0f){
                rt.timeStopActiveTimer = Math.max(0f, rt.timeStopActiveTimer - Time.delta);
            }else if(rt.timeStopCooldown > 0f){
                // 30s cooldown counts down AFTER the 12s Time Stop finishes
                rt.timeStopCooldown = Math.max(0f, rt.timeStopCooldown - Time.delta);
            }

            if(!rt.standAwakened || rt.entranceTimer > 0f || rt.timeStopCooldown > 0f || rt.timeStopActiveTimer > 0f) return;

            Unit threat = Units.closestEnemy(unit.team, unit.x, unit.y, 128f, u -> !u.dead && !u.isFlying() && u.hittable());
            if(threat == null) return;

            rt.timeStopCooldown = TIME_STOP_COOLDOWN;
            rt.timeStopActiveTimer = TIME_STOP_DURATION;
            rt.clockVisualTimer = CLOCK_VISUAL_TICKS;
            rt.clockOriginX = unit.x;
            rt.clockOriginY = unit.y;

            TimeStopZone zone = new TimeStopZone();
            zone.owner = unit;
            zone.team = unit.team;
            zone.x = unit.x;
            zone.y = unit.y;
            zone.radius = TIME_STOP_RADIUS;
            zone.timer = TIME_STOP_DURATION;
            timeStopZones.add(zone);

            Effect.shake(4.0f, 16f, unit);
            playSound(timeStopSound, unit.x, unit.y, 1f, 1.25f);
        }
    }

    // Ability 6: Yare Yare Daze (Permanent Resolve <= 45% HP + 2s untargetable/passthrough burst)
    public static class YareResolveAbility extends Ability{
        @Override
        public String localized(){
            return "Yare Yare Daze";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Dropping to <=45% HP triggers 2s of untargetable bullet-passthrough invulnerability (20s CD) and permanent Resolve until healed above 45% HP.[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);
            if(rt.resolveCooldown > 0f){
                rt.resolveCooldown = Math.max(0f, rt.resolveCooldown - Time.delta);
            }

            if(unit.healthf() <= 0.45f){
                unit.apply(resolveBuff, 12f);
                triggerYareYare(unit, rt);
            }else{
                rt.resolveTriggeredBelow = false;
                if(unit.hasEffect(resolveBuff)){
                    unit.unapply(resolveBuff);
                }
            }
        }
    }

    private static class TimeStopZone{
        Unit owner;
        Team team;
        float x, y, radius, timer;
        final IntFloatMap queuedDamage = new IntFloatMap();
    }

    private static class FrozenBullet{
        Bullet bullet;
        final Vec2 savedVel = new Vec2();
        float savedTime;
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

        boolean standAwakened = false;
        float standEntranceAnim = 0f;
        float entranceTimer = 0f;
        float standPunchBob = 0f;
        float bulletCatchGrace = 0f;

        float timeStopCooldown = 0f;
        float timeStopActiveTimer = 0f;
        float clockVisualTimer = 0f;
        float clockOriginX = 0f;
        float clockOriginY = 0f;

        float resolveCooldown = 0f;
        float invulnTimer = 0f;
        boolean resolveTriggeredBelow = false;
    }
}
