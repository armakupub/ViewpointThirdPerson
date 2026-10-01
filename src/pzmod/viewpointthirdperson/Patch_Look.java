package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

// Runs on both the main thread and, with fresh mouse reads, the render thread.
@Patch(className = "viewpoint.input.Look", methodName = "read")
public class Patch_Look {
    @Patch.OnEnter
    public static void enter() {
        VehicleCamera.beforeRead();
    }

    @Patch.OnExit
    public static void exit() {
        VehicleCamera.afterRead();
    }
}
