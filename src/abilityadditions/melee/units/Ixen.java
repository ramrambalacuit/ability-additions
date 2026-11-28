package abilityadditions.melee.units;

import abilityadditions.melee.core.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;

public class Ixen{

    public static UnitType ixen;
    public static StatusEffect sprintBuff;
    public static Effect trailSmokeEffect;

    private static final IntMap<ArmAnimState> armStates = new IntMap<>();
    private static boolean eventsHooked = false;

    public static void load(){
        sprintBuff = new StatusEffect("ixen-sprint"){{
            speedMultiplier = 1.28f;
            show = false;
        }};

        trailSmokeEffect = new Effect(20f, e -> {
            Draw.color(Color.lightGray, Pal.gray, 0.35f);
            Draw.alpha(0.42f * e.fout());
            Fill.circle(e.x, e.y, 1.9f * e.fout() + 0.55f);
        }).layer(Layer.debris);

        ixen = new UnitType("ixen"){
            public TextureRegion armRegion;
            public TextureRegion armOutlineRegion;
            private boolean outlineLookedUp = false;

            {
                localizedName = "Ixen";
                description = "Tier 1 agile melee unit. Sprints across long gaps, lunges into close combat, and circles targets at arm length.";
                constructor = MechUnit::create;
                aiController = () -> createAI();
                controller = u -> createAI();

                flying = false;
                speed = 0.60f;
                rotateSpeed = 6.5f;
                baseRotateSpeed = 6.5f;
                hitSize = 9f;
                health = 180f;
                armor = 2f;
                drag = 0.35f;
                accel = 0.55f;

                targetAir = false;
                targetGround = true;
                autoFindTarget = false;
                faceTarget = true;
                omniMovement = true;
                mechStepParticles = true;

                stepSound = UnitTypes.dagger != null ? UnitTypes.dagger.stepSound : Sounds.mechStepSmall;
                stepSoundVolume = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundVolume : 0.45f;
                stepSoundPitch = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitch : 1f;
                stepSoundPitchRange = UnitTypes.dagger != null ? UnitTypes.dagger.stepSoundPitchRange : 0.1f;

                allowedInPayloads = true;
            }

            @Override
            public void load(){
                super.load();
                armRegion = Core.atlas.find(name + "-arm");
                armOutlineRegion = Core.atlas.find(name + "-arm-outline");
                outlineLookedUp = false;
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

                ArmAnimState st = getState(unit.id);
                float moveSpeed = unit.vel.len();
                float moveFrac = Mathf.clamp(moveSpeed / Math.max(0.05f, speed));
                if(moveFrac > 0.04f){
                    st.walkPhase += moveSpeed * Time.delta;
                    st.moveBlend = Mathf.lerpDelta(st.moveBlend, Math.min(1f, moveFrac), 0.25f);
                }else{
                    st.moveBlend = Mathf.lerpDelta(st.moveBlend, 0f, 0.22f);
                }

                boolean sprinting = false;
                boolean lunging = false;
                if(unit.controller() instanceof MeleeCommandAI ai){
                    sprinting = ai.sprinting;
                    lunging = ai.dashTimer > 0f;
                }

                st.sprintBlend = Mathf.lerpDelta(st.sprintBlend, sprinting ? 1f : 0f, 0.18f);
                st.lungeBlend = Mathf.lerpDelta(st.lungeBlend, lunging ? 1f : 0f, lunging ? 0.45f : 0.16f);

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
                super.drawMech(mech);
                if(armRegion == null || !armRegion.found()) return;

                if(!outlineLookedUp || armOutlineRegion == null || !armOutlineRegion.found()){
                    armOutlineRegion = Core.atlas.find(name + "-arm-outline");
                    outlineLookedUp = true;
                }

                Unit unit = (Unit)mech;
                ArmAnimState st = armStates.get(unit.id);
                float walkPhase = st != null ? st.walkPhase : 0f;
                float moveBlend = st != null ? st.moveBlend : 0f;
                float sprintBlend = st != null ? st.sprintBlend : 0f;
                float lungeBlend = st != null ? st.lungeBlend : 0f;

                float swingAmp = Mathf.lerp(2.0f, 2.7f, sprintBlend);
                float walkWave = Mathf.sin(walkPhase, 4.4f, swingAmp) * moveBlend;
                float sprintBack = -1.35f * sprintBlend;
                float lungeForward = 2.6f * lungeBlend;

                float w = armRegion.width * armRegion.scl();
                float h = armRegion.height * armRegion.scl();

                for(int i : Mathf.signs){
                    float forwardShift = i * walkWave + sprintBack + lungeForward;
                    float sideShift = i * (0.35f * sprintBlend - 0.35f * lungeBlend);
                    float armX = unit.x + Angles.trnsx(unit.rotation, forwardShift) + Angles.trnsx(unit.rotation - 90f, sideShift);
                    float armY = unit.y + Angles.trnsy(unit.rotation, forwardShift) + Angles.trnsy(unit.rotation - 90f, sideShift);
                    float armRot = unit.rotation - 90f + i * (walkWave * 2.2f - sprintBlend * 7f + lungeBlend * 9f);

                    if(armOutlineRegion != null && armOutlineRegion.found()){
                        applyOutlineColor(unit);
                        Draw.rect(armOutlineRegion, armX, armY,
                            armOutlineRegion.width * armOutlineRegion.scl() * i,
                            armOutlineRegion.height * armOutlineRegion.scl(),
                            armRot);
                    }

                    applyColor(unit);
                    Draw.rect(armRegion, armX, armY, w * i, h, armRot);
                }

                Draw.color();
            }
        };
    }

    public static void init(){
        if(eventsHooked) return;
        eventsHooked = true;

        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit != null && e.unit.type == ixen && e.unit.controller() instanceof MeleeCommandAI ai){
                ai.notifyThreatened();
            }
        });

        Events.on(ResetEvent.class, e -> armStates.clear());
    }

    private static MeleeCommandAI createAI(){
        MeleeCommandAI ai = new MeleeCommandAI();
        ai.viewRange = 160f;
        ai.meleeEdgeReach = 22f;
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

    private static ArmAnimState getState(int id){
        ArmAnimState st = armStates.get(id);
        if(st == null){
            st = new ArmAnimState();
            armStates.put(id, st);
        }
        return st;
    }

    private static class ArmAnimState{
        float walkPhase = 0f;
        float moveBlend = 0f;
        float sprintBlend = 0f;
        float lungeBlend = 0f;
    }
                      }
