package abilityadditions.core;

import arc.graphics.*;
import mindustry.type.*;

public class AbilityProfile{

    public enum AbilityId{
        DUO_OVERDRIVE_RUSH,
        SCATTER_SNAKE_SWARM,
        ARC_STATIC_CHAIN,
        HAIL_SEISMIC_MORTAR,
        SCORCH_THERMAL_RAMP,
        SALVO_ESCALATING_BARREL,
        LANCER_ARC_PARALYSIS,
        RIPPLE_CARPET_BOMBLETS,
        FUSE_ECHO_KICK,
        SWARMER_HUNTERS_MARK,
        WAVE_ALCHEMICAL_MONSOON,
        TSUNAMI_TIDAL_WALL,
        PARALLAX_GRAVITY_SLAM,
        SEGMENT_AEGIS_PRISM,
        FRAGMENT_STATIC_INTERCEPTOR,
        CYCLONE_GALE_RICOCHET,
        SPECTRE_SINGULARITY_ORB,
        MELTDOWN_GRANITE_BLAST,
        FORESHADOW_APEX_EXECUTION,
        PIERCE_DEAD_EYE
    }

    public AbilityId id;
    public String turretName;
    public String abilityName;
    public String description;
    public Color accentColor = Color.gold.cpy();

    public AbilityTrigger trigger;

    public float activeDuration = 0f;
    public float chargeDuration = 0f;
    public float meltdownDuration = 0f;
    public boolean canTurnWhileCharging = false;
    public boolean lockRotationWhileActive = false;

    public float damageMultiplier = 1f;
    public float fireRateMultiplier = 1f;
    public float bulletSpeedMultiplier = 1f;
    public float rangeMultiplier = 1f;
    public float inaccuracyMultiplier = 1f;

    public float stage2FireRateMult = 1f;
    public float stage2SpeedMult = 1f;
    public float stage2InaccuracyMult = 1f;
    public float stage3FireRateMult = 1f;
    public float stage3SpeedMult = 1f;
    public float stage3RangeMult = 1f;
    public float stage3InaccuracyMult = 1f;
    public float stage3HoldDuration = 5f;

    public String primaryStatusEffect;
    public float primaryStatusDuration = 2.5f;
    public String secondaryStatusEffect;
    public String tertiaryStatusEffect;
    public float secondaryStatusDuration = 5f;

    public float pullRadius = 0f;
    public float pullStrength = 0f;
    public int chainTargets = 0;
    public float chainRange = 0f;
    public float chainDamageFraction = 0.7f;
    public float echoDelay = 0.45f;
    public float echoDamageFraction = 0.45f;
    public int bombletCount = 0;
    public float bombletDelay = 0.55f;
    public float bombletDamageFraction = 0.3f;
    public float knockbackForce = 0f;
    public float markDuration = 4f;
    public float markBonusMultiplier = 1.45f;
    public boolean airOnlyMark = true;

    public int secondaryThreshold = 0;
    public int ricochetBounces = 0;
    public float ricochetRange = 110f;
    public float ricochetDamageMult = 1.35f;
    public float ricochetKnockback = 6.5f;
    public float maxHpBonusPercent = 0f;
    public float maxHpBonusCap = 4500f;
    public int deadEyeShots = 5;
    public float deadEyeInterval = 3.2f;

    public StatusEffect resolvedPrimaryStatus;
    public StatusEffect resolvedSecondaryStatus;
    public StatusEffect resolvedTertiaryStatus;
}
