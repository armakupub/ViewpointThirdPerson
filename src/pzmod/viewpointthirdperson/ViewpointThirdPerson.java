package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Exposer;
import viewpoint.core.View;
import viewpoint.input.ThirdPerson;
import viewpoint.platform.KeyBind;
import viewpoint.platform.Keys;

@Exposer.LuaClass
public class ViewpointThirdPerson {
    public static void init() {
        ViewpointKeys.init();
    }

    public static boolean cursorPressed() {
        return ViewpointKeys.pressed(ViewpointKeys.CURSOR);
    }

    public static boolean installControllerPreset() {
        return ControllerPreset.install();
    }

    // The Back wheel's slices, by Viewpoint's key ids.
    public static void padKey(String id) {
        KeyBind bind = "keys.firstPerson".equals(id) ? Keys.FIRST_PERSON
                : "keys.thirdPerson".equals(id) ? Keys.THIRD_PERSON
                : "thirdPersonCamera.keys.swapShoulder".equals(id) ? ViewpointKeys.SHOULDER : null;
        if (bind != null) Patch_KeyBind.press(bind);
    }

    public static boolean viewOn() {
        return View.enabled;
    }

    public static boolean thirdPersonOn() {
        return ThirdPerson.active;
    }

    public static boolean lootPanelPress() {
        return PadLoot.press();
    }

    public static void lootPanelRelease() {
        PadLoot.release();
    }

    public static boolean lootPanelTake() {
        return PadLoot.take();
    }

    public static boolean shoulderPressed() {
        return ViewpointKeys.pressed(ViewpointKeys.SHOULDER);
    }
}
