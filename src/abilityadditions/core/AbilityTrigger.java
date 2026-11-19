package abilityadditions.core;

import arc.math.Mathf;

public interface AbilityTrigger{
    boolean checkAndAdvance(AbilityManager.TurretState state);

    String describe();

    default float progress(AbilityManager.TurretState state){
        return 0f;
    }

    default String barText(AbilityManager.TurretState state){
        return describe();
    }

    static AbilityTrigger everyShots(int requiredShots){
        return new AbilityTrigger(){
            @Override
            public boolean checkAndAdvance(AbilityManager.TurretState state){
                state.shotCount++;
                if(state.shotCount >= requiredShots){
                    state.shotCount = 0;
                    return true;
                }
                return false;
            }

            @Override
            public String describe(){
                return "Every " + requiredShots + " shots";
            }

            @Override
            public float progress(AbilityManager.TurretState state){
                return state == null ? 0f : (float)state.shotCount / requiredShots;
            }

            @Override
            public String barText(AbilityManager.TurretState state){
                int count = state == null ? 0 : state.shotCount;
                return count + "/" + requiredShots;
            }
        };
    }

    static AbilityTrigger chance(float probability){
        return new AbilityTrigger(){
            @Override
            public boolean checkAndAdvance(AbilityManager.TurretState state){
                return Mathf.chance(probability);
            }

            @Override
            public String describe(){
                return Math.round(probability * 100f) + "% chance per shot";
            }

            @Override
            public String barText(AbilityManager.TurretState state){
                return Math.round(probability * 100f) + "% chance";
            }
        };
    }

    static AbilityTrigger chancePerCycle(float probability, float cycleCooldownSeconds){
        return new AbilityTrigger(){
            @Override
            public boolean checkAndAdvance(AbilityManager.TurretState state){
                if(state.triggerCooldown > 0f) return false;
                state.triggerCooldown = cycleCooldownSeconds * 60f;
                return Mathf.chance(probability);
            }

            @Override
            public String describe(){
                return Math.round(probability * 100f) + "% chance per volley";
            }

            @Override
            public String barText(AbilityManager.TurretState state){
                return Math.round(probability * 100f) + "% / volley";
            }
        };
    }

    static AbilityTrigger chanceOrShots(float probability, int maxShots){
        return new AbilityTrigger(){
            @Override
            public boolean checkAndAdvance(AbilityManager.TurretState state){
                state.shotCount++;
                if(state.shotCount >= maxShots || Mathf.chance(probability)){
                    state.shotCount = 0;
                    return true;
                }
                return false;
            }

            @Override
            public String describe(){
                return Math.round(probability * 100f) + "% chance (guaranteed every " + maxShots + " shots)";
            }

            @Override
            public float progress(AbilityManager.TurretState state){
                return state == null ? 0f : (float)state.shotCount / maxShots;
            }

            @Override
            public String barText(AbilityManager.TurretState state){
                int count = state == null ? 0 : state.shotCount;
                return count + "/" + maxShots;
            }
        };
    }
}
