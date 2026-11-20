package abilityadditions.core;

import mindustry.type.StatusEffect;

public class AbilityProfile{
    public enum Mode{
        OVERDRIVE_RUSH,
        MISSILE_SWARM,
        CHAIN_LIGHTNING,
        SEISMIC_MORTAR,
        HEAT_RAMP,
        SALVO_STAGES,
        RAIL_ECHO,
        CARPET_BOMBLETS,
        REPULSOR_CONE,
        HUNTER_MARK
    }

    public final Mode mode;
    public final String abilityName;
    public final AbilityTrigger trigger;
    public final float durationTicks;
    public final float damageMultiplier;
    public final float fireRateMultiplier;
    public final float bulletSpeedMultiplier;
    public final float rangeMultiplier;
    public final float overheatTicks;
    public final float meltdownTicks;
    public final float heatGainPerShot;
    public final float heatCoolPerSecond;
    public final int chainTargets;
    public final float chainRadius;
    public final float chainDamageRatio;
    public final float pullRadius;
    public final float pullStrength;
    public final float stage2Ticks;
    public final float stage3Ticks;
    public final float stage2FireRate;
    public final float stage2Speed;
    public final float stage3FireRate;
    public final float stage3Speed;
    public final float stage3Range;
    public final StatusEffect debuff;
    public final StatusEffect secondaryDebuff;
    public final float debuffDurationTicks;

    private AbilityProfile(
        Mode mode,
        String abilityName,
        AbilityTrigger trigger,
        float durationSeconds,
        float damageMultiplier,
        float fireRateMultiplier,
        float bulletSpeedMultiplier,
        float rangeMultiplier,
        float overheatSeconds,
        float meltdownSeconds,
        float heatGainPerShot,
        float heatCoolPerSecond,
        int chainTargets,
        float chainRadius,
        float chainDamageRatio,
        float pullRadius,
        float pullStrength,
        float stage2Seconds,
        float stage3AdditionalSeconds,
        float stage2FireRate,
        float stage2Speed,
        float stage3FireRate,
        float stage3Speed,
        float stage3Range,
        StatusEffect debuff,
        StatusEffect secondaryDebuff,
        float debuffDurationSeconds
    ){
        this.mode = mode;
        this.abilityName = abilityName;
        this.trigger = trigger;
        this.durationTicks = durationSeconds * 60f;
        this.damageMultiplier = damageMultiplier;
        this.fireRateMultiplier = fireRateMultiplier;
        this.bulletSpeedMultiplier = bulletSpeedMultiplier;
        this.rangeMultiplier = rangeMultiplier;
        this.overheatTicks = overheatSeconds * 60f;
        this.meltdownTicks = meltdownSeconds * 60f;
        this.heatGainPerShot = heatGainPerShot;
        this.heatCoolPerSecond = heatCoolPerSecond;
        this.chainTargets = chainTargets;
        this.chainRadius = chainRadius;
        this.chainDamageRatio = chainDamageRatio;
        this.pullRadius = pullRadius;
        this.pullStrength = pullStrength;
        this.stage2Ticks = stage2Seconds * 60f;
        this.stage3Ticks = (stage2Seconds + stage3AdditionalSeconds) * 60f;
        this.stage2FireRate = stage2FireRate;
        this.stage2Speed = stage2Speed;
        this.stage3FireRate = stage3FireRate;
        this.stage3Speed = stage3Speed;
        this.stage3Range = stage3Range;
        this.debuff = debuff;
        this.secondaryDebuff = secondaryDebuff;
        this.debuffDurationTicks = debuffDurationSeconds * 60f;
    }

    public static AbilityProfile overdriveRush(
        String abilityName, AbilityTrigger trigger, float durationSeconds,
        float damageMultiplier, float fireRateMultiplier, float bulletSpeedMultiplier,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.OVERDRIVE_RUSH, abilityName, trigger,
            durationSeconds, damageMultiplier, fireRateMultiplier, bulletSpeedMultiplier, 1f,
            0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile missileSwarm(
        String abilityName, AbilityTrigger trigger, float enhancerSeconds,
        float missileDamageMultiplier, float meltdownSeconds,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.MISSILE_SWARM, abilityName, trigger,
            enhancerSeconds, missileDamageMultiplier, 1f, 1f, 1f,
            0f, meltdownSeconds, 0f, 0f, 0, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile chainLightning(
        String abilityName, AbilityTrigger trigger, float mainDamageMultiplier,
        int chainTargets, float chainRadius, float chainDamageRatio,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.CHAIN_LIGHTNING, abilityName, trigger,
            0f, mainDamageMultiplier, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, chainTargets, chainRadius, chainDamageRatio, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile seismicMortar(
        String abilityName, AbilityTrigger trigger, float damageMultiplier,
        float pullRadius, float pullStrength,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.SEISMIC_MORTAR, abilityName, trigger,
            0f, damageMultiplier, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0, 0f, 0f, pullRadius, pullStrength,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile heatRamp(
        String abilityName, float maxDamageMultiplier, float maxFireRateMultiplier,
        float overheatSpeedMult, float overheatRangeMult,
        float heatGainPerShot, float heatCoolPerSecond,
        float overheatSeconds, float meltdownSeconds,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.HEAT_RAMP, abilityName, AbilityTrigger.chance(0f),
            0f, maxDamageMultiplier, maxFireRateMultiplier, overheatSpeedMult, overheatRangeMult,
            overheatSeconds, meltdownSeconds, heatGainPerShot, heatCoolPerSecond,
            0, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile salvoStages(
        String abilityName, float stage2Seconds, float stage3AdditionalSeconds,
        float stage2FireRate, float stage2Speed,
        float stage3FireRate, float stage3Speed, float stage3Range,
        StatusEffect stage2SlowReload, StatusEffect stage3DmgBoost, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.SALVO_STAGES, abilityName, AbilityTrigger.chance(0f),
            0f, 1f, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f,
            stage2Seconds, stage3AdditionalSeconds,
            stage2FireRate, stage2Speed, stage3FireRate, stage3Speed, stage3Range,
            stage2SlowReload, stage3DmgBoost, debuffDurationSeconds
        );
    }

    public static AbilityProfile railEcho(
        String abilityName, AbilityTrigger trigger, float echoDamageRatio,
        float beamLength, StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.RAIL_ECHO, abilityName, trigger,
            0.45f, echoDamageRatio, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0, beamLength, 0f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile carpetBomblets(
        String abilityName, AbilityTrigger trigger, float shellDamageMultiplier,
        int bombletCount, float bombletRadius, float bombletDamageRatio,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.CARPET_BOMBLETS, abilityName, trigger,
            0.55f, shellDamageMultiplier, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, bombletCount, bombletRadius, bombletDamageRatio, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile repulsorCone(
        String abilityName, AbilityTrigger trigger, float damageMultiplier,
        float coneRange, float pushStrength,
        StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.REPULSOR_CONE, abilityName, trigger,
            0f, damageMultiplier, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0, 0f, 0f, coneRange, pushStrength,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public static AbilityProfile hunterMark(
        String abilityName, AbilityTrigger trigger, float markDurationSeconds,
        float bonusDamageMultiplier, StatusEffect debuff, float debuffDurationSeconds
    ){
        return new AbilityProfile(
            Mode.HUNTER_MARK, abilityName, trigger,
            markDurationSeconds, bonusDamageMultiplier, 1f, 1f, 1f,
            0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 1f, 1f, 1f, 1f,
            debuff, null, debuffDurationSeconds
        );
    }

    public String statSummary(){
        StringBuilder sb = new StringBuilder();
        sb.append("[accent]").append(abilityName).append("[]\n");

        switch(mode){
            case HEAT_RAMP -> {
                sb.append("[lightgray]Heat Up: []10s firing -> ").append(formatNum(damageMultiplier)).append("x dmg, ")
                    .append(formatNum(fireRateMultiplier)).append("x fire rate\n");
                sb.append("[lightgray]Overheated (").append(formatNum(overheatTicks / 60f)).append("s): []Extra flame speed & ")
                    .append(formatNum(rangeMultiplier)).append("x reach\n");
                sb.append("[scarlet]Meltdown: []Stops firing for ").append(formatNum(meltdownTicks / 60f)).append("s\n");
            }
            case SALVO_STAGES -> {
                sb.append("[lightgray]Stage 2 (").append(formatNum(stage2Ticks / 60f)).append("s): []")
                    .append(formatNum(stage2FireRate)).append("x rate, tighter aim, ")
                    .append(formatNum(stage2Speed)).append("x bullet speed\n");
                sb.append("[lightgray]Stage 3 (+").append(formatNum((stage3Ticks - stage2Ticks) / 60f)).append("s): []")
                    .append(formatNum(stage3FireRate)).append("x rate, pinpoint aim, ")
                    .append(formatNum(stage3Range)).append("x range\n");
                if(debuff != null && secondaryDebuff != null){
                    sb.append("[lightgray]Debuffs: []").append(debuff.emoji()).append(" S2 / ")
                        .append(secondaryDebuff.emoji()).append(" S3");
                    return sb.toString().trim();
                }
            }
            case OVERDRIVE_RUSH -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Duration: []").append(formatNum(durationTicks / 60f)).append("s\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
                if(fireRateMultiplier > 1f){
                    sb.append("[lightgray]Fire Rate: []").append(formatNum(fireRateMultiplier)).append("x\n");
                }
            }
            case MISSILE_SWARM -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Enhancer: []Turns volley into homing snake missiles (")
                    .append(formatNum(durationTicks / 60f)).append("s)\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
                if(meltdownTicks > 0f){
                    sb.append("[scarlet]Meltdown: []Stops firing for ").append(formatNum(meltdownTicks / 60f)).append("s\n");
                }
            }
            case CHAIN_LIGHTNING -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x + ")
                    .append(chainTargets).append(" chain jumps (").append(Math.round(chainDamageRatio * 100f)).append("%)\n");
            }
            case SEISMIC_MORTAR -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
                sb.append("[lightgray]Effect: []Pulls ground enemies inward\n");
            }
            case RAIL_ECHO -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Effect: []Delayed echo beam (").append(Math.round(damageMultiplier * 100f)).append("% dmg)\n");
            }
            case CARPET_BOMBLETS -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x + ")
                    .append(chainTargets).append(" delayed bomblets\n");
            }
            case REPULSOR_CONE -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x + knockback shockwave cone\n");
            }
            case HUNTER_MARK -> {
                sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
                sb.append("[lightgray]Mark: []Enemies take ").append(formatNum(damageMultiplier))
                    .append("x missile dmg (").append(formatNum(durationTicks / 60f)).append("s)\n");
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
