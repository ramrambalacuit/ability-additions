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

        // Duo: Every 10 shots -> 3s rush (1.5x dmg, 1.65x fire rate, 1.45x speed + mov damage-increase)
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

        // Scatter: 10% chance per volley -> full 1.5s volley enhancer into homing snake missiles -> 2s Meltdown
        manager.register(Blocks.scatter, AbilityProfile.missileSwarm(
            "Snake Swarm",
            AbilityTrigger.chancePerCycle(0.10f, 2.0f),
            1.5f,
            1.4f,
            2f,
            movDeceleration,
            3f
        ));

        // Arc: 25% chance -> 1.35x bolt + bounces to 2 nearby ground enemies (75% dmg + shocked)
        manager.register(Blocks.arc, AbilityProfile.chainLightning(
            "Static Chain",
            AbilityTrigger.chance(0.25f),
            1.35f,
            2,
            68f,
            0.75f,
            StatusEffects.shocked,
            2f
        ));

        // Hail: Every 5th shell -> 1.65x dmg + pulls ground enemies inward + mov damage-increase
        manager.register(Blocks.hail, AbilityProfile.seismicMortar(
            "Seismic Mortar",
            AbilityTrigger.everyShots(5),
            1.65f,
            56f,
            3.2f,
            movDamageIncrease,
            3f
        ));

        // Scorch: Heat builds while firing -> ramps up to 1.85x dmg & 1.75x fire rate + melting -> 5s Meltdown at 100%
        manager.register(Blocks.scorch, AbilityProfile.heatRamp(
            "Thermal Ramp",
            1.85f,
            1.75f,
            0.055f,
            0.22f,
            5f,
            StatusEffects.melting,
            2.5f
        ));
    }
}
