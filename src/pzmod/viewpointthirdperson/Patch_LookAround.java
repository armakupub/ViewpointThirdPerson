package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.iso.Vector2;

public class Patch_LookAround {

    @Patch(className = "viewpoint.input.Controls", methodName = "moveVector")
    public static class Patch_moveVector {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(1) Vector2 input) {
            LookAround.steer(input);
        }

        @Patch.OnExit
        public static void exit(@Patch.Argument(0) IsoPlayer player, @Patch.Argument(1) Vector2 vector) {
            LookAround.afterMoveVector(player, vector);
        }
    }

    @Patch(className = "viewpoint.input.CrosshairAim", methodName = "yaw")
    public static class Patch_yaw {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) IsoGameCharacter chr, @Patch.Return(readOnly = false) float ret) {
            ret = LookAround.bodyYaw(chr, ret);
        }
    }
}
