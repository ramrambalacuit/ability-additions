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

    public static void load(AbilityManager manager){
        load();
    }

    public static void load(){
        all.clear();

        // 1. Duo (1x1) - Overdrive Rush
        register(Blocks.duo, new AbilityProfile(){{
            id = AbilityId.DUO_OVERDRIVE_RUSH;
            turretName = "duo";
            abilityName = "Overdrive Rush";
            description = "The little gun that thinks it's a big one. Land 10 shots and Duo snaps into a 3s frenzy: +50% damage, +65% fire rate and +45% bullet speed, with a Tier-1 damage boost on top.";
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
            description = "Sometimes flak just isn't enough. 10% chance per volley to swap the shells for a writhing nest of homing snake missiles that hunt down aircraft, hit for 1.4x damage, glow in your ammo's color and slow whatever they touch.";
            accentColor = Color.valueOf("9cff85");
            trigger = AbilityTrigger.chancePerCycle(0.10f, 1.8f);
            activeDuration = 1.8f;
            meltdownDuration = 0f;
            damageMultiplier = 1.4f;
            primaryStatusEffect = "mov-motor-deceleration-tier-1";
            primaryStatusDuration = 2.5f;
        }});

        // 3. Arc (1x1) - Static Chain
        register(Blocks.arc, new AbilityProfile(){{
            id = AbilityId.ARC_STATIC_CHAIN;
            turretName = "arc";
            abilityName = "Static Chain";
            description = "Lightning that doesn't know when to stop. 25% chance per shot for a 1.35x hit that leaps on to 2 more nearby ground enemies for 75% damage each, shocking everything in the chain.";
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
            description = "Every 5th shell lands like a small earthquake. It hits for 1.65x damage and drags nearby ground enemies into the crater (smaller units get yanked harder), leaving them marked with a Tier-1 damage-increase status.";
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
            description = "Hold the trigger and Scorch only gets hotter. 10s of firing builds Heat to 100%, ramping up to 1.4x damage and 1.55x fire rate. Then it overheats for 5s with +35% flame reach and melting flames, before shutting down for a 3s cooldown.";
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
            description = "The longer Salvo fires, the more it wakes up. After 10s it reaches Stage 2: 1.30x fire rate, tighter aim, 1.25x bullet speed. After 15s more it hits Stage 3: 1.55x fire rate, pinpoint aim, 1.45x bullet speed and 1.25x range, with a Tier-2 damage boost. Stage 3 holds for 5s once it stops firing.";
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

        // 7. Lancer (2x2) - Arc Paralysis
        register(Blocks.lancer, new AbilityProfile(){{
            id = AbilityId.LANCER_ARC_PARALYSIS;
            turretName = "lancer";
            abilityName = "Arc Paralysis";
            description = "Every 3rd beam doesn't just hurt, it shuts enemies down. Every unit it hits is paralyzed: movement and weapons frozen, sprites darkened and crackling with lightning, until Lancer is ready to fire again.";
            accentColor = Color.valueOf("a9d8ff");
            trigger = AbilityTrigger.everyNShots(3, 0f);
        }});

        // 8. Ripple (3x3) - Carpet Bomblets
        register(Blocks.ripple, new AbilityProfile(){{
            id = AbilityId.RIPPLE_CARPET_BOMBLETS;
            turretName = "ripple";
            abilityName = "Carpet Bomblets";
            description = "One shell, then a second wave. 25% chance per volley for a 1.25x shell that, on impact, scatters 3 delayed bomblets around the crater. Ground units caught in them are slowed (Tier 2).";
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
            description = "A blast that comes back for seconds. 5% chance per shot that a ghost copy of the shot fires 0.18s later for 10% damage, shoving enemies back hard and leaving them with a Tier-2 armor-piercing status.";
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
            description = "Pick the prey, then unleash the pack. 20% chance per volley to lock tactical brackets on a flying enemy for 4s. Swarmer missiles fired near a marked target deal +45% damage, and the target is slowed (Tier 2).";
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
            description = "Keep the pressure up and the storm breaks. After 8s of spraying, Wave erupts into a 3s pressurized surge that takes on the nature of its liquid. Water cleanses and slows, Cryofluid cools allies and freezes enemies, Slag strips Tier-1 armor, Oil tars enemies and slows their reload.";
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
            description = "9s of firing and the sea stands up. Tsunami sends out an advancing crescent wall of water that shoves ground swarms along, leaves puddles in its wake and applies Tier-2 effects based on the liquid you feed it.";
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
            description = "Hold a flyer in the beam for 2.5s and gravity takes over. Parallax tethers up to 2 nearby flyers to it for 2.5s, reels them in, then slams them together with Tier-2 EMP, overload and armor-piercing effects.";
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
            description = "Every shot it stops, it remembers. Every 8th intercepted bullet is thrown back as a 150% counter-laser at whoever fired it, with a Tier-1 EMP. Reach 24 intercepts and Segment unleashes a 2.5s Aegis Prism Sweep.";
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
                description = "Shot down shots leave a spark behind. Every 10th intercepted projectile arcs a short zap from the wreckage onto a nearby enemy unit, shocking it.";
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
            description = "Some flak goes where the wind sends it. 10% chance per bullet to ricochet through up to 2 more nearby units. Only the ricochet hits count: 1.35x damage, heavy knockback, a spin that leaves enemies dizzy, and a Tier-2 reload slow.";
            accentColor = Color.valueOf("b8ffe1");
            trigger = AbilityTrigger.chancePerCycle(0.10f, 0f);
            ricochetBounces = 2;
            ricochetRange = 110f;
            ricochetDamageMult = 1.35f;
            ricochetKnockback = 6.5f;
            primaryStatusEffect = "mov-slow-reloading-tier-2";
            primaryStatusDuration = 2.5f;
        }});

        // 17. Spectre (4x4) - Giant Shell (PvZ2 Repeater Big Pea)
        register(Blocks.spectre, new AbilityProfile(){{
            id = AbilityId.SPECTRE_SINGULARITY_ORB;
            turretName = "spectre";
            abilityName = "Giant Shell";
            description = "Fifteen shots to load one very bad idea. Every 15th shot fires a colossal mega-shell in your ammo's color for 5x damage, with a heavy shockwave splash, hard knockback and a Tier-3 armor-piercing status.";
            accentColor = Color.valueOf("ffba70");
            trigger = AbilityTrigger.everyNShots(15, 0f);
            chargeDuration = 0f;
            meltdownDuration = 0f;
            damageMultiplier = 5.0f;
            primaryStatusEffect = "mov-armor-piercing-tier-3";
            primaryStatusDuration = 4f;
        }});

        // 18. Meltdown (4x4) - Granite Blast
        register(Blocks.meltdown, new AbilityProfile(){{
            id = AbilityId.MELTDOWN_GRANITE_BLAST;
            turretName = "meltdown";
            abilityName = "Granite Blast";
            description = "Patience, then ruin. Every 7th cycle Meltdown winds up a Royal Blue and Cyan spiral core for 5.5s, locks its rotation, and fires one empowered Granite Blast that leaves a Tier-3 oxidation status. Then it goes quiet for a 20s cooldown.";
            accentColor = Color.valueOf("4a7fe5");
            trigger = AbilityTrigger.everyNShots(7, 0f);
            chargeDuration = 5.5f;
            activeDuration = 7.0f;
            meltdownDuration = 20f;
            damageMultiplier = 2.2f;
            canTurnWhileCharging = false;
            lockRotationWhileActive = true;
            primaryStatusEffect = "mov-oxidation-tier-3";
            primaryStatusDuration = 5f;
        }});

        // 19. Foreshadow (4x4) - Apex Execution
        register(Blocks.foreshadow, new AbilityProfile(){{
            id = AbilityId.FORESHADOW_APEX_EXECUTION;
            turretName = "foreshadow";
            abilityName = "Apex Execution";
            description = "Five clean hits, then it picks the biggest threat in range. Sniper reticles lock onto the highest-HP enemy, and the next hit lands for 2.2x damage plus 10% of its Max HP (capped at 4500), along with High EMP, Tier-3 armor-piercing and Tier-3 damage-reduction statuses.";
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

        // 20. Pierce (1x1 New Mod Turret) - Dead Eye
        if(PierceTurret.pierce != null){
            register(PierceTurret.pierce, new AbilityProfile(){{
                id = AbilityId.PIERCE_DEAD_EYE;
                turretName = "pierce";
                abilityName = "Dead Eye";
                description = "Time slows, targets get marked. After 5 confirmed unit hits, Pierce stamps X-marks on random enemies in range, then fires 5 near-instant piercing hypershots for 3.2x damage each, glowing in your ammo's color.";
                accentColor = Color.valueOf("ff6b57");
                trigger = AbilityTrigger.confirmedUnitHits(5);
                damageMultiplier = 3.2f;
                deadEyeShots = 5;
                deadEyeInterval = 3.0f;
            }});
        }
    }

    private static void register(Block block, AbilityProfile profile){
        if(block == null) return;
        all.add(profile);
        AbilityManager.register(block, profile);
    }
                                                     }
