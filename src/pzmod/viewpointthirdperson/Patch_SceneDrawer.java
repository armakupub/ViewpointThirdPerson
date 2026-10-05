package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

// Render thread, just before Viewpoint reads Look.yaw to draw a frame.
@Patch(className = "viewpoint.SceneDrawer", methodName = "drawFrame")
public class Patch_SceneDrawer {
    @Patch.OnEnter
    public static void enter(@Patch.This Object self) {
        ThirdPersonRig.beforeDraw(self);
    }
}
