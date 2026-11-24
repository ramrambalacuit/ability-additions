package abilityadditions.core;

import arc.math.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class MeleeWeapon extends Weapon{

    public MeleeType melee;
    public @Nullable MeleeStrikeHandler strikeHandler;

    public interface MeleeStrikeHandler{
        void onStrike(Unit unit, WeaponMount mount, float originX, float originY, float angle, MeleeWeapon weapon);
    }

    public MeleeWeapon(String name, MeleeType melee){
        super(name);
        this.melee = melee;
        this.predictTarget = false;
        this.inaccuracy = 0f;
        this.shootCone = 35f;
        this.ejectEffect = Fx.none;
        this.shootSound = Sounds.none;
        this.activeSound = Sounds.none;
        this.initialShootSound = Sounds.none;
        this.chargeSound = Sounds.none;
        syncBulletMarker();
    }

    public MeleeWeapon(MeleeType melee){
        this("", melee);
    }

    public void syncBulletMarker(){
        if(melee == null) return;
        this.bullet = new BulletType(0f, 0f){{
            speed = 999f;
            lifetime = 1f;
            rangeOverride = melee.reach;
            collidesAir = melee.hitAir;
            collidesGround = melee.hitGround;
            hittable = false;
            absorbable = false;
            reflectable = false;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            hitEffect = Fx.none;
            despawnEffect = Fx.none;
        }};
    }

    @Override
    public void init(){
        super.init();
        syncBulletMarker();
    }

    @Override
    public float range(){
        return melee != null ? melee.reach : super.range();
    }

    @Override
    public float dps(){
        if(melee == null || reload <= 0f) return 0f;
        return (melee.damage / reload) * shoot.shots * 60f;
    }

    @Override
    public void addStats(UnitType u, Table t){
        if(melee == null) return;
        t.row();
        t.add("[lightgray]" + Stat.damage.localized() + ": [white]" + Strings.autoFixed(melee.damage, 1) + " (Melee AoE)");
        t.row();
        t.add("[lightgray]" + Stat.range.localized() + ": [white]" + Strings.autoFixed(melee.reach / tilesize, 1) + " " + StatUnit.blocks.localized());
        if(reload > 0f){
            t.row();
            t.add("[lightgray]" + Stat.reload.localized() + ": " + (mirror ? "2x " : "") + "[white]" + Strings.autoFixed(60f / reload * shoot.shots, 2) + " " + StatUnit.perSecond.localized());
        }
    }

    @Override
    protected void bullet(Unit unit, WeaponMount mount, float xOffset, float yOffset, float angleOffset, Mover mover){
        if(!unit.isAdded() || melee == null) return;

        mount.charging = false;
        float weaponRotation = unit.rotation - 90f + (rotate ? mount.rotation : baseRotation);
        float mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
        float mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
        float originX = mountX + Angles.trnsx(weaponRotation, this.shootX + xOffset, this.shootY + yOffset);
        float originY = mountY + Angles.trnsy(weaponRotation, this.shootX + xOffset, this.shootY + yOffset);
        float strikeAngle = bulletRotation(unit, mount, originX, originY) + angleOffset;

        // Zero bullets created! Execute direct melee strike or custom combo handler.
        if(strikeHandler != null){
            strikeHandler.onStrike(unit, mount, originX, originY, strikeAngle, this);
        }else{
            melee.execute(unit, originX, originY, strikeAngle);
        }

        if(shootSound != null && shootSound != Sounds.none){
            shootSound.at(originX, originY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);
        }

        Effect.shake(shake, shake, originX, originY);
        mount.recoil = 1f;
        if(recoils > 0){
            mount.recoils[mount.barrelCounter % recoils] = 1f;
        }
        mount.heat = 1f;
    }
          }
