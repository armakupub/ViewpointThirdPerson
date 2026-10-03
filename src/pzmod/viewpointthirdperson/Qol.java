package pzmod.viewpointthirdperson;

import org.lwjglx.input.Keyboard;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.input.GameKeyboard;

// Project Viewpoint QOL (WS 3811256204) frees the cursor for windows and menus under its own options, and
// cycles the hotbar on its modifier key + wheel. Its options table is a Lua global.
public class Qol {
    public static boolean loaded() {
        return options() != null;
    }

    public static boolean hotbarWheel() {
        KahluaTable options = options();
        if (options == null || Boolean.FALSE.equals(options.rawget("enableHotbarWheel"))) return false;
        Object set = options.rawget("hotbarModifierKey");
        int key = set instanceof Double ? ((Double) set).intValue() : Keyboard.KEY_LMENU;
        return key > 0 && (GameKeyboard.isKeyDown(key) || GameKeyboard.isKeyDown(otherSide(key)));
    }

    // QOL takes either Alt, Ctrl or Shift for the one bound.
    private static int otherSide(int key) {
        switch (key) {
            case Keyboard.KEY_LMENU: return Keyboard.KEY_RMENU;
            case Keyboard.KEY_RMENU: return Keyboard.KEY_LMENU;
            case Keyboard.KEY_LCONTROL: return Keyboard.KEY_RCONTROL;
            case Keyboard.KEY_RCONTROL: return Keyboard.KEY_LCONTROL;
            case Keyboard.KEY_LSHIFT: return Keyboard.KEY_RSHIFT;
            case Keyboard.KEY_RSHIFT: return Keyboard.KEY_LSHIFT;
            default: return key;
        }
    }

    private static KahluaTable options() {
        KahluaTable env = LuaManager.env;
        Object options = env == null ? null : env.rawget("ProjectViewpointQOLOptions");
        return options instanceof KahluaTable ? (KahluaTable) options : null;
    }
}
