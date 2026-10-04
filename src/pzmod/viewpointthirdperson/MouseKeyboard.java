package pzmod.viewpointthirdperson;

import viewpoint.platform.LiveSettings;

// Loaded from init() only: classes the render thread touches in the main menu would register
// these before Viewpoint's own tabs and move ours to the top.
public class MouseKeyboard {
    static final String SECTION = ThirdPersonRig.TAB + "/Mouse and keyboard";

    public static final LiveSettings.Toggle FREE_CURSOR = LiveSettings.toggle("thirdPersonCamera.menusFreeCursor", "Menus and windows free the cursor", SECTION, true);
    public static final LiveSettings.Toggle HIDE_LOOT = LiveSettings.toggle("thirdPersonCamera.menusHideLootPanel", "Radial menus hide Viewpoint's loot menu", SECTION, true);
    public static final LiveSettings.Toggle INVENTORY_KEY = LiveSettings.toggle("thirdPersonCamera.inventoryKey", "Loot window key always opens the inventory", SECTION, false);

    static {
        FREE_CURSOR.describe("Context and radial menus, and windows opened while you look around (map, health and others), free the cursor; closing them takes the view back. Off: Viewpoint's own handling. Steps back while Project Viewpoint QOL is loaded, which does this itself.");
        HIDE_LOOT.describe("While a radial menu is open, Viewpoint's loot menu is not shown, so the two do not overlap.");
        INVENTORY_KEY.describe("Viewpoint opens the loot window on this key (Tab) only while its loot menu is up. On: the key always works. With no loot menu up, it opens your inventory and frees the cursor; press it again to close it and look around.");
    }

    public static void init() {
    }
}
