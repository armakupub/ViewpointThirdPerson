package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

// Render thread, where Viewpoint takes or lets go of the mouse.
@Patch(className = "viewpoint.input.Look", methodName = "onUpdateMouseCursor")
public class Patch_LookCursor {
    @Patch.OnEnter
    public static void enter() {
        AimCursor.beforeCapture();
        MenuCursor.beforeCapture();
    }
}
