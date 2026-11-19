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
    public static BasicBulletType barrageSnowball;

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

        barrageSnowball = new BasicBulletType(5.8f, 1f){{
            width = 9f;
            height = 9f;
            lifetime = 46f;
            shrinkX = 0f;
            shrinkY = 0f;
            frontColor = Color.white;
            backColor = Color.valueOf("aee5ff");
            lightColor = Color.valueOf("dff5ff");
            trailColor = Color.valueOf("9bdfff");
            trailLength = 7;
            trailWidth = 1.8f;
            knockback = 20f;
            status = StatusEffects.freezing;
            statusDuration = 120f;
            hitEffect = AbilityEffects.snowballHit;
            despawnEffect = AbilityEffects.snowballHit;
            collidesTeam = false;
        }};

        bob = new UnitType("bob"){
            {
                localizedName = "Bob";
                description = "A festive snow-dwelling prankster. Harmless until you annoy him.";
                health = 5000f;
                armor = 0f;
                speed = 1.65f;
                hitSize = 8f;
                rotateSpeed = 6f;
                mechLegColor = Color.valueOf("d4e6f5");
                lightColor = Color.valueOf("d8f3ff");
                lightRadius = 35f;

                hidden = true;
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
                // Uses Dagger's existing sprites; skip generating mod atlas icons
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
                return true;
            }

            @Override
            public void draw(Unit unit){
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
