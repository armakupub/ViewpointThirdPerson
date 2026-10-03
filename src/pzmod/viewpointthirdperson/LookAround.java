package pzmod.viewpointthirdperson;

import java.lang.reflect.Field;
import viewpoint.core.View;
import viewpoint.input.Look;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.input.GameKeyboard;
import zombie.iso.Vector2;

// Viewpoint walks and turns the body by Look.yaw; while looking around, what it works out from
// Look.yaw is turned back by the orbit. Look.yaw itself is left alone: the render thread reads it
// at any time. Left and right steer instead of strafing, which would go the wrong way on screen
// while looking back.
public class LookAround {
    static final float RETURN_TIME = 0.3f;
    static final float DONE = 1.0e-3f;
    static final float STEER_RATE = (float) Math.toRadians(135.0);

    static final VehicleCamera.Spring spring = new VehicleCamera.Spring();
    static final Field MOVED;
    static final Field MOVE_X;
    static final Field MOVE_Y;
    static volatile float orbit;
    static volatile boolean held;
    static float readYaw;
    static float steer;

    static {
        Field moved = null, moveX = null, moveY = null;
        try {
            Class<?> controls = Class.forName("viewpoint.input.Controls");
            moved = controls.getDeclaredField("moved");
            moveX = controls.getDeclaredField("moveX");
            moveY = controls.getDeclaredField("moveY");
            moved.setAccessible(true);
            moveX.setAccessible(true);
            moveY.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            moved = null;
            System.out.println("[ViewpointThirdPerson] looking around turns the walk, Viewpoint changed: " + e);
        }
        MOVED = moved;
        MOVE_X = moveX;
        MOVE_Y = moveY;
    }

    // Main thread.
    public static synchronized void update(IsoGameCharacter chr, float dt) {
        boolean aiming = chr.isAiming();
        held = Look.captured && !aiming && MOVED != null && ViewpointKeys.LOOK_AROUND != null
                && ViewpointKeys.LOOK_AROUND.down(GameKeyboard::isKeyDown);
        float s = steer;
        steer = 0.0f;
        if (held) {
            spring.speed = 0.0f;
            turnView(s * STEER_RATE * dt);
            return;
        }
        if (orbit == 0.0f) return;
        if (aiming) {
            reset();
            return;
        }
        float before = orbit;
        float after = spring.step(VehicleCamera.wrap(before), RETURN_TIME, dt);
        if (Math.abs(after) < DONE && Math.abs(spring.speed) < DONE) after = 0.0f;
        turnView(after - before);
        orbit = after;
    }

    public static synchronized void reset() {
        held = false;
        if (orbit != 0.0f) turnView(-orbit);
        orbit = 0.0f;
        spring.speed = 0.0f;
    }

    // A turn of our own between the render thread's beforeRead and afterRead is not the mouse's.
    static void turnView(float delta) {
        if (delta == 0.0f) return;
        Look.yaw = VehicleCamera.wrap(Look.yaw + delta);
        readYaw = VehicleCamera.wrap(readYaw + delta);
    }

    public static synchronized void beforeRead() {
        readYaw = Look.yaw;
    }

    public static synchronized void afterRead() {
        if (held) orbit += VehicleCamera.wrap(Look.yaw - readYaw);
    }

    // The game's move input before Viewpoint reads it: x is right, -y forward.
    public static synchronized void steer(Vector2 input) {
        if (!held || input.x == 0.0f) return;
        steer = Math.signum(input.x);
        float length = input.getLength();
        input.set(0.0f, input.y == 0.0f ? 0.0f : Math.signum(input.y) * length);
    }

    static boolean applies(IsoGameCharacter chr) {
        return View.enabled && chr == IsoPlayer.players[0] && chr.getVehicle() == null && !chr.isDead();
    }

    // Viewpoint's move vector is its walk direction turned by 45 degrees and scaled, so turning
    // the vector turns the walk the same way.
    public static void afterMoveVector(IsoGameCharacter chr, Vector2 vector) {
        float o = orbit;
        if (o == 0.0f || MOVED == null || !applies(chr)) return;
        try {
            if (!MOVED.getBoolean(null)) return;
            float c = (float) Math.cos(o);
            float s = (float) Math.sin(o);
            float x = vector.x, y = vector.y;
            vector.set(x * c + y * s, y * c - x * s);
            float mx = MOVE_X.getFloat(null), my = MOVE_Y.getFloat(null);
            MOVE_X.setFloat(null, mx * c + my * s);
            MOVE_Y.setFloat(null, my * c - mx * s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] looking around turns the walk, Viewpoint changed: " + e);
        }
    }

    // CrosshairAim.yaw: where Viewpoint turns the body and the game's look angle.
    public static float bodyYaw(IsoGameCharacter chr, float yaw) {
        float o = orbit;
        if (o == 0.0f || !applies(chr)) return yaw;
        return VehicleCamera.wrap(yaw - o);
    }
}
