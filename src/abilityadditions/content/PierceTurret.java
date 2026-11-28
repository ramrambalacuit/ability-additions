package abilityadditions.content;

import arc.Events;
import arc.graphics.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

import static mindustry.type.ItemStack.*;

public class PierceTurret{

    public static Block pierce;
    public static StatusEffect lancerParalyzed;

    public static void load(){
        lancerParalyzed = new StatusEffect("lancer-paralyzed"){{
            speedMultiplier = 0f;
            reloadMultiplier = 0f;
            disarm = true;
            show = false;
            outline = false;
            color = Color.valueOf("2a3242");
        }
            @Override
            public void load(){
                super.load();
                uiIcon = StatusEffects.shocked.uiIcon;
                fullIcon = StatusEffects.shocked.fullIcon;
            }
        };

        final StatusEffect movDecel = Vars.content.statusEffect("mov-motor-deceleration-tier-1");

        pierce = new ItemTurret("pierce"){{
            localizedName = "Pierce";
            description = "Long-range 1x1 rifle turret with 2x Duo range. Fires very fast, hard-hitting rounds that pierce straight through whole lines of enemies without losing damage. Slow to reload.";
            requirements(Category.turret, with(Items.copper, 75, Items.graphite, 35, Items.lead, 40));

            size = 1;
            health = 420;
            armor = 2f;
            range = 368f;
            reload = 130f;
            rotateSpeed = 7f;
            inaccuracy = 0.4f;
            shootCone = 6f;
            shootY = 3f;
            recoil = 1.2f;
            maxAmmo = 32;
            outlineIcon = true;
            outlineColor = Pal.darkerMetal;
            outlineRadius = 4;
            shootSound = Sounds.shootDuo;
            ammoUseEffect = Fx.casing1;
            coolant = consumeCoolant(0.1f);
            coolantMultiplier = 6f;
            researchCostMultiplier = 0.25f;

            ammo(
                Items.copper, new BasicBulletType(30f, 140f){{
                    width = 6f;
                    height = 13f;
                    lifetime = 14f;
                    ammoMultiplier = 4f;
                    pierce = true;
                    pierceCap = 8;
                    pierceBuilding = false;
                    trailLength = 10;
                    trailWidth = 1.4f;
                    frontColor = Pal.copperAmmoFront;
                    backColor = trailColor = hitColor = Color.valueOf("f9c27a");
                    lightColor = Color.valueOf("fff8e8");
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                }},
                Items.scrap, new BasicBulletType(34f, 110f){{
                    width = 5.5f;
                    height = 12f;
                    lifetime = 12f;
                    ammoMultiplier = 4f;
                    reloadMultiplier = 0.85f;
                    pierce = true;
                    pierceCap = 10;
                    pierceBuilding = false;
                    pierceArmor = true;
                    trailLength = 9;
                    trailWidth = 1.2f;
                    frontColor = Color.valueOf("e0b28d");
                    backColor = trailColor = hitColor = Color.valueOf("9b928b");
                    lightColor = Color.valueOf("e0b28d");
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                    if(movDecel != null){
                        status = movDecel;
                        statusDuration = 180f;
                    }
                }},
                Items.graphite, new BasicBulletType(28f, 190f){{
                    width = 7.5f;
                    height = 15f;
                    lifetime = 15f;
                    ammoMultiplier = 3f;
                    reloadMultiplier = 0.8f;
                    pierce = true;
                    pierceCap = 10;
                    pierceBuilding = false;
                    pierceArmor = true;
                    trailLength = 11;
                    trailWidth = 1.8f;
                    frontColor = Color.valueOf("95abd9");
                    backColor = trailColor = hitColor = Color.valueOf("6b77a1");
                    lightColor = Color.valueOf("95abd9");
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                }},
                Items.silicon, new BasicBulletType(32f, 150f){{
                    width = 6f;
                    height = 13f;
                    lifetime = 13f;
                    ammoMultiplier = 6f;
                    reloadMultiplier = 1.35f;
                    pierce = true;
                    pierceCap = 8;
                    pierceBuilding = false;
                    trailLength = 10;
                    trailWidth = 1.4f;
                    frontColor = Color.valueOf("8e9097");
                    backColor = trailColor = hitColor = Color.valueOf("767a84");
                    lightColor = Color.valueOf("8e9097");
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                }},
                Items.pyratite, new BasicBulletType(30f, 160f){{
                    width = 6f;
                    height = 14f;
                    lifetime = 14f;
                    ammoMultiplier = 4f;
                    reloadMultiplier = 1.2f;
                    makeFire = true;
                    status = StatusEffects.burning;
                    statusDuration = 300f;
                    splashDamage = 40f;
                    splashDamageRadius = 12f;
                    pierce = true;
                    pierceCap = 8;
                    pierceBuilding = false;
                    trailLength = 11;
                    trailWidth = 1.4f;
                    frontColor = Color.valueOf("ffaa5f");
                    backColor = trailColor = hitColor = Color.valueOf("d37f47");
                    lightColor = Color.valueOf("ffaa5f");
                    hitEffect = despawnEffect = Fx.hitBulletColor;
                }}
            );
        }};

        // Try to add the research node right away. If the base tech tree isn't ready yet
        // at this point, retry once the game has finished loading. attachTechTree() is safe
        // to call any number of times, it never adds the node twice.
        attachTechTree();
        Events.on(ClientLoadEvent.class, e -> attachTechTree());
    }

    /** Adds Pierce to the research tree as a child of Duo (idempotent). */
    public static void attachTechTree(){
        if(pierce == null) return;
        if(TechTree.all.find(t -> t.content == pierce) != null) return;

        TechNode duoNode = TechTree.all.find(t -> t.content == Blocks.duo);
        if(duoNode == null) return; // tech tree not ready yet, will be retried

        new TechNode(duoNode, pierce, pierce.researchRequirements());
    }
}
