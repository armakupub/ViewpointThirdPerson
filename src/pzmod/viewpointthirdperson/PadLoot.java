package pzmod.viewpointthirdperson;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.interact.LootMenu;
import zombie.core.Core;
import zombie.core.Translator;
import zombie.characters.CharacterJoypadButtonBinding;
import zombie.input.JoypadButton;
import zombie.input.JoypadManager;

// Viewpoint's loot panel answers only the mouse wheel and keys. With a controller, while the
// panel shows: D-pad down (the emote wheel otherwise) selects the next row, round to the first
// after the last; RB (reload otherwise) takes the row or does its action, held it takes all.
// The panel only shows while not aiming, so reloading while aiming stays as it was.
public class PadLoot {
    static final long HOLD_NANOS = 450_000_000L;
    static final int TAKE = 1;
    static final int TAKE_ALL = 2;

    static Field shown, scroll, pending, rows, selected;
    static Method interacts, nextSelectable, loot, action;
    static boolean ok;
    static boolean pressing;
    static boolean rbPressed, rbHeld;
    static long rbAt;
    public static volatile boolean rbTaken;
    static String hint = "";
    static int hintFlags = -1;

    static {
        try {
            shown = field(LootMenu.class, "shown");
            scroll = field(LootMenu.class, "scroll");
            pending = field(LootMenu.class, "pending");
            rows = field(LootMenu.class, "rows");
            interacts = LootMenu.class.getDeclaredMethod("interacts");
            interacts.setAccessible(true);
            Class<?> lootRows = rows.getType();
            selected = field(lootRows, "selected");
            nextSelectable = lootRows.getDeclaredMethod("nextSelectable", int.class, int.class);
            nextSelectable.setAccessible(true);
            loot = LootMenu.class.getDeclaredMethod("loot");
            loot.setAccessible(true);
            action = lootRows.getDeclaredMethod("action");
            action.setAccessible(true);
            ok = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] loot panel stays mouse-only, Viewpoint changed: " + e);
        }
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public static boolean shown() {
        try {
            return ok && ControllerLook.active && shown.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    // Lua, on D-pad down (repeated while held). True keeps the emote wheel shut.
    public static boolean press() {
        if (!shown()) {
            pressing = false;
            return false;
        }
        pressing = true;
        return true;
    }

    public static void release() {
        if (!pressing) return;
        pressing = false;
        if (!shown()) return;
        try {
            Object r = rows.get(null);
            int next = (int) nextSelectable.invoke(r, selected.getInt(r), 1);
            scroll.setInt(null, next < 0 ? -1000 : 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    // Lua, on RB in the world. True keeps the game's RB prompt from running. What it does is
    // settled on letting go, or once held long enough.
    public static boolean take() {
        if (!shown()) return false;
        rbTaken = true;
        if (!rbPressed) {
            rbPressed = true;
            rbHeld = false;
            rbAt = System.nanoTime();
        }
        return true;
    }

    // Main thread, once a frame. RB stays off reloading until let go, even once the panel
    // has gone with what it took.
    public static void update() {
        int id = ControllerLook.bind;
        boolean down = id >= 0 && JoypadManager.instance.isRBPressed(id);
        if (rbPressed) {
            if (!down) {
                rbPressed = false;
                if (!rbHeld) ask(TAKE);
            } else if (!rbHeld && System.nanoTime() - rbAt >= HOLD_NANOS) {
                rbHeld = true;
                ask(TAKE_ALL);
            }
        }
        if (!down) rbTaken = false;
        else if (shown()) rbTaken = true;
    }

    // As Viewpoint's own keys ask it.
    static void ask(int what) {
        if (!shown()) return;
        try {
            boolean can = what == TAKE_ALL ? (boolean) loot.invoke(null) : (boolean) interacts.invoke(null);
            if (can) pending.setInt(null, what);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    // Render thread: the panel's key line with the controller's buttons, in the game's words.
    public static String hint(Object lootRows) {
        try {
            boolean press = (boolean) interacts.invoke(null);
            boolean act = (int) action.invoke(lootRows) >= 0;
            boolean all = (boolean) loot.invoke(null);
            JoypadButton lootButton = CharacterJoypadButtonBinding.Inventory.getJoypadButton();
            int style = Core.getInstance().getOptionControllerButtonStyle();
            int flags = (press ? 1 : 0) | (act ? 2 : 0) | (all ? 4 : 0) | (lootButton == null ? 0 : (lootButton.ordinal() + 1) << 3) | style << 8;
            if (flags != hintFlags) {
                hintFlags = flags;
                String rb = name(JoypadButton.RightBump, style);
                String h = press ? key(rb, act ? "IGUI_Controller_Interact" : "ContextMenu_Grab") : "";
                if (all) h = join(h, key(rb + " (hold)", "IGUI_invpage_Loot_all"));
                h = join(h, key("D-pad Down", "IGUI_CycleItems"));
                if (all && lootButton != null) h = join(h, key(name(lootButton, style), "IGUI_Controller_Loot"));
                hint = h;
            }
            return hint;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    // The button the game shows as Loot beside something to loot (its Inventory binding).
    // As the controller the player picked in the game's options calls it (Xbox 1, PlayStation 2,
    // Steam Deck 3).
    static String name(JoypadButton b, int style) {
        boolean ps = style == 2;
        boolean xbox = style != 2 && style != 3;
        switch (b) {
            case A: return ps ? "Cross" : "A";
            case B: return ps ? "Circle" : "B";
            case X: return ps ? "Square" : "X";
            case Y: return ps ? "Triangle" : "Y";
            case LeftBump: return xbox ? "LB" : "L1";
            case RightBump: return xbox ? "RB" : "R1";
            case LeftStick: return "L3";
            case RightStick: return "R3";
            case DPadUp: return "D-pad Up";
            case DPadDown: return "D-pad Down";
            case DPadLeft: return "D-pad Left";
            case DPadRight: return "D-pad Right";
            default: return b.name();
        }
    }

    static String key(String button, String text) {
        return "[" + button + "] " + Translator.getText(text);
    }

    static String join(String a, String b) {
        return a.isEmpty() ? b : a + "   " + b;
    }

    static void fail(Exception e) {
        ok = false;
        pressing = false;
        System.out.println("[ViewpointThirdPerson] loot panel controller input off after error: " + e);
    }

    @Patch(className = "viewpoint.interact.LootPanel", methodName = "hint")
    public static class Patch_hint {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object lootRows, @Patch.Return(readOnly = false) String ret) {
            if (!ControllerLook.active) return;
            String h = PadLoot.hint(lootRows);
            if (h != null) ret = h;
        }
    }

    @Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isReloadWeaponButtonDownInternal")
    public static class Patch_reload {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean ret) {
            if (ret && (PadLoot.rbTaken || PadLoot.shown())) ret = false;
        }
    }
}
