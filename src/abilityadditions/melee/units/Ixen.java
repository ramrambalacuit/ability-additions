package abilityadditions.melee.units;

import abilityadditions.melee.core.*;
import abilityadditions.melee.core.MeleeType.*;
import arc.*;
import arc.audio.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;

public class Ixen{

    public static UnitType ixen;
    public static StatusEffect sprintBuff;
    public static StatusEffect kickStun;

    public static Effect trailSmokeEffect;
    public static Effect punchStrikeEffect;
    public static Effect rightKickStrikeEffect;

    public static TextureRegion punchRegion;
    public static TextureRegion rightKickRegion;

    public static Sound punchSound = Sounds.none;
    public static Sound heavyPunchSound = Sounds.none;

    private static final IntMap<IxenState> states = new IntMap<>();
    private static boolean eventsHooked = false;
    private static boolean soundsLoaded = false;

    private static final int KICK_HITS_REQUIRED = 9;
    private static final float KICK_STUN_TICKS = 90f;

    public static void load(){
        sprintBuff = new StatusEffect("ixen-sprint"){{
            speedMultiplier = 1.28f;
            show = false;
        }};

        kickStun = new StatusEffect("ixen-kick-stun"){{
            disarm = true;
            speedMultiplier = 0.04f;
            reloadMultiplier = 0.04f;
            color = Pal.accent;
            show = true;
        }};

        trailSmokeEffect = new Effect(20f, e -> {
            Draw.color(Color.lightGray, Pal.gray, 0.35f);
            Draw.alpha(0.42f * e.fout());
            Fill.circle(e.x, e.y, 1.9f * e.fout() + 0.55f);
        }).layer(Layer.debris);

        punchStrikeEffect = new Effect(14f, e -> {
            float prog = Interp.pow2Out.apply(e.fin());
            float alpha = e.fout();
            float startDist = 5f;
            float endDist = 17f;

            if(punchRegion != null && punchRegion.found()){
                float w = punchRegion.width * punchRegion.scl();
                float h = punchRegion.height * punchRegion.scl();

                for(int t = 3; t >= 1; t--){
                    float tf = Math.max(0f, prog - t * 0.16f);
                    float dist = Mathf.lerp(startDist, endDist, tf);
                    float px = e.x + Angles.trnsx(e.rotation, dist);
                    float py = e.y + Angles.trnsy(e.rotation, dist);
                    Draw.color(e.color, Color.white, 0.15f);
                    Draw.alpha(alpha * (0.26f / t));
                    Draw.rect(punchRegion, px, py, w * (1f - t * 0.06f), h * (1f - t * 0.06f), e.rotation - 90f);
                }

                float mainDist = Mathf.lerp(startDist, endDist, prog);
                float mx = e.x + Angles.trnsx(e.rotation, mainDist);
                float my = e.y + Angles.trnsy(e.rotation, mainDist);
                Draw.color(e.color, Color.white, 0.30f);
                Draw.alpha(alpha * 0.95f);
                Draw.rect(punchRegion, mx, my, w, h, e.rotation - 90f);
            }else{
                float mx = e.x + Angles.trnsx(e.rotation, Mathf.lerp(startDist, endDist, prog));
                float my = e.y + Angles.trnsy(e.rotation, Mathf.lerp(startDist, endDist, prog));
                Draw.color(e.color, Color.white, 0.3f);
                Draw.alpha(alpha * 0.85f);
                Drawf.tri(mx, my, 4.5f * alpha, 11f, e.rotation);
            }
            Draw.color();
        }).layer(Layer.effect);

        rightKickStrikeEffect = new Effect(20f, e -> {
            float prog = Interp.pow2Out.apply(e.fin());
            float alpha = e.fout();
            float startDist = 6f;
            float endDist = 21f;

            if(rightKickRegion != null && rightKickRegion.found()){
                float w = rightKickRegion.width * rightKickRegion.scl();
                float h = rightKickRegion.height * rightKickRegion.scl();

                for(int t = 4; t >= 1; t--){
                    float tf = Math.max(0f, prog - t * 0.13f);
                    float dist = Mathf.lerp(startDist, endDist, tf);
                    float kx = e.x + Angles.trnsx(e.rotation, dist);
                    float ky = e.y + Angles.trnsy(e.rotation, dist);
                    Draw.color(e.color, Color.white, 0.2f);
                    Draw.alpha(alpha * (0.30f / t));
                    Draw.rect(rightKickRegion, kx, ky, w * (1f - t * 0.05f), h * (1f - t * 0.05f), e.rotation - 90f);
                }

                float mainDist = Mathf.lerp(startDist, endDist, prog);
                float mx = e.x + Angles.trnsx(e.rotation, mainDist);
                float my = e.y + Angles.trnsy(e.rotation, mainDist);
                Draw.color(e.color, Color.white, 0.38f);
                Draw.alpha(alpha);
                Draw.rect(rightKickRegion, mx, my, w * 1.08f, h * 1.08f, e.rotation - 90f);
            }else{
                float mx = e.x + Angles.trnsx(e.rotation, Mathf.lerp(startDist, endDist, prog));
                float my = e.y + Angles.trnsy(e.rotation, Mathf.lerp(startDist, endDist, prog));
                Draw.color(e.color, Color.white, 0.35f);
                Draw.alpha(alpha * 0.9f);
                Drawf.tri(mx, my, 7f * alpha, 15f, e.rotation);
            }

            Draw.color(e.color, Color.white, 0.4f);
            Draw.alpha(alpha * 0.8f);
            Lines.stroke(2.4f * alpha);
            Lines.arc(e.x, e.y, 12f + prog * 13f, 0.34f, e.rotation - 61f);
            Draw.color();
        }).layer(Layer.effect);

        MeleeType jabStrike = new MeleeType(){{
            shape = MeleeShape.THRUST;
            reach = 23f;
            halfWidth = 6.5f;
            damage = 32f;
            buildingDamageMultiplier = 1.15f;
            maxTargets = 2;
            hitGround = true;
            hitAir = false;
            knockback = 0.5f;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType crossStrike = new MeleeType(){{
            shape = MeleeShape.THRUST;
            reach = 25f;
            halfWidth = 7.5f;
            damage = 46f;
            buildingDamageMultiplier = 1.25f;
            maxTargets = 3;
            hitGround = true;
            hitAir = false;
            knockback = 2.2f;
            hitEffect = Fx.hitBulletColor;
        }};

        MeleeType rightKickStrike = new MeleeType(){{
            shape = MeleeShape.ARC;
            reach = 28f;
            arcAngle = 115f;
            damage = 92f;
            buildingDamageMultiplier = 1.4f;
            maxTargets = 4;
            hitGround = true;
            hitAir = false;
            knockback = 5.4f;
            lungeSpeed = 0.85f;
            shake = 1.6f;
            hitStatus = kickStun;
            hitStatusDuration = KICK_STUN_TICKS;
            hitEffect = Fx.flakExplosion;
        }};

        MeleeWeapon ixenPunch = new MeleeWeapon("", jabStrike){{
            x = 0f;
            y = 2f;
            shootX = 0f;
            shootY = 3f;
            reload = 20f;
            mirror = false;
            rotate = false;
            recoil = 0f;
            shootCone = 50f;
            shootSound = Sounds.none;

            strikeHandler = (unit, mount, ox, oy, angle, weapon) -> {
                IxenState st = getState(unit.id);
                Color teamCol = unit.team.color;

                if(st.kickHits >= KICK_HITS_REQUIRED){
                    st.kickHits = 0;
                    st.kickAnim = 1f;
                    rightKickStrike.accentColor = teamCol;
                    rightKickStrike.execute(unit, unit.x, unit.y, angle);
                    rightKickStrikeEffect.at(unit.x, unit.y, angle, teamCol);
                    playSound(heavyPunchSound, unit.x, unit.y, Mathf.random(0.95f, 1.05f), 0.9f);
                    return;
                }

                st.isCross = !st.isCross;
                int strikeSign = st.isCross ? 1 : -1;
                if(strikeSign < 0){
                    st.leftPunchAnim = 1f;
                    st.leftCrossStyle = false;
                }else{
                    st.rightPunchAnim = 1f;
                    st.rightCrossStyle = true;
                }

                float shoulderSide = strikeSign * 4.2f;
                float spawnX = unit.x + Angles.trnsx(angle - 90f, shoulderSide) + Angles.trnsx(angle, 2f);
                float spawnY = unit.y + Angles.trnsy(angle - 90f, shoulderSide) + Angles.trnsy(angle, 2f);

                MeleeType activeStrike = st.isCross ? crossStrike : jabStrike;
                activeStrike.accentColor = teamCol;
                int hits = activeStrike.execute(unit, unit.x, unit.y, angle);

                punchStrikeEffect.at(spawnX, spawnY, angle, teamCol);
                playSound(punchSound, unit.x, unit.y, st.isCross ? Mathf.random(0.88f, 0.96f) : Mathf.random(1.02f, 1.12f), 0.78f);

                if(hits > 0){
                    st.kickHits = Math.min(KICK_HITS_REQUIRED, st.kickHits + 1);
                }
            };
        }};

        ixen = new UnitType("ixen"){
            public TextureRegion armRegion;
            public TextureRegion armOutlineRegion;
            public TextureRegion legOutlineRegion;
            private boolean outlineLookedUp = false;

            {
                localizedName = "Ixen";
                description = "Tier 1 agile melee brawler. Sprints across long gaps, lunges into close combat, alternates jabs & crosses, and unleashes a 2x-damage 1.5s-stun Right Kick every 9 hits.";
                constructor = MechUnit::create;
                aiController = () -> createAI();
                controller = u -> createAI();

                flying = false;
                speed = 0.62f;
                rotateSpeed = 11f;
                baseRotateSpeed = 11f;
                hitSize = 9f;
                range = 24f;
                health = 270f;
                armor = 6f;
                drag = 0.35f;
                accel = 0.55f;

                targetAir = false;
                targetGround = true;
                autoFindTarget = false;
                faceTarget = true;
                omniMovement = true;
                mechStepParticles = true;
                mechStride = 4.6f;
                mechFrontSway = 0.18f;
                mechSideSway = 0.54f;

                stepSound = UnitTypes.dagger != null ? UnitTypes.dagger.stepSound : Sounds.mechStepSmall;
                stepSoundVolume = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundVolume : 0.45f;
                stepSoundPitch = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitch : 1f;
                stepSoundPitchRange = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitchRange : 0.1f;

                allowedInPayloads = true;

                weapons.add(ixenPunch);
                abilities.add(new RightKickAbility());
            }

            @Override
            public void load(){
                super.load();
                armRegion = Core.atlas.find(name + "-arm");
                armOutlineRegion = Core.atlas.find(name + "-arm-outline");
                legOutlineRegion = Core.atlas.find(name + "-leg-outline");
                outlineLookedUp = false;

                punchRegion = Core.atlas.find("ability-additions-punch");
                if(!punchRegion.found()){
                    punchRegion = Core.atlas.find("punch");
                }
                rightKickRegion = Core.atlas.find("ability-additions-right-kick");
                if(!rightKickRegion.found()){
                    rightKickRegion = Core.atlas.find("right-kick");
                }
            }

            @Override
            public void getRegionsToOutline(Seq<TextureRegion> out){
                super.getRegionsToOutline(out);
                if(armRegion != null && armRegion.found()){
                    out.add(armRegion);
                }
            }

            @Override
            public void update(Unit unit){
                super.update(unit);

                if(!(unit.controller() instanceof MeleeCommandAI) && !unit.isPlayer()){
                    UnitController prev = unit.controller();
                    MeleeCommandAI next = createAI();
                    if(prev instanceof CommandAI old){
                        next.command = old.command;
                        next.targetPos = old.targetPos;
                        if(!(old.attackTarget instanceof Unit u && u.isFlying())){
                            next.attackTarget = old.attackTarget;
                        }
                    }
                    unit.controller(next);
                }

                IxenState st = getState(unit.id);
                if(!st.posInit){
                    st.posInit = true;
                    st.prevX = unit.x;
                    st.prevY = unit.y;
                }
                float stepDist = Mathf.dst(unit.x, unit.y, st.prevX, st.prevY);
                st.prevX = unit.x;
                st.prevY = unit.y;

                boolean sprinting = false;
                boolean lunging = false;
                if(unit.controller() instanceof MeleeCommandAI ai){
                    sprinting = ai.sprinting;
                    lunging = ai.dashTimer > 0f;
                }

                float moveSpeed = unit.vel.len();
                if(stepDist > 0.02f || moveSpeed > 0.04f){
                    float tempo = sprinting ? 1.45f : 1.0f;
                    st.armPhase += Math.max(stepDist, moveSpeed * Time.delta * 0.5f) * tempo;
                    st.moveBlend = Mathf.lerpDelta(st.moveBlend, Mathf.clamp(Math.max(stepDist, moveSpeed) / Math.max(0.05f, speed)), 0.25f);
                }else{
                    st.moveBlend = Mathf.lerpDelta(st.moveBlend, 0f, 0.22f);
                }

                st.sprintBlend = Mathf.lerpDelta(st.sprintBlend, sprinting ? 1f : 0f, 0.18f);
                st.lungeBlend = Mathf.lerpDelta(st.lungeBlend, lunging ? 1f : 0f, lunging ? 0.45f : 0.16f);

                if(st.leftPunchAnim > 0f){
                    st.leftPunchAnim = Math.max(0f, st.leftPunchAnim - Time.delta / 13f);
                }
                if(st.rightPunchAnim > 0f){
                    st.rightPunchAnim = Math.max(0f, st.rightPunchAnim - Time.delta / 14f);
                }
                if(st.kickAnim > 0f){
                    st.kickAnim = Math.max(0f, st.kickAnim - Time.delta / 18f);
                }

                if(sprinting){
                    unit.apply(sprintBuff, 8f);
                    if(moveSpeed > 0.2f && Mathf.chanceDelta(0.32f)){
                        emitTrailSmoke(unit);
                    }
                }else if(unit.hasEffect(sprintBuff)){
                    unit.unapply(sprintBuff);
                }

                if(lunging && Mathf.chanceDelta(0.85f)){
                    emitTrailSmoke(unit);
                }
            }

            @Override
            public void drawMech(Mechc mech){
                Unit unit = (Unit)mech;

                if(!outlineLookedUp || armOutlineRegion == null || !armOutlineRegion.found()){
                    armOutlineRegion = Core.atlas.find(name + "-arm-outline");
                    legOutlineRegion = Core.atlas.find(name + "-leg-outline");
                    outlineLookedUp = true;
                }

                IxenState st = states.get(unit.id);
                float kickVal = st != null ? st.kickAnim : 0f;
                float kickCurve = kickVal > 0f ? Mathf.sin(kickVal * Mathf.PI) : 0f;

                // Use native Mindustry drawMech for true footstep-synced legs & base whenever not mid-kick
                if(kickVal <= 0.001f){
                    super.drawMech(mech);
                }else{
                    float legRot = unit.rotation - 90f;
                    if(legRegion != null && legRegion.found()){
                        float lw = legRegion.width * legRegion.scl();
                        float lh = legRegion.height * legRegion.scl();
                        for(int i : Mathf.signs){
                            float legShift = 0f;
                            float legAng = 0f;
                            if(i == 1){
                                if(kickVal > 0.72f){
                                    float wind = (kickVal - 0.72f) / 0.28f;
                                    legShift = -wind * 2.2f;
                                    legAng = -wind * 16f;
                                }else{
                                    legShift = kickCurve * 5.0f;
                                    legAng = kickCurve * 24f;
                                }
                            }
                            float lx = unit.x + Angles.trnsx(unit.rotation, legShift);
                            float ly = unit.y + Angles.trnsy(unit.rotation, legShift);
                            if(legOutlineRegion != null && legOutlineRegion.found()){
                                applyOutlineColor(unit);
                                Draw.rect(legOutlineRegion, lx, ly,
                                    legOutlineRegion.width * legOutlineRegion.scl() * i,
                                    legOutlineRegion.height * legOutlineRegion.scl(),
                                    legRot + legAng);
                            }
                            applyColor(unit);
                            Draw.rect(legRegion, lx, ly, lw * i, lh, legRot + legAng);
                        }
                    }
                    if(baseRegion != null && baseRegion.found()){
                        applyColor(unit);
                        Draw.rect(baseRegion, unit.x, unit.y, legRot);
                    }
                }

                // Draw animated arms cleanly protruding out in front of the shoulders (no ugly rotation)
                if(armRegion != null && armRegion.found()){
                    float armPhase = st != null ? st.armPhase : 0f;
                    float moveBlend = st != null ? st.moveBlend : 0f;
                    float sprintBlend = st != null ? st.sprintBlend : 0f;
                    float lungeBlend = st != null ? st.lungeBlend : 0f;

                    float armStride = Mathf.lerp(2.7f, 3.6f, sprintBlend);
                    float armWave = -Mathf.sin(armPhase, 5.2f, 1f) * moveBlend;

                    float aw = armRegion.width * armRegion.scl();
                    float ah = armRegion.height * armRegion.scl();
                    float baseRot = unit.rotation - 90f;

                    for(int i : Mathf.signs){
                        float forwardShift = 1.6f + (i * armWave) * armStride + 2.6f * lungeBlend;
                        float sideShift = 0f;

                        float punchVal = st != null ? (i < 0 ? st.leftPunchAnim : st.rightPunchAnim) : 0f;
                        boolean crossStyle = st != null && (i < 0 ? st.leftCrossStyle : st.rightCrossStyle);
                        if(punchVal > 0f){
                            float snap = punchVal > 0.7f
                                ? Interp.pow2Out.apply((1f - punchVal) / 0.3f)
                                : Interp.pow2In.apply(punchVal / 0.7f);
                            forwardShift += snap * (crossStyle ? 6.8f : 5.5f);
                            sideShift -= i * snap * (crossStyle ? 1.4f : 0.4f);
                        }

                        if(kickCurve > 0f){
                            forwardShift -= i * kickCurve * 1.4f;
                        }

                        float armX = unit.x + Angles.trnsx(unit.rotation, forwardShift) + Angles.trnsx(unit.rotation - 90f, sideShift);
                        float armY = unit.y + Angles.trnsy(unit.rotation, forwardShift) + Angles.trnsy(unit.rotation - 90f, sideShift);

                        if(armOutlineRegion != null && armOutlineRegion.found()){
                            applyOutlineColor(unit);
                            Draw.rect(armOutlineRegion, armX, armY,
                                armOutlineRegion.width * armOutlineRegion.scl() * i,
                                armOutlineRegion.height * armOutlineRegion.scl(),
                                baseRot);
                        }

                        applyColor(unit);
                        Draw.rect(armRegion, armX, armY, aw * i, ah, baseRot);
                    }
                }

                Draw.color();
            }
        };
    }

    public static void init(){
        resolveSounds();
        if(eventsHooked) return;
        eventsHooked = true;

        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit != null && e.unit.type == ixen && e.unit.controller() instanceof MeleeCommandAI ai){
                ai.notifyThreatened();
            }
        });

        Events.on(ResetEvent.class, e -> states.clear());
    }

    public static class RightKickAbility extends Ability{
        @Override
        public String localized(){
            return "Right Kick";
        }

        @Override
        public void addStats(Table t){
            t.add("[lightgray]Every 9 confirmed melee hits empower the next attack into a Right Kick dealing 2x damage (92) and stunning enemies hit for 1.5 seconds.[]").wrap().width(320f);
        }

        @Override
        public void displayBars(Unit unit, Table bars){
            IxenState st = getState(unit.id);
            bars.add(new Bar(
                () -> st.kickHits >= KICK_HITS_REQUIRED
                    ? "RIGHT KICK READY!"
                    : "Right Kick: " + st.kickHits + "/" + KICK_HITS_REQUIRED,
                () -> st.kickHits >= KICK_HITS_REQUIRED ? Pal.accent : unit.team.color,
                () -> Mathf.clamp((float)st.kickHits / KICK_HITS_REQUIRED)
            )).row();
        }
    }

    private static MeleeCommandAI createAI(){
        MeleeCommandAI ai = new MeleeCommandAI();
        ai.viewRange = 160f;
        ai.meleeEdgeReach = 24f;
        ai.desiredEdgeDist = 16f;
        ai.tooCloseEdgeDist = 10f;
        ai.walkStartEdgeDist = 80f;
        ai.sprintResumeEdgeDist = 94f;
        ai.lungeMinEdgeDist = 30f;
        ai.lungeMaxEdgeDist = 62f;
        ai.lungeBurstSpeed = 4.2f;
        ai.lungeBurstTicks = 8f;
        ai.lungeCooldownDuration = 240f;
        ai.onLungeStart = u -> emitTrailSmoke(u);
        return ai;
    }

    private static void emitTrailSmoke(Unit unit){
        float bx = unit.x - Angles.trnsx(unit.rotation, 5.5f) + Mathf.range(2.2f);
        float by = unit.y - Angles.trnsy(unit.rotation, 5.5f) + Mathf.range(2.2f);
        trailSmokeEffect.at(bx, by);
    }

    private static void resolveSounds(){
        if(soundsLoaded || Vars.headless) return;
        soundsLoaded = true;
        punchSound = loadSoundOgg("punch");
        heavyPunchSound = loadSoundOgg("heavy-punch");
    }

    private static Sound loadSoundOgg(String baseName){
        try{
            String[] keys = {baseName + ".ogg", "sounds/" + baseName + ".ogg"};
            for(String k : keys){
                Sound existing = Core.assets.getOrNull(k, Sound.class);
                if(existing != null) return existing;
            }
            Fi target = null;
            if(Vars.tree.get("sounds/" + baseName + ".ogg").exists()){
                target = Vars.tree.get("sounds/" + baseName + ".ogg");
            }else{
                Mods.LoadedMod mod = Vars.mods.locateMod("ability-additions");
                if(mod != null && mod.root != null){
                    String[] rels = {"sounds/" + baseName + ".ogg", "assets/sounds/" + baseName + ".ogg"};
                    for(String rel : rels){
                        Fi fi = mod.root.child(rel);
                        if(fi.exists()){
                            target = fi;
                            break;
                        }
                    }
                }
            }
            if(target != null && target.exists()){
                return new Sound(target);
            }
        }catch(Throwable t){
            Log.err("[AbilityAdditions] Failed loading sound " + baseName + ".ogg", t);
        }
        return Sounds.none;
    }

    private static void playSound(Sound s, float x, float y, float pitch, float vol){
        if(Vars.headless || s == null || s == Sounds.none) return;
        s.at(x, y, pitch, vol);
    }

    private static IxenState getState(int id){
        IxenState st = states.get(id);
        if(st == null){
            st = new IxenState();
            states.put(id, st);
        }
        return st;
    }

    private static class IxenState{
        boolean posInit = false;
        float prevX = 0f;
        float prevY = 0f;
        float armPhase = 0f;
        float moveBlend = 0f;
        float sprintBlend = 0f;
        float lungeBlend = 0f;

        boolean isCross = false;
        float leftPunchAnim = 0f;
        float rightPunchAnim = 0f;
        boolean leftCrossStyle = false;
        boolean rightCrossStyle = false;

        int kickHits = 0;
        float kickAnim = 0f;
    }
                                       }
