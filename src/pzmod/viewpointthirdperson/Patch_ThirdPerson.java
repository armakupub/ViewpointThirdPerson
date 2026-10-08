package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.CameraSquares;
import viewpoint.core.Frame;
import zombie.characters.IsoGameCharacter;

public class Patch_ThirdPerson {

    // Only sizes the wall grid around the camera once the rig places it on foot.
    @Patch(className = "viewpoint.input.ThirdPerson", methodName = "boom")
    public static class Patch_boom {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) CameraSquares cs, @Patch.Return(readOnly = false) float ret) {
            ret = ThirdPersonRig.gridBoom(cs.seated, ret);
        }
    }

    // Sets which level the wall grid starts at.
    @Patch(className = "viewpoint.input.ThirdPerson", methodName = "lift")
    public static class Patch_lift {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) CameraSquares cs, @Patch.Return(readOnly = false) float ret) {
            ret = ThirdPersonRig.gridLift(ret);
        }
    }

    @Patch(className = "viewpoint.input.ThirdPerson", methodName = "capture")
    public static class Patch_capture {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Frame frame, @Patch.Argument(2) IsoGameCharacter chr) {
            ThirdPersonRig.beforeCapture(frame, chr);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Frame frame) {
            ThirdPersonRig.afterCapture(frame);
        }
    }

    // ZB 2.3.2 matches an array @Argument by its component type, so a float[] parameter never
    // matches its target and the advice is silently not woven; Object skips that check.
    @Patch(className = "viewpoint.input.ThirdPerson", methodName = "view")
    public static class Patch_view {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Frame frame, @Patch.Argument(1) float yaw, @Patch.Argument(2) float pitch,
                                @Patch.Argument(3) Object out, @Patch.Return boolean ret) {
            if (ret) ThirdPersonRig.afterView(frame, yaw, pitch, (float[]) out);
        }
    }

    @Patch(className = "viewpoint.core.View", methodName = "fovY")
    public static class Patch_fovY {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) boolean thirdPerson, @Patch.Return(readOnly = false) float ret) {
            if (thirdPerson) ret = ThirdPersonRig.fov(ret);
        }
    }
}
