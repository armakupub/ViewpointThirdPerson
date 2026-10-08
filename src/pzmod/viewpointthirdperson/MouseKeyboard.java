package pzmod.viewpointthirdperson;

import java.lang.reflect.Field;
import viewpoint.input.ThirdPerson;
import viewpoint.platform.LiveSettings;

// Loaded from init() only: classes the render thread touches in the main menu would register
// these before Viewpoint's own tabs and move ours to the top.
public class MouseKeyboard {
    static final String SECTION = ThirdPersonRig.TAB + "/Mouse and keyboard";
    static final int STRAFE_MODEL = 1;

    public static final LiveSettings.Toggle FREE_CURSOR = LiveSettings.toggle("thirdPersonCamera.menusFreeCursor", "Free the cursor in menus", SECTION, true);
    public static final LiveSettings.Toggle HIDE_LOOT = LiveSettings.toggle("thirdPersonCamera.menusHideLootPanel", "Hide the loot menu behind radial menus", SECTION, true);
    public static final LiveSettings.Toggle INVENTORY_KEY = LiveSettings.toggle("thirdPersonCamera.inventoryKey", "Loot window key always works", SECTION, false);
    public static final LiveSettings.Toggle BACKPEDAL = LiveSettings.toggle("thirdPersonCamera.backpedal", "Backpedal when walking backwards", SECTION, false);
    public static final LiveSettings.Toggle AIM_HOLDS_MOUSE = LiveSettings.toggle("thirdPersonCamera.aimHoldsMouse", "Aiming holds the mouse", SECTION, true);
    public static final LiveSettings.Number FOLLOW_DELAY = LiveSettings.number("thirdPersonCamera.mouseFollowDelay", "Swing delay (s)", SECTION, 0.0f, 5.0f, 0.1f, 2.0f);
    public static final LiveSettings.Number SMOOTHING = LiveSettings.number("thirdPersonCamera.mouseSmoothing", "Mouse smoothing (s)", SECTION, 0.0f, 0.5f, 0.01f, 0.0f);
    public static final LiveSettings.Number AIM_SPEED = LiveSettings.number("thirdPersonCamera.mouseAimSpeed", "Mouse speed while aiming", SECTION, 0.1f, 1.0f, 0.05f, 1.0f);

    static final LiveSettings.Choice MODEL;

    static {
        FREE_CURSOR.describe("Context menus, radial menus and windows such as the map.");
        INVENTORY_KEY.describe("Without a loot menu, it opens your inventory.");
        AIM_HOLDS_MOUSE.describe("With the cursor free, holding the right mouse button turns the view with the mouse until you let go.");
        FOLLOW_DELAY.describe("After the mouse rests. 0 = never.");
        SMOOTHING.describe("0 = instant.");
        AIM_SPEED.describe("1 = unchanged.");
        BACKPEDAL.describe("Your character keeps facing the view and turns back to it when you stop.");
        LiveSettings.Choice model = null;
        try {
            Field f = Class.forName("viewpoint.input.Controls").getDeclaredField("MODEL");
            f.setAccessible(true);
            model = (LiveSettings.Choice) f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] walking model unknown, Viewpoint changed: " + e);
        }
        MODEL = model;
    }

    // Viewpoint's Strafe walking keeps the body to the view by choice.
    public static boolean turnsAround() {
        return !BACKPEDAL.get() && ThirdPerson.active && !LookAround.held && (MODEL == null || MODEL.get() != STRAFE_MODEL);
    }

    public static void init() {
    }
}
