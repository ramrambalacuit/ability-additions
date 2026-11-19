package abilityadditions.core;

import mindustry.type.StatusEffect;

public class AbilityProfile{
    public enum Mode{
        OVERDRIVE_RUSH,
        MISSILE_SWARM
    }

    public final Mode mode;
    public final String abilityName;
    public final AbilityTrigger trigger;
    public final float durationTicks;
    public final float damageMultiplier;
    public final float fireRateMultiplier;
    public final float bulletSpeedMultiplier;
    public final float meltdownTicks;
    public final int missileCount;
    public final StatusEffect debuff;
    public final float debuffDurationTicks;

    private AbilityProfile(
        Mode mode,
        String abilityName,
        AbilityTrigger trigger,
        float durationSeconds,
        float damageMultiplier,
        float fireRateMultiplier,
        float bulletSpeedMultiplier,
        float meltdownSeconds,
        int missileCount,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        this.mode = mode;
        this.abilityName = abilityName;
        this.trigger = trigger;
        this.durationTicks = durationSeconds * 60f;
        this.damageMultiplier = damageMultiplier;
        this.fireRateMultiplier = fireRateMultiplier;
        this.bulletSpeedMultiplier = bulletSpeedMultiplier;
        this.meltdownTicks = meltdownSeconds * 60f;
        this.missileCount = missileCount;
        this.debuff = debuff;
        this.debuffDurationTicks = debuffDurationSeconds * 60f;
    }

    public static AbilityProfile overdriveRush(
        String abilityName,
        AbilityTrigger trigger,
        float durationSeconds,
        float damageMultiplier,
        float fireRateMultiplier,
        float bulletSpeedMultiplier,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.OVERDRIVE_RUSH,
            abilityName,
            trigger,
            durationSeconds,
            damageMultiplier,
            fireRateMultiplier,
            bulletSpeedMultiplier,
            0f,
            0,
            debuff,
            debuffDurationSeconds
        );
    }

    public static AbilityProfile missileSwarm(
        String abilityName,
        AbilityTrigger trigger,
        int missileCount,
        float missileDamageMultiplier,
        float meltdownSeconds,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.MISSILE_SWARM,
            abilityName,
            trigger,
            0f,
            missileDamageMultiplier,
            1f,
            1f,
            meltdownSeconds,
            missileCount,
            debuff,
            debuffDurationSeconds
        );
    }

    public String statSummary(){
        StringBuilder sb = new StringBuilder();
        sb.append("[accent]").append(abilityName).append("[]\n");
        sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");

        if(mode == Mode.OVERDRIVE_RUSH){
            sb.append("[lightgray]Duration: []").append(formatNum(durationTicks / 60f)).append("s\n");
            sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
            if(fireRateMultiplier > 1f){
                sb.append("[lightgray]Fire Rate: []").append(formatNum(fireRateMultiplier)).append("x\n");
            }
        }else{
            sb.append("[lightgray]Missiles: []").append(missileCount).append(" homing snake missiles\n");
            sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
            if(meltdownTicks > 0f){
                sb.append("[scarlet]Meltdown: []Stops firing for ").append(formatNum(meltdownTicks / 60f)).append("s\n");
            }
        }

        if(debuff != null){
            sb.append("[lightgray]Debuff: []").append(debuff.emoji()).append(" ")
                .append(debuff.localizedName)
                .append(" (").append(formatNum(debuffDurationTicks / 60f)).append("s)");
        }
        return sb.toString().trim();
    }

    private static String formatNum(float value){
        if(Math.abs(value - Math.round(value)) < 0.01f){
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
    }
} 
