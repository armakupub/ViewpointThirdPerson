package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Exposer;
import viewpoint.core.View;
import viewpoint.input.ThirdPerson;
import viewpoint.platform.KeyBind;
import viewpoint.platform.KeyInput;
import viewpoint.platform.Keys;
import zombie.characters.IsoPlayer;

@Exposer.LuaClass
public class ViewpointThirdPerson {
    public static void init() {
        ViewpointKeys.init();
    }

    public static boolean cursorPressed() {
        return ViewpointKeys.pressed(ViewpointKeys.CURSOR);
    }

    public static boolean installControllerPreset() {
        return ControllerPreset.install();
    }

    // The Back wheel's slices, by Viewpoint's key ids.
    public static void padKey(String id) {
        KeyBind bind = "keys.firstPerson".equals(id) ? Keys.FIRST_PERSON
                : "keys.thirdPerson".equals(id) ? Keys.THIRD_PERSON
                : "thirdPersonCamera.keys.swapShoulder".equals(id) ? ViewpointKeys.SHOULDER : null;
        if (bind != null) Patch_KeyBind.press(bind);
    }

    public static boolean viewOn() {
        return View.enabled;
    }

    public static boolean thirdPersonOn() {
        return ThirdPerson.active;
    }

    public static boolean lootPanelPress() {
        return PadLoot.press();
    }

    public static void lootPanelRelease() {
        PadLoot.release();
    }

    public static boolean lootPanelTake() {
        return PadLoot.take();
    }

    // Viewpoint's loot window key, which Viewpoint leaves to the game while its loot panel is not
    // up; it opens the player's own inventory then.
    public static boolean inventoryKey(double key) {
        IsoPlayer p = IsoPlayer.players[0];
        return MouseKeyboard.INVENTORY_KEY.get() && View.enabled && p != null && !p.isDead() && !ControllerLook.usesPad(p)
                && KeyInput.menu((int) key) == Keys.LOOT_WINDOW;
    }

    // True if the cursor was held and is free now.
    public static boolean freeCursor() {
        if (WindowCursor.cursorMode()) return false;
        WindowCursor.setCursorMode(true);
        return true;
    }

    public static void holdCursor() {
        WindowCursor.setCursorMode(false);
    }

    public static boolean shoulderPressed() {
        return ViewpointKeys.pressed(ViewpointKeys.SHOULDER);
    }
}
