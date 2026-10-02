package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.View;
import zombie.characters.IsoPlayer;

// Seated, aiming stands the body up in the seat, and Viewpoint's seated eye follows the head,
// so the camera jerks while the reticle misses where shots go. Off while Viewpoint's view is on.
@Patch(className = "zombie.characters.IsoPlayer", methodName = "isAimControlActive")
public class Patch_IsoPlayer {
    @Patch.OnExit
    public static void exit(@Patch.This Object self, @Patch.Return(readOnly = false) boolean ret) {
        if (ret && View.enabled && self == IsoPlayer.players[0] && ((IsoPlayer) self).getVehicle() != null) ret = false;
    }
}
