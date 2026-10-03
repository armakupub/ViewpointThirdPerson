package pzmod.viewpointthirdperson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import viewpoint.platform.KeyBind;
import viewpoint.platform.Keys;
import viewpoint.platform.LiveSettings;

// Viewpoint keeps KeyBind's constructor and its key list to itself; declaring ours through
// them puts them in its window with its own capture, conflict freeing and saving.
public class ViewpointKeys {
    public static volatile KeyBind CURSOR;
    public static volatile KeyBind SHOULDER;
    public static volatile KeyBind LOOK_AROUND;
    static boolean declared;

    // After the camera's settings, so the keys come last in the tab, as Viewpoint's own come
    // after its controls.
    public static void init() {
        ThirdPersonRig.init();
        if (declared) return;
        declared = true;
        try {
            KeyBind cursor = declare("thirdPersonCamera.keys.cursor", "Cursor on or off", KeyBind.Kind.PRESS, "",
                    "Frees the cursor, or looks around again. Viewpoint does this on the middle mouse button; a key set here takes its place, and Default or Clear give it back.");
            KeyBind shoulder = declare("thirdPersonCamera.keys.swapShoulder", "Swap shoulder", KeyBind.Kind.PRESS, "",
                    "Moves the third-person camera to the other shoulder.");
            KeyBind lookAround = declare("thirdPersonCamera.keys.lookAround", "Look around", KeyBind.Kind.HOLD, "",
                    "While held on foot, the mouse turns the camera round the character, who keeps facing and walking the same way. Letting go or aiming brings it back behind.");
            Patch_KeyBind.cursor = cursor;
            CURSOR = cursor;
            SHOULDER = shoulder;
            LOOK_AROUND = lookAround;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] keys not in Viewpoint's window, Viewpoint changed: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    static KeyBind declare(String key, String label, KeyBind.Kind kind, String chord, String tooltip) throws ReflectiveOperationException {
        Constructor<KeyBind> c = KeyBind.class.getDeclaredConstructor(String.class, String.class, String.class,
                KeyBind.Kind.class, boolean.class, String.class);
        c.setAccessible(true);
        KeyBind bind = c.newInstance(key, label, ThirdPersonRig.TAB + "/Keys", kind, false, chord);
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
