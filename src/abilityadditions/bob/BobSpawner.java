package abilityadditions.bob;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.world.Tile;

public class BobSpawner{
    private static final float RESPAWN_DELAY_TICKS = 120f * 60f; // 2 minutes
    private static final float LOAD_GRACE_TICKS = 60f; // wait 1s after sector load before checking

    private static final Seq<Tile> snowTiles = new Seq<>();
    private static float respawnTimer = 0f;
    private static float loadGraceTimer = 0f;
    private static boolean initialCheckDone = false;
    private static boolean initialized = false;

    public static void init(){
        if(initialized) return;
        initialized = true;

        Events.on(EventType.WorldLoadEvent.class, e -> {
            collectSnowTiles();
            respawnTimer = 0f;
            loadGraceTimer = LOAD_GRACE_TICKS;
            initialCheckDone = false;
        });

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if(!Vars.net.client() && e.unit != null && e.unit.type == BobUnit.bob){
                if(countBobs() <= 1){
                    respawnTimer = RESPAWN_DELAY_TICKS;
                }
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if(Vars.net.client() || Vars.state == null || Vars.state.isPaused()) return;
            if(snowTiles.size == 0) return;

            // Remove any extra duplicate Bobs if a sector save ever created more than 1
            dedupeBobs();

            if(!initialCheckDone){
                loadGraceTimer -= Time.delta;
                if(loadGraceTimer <= 0f){
                    initialCheckDone = true;
                    if(countBobs() == 0){
                        spawnBob();
                    }
                }
                return;
            }

            if(countBobs() > 0) return;

            if(respawnTimer > 0f){
                respawnTimer -= Time.delta;
                if(respawnTimer <= 0f){
                    respawnTimer = 0f;
                    spawnBob();
                }
            }
        });
    }

    private static void collectSnowTiles(){
        snowTiles.clear();
        if(Vars.world == null || Vars.world.tiles == null) return;

        for(Tile tile : Vars.world.tiles){
            if(tile.floor() == Blocks.snow && !tile.solid()){
                snowTiles.add(tile);
            }
        }
    }

    private static int countBobs(){
        int count = 0;
        for(Unit u : Groups.unit){
            if(u.type == BobUnit.bob && u.isValid() && !u.dead){
                count++;
            }
        }
        return count;
    }

    private static void dedupeBobs(){
        boolean foundFirst = false;
        for(Unit u : Groups.unit){
            if(u.type == BobUnit.bob && u.isValid() && !u.dead){
                if(!foundFirst){
                    foundFirst = true;
                }else{
                    u.remove();
                }
            }
        }
    }

    private static void spawnBob(){
        if(snowTiles.size == 0 || BobUnit.bob == null || countBobs() > 0) return;
        Tile spot = snowTiles.get(Mathf.random(snowTiles.size - 1));
        BobUnit.bob.spawn(Team.derelict, spot.worldx(), spot.worldy());
    }
} 
