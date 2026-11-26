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
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

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
    public static StatusEffect cutsceneLock;

    public static TextureRegion starPlatinumRegion;

    public static Sound punchSound = Sounds.none;
    public static Sound heavyPunchSound = Sounds.none;
    public static Sound oraRushSound = Sounds.none;
    public static Sound oraRush2Sound = Sounds.none;
    public static Sound oraFinisherSound = Sounds.none;
    public static Sound standAppearSound = Sounds.none;
    public static Sound standVanishSound = Sounds.none;
    public static Sound dashSound = Sounds.none;
    public static Sound yareYareSound = Sounds.none;
    public static Sound standSummonSound = Sounds.none;
    public static Sound teleportSound = Sounds.none;
    public static Sound timeStopSound = Sounds.none;
    public static Sound inhaleSound = Sounds.none;
    public static Sound ultimateSound = Sounds.none;

    private static final IntMap<JotaroRuntime> runtimes = new IntMap<>();
    private static final Seq<TimeStopZone> timeStopZones = new Seq<>();
    private static final IntMap<FrozenBullet> frozenBullets = new IntMap<>();
    private static final Seq<CutsceneDuel> activeCutscenes = new Seq<>();
    private static boolean eventsHooked = false;
    private static boolean soundsLoaded = false;

    private static final Color JOTARO_PURPLE = Color.valueOf("9c6bff");
    private static final Color JOTARO_GOLD = Color.valueOf("ffcf5c");
    private static final Color STAND_CYAN = Color.valueOf("7cf7ff");
    private static final Color JJBA_MAGENTA = Color.valueOf("e066ff");

    private static final float STAND_FORWARD_DIST = 22f;
    private static final float ORA_DURATION_TICKS = 210f; // 3.50s
    private static final float ORA_COOLDOWN_TICKS = 600f; // 10.0s
    private static final float ORA_VANISH_DELAY_TICKS = 21f; // 0.35s after finisher punch
    private static final float COMBO_RECOVERY_TICKS = 48f; // 0.80s
    private static final float YARE_INVULN_TICKS = 120f; // 2.00s

    private static final float ENTRANCE_ROAR_TICKS = 300f; // 5.00s roar
    private static final float ENTRANCE_STUN_TICKS = 300f; // 5.00s stun
    private static final float ENTRANCE_STUN_RADIUS = 128f; // 16 tiles

    private static final float INHALE_HOLD_TICKS = 732f; // 12.2s full suction
    private static final float INHALE_TOTAL_TICKS = 936f; // 15.6s total (12.2->15.6 ramp down)
    private static final float INHALE_COOLDOWN_TICKS = 720f; // 12.0s
    private static final float INHALE_RANGE = 240f; // 30 tiles wide cone

    private static final float TIME_STOP_DURATION = 720f; // 12.0s freeze
    private static final float TIME_STOP_INTRO_EXPAND = 180f; // 0->3s expand
    private static final float TIME_STOP_INTRO_SHRINK = 240f; // 3->4s sudden collapse
    private static final float TIME_STOP_COOLDOWN = 1800f; // 30.0s
    private static final float TIME_STOP_RADIUS = 176f; // 22 tiles
    private static final int TIME_STOP_HITS_REQUIRED = 50;

    private static final int ULTIMATE_M1_REQUIREMENT = 400;

    private static Effect punchArcEffect;
    private static Effect heavyPunchEffect;
    private static Effect dashAfterimageEffect;
    private static Effect sprintDustEffect;
    private static Effect oraFistEffect;
    private static Effect hardOraFistEffect;
    private static Effect downwashSmokeEffect;
    private static Effect downwashStreakEffect;
    private static Effect roarRingEffect;
    private static Effect bulletDeflectEffect;
    private static Effect teleportClockEffect;
    private static Effect inhaleWindStreakEffect;
    private static Effect swallowPopEffect;
    private static Effect chargeFlareEffect;
    private static Effect speedLineEffect;

    public static void load(){
        loadEffectsAndStatuses();

        MeleeType normalPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 28f;
            arcAngle = 120f;
            damage = 190f;
            buildingDamageMultiplier = 1.0f;
            maxTargets = 5;
            hitGround = true;
            hitAir = false;
            knockback = 2.6f;
            lungeSpeed = 0.65f;
            hitStatus = punchSlow;
            hitStatusDuration = 14f;
            accentColor = JOTARO_PURPLE;
            swingEffect = punchArcEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType standNormalPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 32f;
            arcAngle = 135f;
            damage = 280f;
            buildingDamageMultiplier = 1.15f;
            maxTargets = 6;
            hitGround = true;
            hitAir = false;
            knockback = 3.2f;
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
            damage = 540f;
            buildingDamageMultiplier = 1.35f;
            pierceArmor = true;
            maxTargets = 7;
            hitGround = true;
            hitAir = false;
            knockback = 8.0f;
            lungeSpeed = 1.6f;
            shake = 2.2f;
            hitStatus = heavyStun;
            hitStatusDuration = 30f;
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeType standHeavyFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 36f;
            arcAngle = 155f;
            damage = 750f;
            buildingDamageMultiplier = 1.5f;
            pierceArmor = true;
            maxTargets = 8;
            hitGround = true;
            hitAir = false;
            knockback = 9.5f;
            lungeSpeed = 0f;
            shake = 2.8f;
            hitStatus = standHeavyStun;
            hitStatusDuration = 42f;
            accentColor = JOTARO_GOLD;
            swingEffect = heavyPunchEffect;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeType oraFlurryPunch = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 34f;
            arcAngle = 140f;
            damage = 110f;
            buildingDamageMultiplier = 1.15f;
            pierceArmor = true;
            maxTargets = 6;
            hitGround = true;
            hitAir = false;
            knockback = 0.65f;
            hitStatus = oraStun;
            hitStatusDuration = 14f;
            accentColor = JOTARO_PURPLE;
            swingEffect = oraFistEffect;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType oraFinalFinisher = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 38f;
            arcAngle = 160f;
            damage = 850f;
            buildingDamageMultiplier = 1.5f;
            pierceArmor = true;
            maxTargets = 8;
            hitGround = true;
            hitAir = false;
            knockback = 9.8f;
            lungeSpeed = 0f;
            shake = 3.0f;
            hitStatus = standHeavyStun;
            hitStatusDuration = 48f;
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
                if(rt.entranceTimer > 0f || rt.cutsceneActive || rt.oraActive || rt.comboRecoveryTimer > 0f){
                    return;
                }

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

                if(hits > 0){
                    registerCombatHit(rt, false);
                }
            };
        }};

        jotaro = new UnitType("jotaro-kujo"){
            {
                localizedName = "Jotaro Kujo";
                description = "Tier-4 melee Stand powerhouse. Uses Vacuum Inhale against flyers & bullets, awakens Star Platinum at <=85% HP with a 5s roar, deflects bullets, time-skip teleports, stops time for 12s, and unlocks a 25s 400-hit Beatdown Ultimate.";
                constructor = MechUnit::create;
                aiController = () -> createJotaroController();
                controller = u -> createJotaroController();

                flying = false;
                speed = 0.98f;
                rotateSpeed = 6.5f;
                baseRotateSpeed = 6.5f;
                hitSize = 11f;
                health = 18500f;
                armor = 36f;
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
                    new VacuumInhaleAbility(),
                    new StandOraRushAbility(oraFlurryPunch, oraFinalFinisher),
                    new StarPlatinumAbility(),
                    new TheWorldAbility(),
                    new UltimateBeatdownAbility(),
                    new YareResolveAbility()
                );
            }

            @Override
            public void init(){
                super.init();
                for(Weapon w : weapons){
                    if(w.bullet != null){
                        w.bullet.rangeOverride = 58f;
                    }
                }
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
                if(rt != null && (rt.invulnTimer > 0f || rt.entranceTimer > 0f || rt.cutsceneActive)) return false;
                return super.targetable(unit, targeter);
            }

            @Override
            public boolean hittable(Unit unit){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && (rt.invulnTimer > 0f || rt.entranceTimer > 0f || rt.cutsceneActive)) return false;
                return super.hittable(unit);
            }

            @Override
            public boolean killable(Unit unit){
                JotaroRuntime rt = runtimes.get(unit.id);
                if(rt != null && (rt.invulnTimer > 0f || rt.entranceTimer > 0f || rt.cutsceneActive)) return false;
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
                    ai.standActiveOut = rt.standAwakened || rt.oraActive || rt.oraVanishDelay > 0f || rt.inhaleActive;
                    ai.entrancePaused = rt.entranceTimer > 0f || rt.cutsceneActive;
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

                drawTimeStopClockVisual(rt);
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

    private static void registerCombatHit(JotaroRuntime rt, boolean fromBarrage){
        if(rt.oraCooldown <= 0f && !rt.oraActive){
            rt.oraCharge = Math.min(6, rt.oraCharge + 1);
        }
        if(rt.standAwakened && rt.timeStopCooldown <= 0f && rt.timeStopActiveTimer <= 0f){
            rt.timeStopHits = Math.min(TIME_STOP_HITS_REQUIRED, rt.timeStopHits + 1);
        }
        if(!rt.ultimateReady && !rt.cutsceneActive){
            if(fromBarrage){
                rt.barrageUltTick++;
                if(rt.barrageUltTick >= 3){
                    rt.barrageUltTick = 0;
                    rt.ultimateM1Count = Math.min(ULTIMATE_M1_REQUIREMENT, rt.ultimateM1Count + 1);
                }
            }else{
                rt.ultimateM1Count = Math.min(ULTIMATE_M1_REQUIREMENT, rt.ultimateM1Count + 1);
            }
            if(rt.ultimateM1Count >= ULTIMATE_M1_REQUIREMENT){
                rt.ultimateReady = true;
            }
        }
    }

    private static MeleeCommandAI createJotaroController(){
        MeleeCommandAI ai = new MeleeCommandAI();
        ai.onDashAudio = u -> playSound(dashSound, u.x, u.y, Mathf.random(0.95f, 1.1f), 1.45f);
        ai.onTeleportBlink = (u, oldPos) -> {
            teleportClockEffect.at(oldPos.getX(), oldPos.getY(), u.rotation, JOTARO_PURPLE);
            teleportClockEffect.at(u.x, u.y, u.rotation, JOTARO_GOLD);
            playSound(teleportSound != Sounds.none ? teleportSound : dashSound, u.x, u.y, 1.15f, 1.45f);
        };
        return ai;
    }

    private static Vec2 getStandWorldPos(Unit unit, JotaroRuntime rt, boolean combatForward){
        if(rt.cutsceneActive && rt.cutsceneShowStand){
            float bob = Mathf.sin(Time.time, 7f, 1.1f);
            float push = rt.standPunchBob * 4f;
            return Tmp.v2.set(
                rt.cutsceneStandX + Angles.trnsx(rt.cutsceneStandRot, push),
                rt.cutsceneStandY + Angles.trnsy(rt.cutsceneStandRot, push) + bob
            );
        }
        float bob = Mathf.sin(Time.time, 7f, 1.4f);
        float roarJitter = rt.entranceTimer > 0f ? Mathf.range(0.6f) : 0f;
        float forward = combatForward ? (STAND_FORWARD_DIST + rt.standPunchBob * 5f) : -6f;
        float side = combatForward ? 0f : 7f;
        float sx = unit.x + Angles.trnsx(unit.rotation, forward) + Angles.trnsx(unit.rotation - 90f, side) + roarJitter;
        float sy = unit.y + Angles.trnsy(unit.rotation, forward) + Angles.trnsy(unit.rotation - 90f, side) + bob + roarJitter;
        return Tmp.v2.set(sx, sy);
    }

    private static void drawStandManifestation(Unit unit, JotaroRuntime rt){
        boolean showStand = rt.standAwakened || rt.oraActive || rt.oraVanishDelay > 0f || rt.inhaleActive || rt.cutsceneShowStand;
        if(!showStand) return;

        boolean fighting = rt.oraActive || rt.oraVanishDelay > 0f || rt.inhaleActive || rt.cutsceneShowStand || rt.standPunchBob > 0.01f
            || (unit.controller() instanceof MeleeCommandAI ai && ai.attackTarget != null);

        float appear = rt.cutsceneShowStand ? 1f : (rt.standAwakened ? Mathf.clamp(rt.standEntranceAnim) : 1f);
        Vec2 spPos = getStandWorldPos(unit, rt, fighting);
        float sx = spPos.x;
        float sy = spPos.y;
        float drawRot = rt.cutsceneShowStand ? rt.cutsceneStandRot : (rt.inhaleActive ? rt.inhaleAngle : unit.rotation);

        float z = Draw.z();
        Draw.z(Layer.groundUnit + 0.08f);

        Draw.color(JOTARO_PURPLE, STAND_CYAN, Mathf.absin(Time.time, 8f, 0.45f));
        Draw.alpha((rt.entranceTimer > 0f ? 0.45f : 0.28f) * appear);
        Fill.circle(sx, sy, (rt.entranceTimer > 0f ? 15f : 11f) * appear);

        if(starPlatinumRegion != null && starPlatinumRegion.found()){
            float roarPulse = rt.entranceTimer > 0f ? (1f + Mathf.absin(Time.time, 4f, 0.08f)) : 1f;
            float scl = (0.72f + 0.28f * appear) * roarPulse;
            float w = starPlatinumRegion.width * starPlatinumRegion.scl() * scl;
            float h = starPlatinumRegion.height * starPlatinumRegion.scl() * scl;
            Draw.color(Color.white, JOTARO_PURPLE, 0.18f);
            Draw.alpha(0.92f * appear);
            Draw.rect(starPlatinumRegion, sx, sy, w, h, drawRot - 90f);
        }else{
            Draw.color(JOTARO_PURPLE, STAND_CYAN, 0.4f);
            Draw.alpha(0.75f * appear);
            Drawf.tri(sx, sy, 13f * appear, 20f * appear, drawRot);
            Fill.circle(sx, sy, 7f * appear);
        }

        Draw.color();
        Draw.z(z);
    }

    private static void drawTimeStopClockVisual(JotaroRuntime rt){
        if(rt.clockVisualTimer <= 0f) return;

        float total = TIME_STOP_INTRO_EXPAND;
        float frac = 1f - (rt.clockVisualTimer / total);
        float alpha = Mathf.clamp(1f - frac * frac);
        float radius = Mathf.lerp(18f, 152f, Interp.pow2Out.apply(frac));

        float z = Draw.z();
        Draw.z(Layer.flyingUnit + 0.5f);

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

        float minuteAng = 90f - rt.clockHandAngle;
        float hourAng = 90f - rt.clockHandAngle * 0.16f;
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
        rt.resolveCooldown = 1200f;
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

        Events.run(Trigger.update, () -> {
            updateTimeStopWorld();
            updateCutscenes();
        });
        Events.run(Trigger.draw, () -> {
            drawTimeStopDomes();
            drawCutsceneOverlays();
        });

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
            activeCutscenes.clear();
        });
    }

    private static void updateCutscenes(){
        if(Vars.state.isPaused() || activeCutscenes.size == 0) return;

        for(int i = activeCutscenes.size - 1; i >= 0; i--){
            CutsceneDuel duel = activeCutscenes.get(i);
            Unit jotaroUnit = duel.jotaro;
            Unit victim = duel.victim;
            JotaroRuntime rt = duel.rt;

            if(jotaroUnit == null || jotaroUnit.dead || !jotaroUnit.isAdded() || victim == null || victim.dead || !victim.isAdded()){
                if(rt != null){
                    rt.cutsceneActive = false;
                    rt.cutsceneShowStand = false;
                }
                activeCutscenes.remove(i);
                continue;
            }

            float prevElapsed = duel.elapsed;
            duel.elapsed += Time.delta;
            float sec = duel.elapsed / 60f;
            float prevSec = prevElapsed / 60f;

            // Jotaro NEVER moves during the entire 25s ultimate — only Star Platinum moves!
            jotaroUnit.set(duel.jotaroStartX, duel.jotaroStartY);
            jotaroUnit.vel.setZero();
            jotaroUnit.rotation = Angles.angle(duel.jotaroStartX, duel.jotaroStartY, victim.x, victim.y);

            victim.health = Math.max(1f, duel.lockedVictimHealth);
            victim.dead = false;
            victim.apply(cutsceneLock, 12f);

            for(Bullet b : Groups.bullet){
                if(b == null || !b.isAdded()) continue;
                if(b.within(jotaroUnit, 64f) || b.within(victim, 64f)){
                    b.remove();
                }
            }

            float faceAng = Angles.angle(duel.jotaroStartX, duel.jotaroStartY, victim.x, victim.y);
            if(sec < 24.0f){
                victim.vel.setZero();
            }

            float spStandDist = victim.hitSize * 0.5f + 16f;

            // 0.0 -> 1.5s: Jotaro talks
            if(sec < 1.5f){
                rt.cutsceneShowStand = false;
                rt.cutsceneStandX = duel.jotaroStartX;
                rt.cutsceneStandY = duel.jotaroStartY;
                rt.cutsceneStandRot = faceAng;
            }
            // 1.5s -> 4.0s: Star Platinum spawns and approaches victim
            else if(sec < 4.0f){
                if(prevSec < 1.5f){
                    roarRingEffect.at(duel.jotaroStartX, duel.jotaroStartY, 0f, JOTARO_PURPLE);
                    Effect.shake(2.8f, 12f, jotaroUnit);
                }
                rt.cutsceneShowStand = true;
                float t = Mathf.clamp((sec - 1.5f) / 2.5f);
                float tx = victim.x - Angles.trnsx(faceAng, spStandDist);
                float ty = victim.y - Angles.trnsy(faceAng, spStandDist);
                rt.cutsceneStandX = Mathf.lerp(duel.jotaroStartX + Angles.trnsx(faceAng, 14f), tx, Interp.pow2Out.apply(t));
                rt.cutsceneStandY = Mathf.lerp(duel.jotaroStartY + Angles.trnsy(faceAng, 14f), ty, Interp.pow2Out.apply(t));
                rt.cutsceneStandRot = faceAng;
            }
            // 4.0s -> 4.3s: 1st buffed punch at 4.1s + micro pause at 4.2s
            else if(sec < 4.3f){
                positionStandInFront(rt, victim, faceAng, spStandDist);
                if(prevSec < 4.1f && sec >= 4.1f){
                    rt.standPunchBob = 1.2f;
                    heavyPunchEffect.at(victim.x, victim.y, faceAng, JOTARO_GOLD);
                    roarRingEffect.at(victim.x, victim.y, faceAng, JOTARO_PURPLE);
                    Effect.shake(3.5f, 12f, victim);
                }
            }
            // 4.3s -> 8.0s: Barrage #1
            else if(sec < 8.0f){
                positionStandInFront(rt, victim, faceAng, spStandDist);
                spawnBarrageImpact(duel, rt, victim, faceAng, 4.5f, false);
            }
            // 8.0s -> 8.7s: Pause + push enemy back a little
            else if(sec < 8.7f){
                if(prevSec < 8.0f){
                    duel.knockSpeed = 2.6f;
                    heavyPunchEffect.at(victim.x, victim.y, faceAng, JOTARO_PURPLE);
                }
                duel.knockSpeed *= Mathf.pow(0.88f, Time.delta);
                moveUnitWallSafe(victim, faceAng, duel.knockSpeed * Time.delta);
                positionStandInFront(rt, victim, faceAng, spStandDist);
            }
            // 8.7s -> 10.7s: Barrage #2
            else if(sec < 10.7f){
                positionStandInFront(rt, victim, faceAng, spStandDist);
                spawnBarrageImpact(duel, rt, victim, faceAng, 4.2f, false);
            }
            // 10.7s -> 11.0s: Pause + push slightly again
            else if(sec < 11.0f){
                if(prevSec < 10.7f){
                    duel.knockSpeed = 2.4f;
                    heavyPunchEffect.at(victim.x, victim.y, faceAng, JOTARO_PURPLE);
                }
                duel.knockSpeed *= Mathf.pow(0.88f, Time.delta);
                moveUnitWallSafe(victim, faceAng, duel.knockSpeed * Time.delta);
                positionStandInFront(rt, victim, faceAng, spStandDist);
            }
            // 11.0s -> 14.2s: Barrage #3
            else if(sec < 14.2f){
                positionStandInFront(rt, victim, faceAng, spStandDist);
                spawnBarrageImpact(duel, rt, victim, faceAng, 4.0f, false);
            }
            // 14.2s -> 16.7s: Push further + close in & charge for a hard punch at 15.7s
            else if(sec < 16.7f){
                if(prevSec < 14.2f){
                    duel.knockSpeed = 4.6f;
                    heavyPunchEffect.at(victim.x, victim.y, faceAng, JOTARO_GOLD);
                    Effect.shake(3.0f, 10f, victim);
                }
                if(sec < 15.0f){
                    duel.knockSpeed *= Mathf.pow(0.88f, Time.delta);
                    moveUnitWallSafe(victim, faceAng, duel.knockSpeed * Time.delta);
                }
                positionStandInFront(rt, victim, faceAng, spStandDist);
                if(sec >= 15.7f && Mathf.chanceDelta(0.45f)){
                    chargeFlareEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng, JOTARO_GOLD);
                }
            }
            // 16.7s -> 17.7s: Hard punch pushes enemy back + Star Platinum blinks BEHIND enemy
            else if(sec < 17.7f){
                if(prevSec < 16.7f){
                    rt.standPunchBob = 1.3f;
                    duel.knockSpeed = 3.8f;
                    heavyPunchEffect.at(victim.x, victim.y, faceAng, JOTARO_GOLD);
                    roarRingEffect.at(victim.x, victim.y, faceAng, JOTARO_GOLD);
                    Effect.shake(4.8f, 14f, victim);
                }
                if(sec < 17.2f){
                    duel.knockSpeed *= Mathf.pow(0.88f, Time.delta);
                    moveUnitWallSafe(victim, faceAng, duel.knockSpeed * Time.delta);
                }
                if(sec >= 17.55f && prevSec < 17.55f){
                    teleportClockEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng, JOTARO_PURPLE);
                    positionStandBehind(rt, victim, faceAng, spStandDist);
                    teleportClockEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng + 180f, JOTARO_GOLD);
                }
            }
            // 17.7s -> 18.4s: Barrage BEHIND the enemy
            else if(sec < 18.4f){
                positionStandBehind(rt, victim, faceAng, spStandDist);
                spawnBarrageImpact(duel, rt, victim, faceAng + 180f, 4.0f, false);
            }
            // 18.4s -> 19.0s: Hard punch mid-barrage + push slightly + continue barraging
            else if(sec < 19.0f){
                if(prevSec < 18.4f){
                    rt.standPunchBob = 1.2f;
                    duel.knockSpeed = 2.6f;
                    heavyPunchEffect.at(victim.x, victim.y, faceAng + 180f, JOTARO_GOLD);
                    Effect.shake(3.8f, 10f, victim);
                }
                duel.knockSpeed *= Mathf.pow(0.88f, Time.delta);
                moveUnitWallSafe(victim, faceAng + 180f, duel.knockSpeed * Time.delta);
                positionStandBehind(rt, victim, faceAng, spStandDist);
                spawnBarrageImpact(duel, rt, victim, faceAng + 180f, 4.0f, false);
            }
            // 19.0s -> 19.9s: Pause + Star Platinum blinks BACK IN FRONT to prepare HARD BARRAGE
            else if(sec < 19.9f){
                if(prevSec < 19.0f){
                    teleportClockEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng + 180f, JOTARO_PURPLE);
                    positionStandInFront(rt, victim, faceAng, spStandDist);
                    teleportClockEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng, JOTARO_GOLD);
                }
                positionStandInFront(rt, victim, faceAng, spStandDist);
                if(Mathf.chanceDelta(0.45f)){
                    chargeFlareEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng, JOTARO_PURPLE);
                }
            }
            // 19.9s -> 23.3s: HARD BARRAGE (faster, bigger, anime speed-lines + heavy shake)
            else if(sec < 23.3f){
                positionStandInFront(rt, victim, faceAng, spStandDist);
                spawnBarrageImpact(duel, rt, victim, faceAng, 2.5f, true);
                if(Mathf.chanceDelta(0.55f)){
                    speedLineEffect.at(victim.x, victim.y, faceAng + Mathf.range(30f), JOTARO_GOLD);
                }
            }
            // 23.3s -> 25.0s: Star Platinum backs up (23.4->24.0) -> FINAL PUNCH sends enemy flying & instant-kills at 25.0s!
            else if(sec < 25.0f){
                if(sec < 24.0f){
                    float backFrac = Mathf.clamp((sec - 23.3f) / 0.7f);
                    positionStandInFront(rt, victim, faceAng, Mathf.lerp(spStandDist, spStandDist + 16f, backFrac));
                    if(Mathf.chanceDelta(0.65f)){
                        chargeFlareEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, faceAng, JOTARO_GOLD);
                    }
                }else{
                    if(prevSec < 24.0f){
                        positionStandInFront(rt, victim, faceAng, spStandDist);
                        rt.standPunchBob = 1.6f;
                        heavyPunchEffect.at(victim.x, victim.y, faceAng, JOTARO_GOLD);
                        roarRingEffect.at(victim.x, victim.y, faceAng, JOTARO_GOLD);
                        Effect.shake(9.0f, 28f, victim);
                        duel.launchAngle = faceAng;
                        duel.launchSpeed = 24f; // ~30 tiles explosive launch across 1.0s
                    }
                    duel.launchSpeed *= Mathf.pow(0.90f, Time.delta);
                    moveUnitWallSafe(victim, duel.launchAngle, duel.launchSpeed * Time.delta);
                    if(Mathf.chanceDelta(0.88f)){
                        downwashSmokeEffect.at(victim.x, victim.y, duel.launchAngle + 180f, Color.gray);
                    }
                }
            }
            // 25.0s: Instant kill any real enemy regardless of HP
            else{
                victim.unapply(cutsceneLock);
                if(victim.type.killable(victim)){
                    victim.health = 0f;
                    victim.kill();
                    if(!victim.dead){
                        victim.damagePierce(victim.maxHealth * 100f);
                    }
                }
                Fx.massiveExplosion.at(victim.x, victim.y);
                rt.cutsceneActive = false;
                rt.cutsceneShowStand = false;
                activeCutscenes.remove(i);
            }
        }
    }

    private static void positionStandInFront(JotaroRuntime rt, Unit victim, float faceAng, float dist){
        rt.cutsceneStandX = victim.x - Angles.trnsx(faceAng, dist);
        rt.cutsceneStandY = victim.y - Angles.trnsy(faceAng, dist);
        rt.cutsceneStandRot = faceAng;
    }

    private static void positionStandBehind(JotaroRuntime rt, Unit victim, float faceAng, float dist){
        rt.cutsceneStandX = victim.x + Angles.trnsx(faceAng, dist);
        rt.cutsceneStandY = victim.y + Angles.trnsy(faceAng, dist);
        rt.cutsceneStandRot = faceAng + 180f;
    }

    private static void spawnBarrageImpact(CutsceneDuel duel, JotaroRuntime rt, Unit victim, float ang, float interval, boolean hard){
        duel.barrageTick -= Time.delta;
        if(duel.barrageTick <= 0f){
            duel.barrageTick = interval;
            rt.standPunchBob = hard ? 1.1f : 0.8f;
            if(hard){
                hardOraFistEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, ang);
                Fx.flakExplosion.at(victim.x + Mathf.range(victim.hitSize * 0.35f), victim.y + Mathf.range(victim.hitSize * 0.35f));
                Effect.shake(2.2f, 6f, victim);
            }else{
                oraFistEffect.at(rt.cutsceneStandX, rt.cutsceneStandY, ang);
                Fx.hitBulletColor.at(victim.x + Mathf.range(victim.hitSize * 0.35f), victim.y + Mathf.range(victim.hitSize * 0.35f), ang, JOTARO_PURPLE);
                Effect.shake(1.1f, 4f, victim);
            }
        }
    }

    private static void moveUnitWallSafe(Unit u, float angle, float dist){
        if(dist <= 0.01f) return;
        float nx = u.x + Angles.trnsx(angle, dist);
        float ny = u.y + Angles.trnsy(angle, dist);
        Tile t = Vars.world.tileWorld(nx, ny);
        if(t != null && !t.solid()){
            u.set(nx, ny);
        }
    }

    private static void drawCutsceneOverlays(){
        if(Vars.headless || activeCutscenes.size == 0) return;

        float oldZ = Draw.z();
        Draw.z(Layer.groundUnit - 0.08f);
        for(int i = 0; i < activeCutscenes.size; i++){
            CutsceneDuel duel = activeCutscenes.get(i);
            if(duel.jotaro == null || duel.victim == null) continue;
            float cx = (duel.jotaro.x + duel.victim.x) * 0.5f;
            float cy = (duel.jotaro.y + duel.victim.y) * 0.5f;
            Fill.light(cx, cy, 48, 220f, Tmp.c1.set(0.05f, 0.02f, 0.14f, 0.50f), Color.clear);
        }
        Draw.color();
        Draw.z(oldZ);
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
            float elapsed = TIME_STOP_DURATION - zone.timer;

            if(elapsed <= TIME_STOP_INTRO_SHRINK){
                // Option A JJBA Intro: 0->3s expands chromatic wave; 3->4s sudden collapse inward!
                float scale = elapsed <= TIME_STOP_INTRO_EXPAND
                    ? Interp.pow3Out.apply(elapsed / TIME_STOP_INTRO_EXPAND)
                    : Interp.pow4In.apply(Math.max(0f, 1f - (elapsed - TIME_STOP_INTRO_EXPAND) / (TIME_STOP_INTRO_SHRINK - TIME_STOP_INTRO_EXPAND)));

                float r = zone.radius * scale;
                if(r > 1f){
                    Draw.color(0.14f, 0.04f, 0.28f, 0.24f);
                    Fill.circle(zone.x, zone.y, r);

                    Draw.blend(Blending.additive);
                    Draw.color(STAND_CYAN);
                    Draw.alpha(0.45f);
                    Lines.stroke(3.6f);
                    Lines.circle(zone.x, zone.y, r);

                    Draw.color(JJBA_MAGENTA);
                    Draw.alpha(0.40f);
                    Lines.stroke(2.4f);
                    Lines.circle(zone.x, zone.y, r * 0.96f);

                    Draw.color(JOTARO_GOLD);
                    Draw.alpha(0.35f);
                    Lines.stroke(1.8f);
                    Lines.circle(zone.x, zone.y, r * 0.88f);
                    Draw.blend();
                }
            }else{
                // 4s -> 12s: Subtle frozen-time violet tint while enemies remain frozen
                float tailFade = zone.timer < 18f ? Mathf.clamp(zone.timer / 18f) : 1f;
                Draw.color(0.10f, 0.04f, 0.22f, 0.14f * tailFade);
                Fill.circle(zone.x, zone.y, zone.radius);
                Draw.color(JOTARO_PURPLE, JOTARO_GOLD, 0.35f);
                Draw.alpha(0.45f * tailFade);
                Lines.stroke(1.8f);
                Lines.circle(zone.x, zone.y, zone.radius);
            }
        }
        Draw.color();
        Draw.z(oldZ);
    }

    private static void resolveModSounds(){
        if(soundsLoaded || Vars.headless) return;
        soundsLoaded = true;

        punchSound = loadSoundOgg("punch", true);
        heavyPunchSound = loadSoundOgg("heavy-punch", true);
        oraRushSound = loadSoundOgg("ora-rush", true);
        oraRush2Sound = loadSoundOgg("ora-rush-2", false);
        oraFinisherSound = loadSoundOgg("ora-finisher", false);
        standAppearSound = loadSoundOgg("stand-appear", false);
        standVanishSound = loadSoundOgg("stand-vanish", true);
        dashSound = loadSoundOgg("dash", true);
        yareYareSound = loadSoundOgg("yare-yare", true);

        standSummonSound = loadSoundOgg("stand-summon", false);
        teleportSound = loadSoundOgg("teleport", false);
        timeStopSound = loadSoundOgg("time-stop", false);
        inhaleSound = loadSoundOgg("inhale", false);
        ultimateSound = loadSoundOgg("ultimate", false);
    }

    private static Sound loadSoundOgg(String baseName, boolean warnIfMissing){
        try{
            String[] assetKeys = {baseName + ".ogg", "sounds/" + baseName + ".ogg"};
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
            }else{
                Mods.LoadedMod mod = Vars.mods.locateMod("ability-additions");
                if(mod != null && mod.root != null){
                    String[] rels = {"sounds/" + baseName + ".ogg", "assets/sounds/" + baseName + ".ogg"};
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
                Log.warn("[AbilityAdditions] Missing .ogg sound: @", baseName);
            }
            return Sounds.none;
        }catch(Throwable t){
            Log.err("[AbilityAdditions] Sound DECODE FAILED for " + baseName + ".ogg", t);
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
            healthMultiplier = 1.55f;
            reloadMultiplier = 1.45f;
            speedMultiplier = 1.25f;
            damageMultiplier = 1.30f;
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

        cutsceneLock = new StatusEffect("jotaro-cutscene-lock"){{
            disarm = true;
            speedMultiplier = 0f;
            reloadMultiplier = 0f;
            dragMultiplier = 99f;
            color = JOTARO_GOLD;
            show = false;
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

        hardOraFistEffect = new Effect(14f, e -> {
            float off = Mathf.range(12f);
            float spread = Mathf.range(22f);
            float ang = e.rotation + spread;
            float fx = e.x + Angles.trnsx(e.rotation + 90f, off) + Angles.trnsx(ang, 9f + e.fin() * 24f);
            float fy = e.y + Angles.trnsy(e.rotation + 90f, off) + Angles.trnsy(ang, 9f + e.fin() * 24f);

            Draw.color(JOTARO_GOLD, JOTARO_PURPLE, Mathf.random(0.5f));
            Draw.alpha(0.95f * e.fout());
            Drawf.tri(fx, fy, 7.2f * e.fout(), 16f, ang);
            Fill.circle(fx, fy, 3.8f * e.fout());
            Lines.stroke(2.0f * e.fout());
            Lines.circle(fx, fy, 4f + e.fin() * 8f);
        });

        downwashSmokeEffect = new Effect(62f, e -> {
            float dist = Interp.pow2Out.apply(e.fin()) * 58f;
            float px = e.x + Angles.trnsx(e.rotation, dist);
            float py = e.y + Angles.trnsy(e.rotation, dist);
            float rad = Mathf.lerp(2.5f, 11.5f, Interp.pow2Out.apply(e.fin()));
            Draw.color(e.color, Color.lightGray, 0.35f);
            Draw.alpha(0.42f * e.fout() * Mathf.clamp(e.fin() * 4f));
            Fill.circle(px, py, rad);
        }).layer(Layer.debris);

        downwashStreakEffect = new Effect(44f, e -> {
            float dist = 10f + Interp.pow2Out.apply(e.fin()) * 72f;
            float px = e.x + Angles.trnsx(e.rotation, dist);
            float py = e.y + Angles.trnsy(e.rotation, dist);
            Draw.color(e.color, Color.white, 0.3f);
            Draw.alpha(0.45f * e.fout());
            Drawf.tri(px, py, 2.6f * e.fout(), 13f * (0.5f + e.fin() * 0.6f), e.rotation);
        }).layer(Layer.debris + 0.02f);

        roarRingEffect = new Effect(54f, e -> {
            float r = Mathf.lerp(14f, 134f, Interp.pow2Out.apply(e.fin()));
            Draw.color(e.color, Color.white, 0.25f);
            Draw.alpha(0.75f * e.fout());
            Lines.stroke(3.8f * e.fout() + 0.6f);
            Lines.circle(e.x, e.y, r);
        }).layer(Layer.groundUnit - 0.04f);

        bulletDeflectEffect = new Effect(16f, e -> {
            Draw.color(JOTARO_GOLD, STAND_CYAN, 0.5f);
            Lines.stroke(2.5f * e.fout());
            Lines.arc(e.x, e.y, 6f + e.fin() * 10f, 0.45f, e.rotation - 80f);
            Draw.color(Color.white);
            Fill.circle(e.x, e.y, 2.2f * e.fout());
        });

        // Clock ring around origin & arrival for Time-Skip Teleport
        teleportClockEffect = new Effect(24f, e -> {
            float r = 16f + e.fin() * 8f;
            Draw.color(e.color, Color.white, 0.3f);
            Draw.alpha(0.85f * e.fout());
            Lines.stroke(2.2f * e.fout());
            Lines.circle(e.x, e.y, r);
            for(int i = 0; i < 12; i++){
                float a = i * 30f;
                Lines.lineAngle(e.x + Angles.trnsx(a, r * 0.78f), e.y + Angles.trnsy(a, r * 0.78f), a, r * 0.22f);
            }
            Lines.stroke(2.0f * e.fout());
            Lines.lineAngle(e.x, e.y, 90f - e.fin() * 120f, r * 0.68f);
            Lines.lineAngle(e.x, e.y, 90f - e.fin() * 25f, r * 0.44f);
        }).layer(Layer.effect);

        // Inward rushing wind/mist streaks into Star Platinum's mouth
        inhaleWindStreakEffect = new Effect(18f, e -> {
            float startDist = 110f + Mathf.range(25f);
            float dist = Mathf.lerp(startDist, 8f, Interp.pow2In.apply(e.fin()));
            float px = e.x + Angles.trnsx(e.rotation, dist);
            float py = e.y + Angles.trnsy(e.rotation, dist);
            Draw.color(STAND_CYAN, Color.white, 0.45f);
            Draw.alpha(0.65f * e.fin() * e.fout() * 2.5f);
            Drawf.tri(px, py, 2.8f * e.fout(), 14f, e.rotation + 180f);
        }).layer(Layer.flyingUnit + 0.1f);

        swallowPopEffect = new Effect(14f, e -> {
            Draw.color(JOTARO_PURPLE, STAND_CYAN, 0.5f);
            Lines.stroke(2.2f * e.fout());
            Lines.circle(e.x, e.y, 12f * e.fout());
        });

        chargeFlareEffect = new Effect(16f, e -> {
            Draw.color(e.color, Color.white, 0.4f);
            Lines.stroke(2.4f * e.fout());
            Lines.circle(e.x, e.y, 20f * e.fout());
            Drawf.tri(e.x, e.y, 5f * e.fout(), 16f, e.rotation);
        });

        speedLineEffect = new Effect(14f, e -> {
            float rx = e.x + Angles.trnsx(e.rotation + 90f, Mathf.range(26f));
            float ry = e.y + Angles.trnsy(e.rotation + 90f, Mathf.range(26f));
            Draw.color(e.color, Color.white, 0.5f);
            Draw.alpha(0.75f * e.fout());
            Lines.stroke(2.0f * e.fout());
            Lines.lineAngle(rx, ry, e.rotation, 16f + e.fin() * 14f);
        });
    }

    // Ability 1A: Lunge Dash (Base) -> Time-Skip Teleport (Stand Awakened) + Sprint
    public static class LungeOrTeleportAbility extends Ability{
        @Override
        public String localized(){
            return "Lunge / Time-Skip Teleport";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Sprints toward far enemies and lunges across gaps (6s CD). With Star Platinum awakened, lunges become instant Time-Skip Teleports with clock flashes.[]").wrap().width(320f);
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

    // Ability 1B: Combat Flank Dodge Dash (3s CD, curves behind enemy scaled by target size)
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

    // Ability 2: Star Platinum Vacuum Inhale (Replaces Rock Throw — Justice Fight!)
    public static class VacuumInhaleAbility extends Ability{
        @Override
        public String localized(){
            return "Star Platinum: Vacuum Inhale";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Kills close ground enemies first, then inhales flyers & enemy bullets in a 30-tile wide cone for 15.6s (ramping down after 12.2s). Swallows T1 flyers whole and crushes T2-T5 flyers with zero debris/craters. Cancels immediately if no flyers remain.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            JotaroRuntime rt = getRuntime(unit);
            bars.add(new Bar(
                () -> rt.inhaleActive ? "INHALE! " + Strings.fixed(rt.inhaleTimer / 60f, 1) + "s"
                    : rt.inhaleCooldown > 0f ? "Inhale CD: " + Strings.fixed(rt.inhaleCooldown / 60f, 1) + "s"
                    : "Inhale Ready",
                () -> rt.inhaleActive ? STAND_CYAN : (rt.inhaleCooldown > 0f ? Color.gray : JOTARO_PURPLE),
                () -> rt.inhaleActive ? Mathf.clamp(rt.inhaleTimer / INHALE_TOTAL_TICKS)
                    : 1f - Mathf.clamp(rt.inhaleCooldown / INHALE_COOLDOWN_TICKS)
            )).row();
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);
            if(rt.entranceTimer > 0f || rt.cutsceneActive){
                rt.inhaleActive = false;
                return;
            }

            if(rt.inhaleCooldown > 0f){
                rt.inhaleCooldown = Math.max(0f, rt.inhaleCooldown - Time.delta);
            }

            if(!rt.inhaleActive){
                if(rt.inhaleCooldown > 0f) return;

                // Ground-first priority: if close ground enemies are actively engaging Jotaro, kill them first!
                Unit closeGround = Units.closestEnemy(unit.team, unit.x, unit.y, 82f, u -> !u.dead && !u.isFlying() && u.hittable());
                if(closeGround != null) return;

                Unit flyer = Units.closestEnemy(unit.team, unit.x, unit.y, INHALE_RANGE, u -> !u.dead && u.isFlying() && u.hittable());
                if(flyer == null) return;

                rt.inhaleActive = true;
                rt.inhaleTimer = INHALE_TOTAL_TICKS;
                rt.inhaleAngle = Angles.angle(unit.x, unit.y, flyer.x, flyer.y);
                if(!rt.standAwakened){
                    playSound(standAppearSound, unit.x, unit.y, 1f, 1.05f);
                }
                playSound(inhaleSound, unit.x, unit.y, 1f, 1.3f);
                return;
            }

            // Active Vacuum Inhale (15.6s total; 0->12.2s full power, 12.2->15.6s slowly ramps down)
            rt.inhaleTimer -= Time.delta;
            float elapsed = INHALE_TOTAL_TICKS - rt.inhaleTimer;
            float power = elapsed <= INHALE_HOLD_TICKS ? 1f : Mathf.clamp(rt.inhaleTimer / (INHALE_TOTAL_TICKS - INHALE_HOLD_TICKS));

            // Check if any flying units remain in vacuum range; cancel immediately if none!
            Unit nearestFlyer = Units.closestEnemy(unit.team, unit.x, unit.y, INHALE_RANGE + 16f, u -> !u.dead && u.isFlying() && u.hittable());
            if(nearestFlyer == null || rt.inhaleTimer <= 0f){
                rt.inhaleActive = false;
                rt.inhaleCooldown = INHALE_COOLDOWN_TICKS;
                if(!rt.standAwakened){
                    playSound(standVanishSound, unit.x, unit.y, 1f, 1.15f);
                }
                return;
            }

            rt.inhaleAngle = Angles.moveToward(rt.inhaleAngle, Angles.angle(unit.x, unit.y, nearestFlyer.x, nearestFlyer.y), 4f * Time.delta);
            Vec2 mouth = getStandWorldPos(unit, rt, true);

            if(Mathf.chanceDelta(0.85f * power)){
                inhaleWindStreakEffect.at(mouth.x, mouth.y, rt.inhaleAngle + Mathf.range(62f));
            }

            // 1. Suck & inhale incoming bullets (from Daggers, ground units, or flyers) inside the wide cone
            for(Bullet b : Groups.bullet){
                if(b == null || !b.isAdded() || b.team == unit.team || !b.type.hittable) continue;
                if(b.type instanceof ContinuousBulletType || b.type instanceof LaserBulletType || b.type instanceof PointLaserBulletType) continue;
                if(!b.within(mouth.x, mouth.y, INHALE_RANGE * 0.9f)) continue;
                float angToB = Angles.angle(mouth.x, mouth.y, b.x, b.y);
                if(b.within(mouth.x, mouth.y, 38f) || Math.abs(Angles.angleDist(rt.inhaleAngle, angToB)) <= 70f){
                    float dst = Mathf.dst(mouth.x, mouth.y, b.x, b.y);
                    float pull = Mathf.lerp(7.5f, 1.8f, dst / INHALE_RANGE) * power;
                    float pullAng = Angles.angle(b.x, b.y, mouth.x, mouth.y);
                    b.vel.approachDelta(Tmp.v1.trns(pullAng, Math.max(b.vel.len(), 4f) + pull), 1.4f);
                    if(dst <= 16f){
                        swallowPopEffect.at(b.x, b.y);
                        b.remove();
                    }
                }
            }

            // 2. Pull flying units in wide cone (nearer = faster pull; zero debris/crater removal!)
            Units.nearbyEnemies(unit.team, mouth.x, mouth.y, INHALE_RANGE, flyer -> {
                if(flyer == null || flyer.dead || !flyer.isFlying() || !flyer.hittable()) return;
                float dst = Mathf.dst(mouth.x, mouth.y, flyer.x, flyer.y);
                float angFromMouth = Angles.angle(mouth.x, mouth.y, flyer.x, flyer.y);
                if(dst > 48f && Math.abs(Angles.angleDist(rt.inhaleAngle, angFromMouth)) > 75f) return;

                // Nearer = much faster pull; T1 flyers pulled faster than heavy T2-T5 flyers
                float proximity = 1f - Mathf.clamp(dst / INHALE_RANGE);
                boolean isT1 = flyer.hitSize <= 11.5f && flyer.maxHealth <= 450f;
                float tierSlow = isT1 ? 1.55f : Mathf.clamp(15f / Math.max(10f, flyer.hitSize), 0.32f, 0.95f);
                float pullSpeed = (1.6f + proximity * proximity * 6.8f) * tierSlow * power;
                float pullAng = Angles.angle(flyer.x, flyer.y, mouth.x, mouth.y);

                flyer.vel.add(Angles.trnsx(pullAng, pullSpeed * Time.delta * 0.42f), Angles.trnsy(pullAng, pullSpeed * Time.delta * 0.42f));

                if(dst <= 18f){
                    if(isT1){
                        // T1 flyers get sucked whole with zero crater/wreck/debris!
                        swallowPopEffect.at(flyer.x, flyer.y);
                        flyer.remove();
                    }else{
                        // T2-T5 flyers take heavy vacuum crush damage; remove cleanly on lethal damage (zero debris!)
                        float crushDmg = (18f + flyer.maxHealth * 0.008f) * power * Time.delta;
                        if(flyer.health <= crushDmg){
                            swallowPopEffect.at(flyer.x, flyer.y);
                            flyer.remove();
                        }else{
                            flyer.damagePierce(crushDmg);
                        }
                    }
                }
            });
        }
    }

    // Ability 3: Stand ORA ORA Rush (alternates 2 ORA sounds, singular finisher ORA!, 0.35s delayed vanish)
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
            t.add("[lightgray]6 confirmed hits unleash a 3.5s Stand ORA flurry (stunning targets until finished), ending in a singular 850-damage ORA Finisher (10s CD).[]").wrap().width(320f);
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
            if(rt.entranceTimer > 0f || rt.cutsceneActive) return;

            if(rt.oraVanishDelay > 0f){
                rt.oraVanishDelay = Math.max(0f, rt.oraVanishDelay - Time.delta);
                if(rt.oraVanishDelay <= 0f && !rt.standAwakened){
                    playSound(standVanishSound, unit.x, unit.y, 1f, 1.2f);
                }
            }

            if(rt.oraCooldown > 0f){
                rt.oraCooldown = Math.max(0f, rt.oraCooldown - Time.delta);
            }

            if(!rt.oraActive){
                if(rt.oraCooldown <= 0f && rt.oraCharge >= 6){
                    Unit nearby = Units.closestEnemy(unit.team, unit.x, unit.y, 64f, u -> !u.dead && !u.isFlying() && u.hittable());
                    if(nearby != null){
                        rt.oraCharge = 0;
                        rt.oraActive = true;
                        rt.oraTimer = ORA_DURATION_TICKS;
                        rt.oraTickTimer = 0f;
                        Effect.shake(2.0f, 10f, unit);

                        if(!rt.standAwakened){
                            playSound(standAppearSound, unit.x, unit.y, 1f, 1.15f);
                        }

                        Sound chosenOra = (rt.oraSoundToggle && oraRush2Sound != Sounds.none) ? oraRush2Sound : oraRushSound;
                        rt.oraSoundToggle = !rt.oraSoundToggle;
                        playSound(chosenOra, unit.x, unit.y, 1f, 1.0f);
                    }
                }
                return;
            }

            rt.oraTimer -= Time.delta;
            rt.oraTickTimer -= Time.delta;

            if(rt.oraTickTimer <= 0f){
                rt.oraTickTimer = 4f;
                Vec2 spPos = getStandWorldPos(unit, rt, true);
                rt.standPunchBob = 0.8f;
                int hits = flurryType.execute(unit, spPos.x, spPos.y, unit.rotation);
                if(hits > 0){
                    registerCombatHit(rt, true);
                }
            }

            if(rt.oraTimer <= 0f){
                rt.oraActive = false;
                rt.oraCooldown = ORA_COOLDOWN_TICKS;
                Vec2 spPos = getStandWorldPos(unit, rt, true);
                rt.standPunchBob = 1.2f;
                int hits = finisherType.execute(unit, spPos.x, spPos.y, unit.rotation);
                if(hits > 0){
                    registerCombatHit(rt, true);
                }
                Sound finSound = oraFinisherSound != Sounds.none ? oraFinisherSound : heavyPunchSound;
                playSound(finSound, spPos.x, spPos.y, 1f, 1.1f);

                // Vanish 0.35s AFTER the singular ORA finisher punch!
                if(!rt.standAwakened){
                    rt.oraVanishDelay = ORA_VANISH_DELAY_TICKS;
                }
            }
        }
    }

    // Ability 4: Star Platinum Awakening (5s Helicopter-Downwash Roar + 5s Push/Stun) & Bullet Deflect
    public static class StarPlatinumAbility extends Ability{
        @Override
        public String localized(){
            return "Star Platinum (Stand Awakening)";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Awakens at <=85% HP, stopping untargetable for a slow 5s roar with ground downwash smoke that pushes and stuns nearby enemies for 5s. Deflects ~88% of incoming projectiles back as friendly bullets.[]").wrap().width(320f);
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);

            if(!rt.standAwakened && !rt.cutsceneActive && unit.healthf() <= 0.85f){
                rt.standAwakened = true;
                rt.standEntranceAnim = 0f;
                rt.entranceTimer = ENTRANCE_ROAR_TICKS;
                rt.entranceRingTimer = 0f;

                roarRingEffect.at(unit.x, unit.y, 0f, JOTARO_PURPLE);
                Effect.shake(7.0f, 36f, unit);
                playSound(standSummonSound, unit.x, unit.y, 1f, 1.3f);

                Units.nearbyEnemies(unit.team, unit.x, unit.y, ENTRANCE_STUN_RADIUS, enemy -> {
                    if(enemy != null && !enemy.dead){
                        enemy.apply(entranceStun, ENTRANCE_STUN_TICKS);
                        float ang = enemy.within(unit, 2f) ? Mathf.random(360f) : Angles.angle(unit.x, unit.y, enemy.x, enemy.y);
                        float sizeScale = Mathf.clamp(16f / Math.max(8f, enemy.hitSize), 0.35f, 1.4f);
                        enemy.impulse(Angles.trnsx(ang, 185f * sizeScale), Angles.trnsy(ang, 185f * sizeScale));
                    }
                });
            }

            if(!rt.standAwakened) return;

            if(rt.standEntranceAnim < 1f){
                rt.standEntranceAnim = Math.min(1f, rt.standEntranceAnim + Time.delta / 15f);
            }

            if(rt.entranceTimer > 0f){
                Floor floor = unit.floorOn();
                Color terrainDust = floor != null ? Tmp.c1.set(floor.mapColor).lerp(Color.lightGray, 0.35f).cpy() : Color.lightGray;

                if(Mathf.chanceDelta(0.75f)){
                    float a = Mathf.random(360f);
                    downwashSmokeEffect.at(unit.x + Angles.trnsx(a, 5f), unit.y + Angles.trnsy(a, 5f), a, terrainDust);
                }
                if(Mathf.chanceDelta(0.55f)){
                    float a = Mathf.random(360f);
                    downwashStreakEffect.at(unit.x, unit.y, a, terrainDust);
                }

                rt.entranceRingTimer -= Time.delta;
                if(rt.entranceRingTimer <= 0f){
                    rt.entranceRingTimer = 28f;
                    roarRingEffect.at(unit.x, unit.y, 0f, Mathf.chance(0.5f) ? JOTARO_PURPLE : JOTARO_GOLD);
                    Effect.shake(2.6f, 14f, unit);
                }

                Units.nearbyEnemies(unit.team, unit.x, unit.y, ENTRANCE_STUN_RADIUS, enemy -> {
                    if(enemy != null && !enemy.dead){
                        float ang = enemy.within(unit, 2f) ? Mathf.random(360f) : Angles.angle(unit.x, unit.y, enemy.x, enemy.y);
                        float sizeScale = Mathf.clamp(14f / Math.max(8f, enemy.hitSize), 0.3f, 1.2f);
                        enemy.impulse(Angles.trnsx(ang, 6.5f * sizeScale * Time.delta), Angles.trnsy(ang, 6.5f * sizeScale * Time.delta));
                    }
                });
                return;
            }

            if(rt.cutsceneActive) return;

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

    // Ability 5: Star Platinum: The World! (50 hits, Option A: 4s JJBA intro + 12s freeze, slowing clock)
    public static class TheWorldAbility extends Ability{
        @Override
        public String localized(){
            return "Star Platinum: The World";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Once awakened, every 50 combat hits unleashes a 12.0s anchored Time Stop dome (30s CD) with a 4s chromatic wave & slowing clock intro.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            JotaroRuntime rt = getRuntime(unit);
            if(!rt.standAwakened) return;
            bars.add(new Bar(
                () -> rt.timeStopActiveTimer > 0f ? "THE WORLD! " + Strings.fixed(rt.timeStopActiveTimer / 60f, 1) + "s"
                    : rt.timeStopCooldown > 0f ? "Time Stop CD: " + Strings.fixed(rt.timeStopCooldown / 60f, 1) + "s"
                    : "The World: " + rt.timeStopHits + "/" + TIME_STOP_HITS_REQUIRED + " Hits",
                () -> rt.timeStopActiveTimer > 0f ? STAND_CYAN : (rt.timeStopCooldown > 0f ? Color.gray : JOTARO_GOLD),
                () -> rt.timeStopActiveTimer > 0f ? Mathf.clamp(rt.timeStopActiveTimer / TIME_STOP_DURATION)
                    : rt.timeStopCooldown > 0f ? 1f - Mathf.clamp(rt.timeStopCooldown / TIME_STOP_COOLDOWN)
                    : Mathf.clamp((float)rt.timeStopHits / TIME_STOP_HITS_REQUIRED)
            )).row();
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);

            if(rt.clockVisualTimer > 0f){
                rt.clockVisualTimer = Math.max(0f, rt.clockVisualTimer - Time.delta);
                float remainingFrac = rt.clockVisualTimer / TIME_STOP_INTRO_EXPAND;
                float spinSpeed = 12f * Interp.pow2Out.apply(remainingFrac);
                rt.clockHandAngle += spinSpeed * Time.delta;
            }
            if(rt.timeStopActiveTimer > 0f){
                rt.timeStopActiveTimer = Math.max(0f, rt.timeStopActiveTimer - Time.delta);
            }else if(rt.timeStopCooldown > 0f){
                rt.timeStopCooldown = Math.max(0f, rt.timeStopCooldown - Time.delta);
            }

            if(!rt.standAwakened || rt.entranceTimer > 0f || rt.cutsceneActive || rt.timeStopCooldown > 0f || rt.timeStopActiveTimer > 0f) return;
            if(rt.timeStopHits < TIME_STOP_HITS_REQUIRED) return;

            Unit threat = Units.closestEnemy(unit.team, unit.x, unit.y, 128f, u -> !u.dead && !u.isFlying() && u.hittable());
            if(threat == null) return;

            rt.timeStopHits = 0;
            rt.timeStopCooldown = TIME_STOP_COOLDOWN;
            rt.timeStopActiveTimer = TIME_STOP_DURATION;
            rt.clockVisualTimer = TIME_STOP_INTRO_EXPAND;
            rt.clockHandAngle = 0f;
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

            Effect.shake(4.5f, 18f, unit);
            playSound(timeStopSound, unit.x, unit.y, 1f, 1.25f);
        }
    }

    // Ability 6: 25-Second 400-Hit Cutscene Ultimate (Jotaro stays still, only Star Platinum moves!)
    public static class UltimateBeatdownAbility extends Ability{
        @Override
        public String localized(){
            return "Ultimate: 25s ORA Beatdown";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Reaching 400 hits (with reduced barrage charge) unlocks a 25s 1v1 Cutscene Ultimate where Star Platinum unleashes a multi-stage beatdown, launches the enemy flying, and instant-kills any enemy.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            JotaroRuntime rt = getRuntime(unit);
            bars.add(new Bar(
                () -> rt.cutsceneActive ? "BEATDOWN ULTIMATE!"
                    : rt.ultimateReady ? "ULTIMATE READY!"
                    : "Ultimate: " + rt.ultimateM1Count + "/" + ULTIMATE_M1_REQUIREMENT,
                () -> rt.cutsceneActive || rt.ultimateReady ? JOTARO_GOLD : JOTARO_PURPLE,
                () -> rt.cutsceneActive ? 1f : Mathf.clamp((float)rt.ultimateM1Count / ULTIMATE_M1_REQUIREMENT)
            )).row();
        }

        @Override
        public void update(Unit unit){
            JotaroRuntime rt = getRuntime(unit);
            if(!rt.ultimateReady || rt.cutsceneActive || rt.entranceTimer > 0f) return;

            Unit victim = Units.closestEnemy(unit.team, unit.x, unit.y, 80f, u -> !u.dead && !u.isFlying() && u.hittable());
            if(victim == null) return;

            rt.ultimateReady = false;
            rt.ultimateM1Count = 0;
            rt.oraActive = false;
            rt.oraVanishDelay = 0f;
            rt.inhaleActive = false;
            rt.cutsceneActive = true;
            rt.cutsceneShowStand = false;
            rt.cutsceneStandX = unit.x;
            rt.cutsceneStandY = unit.y;
            rt.cutsceneStandRot = Angles.angle(unit.x, unit.y, victim.x, victim.y);

            CutsceneDuel duel = new CutsceneDuel();
            duel.jotaro = unit;
            duel.victim = victim;
            duel.rt = rt;
            duel.jotaroStartX = unit.x;
            duel.jotaroStartY = unit.y;
            duel.lockedVictimHealth = Math.max(1f, victim.health);
            duel.elapsed = 0f;
            activeCutscenes.add(duel);

            playSound(ultimateSound, unit.x, unit.y, 1f, 1.35f);
        }
    }

    // Ability 7: Yare Yare Daze (Permanent Resolve <= 45% HP + 2s untargetable/passthrough burst)
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

    private static class CutsceneDuel{
        Unit jotaro;
        Unit victim;
        JotaroRuntime rt;
        float jotaroStartX;
        float jotaroStartY;
        float lockedVictimHealth;
        float elapsed;
        float barrageTick = 0f;
        float knockSpeed = 0f;
        float launchAngle = 0f;
        float launchSpeed = 0f;
    }

    private static class JotaroRuntime{
        int comboStep = 0;
        float comboIdleTimer = 0f;
        float comboRecoveryTimer = 0f;

        boolean inhaleActive = false;
        float inhaleTimer = 0f;
        float inhaleCooldown = 0f;
        float inhaleAngle = 0f;

        int oraCharge = 0;
        boolean oraActive = false;
        boolean oraSoundToggle = false;
        float oraTimer = 0f;
        float oraTickTimer = 0f;
        float oraCooldown = 0f;
        float oraVanishDelay = 0f;

        boolean standAwakened = false;
        float standEntranceAnim = 0f;
        float entranceTimer = 0f;
        float entranceRingTimer = 0f;
        float standPunchBob = 0f;
        float bulletCatchGrace = 0f;

        int timeStopHits = 0;
        float timeStopCooldown = 0f;
        float timeStopActiveTimer = 0f;
        float clockVisualTimer = 0f;
        float clockHandAngle = 0f;
        float clockOriginX = 0f;
        float clockOriginY = 0f;

        int ultimateM1Count = 0;
        int barrageUltTick = 0;
        boolean ultimateReady = false;
        boolean cutsceneActive = false;
        boolean cutsceneShowStand = false;
        float cutsceneStandX = 0f;
        float cutsceneStandY = 0f;
        float cutsceneStandRot = 0f;

        float resolveCooldown = 0f;
        float invulnTimer = 0f;
        boolean resolveTriggeredBelow = false;
    }
                    }
