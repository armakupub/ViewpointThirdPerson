package pzmod.viewpointthirdperson;

import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.input.ThirdPerson;
import zombie.characters.IsoPlayer;
import zombie.ui.UIManager;

// With the cursor free, aiming holds the mouse so the view turns the body; letting go frees it
// again, since Viewpoint sets wantCapture anew each frame. The game aims only after the right
// button is held 0.15 s, so a right click still opens the context menu.
public class AimCursor {
    // The render thread reads wantCapture between Viewpoint's write and update(), see MenuCursor.
    static volatile boolean held;

    public static void update() {
        held = holds();
        if (held) Look.wantCapture = true;
    }

    // Render thread.
    public static void beforeCapture() {
        if (held) Look.wantCapture = true;
    }

    private static boolean holds() {
        IsoPlayer p = IsoPlayer.players[0];
        return MouseKeyboard.AIM_HOLDS_MOUSE.get() && !Look.wantCapture && View.enabled && ThirdPerson.active
                && !FreeCam.active && p != null && !p.isDead() && p.isAiming() && p.getVehicle() == null
                && !ControllerLook.usesPad(p) && !UIManager.isModalVisible() && !MenuCursor.menuShown()
                && !ViewpointInterface.radial();
    }
}
