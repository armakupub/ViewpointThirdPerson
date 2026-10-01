package pzmod.viewpointthirdperson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import viewpoint.platform.KeyBind;
import viewpoint.platform.Keys;
import viewpoint.platform.LiveSettings;

// Viewpoint keeps KeyBind's constructor and its key list to itself; declaring ours through
// them puts them in its Keys tab with its own capture, conflict freeing and saving.
public class ViewpointKeys {
    public static final KeyBind CURSOR;
    public static final KeyBind SHOULDER;

    static {
        KeyBind cursor = null;
        KeyBind shoulder = null;
        try {
            cursor = declare("thirdPersonCamera.keys.cursor", "Cursor on or off", "",
                    "Frees the cursor, or looks around again. Viewpoint does this on the middle mouse button; a key set here takes its place, and Default or Clear give it back.");
            shoulder = declare("thirdPersonCamera.keys.swapShoulder", "Swap shoulder", "",
                    "Moves the third-person camera to the other shoulder.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] keys not in Viewpoint's window, Viewpoint changed: " + e);
        }
        CURSOR = cursor;
        Patch_KeyBind.cursor = cursor;
        SHOULDER = shoulder;
    }

    public static void init() {
        ThirdPersonRig.init();
    }

    @SuppressWarnings("unchecked")
    static KeyBind declare(String key, String label, String chord, String tooltip) throws ReflectiveOperationException {
        Constructor<KeyBind> c = KeyBind.class.getDeclaredConstructor(String.class, String.class, String.class,
                KeyBind.Kind.class, boolean.class, String.class);
        c.setAccessible(true);
        KeyBind bind = c.newInstance(key, label, "Keys/Third person camera", KeyBind.Kind.PRESS, false, chord);
        Method add = LiveSettings.class.getDeclaredMethod("add", LiveSettings.Setting.class);
        add.setAccessible(true);
        add.invoke(null, bind);
        bind.describe(tooltip);
        Field all = Keys.class.getDeclaredField("ALL");
        all.setAccessible(true);
        ((List<KeyBind>) all.get(null)).add(bind);
        return bind;
    }

    public static boolean pressed(KeyBind bind) {
        return bind != null && bind.pressed();
    }
}
