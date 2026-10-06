package pzmod.viewpointthirdperson;

import java.util.ArrayList;
import viewpoint.input.Look;
import zombie.characters.IsoPlayer;
import zombie.ui.UIElementInterface;
import zombie.ui.UIManager;

// Context and radial menus set forceCursorVisible, yet Viewpoint holds the mouse through them.
// UIManager.isForceCursorVisible also counts anything under the pinned cursor, so it does not do.
public class MenuCursor {
    // Viewpoint asks for the mouse in FP.renderWorld before update() runs; the render thread
    // grabbing it in between puts the cursor back in the middle, so it is told as well.
    static volatile boolean held;

    public static void update() {
        held = holds();
        if (Look.wantCapture && held) Look.wantCapture = false;
    }

    // Render thread.
    public static void beforeCapture() {
        if (held) Look.wantCapture = false;
    }

    public static boolean holds() {
        return MouseKeyboard.FREE_CURSOR.get() && menuUp() && !ViewpointInterface.radial();
    }

    // Viewpoint shows its loot panel only while it holds the mouse, and builds it before update().
    public static boolean hidesLoot() {
        return MouseKeyboard.HIDE_LOOT.get() && menuUp();
    }

    private static boolean menuUp() {
        return !ControllerLook.usesPad(IsoPlayer.players[0]) && !Qol.loaded() && menuShown();
    }

    static boolean menuShown() {
        ArrayList<UIElementInterface> ui = UIManager.getUI();
        for (int i = ui.size() - 1; i >= 0; i--) {
            UIElementInterface element = ui.get(i);
            if (element.isForceCursorVisible() && Boolean.TRUE.equals(element.isVisible())) return true;
        }
        return false;
    }
}
