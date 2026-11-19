package abilityadditions;

import abilityadditions.content.SerpuloTurretAbilities;
import abilityadditions.core.AbilityManager;
import arc.util.Log;
import mindustry.mod.Mod;

public class AbilityAdditionsMod extends Mod{

    @Override
    public void init(){
        AbilityManager manager = AbilityManager.instance();
        SerpuloTurretAbilities.load(manager);
        manager.applyStats();
        manager.initEvents();

        Log.info("[Ability Additions] Loaded Serpulo turret abilities.");
    }
} 
