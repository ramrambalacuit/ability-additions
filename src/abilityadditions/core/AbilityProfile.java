package abilityadditions.core;

import mindustry.type.StatusEffect;

public class AbilityProfile{
    public final String abilityName;
    public final AbilityTrigger trigger;
    public final float durationTicks;
    public final float damageMultiplier;
    public final float fireRateMultiplier;
    public final float bulletSpeedMultiplier;
    public final StatusEffect debuff;
    public final float debuffDurationTicks;

    public AbilityProfile(
        String abilityName,
        AbilityTrigger trigger,
        float durationSeconds,
        float damageMultiplier,
        float fireRateMultiplier,
        float bulletSpeedMultiplier,
        StatusEffect debuff,
        float debuffDurationSeconds
    ){
        this.abilityName = abilityName;
        this.trigger = trigger;
        this.durationTicks = durationSeconds * 60f;
        this.damageMultiplier = damageMultiplier;
        this.fireRateMultiplier = fireRateMultiplier;
        this.bulletSpeedMultiplier = bulletSpeedMultiplier;
        this.debuff = debuff;
        this.debuffDurationTicks = debuffDurationSeconds * 60f;
    }

    public String statSummary(){
        StringBuilder sb = new StringBuilder();
        sb.append("[accent]").append(abilityName).append("[]\n");
        sb.append("[lightgray]Trigger: []").append(trigger.describe()).append("\n");
        sb.append("[lightgray]Duration: []").append(formatNum(durationTicks / 60f)).append("s\n");
        sb.append("[lightgray]Damage: []").append(formatNum(damageMultiplier)).append("x\n");
        if(fireRateMultiplier > 1f){
            sb.append("[lightgray]Fire Rate: []").append(formatNum(fireRateMultiplier)).append("x\n");
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
