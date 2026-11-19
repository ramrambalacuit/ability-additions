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

        // Duo: Every 10 shots -> 3s rush with 1.5x dmg, 1.65x fire rate, 1.45x bullet speed, and mov debuff
        manager.register(Blocks.duo, new AbilityProfile(
            "Overdrive Rush",
            AbilityTrigger.everyShots(10),
            3f,
            1.5f,
            1.65f,
            1.45f,
            movDamageIncrease,
            4f
        ));
    }
} 
