package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

// Viewpoint decides here, every frame, whether it holds the mouse.
@Patch(className = "viewpoint.FP", methodName = "renderWorld")
public class Patch_FP {
    @Patch.OnExit
    public static void exit() {
        WindowCursor.update();
        MenuCursor.update();
    }
}
