package pzmod.viewpointthirdperson;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.lwjglx.input.Keyboard;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.input.GameKeyboard;

// Project Viewpoint QOL (WS 3811256204) frees the cursor for windows and menus under its own options, and
// cycles the hotbar and changes the field of view on modifier keys + wheel. Its options table is a Lua global.
// Our zoom takes the wheel in third person before QOL sees it, so its switch to first person goes through here.
public class Qol {
    static MethodHandle getFactor;
    static MethodHandle setFactor;
    static MethodHandle setThirdPerson;
    static boolean resolved;

    public static boolean loaded() {
        return options() != null;
    }

    public static boolean hotbarWheel() {
        return wheelWith("enableHotbarWheel", "hotbarModifierKey", Keyboard.KEY_LMENU);
    }

    // Modifier + wheel changes QOL's field of view, in both views.
    public static boolean fovWheel() {
        return wheelWith("enableMouseWheelZoom", "zoomFovModifierKey", Keyboard.KEY_LCONTROL);
    }

    public static boolean toFirstPerson() {
        if (!zoomOn()) return false;
        try {
            dropTarget();
            setFactor.invokeExact(1.0f);
            setThirdPerson.invokeExact(false);
            return true;
        } catch (Throwable t) {
            lost(t);
            return false;
        }
    }

    // After QOL's scroll-out of first person; its factor would shrink our wall grid.
    public static boolean leftFirstPerson() {
        if (!zoomOn()) return false;
        try {
            boolean pending = dropTarget();
            if (!pending && (float) getFactor.invokeExact() == 1.0f) return false;
            setFactor.invokeExact(1.0f);
            return true;
        } catch (Throwable t) {
            lost(t);
            return false;
        }
    }

    private static boolean zoomOn() {
        KahluaTable options = options();
        if (options == null || Boolean.FALSE.equals(options.rawget("enableThirdPersonZoom"))) return false;
        resolve();
        return setThirdPerson != null;
    }

    private static boolean dropTarget() {
        Object qol = LuaManager.env == null ? null : LuaManager.env.rawget("ProjectViewpointQOL");
        if (!(qol instanceof KahluaTable) || ((KahluaTable) qol).rawget("zoomTargetFactor") == null) return false;
        ((KahluaTable) qol).rawset("zoomTargetFactor", null);
        return true;
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        Class<?> zoom = find(Qol.class.getClassLoader());
        if (zoom == null) zoom = find(Thread.currentThread().getContextClassLoader());
        if (zoom == null) zoom = find(ClassLoader.getSystemClassLoader());
        if (zoom == null) return;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getFactor = lookup.findStatic(zoom, "getFactor", MethodType.methodType(float.class));
            setFactor = lookup.findStatic(zoom, "setFactor", MethodType.methodType(void.class, float.class));
            setThirdPerson = lookup.findStatic(zoom, "setThirdPerson", MethodType.methodType(void.class, boolean.class));
        } catch (ReflectiveOperationException | RuntimeException e) {
            lost(e);
        }
    }

    private static void lost(Throwable t) {
        getFactor = null;
        setFactor = null;
        setThirdPerson = null;
        System.out.println("[ViewpointThirdPerson] Project Viewpoint QOL changed, no hand-over to its first-person zoom: " + t);
    }

    private static Class<?> find(ClassLoader loader) {
        if (loader == null) return null;
        try {
            return Class.forName("viewpointqol.ViewpointQOLZoom", false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean wheelWith(String enabled, String modifier, int fallback) {
        KahluaTable options = options();
        if (options == null || Boolean.FALSE.equals(options.rawget(enabled))) return false;
        Object set = options.rawget(modifier);
        int key = set instanceof Double ? ((Double) set).intValue() : fallback;
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
