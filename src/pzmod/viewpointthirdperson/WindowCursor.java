package pzmod.viewpointthirdperson;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import se.krka.kahlua.vm.KahluaTable;
import viewpoint.core.View;
import viewpoint.input.Look;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.ui.UIElement;
import zombie.ui.UIElementInterface;
import zombie.ui.UIManager;

// A game window opening while Viewpoint holds the mouse frees the cursor, and the cursor is held
// again once the windows that opened with it are closed, as Viewpoint does for its loot window.
// Windows already open stay out of it: the inventory usually is, collapsed. Locking the cursor by
// hand ends it. Runs after Viewpoint's own cursor toggles, so it sees the state they left.
public class WindowCursor {
    // Lua classes, by what they derive from; most windows mods add derive from the first two.
    static final String[] WINDOWS = {"ISCollapsableWindow", "ISCollapsableWindowJoypad", "ISInventoryPage",
            "ISWorldMap", "ISOvenUI", "ISMicrowaveUI", "ISVehicleSeatUI", "ISFitnessUI", "ISHealthPanel"};
    static final String[] NOT_WINDOWS = {"ISChat"};
    static final int MAX_DEPTH = 32;

    static final Set<UIElement> shown = Collections.newSetFromMap(new IdentityHashMap<>());
    static final Set<UIElement> now = Collections.newSetFromMap(new IdentityHashMap<>());
    static final Set<UIElement> ours = Collections.newSetFromMap(new IdentityHashMap<>());
    static final KahluaTable[] windows = new KahluaTable[WINDOWS.length];
    static final KahluaTable[] notWindows = new KahluaTable[NOT_WINDOWS.length];
    static Field cursorMode;
    static boolean broken;
    static boolean started;
    static boolean freed;

    static {
        try {
            cursorMode = Class.forName("viewpoint.FP").getDeclaredField("cursorMode");
            cursorMode.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            System.out.println("[ViewpointThirdPerson] windows do not free the cursor, Viewpoint changed: " + e);
        }
    }

    public static void update() {
        IsoPlayer p = IsoPlayer.players[0];
        if (broken || !MouseKeyboard.FREE_CURSOR.get() || !View.enabled || p == null || p.isDead() || ControllerLook.usesPad(p)
                || Qol.loaded()) {
            started = false;
            freed = false;
            shown.clear();
            ours.clear();
            return;
        }
        // Viewpoint frees the cursor for the pause and puts back what it was after.
        if (GameTime.isGamePaused()) return;
        scan();
        boolean free = cursorMode();
        boolean opened = false;
        if (started) {
            for (UIElement e : now) {
                if (shown.contains(e)) continue;
                opened = true;
                if (!free || freed) ours.add(e);
            }
        }
        started = true;
        shown.clear();
        shown.addAll(now);
        ours.retainAll(now);
        if (opened && !free) {
            setCursorMode(true);
            Look.wantCapture = false;
            freed = true;
        } else if (freed && (!free || ours.isEmpty())) {
            if (free) setCursorMode(false);
            freed = false;
            ours.clear();
        }
    }

    private static void scan() {
        now.clear();
        KahluaTable env = LuaManager.env;
        if (env == null) return;
        resolve(env, WINDOWS, windows);
        resolve(env, NOT_WINDOWS, notWindows);
        for (UIElementInterface ui : UIManager.getUI()) {
            if (!(ui instanceof UIElement) || !Boolean.TRUE.equals(ui.isVisible())) continue;
            UIElement e = (UIElement) ui;
            KahluaTable table = e.getTable();
            if (table != null && window(table)) now.add(e);
        }
    }

    private static void resolve(KahluaTable env, String[] names, KahluaTable[] out) {
        for (int i = 0; i < names.length; i++) {
            Object c = env.rawget(names[i]);
            out[i] = c instanceof KahluaTable ? (KahluaTable) c : null;
        }
    }

    private static boolean window(KahluaTable table) {
        KahluaTable c = table.getMetatable();
        for (int depth = 0; c != null && depth < MAX_DEPTH; depth++, c = c.getMetatable()) {
            if (any(notWindows, c)) return false;
            if (any(windows, c)) return true;
        }
        return false;
    }

    private static boolean any(KahluaTable[] classes, KahluaTable c) {
        for (KahluaTable k : classes) if (k == c) return true;
        return false;
    }

    static boolean cursorMode() {
        try {
            return cursorMode.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return true;
        }
    }

    static void setCursorMode(boolean on) {
        try {
            cursorMode.setBoolean(null, on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
        }
    }
}
