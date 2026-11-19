package abilityadditions;

import abilityadditions.bob.BobSpawner;
import abilityadditions.bob.BobUnit;
import abilityadditions.content.SerpuloTurretAbilities;
import abilityadditions.core.AbilityManager;
import arc.util.Log;
import mindustry.content.UnitTypes;
import mindustry.mod.Mod;

public class AbilityAdditionsMod extends Mod{

    @Override
    public void loadContent(){
        BobUnit.load();
    }

    @Override
    public void init(){
        if(BobUnit.bob != null && UnitTypes.dagger != null){
            BobUnit.bob.fullIcon = UnitTypes.dagger.fullIcon;
            BobUnit.bob.uiIcon = UnitTypes.dagger.uiIcon;
        }

        AbilityManager manager = AbilityManager.instance();
        SerpuloTurretAbilities.load(manager);
        manager.applyStats();
        manager.initEvents();

        BobSpawner.init();

        Log.info("[Ability Additions] Loaded Serpulo turret abilities and Bob.");
    }
}
