package abilityadditions.content;

import abilityadditions.core.*;
import abilityadditions.core.AbilityProfile.*;
import arc.graphics.*;
import arc.struct.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.world.*;

public class SerpuloTurretAbilities{

    public static final Seq<AbilityProfile> all = new Seq<>();

    public static void load(){
        all.clear();

        // 1. Duo (1x1) - Overdrive Rush
        register(Blocks.duo, new AbilityProfile(){{
            id = AbilityId.DUO_OVERDRIVE_RUSH;
            turretName = "duo";
            abilityName = "Overdrive Rush";
            description = "Every 10th shot triggers a 3s rush (+50% damage, +65% fire rate, +45% bullet speed) and applies mov-damage-increase-tier-1.";
            accentColor = Color.valueOf("ffd466");
            trigger = AbilityTrigger.everyNShots(10, 3f);
            activeDuration = 3f;
            damageMultiplier = 1.5f;
            fireRateMultiplier = 1.65f;
            bulletSpeedMultiplier = 1.45f;
            primaryStatusEffect = "mov-damage-increase-tier-1";
            primaryStatusDuration = 2.5f;
        }});

        // 2. Scatter (2x2) - Snake Swarm
        register(Blocks.scatter, new AbilityProfile(){{
            id = AbilityId.SCATTER_SNAKE_SWARM;
            turretName = "scatter";
            abilityName = "Snake Swarm";
            description = "10% chance per volley to convert flak into homing snake missiles (1.4x damage) for the volley, then enters a 2s Meltdown.";
            accentColor = Color.valueOf("9cff85");
            trigger = AbilityTrigger.chancePerCycle(0.10f, 1.8f);
            activeDuration = 1.8f;
            meltdownDuration = 2.0f;
            damageMultiplier = 1.4f;
            primaryStatusEffect = "mov-motor-deceleration-tier-1";
            primaryStatusDuration = 2.5f;
        }});

        // 3. Arc (1x1) - Static Chain
        register(Blocks.arc, new AbilityProfile(){{
            id = AbilityId.ARC_STATIC_CHAIN;
            turretName = "arc";
            abilityName = "Static Chain";
            description = "25% chance per shot to deal 1.35x damage and bounce real chain lightning to 2 nearby ground enemies (75% damage) + shocked.";
            accentColor = Color.valueOf("a6e8ff");
            trigger = AbilityTrigger.chancePerCycle(0.25f, 0f);
            damageMultiplier = 1.35f;
            chainTargets = 2;
            chainRange = 88f;
            chainDamageFraction = 0.75f;
        }});

        // 4. Hail (1x1) - Seismic Mortar
        register(Blocks.hail, new AbilityProfile(){{
            id = AbilityId.HAIL_SEISMIC_MORTAR;
            turretName = "hail";
            abilityName = "Seismic Mortar";
            description = "Every 5th shot deals 1.65x damage, pulls nearby ground enemies inward, and applies mov-damage-increase-tier-1.";
            accentColor = Color.valueOf("ffb066");
            trigger = AbilityTrigger.everyNShots(5, 0f);
            damageMultiplier = 1.65f;
            pullRadius = 56f;
            pullStrength = 4.2f;
            primaryStatusEffect = "mov-damage-increase-tier-1";
            primaryStatusDuration = 2.5f;
        }});

        // 5. Scorch (1x1) - Thermal Ramp
        register(Blocks.scorch, new AbilityProfile(){{
            id = AbilityId.SCORCH_THERMAL_RAMP;
            turretName = "scorch";
            abilityName = "Thermal Ramp";
            description = "Firing for 10s fills Heat to 100% (ramping up to 1.4x damage & 1.55x fire rate), overheats 5s (+35% flame reach + melting), then enters a 3s Meltdown.";
            accentColor = Color.valueOf("ff7442");
            trigger = AbilityTrigger.continuousHeat(10f, 5f);
            activeDuration = 5f;
            meltdownDuration = 3f;
            damageMultiplier = 1.4f;
            fireRateMultiplier = 1.55f;
            bulletSpeedMultiplier = 1.35f;
        }});

        // 6. Salvo (2x2) - Escalating Barrel
        register(Blocks.salvo, new AbilityProfile(){{
            id = AbilityId.SALVO_ESCALATING_BARREL;
            turretName = "salvo";
            abilityName = "Escalating Barrel";
            description = "10s firing -> Stage 2 (1.30x rate, tighter aim, 1.25x speed, mov-slow-reloading-tier-1); +15s -> Stage 3 (1.55x rate, pinpoint aim, 1.45x speed, 1.25x range, mov-damage-increase-tier-2). Holds Stage 3 for 5s when idle.";
            accentColor = Color.valueOf("ffa63b");
            trigger = AbilityTrigger.stageDuration(10f, 15f, 2f);
            stage2FireRateMult = 1.30f;
            stage2SpeedMult = 1.25f;
            stage2InaccuracyMult = 0.45f;
            stage3FireRateMult = 1.55f;
            stage3SpeedMult = 1.45f;
            stage3RangeMult = 1.25f;
            stage3InaccuracyMult = 0.10f;
            stage3HoldDuration = 5f;
            primaryStatusEffect = "mov-slow-reloading-tier-1";
            secondaryStatusEffect = "mov-damage-increase-tier-2";
            primaryStatusDuration = 2.5f;
        }});

        // 7. Lancer (2x2) - Rail Echo
        register(Blocks.lancer, new AbilityProfile(){{
            id = AbilityId.LANCER_RAIL_ECHO;
            turretName = "lancer";
            abilityName = "Rail Echo";
            description = "Every 3rd shot fires a 0.45s delayed echo beam along the same line (45% damage) + mov-emp-tier-2.";
            accentColor = Color.valueOf("82d4ff");
            trigger = AbilityTrigger.everyNShots(3, 0f);
            echoDelay = 0.45f;
            echoDamageFraction = 0.45f;
            primaryStatusEffect = "mov-emp-tier-2";
            primaryStatusDuration = 2.5f;
        }});

        // 8. Ripple (3x3) - Carpet Bomblets
        register(Blocks.ripple, new AbilityProfile(){{
            id = AbilityId.RIPPLE_CARPET_BOMBLETS;
            turretName = "ripple";
            abilityName = "Carpet Bomblets";
            description = "25% chance per volley to deal 1.25x damage, scatter 3 delayed ground bomblets (0.55s later), and apply mov-motor-deceleration-tier-2.";
            accentColor = Color.valueOf("ff944d");
            trigger = AbilityTrigger.chancePerCycle(0.25f, 1.2f);
            damageMultiplier = 1.25f;
            bombletCount = 3;
            bombletDelay = 0.55f;
            bombletDamageFraction = 0.35f;
            primaryStatusEffect = "mov-motor-deceleration-tier-2";
            primaryStatusDuration = 2.5f;
        }});

        // 9. Fuse (3x3) - Echo Kick
        register(Blocks.fuse, new AbilityProfile(){{
            id = AbilityId.FUSE_ECHO_KICK;
            turretName = "fuse";
            abilityName = "Echo Kick";
            description = "5% chance per shot to fire a 0.18s delayed 10%-damage clone blast with heavy knockback + mov-armor-piercing-tier-2.";
            accentColor = Color.valueOf("ff7ad9");
            trigger = AbilityTrigger.chancePerCycle(0.05f, 0f);
            echoDelay = 0.18f;
            echoDamageFraction = 0.10f;
            knockbackForce = 9f;
            primaryStatusEffect = "mov-armor-piercing-tier-2";
            primaryStatusDuration = 2.5f;
        }});

        // 10. Swarmer (3x3) - Hunter's Mark
        register(Blocks.swarmer, new AbilityProfile(){{
            id = AbilityId.SWARMER_HUNTERS_MARK;
            turretName = "swarmer";
            abilityName = "Hunter's Mark";
            description = "20% chance per volley to mark flying enemies for 4s (+45% Swarmer missile damage) + mov-motor-deceleration-tier-2.";
            accentColor = Color.valueOf("ffde6b");
            trigger = AbilityTrigger.chancePerCycle(0.20f, 1.0f);
            markDuration = 4f;
            markBonusMultiplier = 1.45f;
            airOnlyMark = true;
            primaryStatusEffect = "mov-motor-deceleration-tier-2";
            primaryStatusDuration = 3f;
        }});

        // 11. Wave (2x2, Tier 1 only) - Alchemical Monsoon
        register(Blocks.wave, new AbilityProfile(){{
            id = AbilityId.WAVE_ALCHEMICAL_MONSOON;
            turretName = "wave";
            abilityName = "Alchemical Monsoon";
            description = "8s of spraying triggers a 3s Tier-1 pressurized surge matching Wave's liquid (Water cleanses & slows; Cryo cools allies & freezes; Slag strips Tier-1 armor; Oil tars & slows reload).";
            accentColor = Color.valueOf("66cfff");
            trigger = AbilityTrigger.continuousHeat(8f, 3f);
            activeDuration = 3f;
            bulletSpeedMultiplier = 1.45f;
            rangeMultiplier = 1.35f;
            primaryStatusEffect = "mov-motor-deceleration-tier-1";
            secondaryStatusEffect = "mov-fast-reloading-tier-1";
            tertiaryStatusEffect = "mov-armor-piercing-tier-1";
            primaryStatusDuration = 2.5f;
        }});

        // 12. Tsunami (3x3, Tier 2) - Rolling Tidal Wall
        register(Blocks.tsunami, new AbilityProfile(){{
            id = AbilityId.TSUNAMI_TIDAL_WALL;
            turretName = "tsunami";
            abilityName = "Rolling Tidal Wall";
            description = "9s of firing unleashes an advancing crescent flood wall that shoves ground swarms, leaves puddles, and applies Tier-2 liquid effects.";
            accentColor = Color.valueOf("40a9ff");
            trigger = AbilityTrigger.continuousHeat(9f, 2.2f);
            activeDuration = 2.2f;
            knockbackForce = 7.5f;
            primaryStatusEffect = "mov-motor-deceleration-tier-2";
            secondaryStatusEffect = "mov-slow-reloading-tier-2";
            tertiaryStatusEffect = "mov-oxidation-tier-2";
            primaryStatusDuration = 3.5f;
        }});

        // 13. Parallax (2x2) - Gravity Slam
        register(Blocks.parallax, new AbilityProfile(){{
            id = AbilityId.PARALLAX_GRAVITY_SLAM;
            turretName = "parallax";
            abilityName = "Gravity Slam";
            description = "Holding a flyer for 2.5s tethers 2 nearby flyers together for 2.5s, then slams them with mov-emp-tier-2, mov-overload-tier-2, and mov-armor-piercing-tier-2.";
            accentColor = Color.valueOf("7cf7ff");
            trigger = AbilityTrigger.tractorLock(2.5f);
            activeDuration = 2.5f;
            pullRadius = 115f;
            pullStrength = 2.4f;
            chainTargets = 2;
            primaryStatusEffect = "mov-emp-tier-2";
            secondaryStatusEffect = "mov-overload-tier-2";
            tertiaryStatusEffect = "mov-armor-piercing-tier-2";
            primaryStatusDuration = 3f;
        }});

        // 14. Segment (2x2) - Aegis Counter-Prism
        register(Blocks.segment, new AbilityProfile(){{
            id = AbilityId.SEGMENT_AEGIS_PRISM;
            turretName = "segment";
            abilityName = "Aegis Counter-Prism";
            description = "Every 8th intercepted bullet reflects a 150% counter-laser at its shooter (+ mov-emp-tier-1). At 24 intercepts, unleashes a 2.5s Aegis Prism Sweep.";
            accentColor = Color.valueOf("d194ff");
            trigger = AbilityTrigger.interceptCount(8);
            secondaryThreshold = 24;
            activeDuration = 2.5f;
            damageMultiplier = 1.5f;
            primaryStatusEffect = "mov-emp-tier-1";
            primaryStatusDuration = 2f;
        }});

        // 15. Fragment (mov-fragment, 1x1 Tier 1) - Static Interceptor
        Block fragmentBlock = Vars.content.block("mov-fragment");
        if(fragmentBlock != null){
            register(fragmentBlock, new AbilityProfile(){{
                id = AbilityId.FRAGMENT_STATIC_INTERCEPTOR;
                turretName = "mov-fragment";
                abilityName = "Static Interceptor";
                description = "Every 10th intercepted projectile arcs a short static zap from the destroyed shot onto a nearby enemy unit + shocked.";
                accentColor = Color.valueOf("a6e8ff");
                trigger = AbilityTrigger.interceptCount(10);
                chainRange = 56f;
            }});
        }

        // 16. Cyclone (3x3) - Gale Ricochet
        register(Blocks.cyclone, new AbilityProfile(){{
            id = AbilityId.CYCLONE_GALE_RICOCHET;
            turretName = "cyclone";
            abilityName = "Gale Ricochet";
            description = "10% chance per bullet to ricochet up to 2 nearby units; only ricochet hits deal 1.35x damage, heavy knockback, spin enemies, and apply mov-slow-reloading-tier-2.";
            accentColor = Color.valueOf("b8ffe1");
            trigger = AbilityTrigger.chancePerCycle(0.10f, 0f);
            ricochetBounces = 2;
            ricochetRange = 110f;
            ricochetDamageMult = 1.35f;
            ricochetKnockback = 6.5f;
            primaryStatusEffect = "mov-slow-reloading-tier-2";
            primaryStatusDuration = 2.5f;
        }});

        // 17. Spectre (4x4) - Singularity Orb
        register(Blocks.spectre, new AbilityProfile(){{
            id = AbilityId.SPECTRE_SINGULARITY_ORB;
            turretName = "spectre";
            abilityName = "Singularity Orb";
            description = "Every 35th shot charges an ammo-colored absorbing orb for 3s, firing at 5x original bullet damage + mov-armor-piercing-tier-3, then enters a 12s Meltdown.";
            accentColor = Color.valueOf("ffba70");
            trigger = AbilityTrigger.everyNShots(35, 0f);
            chargeDuration = 3f;
            meltdownDuration = 12f;
            damageMultiplier = 5.0f;
            canTurnWhileCharging = false;
            primaryStatusEffect = "mov-armor-piercing-tier-3";
            primaryStatusDuration = 4f;
        }});

        // 18. Meltdown (4x4) - Granite Blast
        register(Blocks.meltdown, new AbilityProfile(){{
            id = AbilityId.MELTDOWN_GRANITE_BLAST;
            turretName = "meltdown";
            abilityName = "Granite Blast";
            description = "Every 7th cycle charges an intense absorbing core for 7s (aimable), then locks rotation to unleash an empowered Granite Blast (+ mov-oxidation-tier-3), followed by a 20s Meltdown.";
            accentColor = Color.valueOf("ff5e4d");
            trigger = AbilityTrigger.everyNShots(7, 0f);
            chargeDuration = 7f;
            activeDuration = 7.5f;
            meltdownDuration = 20f;
            damageMultiplier = 2.2f;
            canTurnWhileCharging = true;
            lockRotationWhileActive = true;
            primaryStatusEffect = "mov-oxidation-tier-3";
            primaryStatusDuration = 5f;
        }});

        // 19. Foreshadow (4x4) - Apex Execution
        register(Blocks.foreshadow, new AbilityProfile(){{
            id = AbilityId.FORESHADOW_APEX_EXECUTION;
            turretName = "foreshadow";
            abilityName = "Apex Execution";
            description = "5 confirmed unit hits lock onto the highest-HP enemy in range, empowering the next hit with 2.2x damage + 10% Max HP bonus + mov-high-emp, mov-armor-piercing-tier-3, and mov-damage-reduction-tier-3.";
            accentColor = Color.valueOf("ffe77a");
            trigger = AbilityTrigger.confirmedUnitHits(5);
            damageMultiplier = 2.2f;
            maxHpBonusPercent = 0.10f;
            maxHpBonusCap = 4500f;
            primaryStatusEffect = "mov-high-emp";
            secondaryStatusEffect = "mov-armor-piercing-tier-3";
            tertiaryStatusEffect = "mov-damage-reduction-tier-3";
            secondaryStatusDuration = 5f;
        }});
    }

    private static void register(Block block, AbilityProfile profile){
        if(block == null) return;
        all.add(profile);
        AbilityManager.register(block, profile);
    }
                }
