package abilityadditions.core;

import arc.math.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.entities.*;
import mindustry.gen.*;

public class MeleeCommandAI extends CommandAI{

    public float sprintStartEdgeDist = 36f;
    public float sprintStopEdgeDist = 26f;
    public float dashMinEdgeDist = 44f;
    public float dashMaxEdgeDist = 116f;
    public float dashCooldownDuration = 210f; // 3.5s
    public float dashImpulse = 7.2f;

    public float dashCooldown = 0f;
    public boolean sprinting = false;
    public boolean dashTriggeredThisTick = false;

    @Override
    public void updateTargeting(){
        super.updateTargeting();
        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }
    }

    @Override
    public void updateUnit(){
        dashTriggeredThisTick = false;
        if(dashCooldown > 0f){
            dashCooldown = Math.max(0f, dashCooldown - Time.delta);
        }

        super.updateUnit();

        // If an air target ever got assigned while targetAir == false, drop it immediately so ground chase takes priority.
        if(attackTarget instanceof Unit u && u.isFlying() && !unit.type.targetAir){
            attackTarget = null;
        }

        if(attackTarget == null || Units.invalidateTarget(attackTarget, unit.team, unit.x, unit.y, Float.MAX_VALUE)){
            sprinting = false;
            return;
        }

        float targetRad = attackTarget instanceof Sized s ? s.hitSize() * 0.5f : 0f;
        float edgeDst = unit.dst(attackTarget) - unit.hitSize * 0.5f - targetRad;

        // Hysteresis prevents flicker between sprint/walk
        if(!sprinting && edgeDst > sprintStartEdgeDist){
            sprinting = true;
        }else if(sprinting && edgeDst <= sprintStopEdgeDist){
            sprinting = false;
        }

        // Dash gap-closer when enemy is far enough away
        if(dashCooldown <= 0f && edgeDst >= dashMinEdgeDist && edgeDst <= dashMaxEdgeDist){
            float dashAng = Angles.angle(unit.x, unit.y, attackTarget.x(), attackTarget.y());
            unit.vel.add(Tmp.v1.trns(dashAng, dashImpulse));
            dashCooldown = dashCooldownDuration;
            dashTriggeredThisTick = true;
        }

        // Keep pushing right up to edge-to-edge melee distance instead of stopping early at center range
        float desiredCenterDist = unit.hitSize * 0.5f + targetRad + 5f;
        if(edgeDst > 10f && edgeDst <= 56f){
            moveTo(attackTarget, desiredCenterDist);
        }

        // Ensure punches fire reliably against big units & buildings when within edge reach
        if(!hasStance(UnitStance.holdFire) && edgeDst <= Math.max(unit.range(), 24f)){
            unit.lookAt(attackTarget);
            unit.aim(attackTarget);
            unit.controlWeapons(true, true);
        }
    }
}
