package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.View;
import viewpoint.input.FreeCam;
import viewpoint.input.Look;
import viewpoint.input.ThirdPerson;
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

    public static final LiveSettings.Number SPEED = LiveSettings.number("thirdPersonCamera.controllerLookSpeed", "Look speed", SECTION, 0.2f, 3.0f, 0.05f, 1.0f);
    public static final LiveSettings.Number AIM_SPEED = LiveSettings.number("thirdPersonCamera.controllerAimSpeed", "Look speed while aiming", SECTION, 0.1f, 1.0f, 0.05f, 0.45f);
    public static final LiveSettings.Toggle INVERT_Y = LiveSettings.toggle("thirdPersonCamera.controllerInvertY", "Invert look", SECTION, false);
    public static final LiveSettings.Number FOLLOW_DELAY = LiveSettings.number("thirdPersonCamera.controllerFollowDelay", "Swing delay (s)", SECTION, 0.0f, 5.0f, 0.1f, 1.5f);

    static final float FOLLOW_TIME = 1.2f;
    static final float FOLLOW_AHEAD = (float) Math.toRadians(100.0);

    public static volatile boolean active;
    public static volatile boolean panelHasPad;
    static final VehicleCamera.Spring yawSpring = new VehicleCamera.Spring();
    static float rested;
    static boolean recentre;
    public static volatile int bind = -1;
    static long last;
    // How fast the view turns, worked out here and turned by the render thread just before it
    // draws, on its own clock as the mouse is read: turned here, a frame drawn sooner or later than
    // the last would show the view turned by the wrong share.
    static volatile float yawRate, pitchRate;
    static long drawnAt;
    static volatile boolean mouseLooked;

    static {
        FOLLOW_DELAY.describe("After the right stick rests. 0 = never.");
    }

    public static void init() {
    }

    static boolean usesPad(IsoPlayer p) {
        return p != null && p.getJoypadBind() >= 0 && p.getInputMode() == CharacterInputMode.GAMEPAD;
    }

    // Main thread, once a frame.
    public static void update() {
        long now = System.nanoTime();
        float dt = last == 0L ? 0.0f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        IsoPlayer p = IsoPlayer.players[0];
        int id = p == null ? -1 : p.getJoypadBind();
        boolean pad = usesPad(p);
        boolean panel = pad && panelHasPad(p);
        boolean on = pad && !panel && View.enabled && Look.captured && !FreeCam.active
                && !p.isDead() && !aimsOnRightStick();
        boolean seated = p != null && p.getVehicle() != null;
        bind = id;
        panelHasPad = panel;
        active = on;
        yawRate = 0.0f;
        pitchRate = 0.0f;
        boolean looked = mouseLooked;
        mouseLooked = false;
        if (!on) {
            recentre = true;
            if (!pad && mouseFollows(p) && !looked) {
                follow(p, dt, MouseKeyboard.FOLLOW_DELAY.get());
            } else {
                rested = 0.0f;
                yawSpring.speed = 0.0f;
            }
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
            if (seated) {
                rested = 0.0f;
                yawSpring.speed = 0.0f;
            } else {
                follow(p, dt, FOLLOW_DELAY.get());
            }
            return;
        }
        rested = 0.0f;
        yawSpring.speed = 0.0f;
        // Holds the vehicle camera's swing back, as the mouse does.
        if (seated) VehicleCamera.mouseMoved = true;
        float speed = SPEED.get() * (p.isAiming() ? AIM_SPEED.get() : 1.0f);
        float up = INVERT_Y.get() ? y : -y;
        yawRate = x * YAW_RATE * speed;
        pitchRate = up * PITCH_RATE * speed;
    }

    // Render thread, before the frame's view is built.
    static void draw() {
        long now = System.nanoTime();
        float dt = drawnAt == 0L ? 0.0f : Math.min(0.05f, (now - drawnAt) / 1.0e9f);
        drawnAt = now;
        float yaw = yawRate * dt;
        float pitch = pitchRate * dt;
        if (yaw == 0.0f && pitch == 0.0f) return;
        VehicleCamera.turnView(yaw, pitch);
        LookAround.turned(yaw);
    }

    // While a panel holds the controller's focus PZ turns the player's buttons off.
    static boolean panelHasPad(IsoPlayer p) {
        CharacterInputComponent c = p.getCharacterInputComponent();
        return c == null || c.isJoypadIgnoreAim() || c.isJoypadIgnoreAimUntilCentered() || !c.isJoypadButtonsActive();
    }

    static boolean mouseFollows(IsoPlayer p) {
        return p != null && !p.isDead() && p.getVehicle() == null && View.enabled && ThirdPerson.active
                && Look.captured && !FreeCam.active && !LookAround.held && MouseKeyboard.FOLLOW_DELAY.get() > 0.0f;
    }

    // As in GTA: once the stick or mouse rests, walking or running away from the camera swings it
    // in behind, gentler at a walk; never towards the camera, which would only chase itself round.
    static void follow(IsoPlayer p, float dt, float delay) {
        rested += dt;
        float heading = p.getDirectionAngleRadians();
        float off = wrap(Look.yaw - heading);
        if (delay <= 0.0f || rested < delay || p.isAiming() || !p.isPlayerMoving() || Math.abs(off) > FOLLOW_AHEAD) {
            yawSpring.speed = 0.0f;
            return;
        }
        float time = p.isRunning() || p.isSprinting() ? FOLLOW_TIME : FOLLOW_TIME * 2.0f;
        if (dt > 0.0f) yawRate = wrap(yawSpring.step(off, time, dt) - off) / dt;
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
            if (ret && ControllerLook.turnsToWalk()) ret = false;
        }
    }

    @Patch(className = "viewpoint.input.Locomotion", methodName = "strafing")
    public static class Patch_strafing {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean ret) {
            if (ret && ControllerLook.turnsToWalk()) ret = false;
        }
    }

    public static boolean turnsToWalk() {
        return (ControllerLook.active && ThirdPerson.active) || MouseKeyboard.turnsAround();
    }

    // Viewpoint turns a body standing still to the view; where the body turns to walk, it keeps
    // facing where it stopped. Aiming still turns it.
    public static boolean keepsFacing(IsoPlayer p) {
        if (!turnsToWalk() || p != IsoPlayer.players[0] || p.isAiming() || LookAround.MOVED == null) return false;
        try {
            return !LookAround.MOVED.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // The interact key works on the square the body faces: standing, it turns to the view first.
    // Walking, the body already faces where it goes, and turning would break the walk.
    public static void faceView(IsoPlayer p) {
        if (!turnsToWalk() || p != IsoPlayer.players[0] || p.getVehicle() != null || p.isSitting()
                || p.isBlockMovement() || !View.enabled || !Look.captured || FreeCam.active
                || LookAround.MOVED == null) return;
        try {
            if (LookAround.MOVED.getBoolean(null)) return;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        float yaw = Look.yaw;
        p.setTargetAndCurrentDirection((float) Math.cos(yaw), (float) Math.sin(yaw));
    }

    @Patch(className = "viewpoint.input.Controls", methodName = "turn")
    public static class Patch_turn {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) IsoPlayer p) {
            return ControllerLook.keepsFacing(p);
        }
    }

    @Patch(className = "zombie.characters.IsoPlayer", methodName = "doContext")
    public static class Patch_doContext {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self) {
            ControllerLook.faceView((IsoPlayer) self);
        }
    }

    // Viewpoint's loot panel would sit over the game's loot window the controller opened, and
    // over a radial or context menu.
    @Patch(className = "viewpoint.interact.LootMenu", methodName = "player")
    public static class Patch_lootPlayer {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) IsoPlayer ret) {
            if (ret != null && (ControllerLook.panelHasPad || MenuCursor.hidesLoot())) ret = null;
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
