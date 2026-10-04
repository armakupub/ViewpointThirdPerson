package pzmod.viewpointthirdperson;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// Viewpoint - Interface (WS 3812616893) keeps the mouse captured through radial menus and moves a hidden
// pointer round them, and scrolls its own menu with the wheel. Its flags lapse 0.3 s after its Lua stops
// renewing them, so a jar left loaded from an earlier save answers false.
public class ViewpointInterface {
    static MethodHandle radialActive;
    static MethodHandle menuIsOpen;
    static boolean resolved;

    public static boolean radial() {
        resolve();
        return ask(radialActive);
    }

    public static boolean menu() {
        resolve();
        return ask(menuIsOpen);
    }

    private static boolean ask(MethodHandle h) {
        if (h == null) return false;
        try {
            return (boolean) h.invokeExact();
        } catch (Throwable t) {
            radialActive = null;
            menuIsOpen = null;
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        Class<?> vp = find(ViewpointInterface.class.getClassLoader());
        if (vp == null) vp = find(Thread.currentThread().getContextClassLoader());
        if (vp == null) vp = find(ClassLoader.getSystemClassLoader());
        if (vp == null) return;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType type = MethodType.methodType(boolean.class);
            radialActive = lookup.findStatic(vp, "radialActive", type);
            menuIsOpen = lookup.findStatic(vp, "menuIsOpen", type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            radialActive = null;
            menuIsOpen = null;
            System.out.println("[ViewpointThirdPerson] Viewpoint - Interface changed, not stepping back for it: " + e);
        }
    }

    private static Class<?> find(ClassLoader loader) {
        if (loader == null) return null;
        try {
            return Class.forName("vpui.VP", false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
