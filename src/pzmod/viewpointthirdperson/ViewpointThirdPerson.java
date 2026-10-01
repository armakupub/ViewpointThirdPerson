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

    public static boolean shoulderPressed() {
        return ViewpointKeys.pressed(ViewpointKeys.SHOULDER);
    }
}
