package abilityadditions;

import abilityadditions.content.*;
import abilityadditions.core.*;
import mindustry.mod.*;

public class AbilityAdditionsMod extends Mod{

    @Override
    public void loadContent(){
        PierceTurret.load();

        AbilityManager manager = AbilityManager.instance();
        SerpuloTurretAbilities.load(manager);
        manager.applyStats();
    }

    @Override
    public void init(){
        PierceTurret.attachTechTree();
        AbilityManager.instance().initEvents();
    }
}
