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

        // Duo: Every 10 shots -> 3s rush with 1.5x dmg, 1.65x fire rate, 1.45x bullet speed, and mov damage-increase debuff
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

        // Scatter: 10% chance per volley -> 2 missiles shatter into 12 snake mini-missiles + 4s Meltdown + mov deceleration
        manager.register(Blocks.scatter, AbilityProfile.missileSwarm(
            "Snake Swarm",
            AbilityTrigger.chancePerCycle(0.10f, 2.0f),
            2,
            6,
            1.5f,
            0.55f,
            4f,
            movDeceleration,
            3f
        ));
    }
}
