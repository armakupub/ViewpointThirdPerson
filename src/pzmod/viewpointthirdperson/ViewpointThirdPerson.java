package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Exposer;

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
