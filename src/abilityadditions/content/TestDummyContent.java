package abilityadditions.content;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

public class TestDummyContent{

    public static UnitType dummyGround;
    public static UnitType dummyAir;
    public static Block dummyPad;

    private static final float SNAP_BACK_DELAY = 150f; // 2.5s of being completely still and undamaged
    private static final float DPS_WINDOW_TICKS = 60f; // 1.0s rolling DPS window
    private static final float DUMMY_MAX_HEALTH = 1_000_000f;
    private static final float MOVE_EPSILON = 0.0004f; // squared speed / squared per-tick movement treated as "moving"

    private static final IntMap<DummyStats> statsByUnitId = new IntMap<>();
    private static boolean eventsRegistered = false;

    public static void load(){
        dummyGround = createDummyUnit("test-dummy-ground", false);
        dummyAir = createDummyUnit("test-dummy-air", true);

        dummyPad = new Block("test-dummy-spawner"){{
            size = 1;
            requirements(Category.units, BuildVisibility.sandboxOnly, ItemStack.empty);
            update = true;
            solid = false;
            targetable = false;
            destructible = false;
            underBullets = true;
            replaceable = true;
            rebuildable = false;
            drawTeamOverlay = false;
            hasShadow = false;
            configurable = true;
            saveConfig = true;
            copyConfig = true;
            sync = true;
            buildType = TestDummyPadBuild::new;

            // Config encoding: bit 0 = autoTarget, bit 1 = airMode, -1 = reset action
            config(Integer.class, (TestDummyPadBuild build, Integer val) -> {
                if(val == null) return;
                if(val == -1){
                    build.resetStatsAndPosition();
                    return;
                }
                boolean newAuto = (val & 1) != 0;
                boolean newAir = (val & 2) != 0;
                boolean typeChanged = build.airMode != newAir;
                build.autoTarget = newAuto;
                build.airMode = newAir;
                if(typeChanged){
                    build.respawnDummy(true);
                }
            });
        }

            @Override
            public void load(){
                super.load();
                if(Blocks.copperWall != null && Blocks.copperWall.region != null){
                    region = Blocks.copperWall.region;
                }
            }

            @Override
            protected TextureRegion[] icons(){
                TextureRegion tex = Blocks.copperWall != null ? Blocks.copperWall.region : region;
                return new TextureRegion[]{tex};
            }

            @Override
            public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
                TextureRegion tex = Blocks.copperWall != null ? Blocks.copperWall.region : region;
                Draw.rect(tex, plan.drawx(), plan.drawy());
            }
        };
    }

    public static void init(){
        if(eventsRegistered) return;
        eventsRegistered = true;

        Events.on(UnitDamageEvent.class, e -> {
            if(e.unit == null) return;
            DummyStats st = statsByUnitId.get(e.unit.id);
            if(st != null){
                st.idleTimer = 0f;
            }
        });

        Events.on(ResetEvent.class, e -> statsByUnitId.clear());
    }

    private static UnitType createDummyUnit(String name, boolean isFlying){
        return new UnitType(name){{
            flying = isFlying;
            constructor = isFlying ? UnitEntity::create : MechUnit::create;
            aiController = AIController::new;

            health = DUMMY_MAX_HEALTH;
            armor = 0f;
            hitSize = 12f;
            speed = 0f;
            accel = 0.08f;
            drag = 0.14f;
            rotateSpeed = 0f;
            baseRotateSpeed = 0f;
            knockbackMultiplier = 1f;

            canAttack = false;
            playerControllable = false;
            logicControllable = false;
            allowedInPayloads = false;
            useUnitCap = false;
            isEnemy = false;   // not counted as a wave enemy
            physics = true;
            hittable = true;
            // IMPORTANT: must be true, otherwise the game ignores damage to health
            // and DPS / total damage can never be measured. Immortality is handled
            // in update() by refilling health every tick.
            killable = true;
            targetable = true; // overridden dynamically per-unit below
            healFlash = false;
            drawCell = false;
            drawMinimap = false;
            createWreck = false;
            createScorch = false;
            hovering = !isFlying;
            canDrown = false;
            engineSize = 0f;
            mechSideSway = 0f;
            mechFrontSway = 0f;
            hidden = true;
        }

            @Override
            public boolean targetable(Unit unit, Team targeter){
                DummyStats st = statsByUnitId.get(unit.id);
                if(st == null || !st.autoTarget) return false; // Manual: never auto-targeted
                return targeter != unit.team;                   // Auto: enemies see it as a target
            }

            @Override
            public void update(Unit unit){
                super.update(unit);

                DummyStats st = statsByUnitId.get(unit.id);
                if(st == null){
                    if(!Vars.net.client()){
                        unit.remove();
                    }
                    return;
                }

                // Keep immortal
                unit.dead = false;
                if(unit.maxHealth != DUMMY_MAX_HEALTH){
                    unit.maxHealth = DUMMY_MAX_HEALTH;
                }

                // Damage taken since last tick, then refill
                float tickDamage = Math.max(0f, st.prevHealth - unit.health);
                unit.health = unit.maxHealth;
                st.prevHealth = unit.maxHealth;

                // Is it being pushed around / still sliding?
                boolean moving = unit.vel.len2() > MOVE_EPSILON
                    || Mathf.dst2(unit.x, unit.y, st.lastX, st.lastY) > MOVE_EPSILON;
                st.lastX = unit.x;
                st.lastY = unit.y;

                if(tickDamage > 0.001f){
                    st.lastHit = tickDamage;
                    st.totalDamage += tickDamage;
                    st.samples.add(new DamageSample(Time.time, tickDamage));
                }

                // Idle timer only counts up while NOT damaged and NOT moving
                if(tickDamage > 0.001f || moving){
                    st.idleTimer = 0f;
                }else{
                    st.idleTimer += Time.delta;
                }

                // Prune rolling 1s DPS samples
                float cutoff = Time.time - DPS_WINDOW_TICKS;
                while(st.samples.size > 0 && st.samples.get(0).time < cutoff){
                    st.samples.remove(0);
                }
                float sum = 0f;
                for(int i = 0; i < st.samples.size; i++){
                    sum += st.samples.get(i).amount;
                }
                st.currentDps = sum;
                if(sum > st.peakDps){
                    st.peakDps = sum;
                }

                // Return to pad center only after being still + undamaged for 2.5s
                if(st.pad != null && st.pad.isAdded()){
                    if(st.idleTimer >= SNAP_BACK_DELAY && !unit.within(st.pad.x, st.pad.y, 2f)){
                        Fx.teleport.at(unit.x, unit.y, Pal.accent);
                        unit.set(st.pad.x, st.pad.y);
                        unit.vel.setZero();
                        unit.rotation = 90f;
                        Fx.teleportActivate.at(st.pad.x, st.pad.y, Pal.accent);
                        st.idleTimer = 0f;
                        st.lastX = unit.x;
                        st.lastY = unit.y;
                    }
                }else if(!Vars.net.client()){
                    statsByUnitId.remove(unit.id);
                    unit.remove();
                }
            }

            @Override
            public void draw(Unit unit){
                super.draw(unit);
                if(Vars.headless) return;

                DummyStats st = statsByUnitId.get(unit.id);
                if(st != null){
                    drawDummyOverlay(unit, st);
                }
            }
        };
    }

    private static void drawDummyOverlay(Unit unit, DummyStats st){
        Font font = Fonts.outline;
        if(font == null) return;

        String modeTag = st.autoTarget ? "[#84f491]AUTO[]" : "[#ffd466]MANUAL[]";
        String typeTag = unit.isFlying() ? "[sky]Air[]" : "[tan]Ground[]";

        String line1 = modeTag + " [lightgray]|[] " + typeTag;
        String line2 = "[accent]DPS:[] " + fmt(st.currentDps) + "  [lightgray]Peak:[] " + fmt(st.peakDps);
        String line3 = "[white]Total:[] " + fmt(st.totalDamage) + "  [lightgray]Last:[] " + fmt(st.lastHit);

        StringBuilder statusSb = new StringBuilder();
        int statusCount = 0;
        for(StatusEffect eff : Vars.content.statusEffects()){
            if(eff != StatusEffects.none && unit.hasEffect(eff)){
                if(statusCount > 0) statusSb.append("[lightgray], []");
                statusSb.append("[#").append(eff.color).append("]").append(eff.localizedName).append("[]");
                statusCount++;
                if(statusCount >= 5){
                    statusSb.append("...");
                    break;
                }
            }
        }
        String line4 = statusCount > 0 ? "[lightgray]Effects:[] " + statusSb : "";

        String fullText = line4.isEmpty()
            ? line1 + "\n" + line2 + "\n" + line3
            : line1 + "\n" + line2 + "\n" + line3 + "\n" + line4;

        float oldZ = Draw.z();
        Draw.z(Layer.flyingUnit + 5f);

        float oldScaleX = font.getData().scaleX;
        float oldScaleY = font.getData().scaleY;
        boolean oldMarkup = font.getData().markupEnabled;

        font.getData().markupEnabled = true;
        font.getData().setScale(0.22f);

        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        layout.setText(font, fullText, Color.white, 0f, Align.center, false);

        float baseX = unit.x;
        float baseY = unit.y + unit.hitSize + 14f + layout.height;

        Draw.color(0f, 0f, 0f, 0.55f);
        Fill.rect(baseX, baseY - layout.height * 0.5f, layout.width + 8f, layout.height + 6f);
        Draw.color();

        font.setColor(Color.white);
        font.draw(fullText, baseX, baseY, 0f, Align.center, false);

        Pools.free(layout);
        font.getData().setScale(oldScaleX, oldScaleY);
        font.getData().markupEnabled = oldMarkup;
        Draw.z(oldZ);
    }

    private static String fmt(float val){
        if(val >= 1_000_000f) return Strings.fixed(val / 1_000_000f, 2) + "M";
        if(val >= 1_000f) return Strings.fixed(val / 1_000f, 1) + "k";
        return Strings.fixed(val, val < 10f && val > 0.01f ? 1 : 0);
    }

    public static class TestDummyPadBuild extends Building{
        public boolean autoTarget = false; // Manual by default on spawn
        public boolean airMode = false;    // Ground by default on spawn
        public @Nullable Unit dummy;

        /** The dummy must be on a team that is hostile to this pad's team, otherwise nothing will target it.
         *  (Derelict is always ignored by targeting.) */
        private Team dummyTeam(){
            Team wave = Vars.state.rules.waveTeam;
            return wave != team ? wave : Team.sharded;
        }

        @Override
        public void placed(){
            super.placed();
            if(!Vars.net.client()){
                respawnDummy(false);
            }
        }

        @Override
        public void updateTile(){
            if(!Vars.net.client()){
                if(dummy == null || !dummy.isAdded() || dummy.dead){
                    respawnDummy(false);
                }else{
                    DummyStats st = statsByUnitId.get(dummy.id);
                    if(st != null){
                        st.autoTarget = autoTarget;
                        st.pad = this;
                    }
                }
            }
        }

        @Override
        public void draw(){
            TextureRegion tex = Blocks.copperWall != null ? Blocks.copperWall.region : block.region;
            Draw.rect(tex, x, y);
        }

        @Override
        public void onRemoved(){
            super.onRemoved();
            removeLinkedDummy();
        }

        @Override
        public void onDestroyed(){
            super.onDestroyed();
            removeLinkedDummy();
        }

        private void removeLinkedDummy(){
            if(dummy != null){
                statsByUnitId.remove(dummy.id);
                if(dummy.isAdded()){
                    dummy.remove();
                }
                dummy = null;
            }
        }

        public void respawnDummy(boolean keepStats){
            DummyStats oldStats = dummy != null ? statsByUnitId.get(dummy.id) : null;
            removeLinkedDummy();

            UnitType type = airMode ? dummyAir : dummyGround;
            if(type == null) return;

            Unit spawned = type.spawn(dummyTeam(), x, y, 90f);
            spawned.maxHealth = DUMMY_MAX_HEALTH;
            spawned.health = DUMMY_MAX_HEALTH;

            DummyStats st = keepStats && oldStats != null ? oldStats : new DummyStats();
            st.pad = this;
            st.autoTarget = autoTarget;
            st.prevHealth = DUMMY_MAX_HEALTH;
            st.idleTimer = 0f;
            st.lastX = spawned.x;
            st.lastY = spawned.y;
            statsByUnitId.put(spawned.id, st);
            dummy = spawned;
        }

        public void resetStatsAndPosition(){
            if(dummy != null){
                DummyStats st = statsByUnitId.get(dummy.id);
                if(st != null){
                    st.totalDamage = 0f;
                    st.lastHit = 0f;
                    st.currentDps = 0f;
                    st.peakDps = 0f;
                    st.idleTimer = 0f;
                    st.samples.clear();
                }
                for(StatusEffect eff : Vars.content.statusEffects()){
                    if(eff != StatusEffects.none && dummy.hasEffect(eff)){
                        dummy.unapply(eff);
                    }
                }
                dummy.set(x, y);
                dummy.vel.setZero();
                dummy.rotation = 90f;
                if(st != null){
                    st.lastX = dummy.x;
                    st.lastY = dummy.y;
                }
                Fx.teleportActivate.at(x, y, Pal.accent);
            }
        }

        private int packConfig(){
            return (autoTarget ? 1 : 0) | (airMode ? 2 : 0);
        }

        @Override
        public Object config(){
            return packConfig();
        }

        @Override
        public void buildConfiguration(Table table){
            table.table(Styles.black6, t -> {
                t.defaults().width(175f).height(42f).pad(3f);

                t.button(b -> b.label(() -> autoTarget ? "Target: Auto" : "Target: Manual"), Styles.togglet, () -> {
                    int next = packConfig() ^ 1;
                    configure(next);
                }).update(b -> b.setChecked(autoTarget)).row();

                t.button(b -> b.label(() -> airMode ? "Type: Air" : "Type: Ground"), Styles.togglet, () -> {
                    int next = packConfig() ^ 2;
                    configure(next);
                }).update(b -> b.setChecked(airMode)).row();

                t.button("Reset Stats & Pos", Icon.refresh, Styles.cleart, () -> {
                    configure(-1);
                }).row();
            });
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(autoTarget);
            write.bool(airMode);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            autoTarget = read.bool();
            airMode = read.bool();
        }
    }

    private static class DummyStats{
        TestDummyPadBuild pad;
        boolean autoTarget = false;
        float prevHealth = DUMMY_MAX_HEALTH;
        float totalDamage = 0f;
        float lastHit = 0f;
        float currentDps = 0f;
        float peakDps = 0f;
        float idleTimer = 0f;   // ticks spent still and undamaged
        float lastX = 0f;
        float lastY = 0f;
        final Seq<DamageSample> samples = new Seq<>();
    }

    private static class DamageSample{
        final float time;
        final float amount;

        DamageSample(float time, float amount){
            this.time = time;
            this.amount = amount;
        }
    }
                                               } 
