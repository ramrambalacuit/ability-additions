package abilityadditions.bob;

import abilityadditions.content.AbilityEffects;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.game.Team;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.MultiPacker;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class BobUnit{
    public static UnitType bob;
    public static BasicBulletType snowballBullet;

    public static void load(){
        snowballBullet = new BasicBulletType(4.5f, 1f){{
            width = 7f;
            height = 7f;
            lifetime = 48f;
            shrinkX = 0f;
            shrinkY = 0f;
            frontColor = Color.white;
            backColor = Color.valueOf("cfefff");
            lightColor = Color.valueOf("dff5ff");
            trailColor = Color.valueOf("bfe8ff");
            trailLength = 5;
            trailWidth = 1.3f;
            knockback = 2.5f;
            status = StatusEffects.freezing;
            statusDuration = 60f;
            hitEffect = AbilityEffects.snowballHit;
            despawnEffect = AbilityEffects.snowballHit;
            collidesTeam = false;
        }};

        bob = new UnitType("bob"){
            {
                localizedName = "Bob";
                description = "A festive snow-dwelling prankster. Calmly trolls your base until annoyed.";
                health = 5000f;
                armor = 0f;
                speed = 1.55f;
                hitSize = 8f;
                rotateSpeed = 5f;
                mechLegColor = Color.valueOf("d4e6f5");
                lightColor = Color.valueOf("d8f3ff");
                lightRadius = 35f;

                isEnemy = false;
                useUnitCap = false;
                playerControllable = false;
                logicControllable = false;

                constructor = UnitTypes.dagger.constructor;
                aiController = BobAI::new;

                Weapon snowWeapon = new Weapon(){{
                    x = 0f;
                    y = 0f;
                    shootX = 0f;
                    shootY = 5f;
                    mirror = false;
                    reload = 60f;
                    rotate = false;
                    inaccuracy = 4f;
                    shootSound = Vars.headless ? Sounds.none : Vars.tree.loadSound("snow");
                    showStatSprite = false;
                    bullet = snowballBullet;
                }};
                weapons.add(snowWeapon);
            }

            @Override
            public void createIcons(MultiPacker packer){
            }

            @Override
            public void load(){
                super.load();
                if(UnitTypes.dagger != null){
                    region = UnitTypes.dagger.region;
                    legRegion = UnitTypes.dagger.legRegion;
                    baseRegion = UnitTypes.dagger.baseRegion;
                    cellRegion = UnitTypes.dagger.cellRegion;
                    outlineRegion = UnitTypes.dagger.outlineRegion;
                    shadowRegion = UnitTypes.dagger.shadowRegion;
                    previewRegion = UnitTypes.dagger.previewRegion;
                    fullIcon = UnitTypes.dagger.fullIcon;
                    uiIcon = UnitTypes.dagger.uiIcon;
                }
            }

            @Override
            public boolean targetable(Unit unit, Team targeter){
                return false;
            }

            @Override
            public boolean hittable(Unit unit){
                if(unit.controller() instanceof BobAI ai && ai.isSmokeCloaked()){
                    return false;
                }
                return true;
            }

            @Override
            public void draw(Unit unit){
                boolean cloaked = unit.controller() instanceof BobAI ai && ai.isSmokeCloaked();

                if(cloaked){
                    Draw.z(Layer.groundUnit + 0.5f);
                    if(region != null && region.found()){
                        Draw.color(0.85f, 0.92f, 1f, 0.16f);
                        Draw.rect(region, unit.x, unit.y, unit.rotation - 90f);
                    }
                    Draw.color(0.95f, 0.35f, 0.35f, 0.14f);
                    Fill.circle(unit.x, unit.y, 2.5f);
                    Draw.color();
                    Draw.z();
                    return;
                }

                super.draw(unit);

                Draw.z(Layer.groundUnit + 0.5f);
                if(region != null && region.found()){
                    Draw.color(Color.valueOf("f0f9ff"), 0.72f);
                    Draw.rect(region, unit.x, unit.y, unit.rotation - 90f);
                }

                float hx = unit.x + Angles.trnsx(unit.rotation - 180f, 1.5f);
                float hy = unit.y + Angles.trnsy(unit.rotation - 180f, 1.5f);
                float tipX = hx + Angles.trnsx(unit.rotation - 150f, 6.5f);
                float tipY = hy + Angles.trnsy(unit.rotation - 150f, 6.5f);

                Draw.color(Color.valueOf("e53935"));
                Fill.circle(hx, hy, 3.1f);
                Fill.circle(Mathf.lerp(hx, tipX, 0.5f), Mathf.lerp(hy, tipY, 0.5f), 2.1f);
                Draw.color(Color.white);
                Fill.circle(tipX, tipY, 1.5f);

                Draw.color(Color.valueOf("2ecc71"));
                Fill.circle(
                    unit.x + Angles.trnsx(unit.rotation, 2.2f),
                    unit.y + Angles.trnsy(unit.rotation, 2.2f),
                    1.6f
                );

                Draw.color();
                Draw.z();
            }
        };
    }
} 
