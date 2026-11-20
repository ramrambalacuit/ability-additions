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
        StatusEffect dmgTier1 = findStatus("mov-damage-increase-tier-1", StatusEffects.sapped);
        StatusEffect dmgTier2 = findStatus("mov-damage-increase-tier-2", dmgTier1);
        StatusEffect slowTier1 = findStatus("mov-motor-deceleration-tier-1", StatusEffects.slow);
        StatusEffect slowTier2 = findStatus("mov-motor-deceleration-tier-2", slowTier1);
        StatusEffect armorTier2 = findStatus("mov-armor-piercing-tier-2", dmgTier2);
        StatusEffect empTier2 = findStatus("mov-emp-tier-2", StatusEffects.shocked);
        StatusEffect slowReload1 = findStatus("mov-slow-reloading-tier-1", slowTier1);

        manager.register(Blocks.duo, AbilityProfile.overdriveRush(
            "Overdrive Rush", AbilityTrigger.everyShots(10),
            3f, 1.5f, 1.65f, 1.45f, dmgTier1, 4f
        ));

        manager.register(Blocks.scatter, AbilityProfile.missileSwarm(
            "Snake Swarm", AbilityTrigger.chancePerCycle(0.10f, 2.0f),
            1.5f, 1.4f, 2f, slowTier1, 3f
        ));

        manager.register(Blocks.arc, AbilityProfile.chainLightning(
            "Static Chain", AbilityTrigger.chance(0.25f),
            1.35f, 2, 68f, 0.75f, StatusEffects.shocked, 2f
        ));

        manager.register(Blocks.hail, AbilityProfile.seismicMortar(
            "Seismic Mortar", AbilityTrigger.everyShots(5),
            1.65f, 56f, 3.2f, dmgTier1, 3f
        ));

        manager.register(Blocks.scorch, AbilityProfile.heatRamp(
            "Thermal Ramp", 1.4f, 1.55f, 1.30f, 1.35f,
            0.011f, 0.18f, 5f, 3f, StatusEffects.melting, 2.5f
        ));

        manager.register(Blocks.salvo, AbilityProfile.salvoStages(
            "Escalating Barrel", 10f, 12f,
            1.30f, 1.25f, 1.55f, 1.45f, 1.25f,
            slowReload1, dmgTier2, 3f
        ));

        manager.register(Blocks.lancer, AbilityProfile.railEcho(
            "Rail Echo", AbilityTrigger.everyShots(3),
            0.45f, 345f, empTier2, 3f
        ));

        manager.register(Blocks.ripple, AbilityProfile.carpetBomblets(
            "Carpet Bomblets", AbilityTrigger.chancePerCycle(0.25f, 2.5f),
            1.25f, 3, 34f, 0.40f, slowTier2, 3f
        ));

        manager.register(Blocks.fuse, AbilityProfile.repulsorCone(
            "Repulsor Cone", AbilityTrigger.everyShots(15),
            1.40f, 130f, 6.2f, armorTier2, 3f
        ));

        manager.register(Blocks.swarmer, AbilityProfile.hunterMark(
            "Hunter's Mark", AbilityTrigger.chancePerCycle(0.20f, 1.5f),
            4f, 1.45f, slowTier2, 3f
        ));
    }

    private static StatusEffect findStatus(String name, StatusEffect fallback){
        StatusEffect found = Vars.content.statusEffect(name);
        if(found == null && name.startsWith("mov-")){
            found = Vars.content.statusEffect(name.substring(4));
        }
        if(found == null){
            Log.warn("[Ability Additions] Could not find '@', using fallback.", name);
            return fallback;
        }
        return found;
    }
}
