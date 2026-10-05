package pzmod.viewpointthirdperson;

import viewpoint.platform.LiveSettings;

// Loaded from init() only: classes the render thread touches in the main menu would register
// these before Viewpoint's own tabs and move ours to the top.
public class MouseKeyboard {
    static final String SECTION = ThirdPersonRig.TAB + "/Mouse and keyboard";

    public static final LiveSettings.Toggle FREE_CURSOR = LiveSettings.toggle("thirdPersonCamera.menusFreeCursor", "Free the cursor in menus", SECTION, true);
    public static final LiveSettings.Toggle HIDE_LOOT = LiveSettings.toggle("thirdPersonCamera.menusHideLootPanel", "Hide the loot menu behind radial menus", SECTION, true);
    public static final LiveSettings.Toggle INVENTORY_KEY = LiveSettings.toggle("thirdPersonCamera.inventoryKey", "Loot window key always works", SECTION, false);

    static {
        FREE_CURSOR.describe("Context menus, radial menus and windows such as the map.");
        INVENTORY_KEY.describe("Without a loot menu, it opens your inventory.");
    }

    public static void init() {
    }
}
