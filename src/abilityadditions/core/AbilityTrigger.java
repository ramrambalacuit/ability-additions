package abilityadditions.core;

public class AbilityTrigger{

    public enum TriggerType{
        EVERY_N_SHOTS,
        CHANCE_PER_CYCLE,
        CONTINUOUS_HEAT,
        STAGE_DURATION,
        CONFIRMED_UNIT_HITS,
        INTERCEPT_COUNT,
        TRACTOR_LOCK
    }

    public TriggerType type;
    public int everyNShots;
    public float chance;
    public float cooldownSeconds;
    public float heatSeconds;
    public float stage1Seconds;
    public float stage2Seconds;
    public float decayDelaySeconds;

    public static AbilityTrigger everyNShots(int everyNShots, float cooldownSeconds){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.EVERY_N_SHOTS;
        t.everyNShots = Math.max(1, everyNShots);
        t.cooldownSeconds = Math.max(0f, cooldownSeconds);
        return t;
    }

    public static AbilityTrigger chancePerCycle(float chance, float cooldownSeconds){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.CHANCE_PER_CYCLE;
        t.chance = Math.max(0f, Math.min(1f, chance));
        t.cooldownSeconds = Math.max(0f, cooldownSeconds);
        return t;
    }

    public static AbilityTrigger continuousHeat(float heatSeconds, float cooldownSeconds){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.CONTINUOUS_HEAT;
        t.heatSeconds = Math.max(0.5f, heatSeconds);
        t.cooldownSeconds = Math.max(0f, cooldownSeconds);
        return t;
    }

    public static AbilityTrigger stageDuration(float stage1Seconds, float stage2Seconds, float decayDelaySeconds){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.STAGE_DURATION;
        t.stage1Seconds = Math.max(0.5f, stage1Seconds);
        t.stage2Seconds = Math.max(0.5f, stage2Seconds);
        t.decayDelaySeconds = Math.max(0.1f, decayDelaySeconds);
        return t;
    }

    public static AbilityTrigger confirmedUnitHits(int hitsRequired){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.CONFIRMED_UNIT_HITS;
        t.everyNShots = Math.max(1, hitsRequired);
        return t;
    }

    public static AbilityTrigger interceptCount(int everyNIntercepts){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.INTERCEPT_COUNT;
        t.everyNShots = Math.max(1, everyNIntercepts);
        return t;
    }

    public static AbilityTrigger tractorLock(float lockSeconds){
        AbilityTrigger t = new AbilityTrigger();
        t.type = TriggerType.TRACTOR_LOCK;
        t.heatSeconds = Math.max(0.5f, lockSeconds);
        return t;
    }
}
