package pzmod.viewpointthirdperson;

import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.input.ThirdPerson;
import zombie.characters.IsoPlayer;
import zombie.input.Mouse;

public class ThirdPersonZoom {
    public static void mouseUpdated() {
        IsoPlayer player = IsoPlayer.players[0];
        boolean seated = player != null && player.getVehicle() != null;
        boolean ours = View.enabled && ThirdPerson.active && !FreeCam.active && ThirdPersonRig.ok;
        if (ours && Qol.leftFirstPerson()) {
            if (seated) VehicleCamera.zoomAllIn();
            else ThirdPersonRig.zoomAllIn();
        }
        int wheel = Mouse.wheelDelta;
        if (wheel == 0 || !ours || !Look.captured
                || Qol.hotbarWheel() || Qol.fovWheel() || ViewpointInterface.menu()) return;
        Mouse.wheelDelta = 0;
        if (wheel > 0 && (seated ? VehicleCamera.zoomedAllIn() : ThirdPersonRig.zoomedAllIn()) && Qol.toFirstPerson()) return;
        if (seated) VehicleCamera.wheel(wheel);
        else ThirdPersonRig.wheel(wheel);
    }
}
