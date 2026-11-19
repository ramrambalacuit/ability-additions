package abilityadditions.content;

import abilityadditions.core.AbilityManager;
import abilityadditions.core.AbilityProfile;
import abilityadditions.core.AbilityTrigger;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.StatusEffects;
import mindustry.type.StatusEffect;

public class SerpuloTurretAbilities{

    public static void load(AbilityManager manager){
        StatusEffect movDamageIncrease = Vars.content.statusEffect("mov-damage-increase-tier-1");
        if(movDamageIncrease == null){
            Log.warn("[Ability Additions] Could not find 'mov-damage-increase-tier-1', falling back to StatusEffects.sapped.");
            movDamageIncrease = StatusEffects.sapped;
        }

        StatusEffect movDeceleration = Vars.content.statusEffect("mov-motor-deceleration-tier-1");
        if(movDeceleration == null){
            Log.warn("[Ability Additions] Could not find 'mov-motor-deceleration-tier-1', falling back to StatusEffects.slow.");
            movDeceleration = StatusEffects.slow;
        }

        manager.register(Blocks.duo, AbilityProfile.overdriveRush(
            "Overdrive Rush",
            AbilityTrigger.everyShots(10),
            3f,
            1.5f,
            1.65f,
            1.45f,
            movDamageIncrease,
            4f
        ));

        manager.register(Blocks.scatter, AbilityProfile.missileSwarm(
            "Snake Swarm",
            AbilityTrigger.chancePerCycle(0.10f, 2.0f),
            6,
            1.4f,
            2f,
            movDeceleration,
            3f
        ));
    }
}
