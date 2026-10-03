package pzmod.viewpointthirdperson;

import viewpoint.platform.LiveSettings;

public class InventoryKey {
    static final String SECTION = ThirdPersonRig.TAB + "/Mouse and keyboard";

    public static final LiveSettings.Toggle ON = LiveSettings.toggle("thirdPersonCamera.inventoryKey", "Loot window key always opens the inventory", SECTION, false);

    static {
        ON.describe("Viewpoint opens the loot window on this key (Tab) only while its loot menu is up. On: the key always works. With no loot menu up, it opens your inventory and frees the cursor; press it again to close it and look around.");
    }

    public static void init() {
    }
}
