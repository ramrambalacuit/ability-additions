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

    private static final Seq<Tile> snowTiles = new Seq<>();
    private static float respawnTimer = 0f;
    private static boolean initialized = false;

    public static void init(){
        if(initialized) return;
        initialized = true;

        Events.on(EventType.WorldLoadEvent.class, e -> {
            collectSnowTiles();
            respawnTimer = 0f;
            if(!Vars.net.client() && snowTiles.size > 0 && !bobExists()){
                spawnBob();
            }
        });

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if(!Vars.net.client() && e.unit != null && e.unit.type == BobUnit.bob){
                respawnTimer = RESPAWN_DELAY_TICKS;
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if(Vars.net.client() || Vars.state == null || Vars.state.isPaused()) return;
            if(snowTiles.size == 0 || bobExists()) return;

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

    private static boolean bobExists(){
        for(Unit u : Groups.unit){
            if(u.type == BobUnit.bob && u.isValid() && !u.dead){
                return true;
            }
        }
        return false;
    }

    private static void spawnBob(){
        if(snowTiles.size == 0 || BobUnit.bob == null) return;
        Tile spot = snowTiles.get(Mathf.random(snowTiles.size - 1));
        BobUnit.bob.spawn(Team.derelict, spot.worldx(), spot.worldy());
    }
}
