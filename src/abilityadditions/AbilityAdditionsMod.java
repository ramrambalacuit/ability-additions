package abilityadditions;

import abilityadditions.content.*;
import abilityadditions.core.*;
import abilityadditions.melee.*;
import mindustry.mod.*;

public class AbilityAdditionsMod extends Mod{

    @Override
    public void loadContent(){
        PierceTurret.load();
        TestDummyContent.load();
        JotaroContent.load();
        MeleeContent.load();

        AbilityManager manager = AbilityManager.instance();
        SerpuloTurretAbilities.load(manager);
        manager.applyStats();
    }

    @Override
    public void init(){
        PierceTurret.attachTechTree();
        TestDummyContent.init();
        JotaroContent.init();
        MeleeContent.init();
        AbilityManager.instance().initEvents();
    }
}
