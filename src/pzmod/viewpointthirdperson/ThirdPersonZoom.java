package pzmod.viewpointthirdperson;

import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.input.ThirdPerson;
import zombie.characters.IsoPlayer;
import zombie.input.Mouse;

public class ThirdPersonZoom {
    public static final float STEP = 1.15f;

    public static void mouseUpdated() {
        int wheel = Mouse.wheelDelta;
        if (wheel == 0 || !View.enabled || !ThirdPerson.active || !Look.captured || FreeCam.active || !ThirdPersonRig.ok) return;
        Mouse.wheelDelta = 0;
        IsoPlayer player = IsoPlayer.players[0];
        if (player != null && player.getVehicle() != null) VehicleCamera.wheel(wheel);
        else ThirdPersonRig.wheel(wheel);
    }
}
