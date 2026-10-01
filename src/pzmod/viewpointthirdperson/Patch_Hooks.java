package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

// After Viewpoint's own handler, so the loot menu and settings window get the wheel first.
@Patch(className = "viewpoint.Hooks", methodName = "mouseUpdated")
public class Patch_Hooks {
    @Patch.OnExit
    public static void exit() {
        ThirdPersonZoom.mouseUpdated();
    }
}
