package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.platform.LiveSettings;
import zombie.characters.CharacterInputMode;
import zombie.characters.CharacterJoypadButtonBinding;
import zombie.characters.IsoPlayer;
import zombie.characters.component.CharacterInputComponent;
import zombie.input.JoypadAxis1d;
import zombie.input.JoypadAxis2d;
import zombie.input.JoypadManager;

// PZ aims with the right stick; under Viewpoint the right stick looks round instead, and aiming
// is left to the light trigger pull (PrecisionAim), as the right mouse button is with the mouse.
public class ControllerLook {
    static final String SECTION = ThirdPersonRig.TAB + "/Controller";
    static final float YAW_RATE = (float) Math.toRadians(220.0);
    static final float PITCH_RATE = (float) Math.toRadians(150.0);
    static final float MAX_PITCH = (float) Math.toRadians(85.0);
    static final float TWO_PI = (float) (Math.PI * 2.0);

    public static final LiveSettings.Number SPEED = LiveSettings.number("thirdPersonCamera.controllerLookSpeed", "Right stick: look speed", SECTION, 0.2f, 3.0f, 0.05f, 1.0f);
    public static final LiveSettings.Toggle INVERT_Y = LiveSettings.toggle("thirdPersonCamera.controllerInvertY", "Right stick: invert vertical look", SECTION, false);
    public static final LiveSettings.Number FOLLOW_DELAY = LiveSettings.number("thirdPersonCamera.controllerFollowDelay", "Swing in behind after (s)", SECTION, 0.0f, 5.0f, 0.1f, 1.5f);

    static final float FOLLOW_TIME = 1.2f;
    static final float FOLLOW_AHEAD = (float) Math.toRadians(100.0);

    public static volatile boolean active;
    public static volatile boolean panelHasPad;
    static final VehicleCamera.Spring yawSpring = new VehicleCamera.Spring();
    static float rested;
    static boolean recentre;
    public static volatile int bind = -1;
    static long last;

    static {
        SPEED.describe("How fast the right stick turns the view. With a controller the right stick looks round while Viewpoint is on, and a light pull on the right trigger aims, as the right mouse button does. With the Original preset, which aims on the right stick, it keeps aiming.");
        INVERT_Y.describe("Pushing the right stick forward looks down, and back looks up.");
        FOLLOW_DELAY.describe("With a controller, the camera swings round behind your character once the right stick rests this long while you walk or run away from it, not while aiming. 0 never swings.");
    }

    public static void init() {
    }

    // Main thread, once a frame.
    public static void update() {
        long now = System.nanoTime();
        float dt = last == 0L ? 0.0f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        IsoPlayer p = IsoPlayer.players[0];
        int id = p == null ? -1 : p.getJoypadBind();
        boolean pad = id >= 0 && p.getInputMode() == CharacterInputMode.GAMEPAD;
        boolean panel = pad && panelHasPad(p);
        boolean on = pad && !panel && View.enabled && Look.captured && !FreeCam.active
                && p.getVehicle() == null && !p.isDead() && !aimsOnRightStick();
        bind = id;
        panelHasPad = panel;
        active = on;
        if (!on) {
            rested = 0.0f;
            yawSpring.speed = 0.0f;
            recentre = true;
            return;
        }
        JoypadManager j = JoypadManager.instance;
        float x = curve(j.getAimingAxisX(id));
        float y = curve(j.getAimingAxisY(id));
        // As the game does after a menu: the stick that just picked a slice looks again only
        // once it has been let go.
        if (recentre) {
            if (x != 0.0f || y != 0.0f) return;
            recentre = false;
        }
        if (x == 0.0f && y == 0.0f) {
            follow(p, dt);
            return;
        }
        rested = 0.0f;
        yawSpring.speed = 0.0f;
        float speed = SPEED.get();
        float up = INVERT_Y.get() ? y : -y;
        Look.yaw = wrap(Look.yaw + x * YAW_RATE * speed * dt);
        Look.pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, Look.pitch + up * PITCH_RATE * speed * dt));
    }

    // While a panel holds the controller's focus PZ turns the player's buttons off.
    static boolean panelHasPad(IsoPlayer p) {
        CharacterInputComponent c = p.getCharacterInputComponent();
        return c == null || c.isJoypadIgnoreAim() || c.isJoypadIgnoreAimUntilCentered() || !c.isJoypadButtonsActive();
    }

    // As in GTA: once the stick rests, walking or running away from the camera swings it in
    // behind, gentler at a walk; never towards the camera, which would only chase itself round.
    static void follow(IsoPlayer p, float dt) {
        float delay = FOLLOW_DELAY.get();
        rested += dt;
        float heading = p.getDirectionAngleRadians();
        float off = wrap(Look.yaw - heading);
        if (delay <= 0.0f || rested < delay || p.isAiming() || !p.isPlayerMoving() || Math.abs(off) > FOLLOW_AHEAD) {
            yawSpring.speed = 0.0f;
            return;
        }
        float time = p.isRunning() || p.isSprinting() ? FOLLOW_TIME : FOLLOW_TIME * 2.0f;
        Look.yaw = wrap(heading + yawSpring.step(off, time, dt));
    }

    // The Original preset and custom sets that aim on the right stick keep it for aiming.
    static boolean aimsOnRightStick() {
        CharacterJoypadButtonBinding b = CharacterJoypadButtonBinding.PrecisionAim;
        JoypadAxis1d a = b.getJoypadAxis1d();
        return b.getJoypadAxis2d() == JoypadAxis2d.RightStick || a == JoypadAxis1d.RightStickX || a == JoypadAxis1d.RightStickY;
    }

    public static boolean owns(int joypad) {
        return active && joypad == bind;
    }

    static float curve(float v) {
        return v * Math.abs(v);
    }

    static float wrap(float a) {
        a %= TWO_PI;
        return a > Math.PI ? a - TWO_PI : (a < -Math.PI ? a + TWO_PI : a);
    }

    // Viewpoint backpedals facing the view when the keys point away from it, and will not run
    // backwards. With the stick the body turns to wherever it points and runs there, as in GTA;
    // aiming keeps the game's own strafe.
    @Patch(className = "viewpoint.input.Locomotion", methodName = "backwards")
    public static class Patch_backwards {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean ret) {
            if (ret && ControllerLook.active) ret = false;
        }
    }

    @Patch(className = "viewpoint.input.Locomotion", methodName = "strafing")
    public static class Patch_strafing {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean ret) {
            if (ret && ControllerLook.active) ret = false;
        }
    }

    // Viewpoint's loot panel would sit over the game's loot window the controller opened.
    @Patch(className = "viewpoint.interact.LootMenu", methodName = "player")
    public static class Patch_lootPlayer {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) IsoPlayer ret) {
            if (ret != null && ControllerLook.panelHasPad) ret = null;
        }
    }

    @Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isJoypadAimingAxisApplied")
    public static class Patch_aimAxis {
        @Patch.OnExit
        public static void exit(@Patch.This Object self, @Patch.Return(readOnly = false) boolean ret) {
            if (ret && ControllerLook.owns(((CharacterInputComponent) self).getJoypadBind())) ret = false;
        }
    }
}
