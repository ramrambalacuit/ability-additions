package abilityadditions.core;

import mindustry.type.StatusEffect;

public class AbilityProfile{
    public enum Mode{
        OVERDRIVE_RUSH,
        MISSILE_SWARM,
        CHAIN_LIGHTNING,
        SEISMIC_MORTAR,
        HEAT_RAMP
    }

    public final Mode mode;
    public final String abilityName;
    public final AbilityTrigger trigger;
    public final float durationTicks;
    public final float damageMultiplier;
    public final float fireRateMultiplier;
    public final float bulletSpeedMultiplier;
    public final float meltdownTicks;
    public final float heatGainPerShot;
    public final float heatCoolPerSecond;
    public final int chainTargets;
    public final float chainRadius;
    public final float chainDamageRatio;
    public final float pullRadius;
    public final float pullStrength;
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
        float heatGainPerShot,
        float heatCoolPerSecond,
        int chainTargets,
        float chainRadius,
        float chainDamageRatio,
        float pullRadius,
        float pullStrength,
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
        this.heatGainPerShot = heatGainPerShot;
        this.heatCoolPerSecond = heatCoolPerSecond;
        this.chainTargets = chainTargets;
        this.chainRadius = chainRadius;
        this.chainDamageRatio = chainDamageRatio;
        this.pullRadius = pullRadius;
        this.pullStrength = pullStrength;
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
            Mode.OVERDRIVE_RUSH, abilityName, trigger,
            durationSeconds, damageMultiplier, fireRateMultiplier, bulletSpeedMultiplier,
            0f, 0f, 0f, 0, 0f, 0f, 0f, 0f,
            debuff, debuffDurationSeconds
        );
    }

    public static AbilityProfile missileSwarm(
        String abilityName,
        AbilityTrigger trigger,
        float enhancerSeconds,
        float missileDamageMultiplier,
        float meltdownSeconds,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.MISSILE_SWARM, abilityName, trigger,
            enhancerSeconds, missileDamageMultiplier, 1f, 1f,
            meltdownSeconds, 0f, 0f, 0, 0f, 0f, 0f, 0f,
            debuff, debuffDurationSeconds
        );
    }

    public static AbilityProfile chainLightning(
        String abilityName,
        AbilityTrigger trigger,
        float mainDamageMultiplier,
        int chainTargets,
        float chainRadius,
        float chainDamageRatio,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.CHAIN_LIGHTNING, abilityName, trigger,
            0f, mainDamageMultiplier, 1f, 1f,
            0f, 0f, 0f, chainTargets, chainRadius, chainDamageRatio, 0f, 0f,
            debuff, debuffDurationSeconds
        );
    }

    public static AbilityProfile seismicMortar(
        String abilityName,
        AbilityTrigger trigger,
        float damageMultiplier,
        float pullRadius,
        float pullStrength,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.SEISMIC_MORTAR, abilityName, trigger,
            0f, damageMultiplier, 1f, 1f,
            0f, 0f, 0f, 0, 0f, 0f, pullRadius, pullStrength,
            debuff, debuffDurationSeconds
        );
    }

    public static AbilityProfile heatRamp(
        String abilityName,
        float maxDamageMultiplier,
        float maxFireRateMultiplier,
        float heatGainPerShot,
        float heatCoolPerSecond,
        float meltdownSeconds,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.HEAT_RAMP, abilityName, AbilityTrigger.chance(0f),
            0f, maxDamageMultiplier, maxFireRateMultiplier, 1f,
            meltdownSeconds, heatGainPerShot, heatCoolPerSecond,
            0, 0f, 0f, 0f, 0f,
            debuff, debuffDurationSeconds
        );
    }

    public String statSummary(){
        StringBuilder sb = new StringBuilder();
        sb.append("[accent]").append(abilityName).append("[]\n");

        if(mode == Mode.HEAT_RAMP){
            sb.append("[lightgray]Mechanic: []Builds Heat while firing\n");
            sb.append("[lightgray]Max Damage: []").append(formatNum(damageMultiplier)).append("x\n");
            sb.append("[lightgray]Max Fire Rate: []").append(formatNum(fireRateMultiplier)).append("x\n");
            sb.append("[scarlet]Meltdown: []Stops firing for ").append(formatNum(meltdownTicks / 60f)).append("s at 100% Heat\n");
        }else{
            sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
            if(mode == Mode.OVERDRIVE_RUSH){
                sb.append("[lightgray]Duration: []").append(formatNum(durationTicks / 60f)).append("s\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
                if(fireRateMultiplier > 1f){
                    sb.append("[lightgray]Fire Rate: []").append(formatNum(fireRateMultiplier)).append("x\n");
                }
            }else if(mode == Mode.MISSILE_SWARM){
                sb.append("[lightgray]Enhancer: []Turns volley into homing snake missiles (")
                    .append(formatNum(durationTicks / 60f)).append("s)\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
                if(meltdownTicks > 0f){
                    sb.append("[scarlet]Meltdown: []Stops firing for ").append(formatNum(meltdownTicks / 60f)).append("s\n");
                }
            }else if(mode == Mode.CHAIN_LIGHTNING){
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x + ")
                    .append(chainTargets).append(" chain jumps (")
                    .append(Math.round(chainDamageRatio * 100f)).append("% dmg)\n");
            }else if(mode == Mode.SEISMIC_MORTAR){
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
                sb.append("[lightgray]Effect: []Pulls nearby ground enemies inward\n");
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
