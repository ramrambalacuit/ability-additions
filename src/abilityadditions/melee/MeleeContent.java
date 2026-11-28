package abilityadditions.melee;

import abilityadditions.melee.units.*;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.blocks.units.*;

public class MeleeContent{

    public static void load(){
        Ixen.load();

        if(Blocks.groundFactory instanceof UnitFactory uf){
            uf.plans.add(new UnitFactory.UnitPlan(
                Ixen.ixen,
                60f * 12f,
                ItemStack.with(Items.silicon, 8, Items.lead, 10)
            ));
        }
    }

    public static void init(){
        Ixen.init();
    }
} 
