package abilityadditions.core;

import arc.math.Mathf;

public interface AbilityTrigger{
    boolean checkAndAdvance(AbilityManager.TurretState state);

    String describe();

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
        };
    }
} 
