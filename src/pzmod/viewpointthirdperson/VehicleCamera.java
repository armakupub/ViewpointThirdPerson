package pzmod.viewpointthirdperson;

import org.joml.Vector3f;
import viewpoint.core.Frame;
import viewpoint.input.Look;
import viewpoint.platform.LiveSettings;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;

public class VehicleCamera {
    static final String SECTION = ThirdPersonRig.TAB + "/Vehicle";
    static final float NEAREST = 1.25f;
    static final float FARTHEST = 10.0f;
    static final float MOVING_KMH = 5.0f;
    static final float FULL_KMH = 40.0f;
    static final float SLIDE_FROM_KMH = 10.0f;
    static final float SLIDE_FULL_KMH = 30.0f;
    static final float MOUSE_EPSILON = 1.0e-5f;
    static final float TWO_PI = (float) (Math.PI * 2.0);
    static final float MAX_PITCH = (float) Math.toRadians(85.0);
    static final float TOP_KMH = 100.0f;
    static final float FULL_SHARE = 0.8f;
    static final float SPEED_BACK = 0.25f;
    static final float SPEED_FOV = 10.0f;
    static final float SETTLE_TIME = 0.25f;
    static final float LIFT = 0.9f;
    // A plain sedan (CarNormal): the size Distance is meant for.
    static final float REF_LENGTH = 4.74f;
    static final float REF_HEIGHT = 1.18f;
    static final float HEIGHT_LIFT = 0.5f;
    // A raised camera tilts down to look at the road as far ahead as it does over a sedan.
    static final float LOOK_AHEAD = 12.0f;
    static final float SIDE_SHARE = 0.5f;
    static final float OFFSET_MAX = 2.5f;
    static final float WEIGHT_TIME = 0.8f;
    static final float FEEL_DEFAULT = 0.5f;
    static final float CLOSE_SWING = 0.5f;

    public static final LiveSettings.Number DISTANCE = LiveSettings.number("thirdPersonCamera.vehicleDistance", "Distance", SECTION, NEAREST, FARTHEST, 0.05f, 5.0f);
    public static final LiveSettings.Number MOUSE = LiveSettings.number("thirdPersonCamera.vehicleMouse", "Mouse smoothing (s)", SECTION, 0.0f, 0.5f, 0.01f, 0.12f);
    public static final LiveSettings.Toggle FOLLOW = LiveSettings.toggle("thirdPersonCamera.vehicleFollow", "Swing in behind", SECTION, true);
    public static final LiveSettings.Number DELAY = LiveSettings.number("thirdPersonCamera.vehicleDelay", "Swing after the mouse rests (s)", SECTION, 0.0f, 5.0f, 0.1f, 1.0f);
    public static final LiveSettings.Number SWING = LiveSettings.number("thirdPersonCamera.vehicleSwing", "Swing time (s)", SECTION, 0.1f, 3.0f, 0.05f, 0.5f);
    public static final LiveSettings.Number SLIDE = LiveSettings.number("thirdPersonCamera.vehicleSlide", "Follow the slide", SECTION, 0.0f, 1.0f, 0.05f, 1.0f);
    public static final LiveSettings.Number FEEL = LiveSettings.number("thirdPersonCamera.vehicleFeel", "Speed feel", SECTION, 0.0f, 1.0f, 0.05f, 0.5f);
    public static final LiveSettings.Number PITCH = LiveSettings.number("thirdPersonCamera.vehiclePitch", "Looking down (degrees)", SECTION, -20.0f, 45.0f, 1.0f, 15.0f);

    // A critically damped spring that keeps its own speed: starts and stops gently.
    // Takes how far off the target it is and returns how far off it is after dt.
    static final class Spring {
        float speed;

        float step(float change, float time, float dt) {
            if (time <= 0.0f) {
                speed = 0.0f;
                return 0.0f;
            }
            float omega = 2.0f / time;
            float x = omega * dt;
            float decay = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);
            float temp = (speed + omega * change) * dt;
            speed = (speed - omega * temp) * decay;
            return (change + temp) * decay;
        }
    }

    static final Vector3f forward = new Vector3f();
    static final Vector3f velocity = new Vector3f();
    static final Spring yawSpring = new Spring();
    static final Spring pitchSpring = new Spring();
    static final Spring offXSpring = new Spring();
    static final Spring offYSpring = new Spring();
    static float shown = -1.0f;
    static float rested;
    static boolean tracking;
    static float feltBoom;
    static float sizeBoom;
    static float sizeLift;
    static float rear;
    static BaseVehicle last;
    static float vx, vy;
    static float fx = 1.0f, fy;
    static float nose;
    static float offX, offY;
    public static volatile float fovAdd;

    static volatile boolean active;
    static volatile boolean mouseMoved;
    static float readYaw, readPitch;
    static float pendingYaw, pendingPitch;
    static long readAt;

    static {
        DISTANCE.describe("How far the camera sits behind a car of ordinary size. Longer vehicles move it back and taller ones raise it; a trailer does not push it back. The mouse wheel changes it while seated.");
        MOUSE.describe("While seated the view follows the mouse a moment later, so it cannot be yanked about. 0 turns at once.");
        FOLLOW.describe("While driving, the camera swings round behind the vehicle once the mouse rests and the vehicle is moving. Reversing keeps it looking ahead.");
        DELAY.describe("How long the mouse must rest before the camera swings in behind.");
        SWING.describe("How long the swing takes at speed; slower driving swings more gently.");
        SLIDE.describe("At speed the camera swings in behind where the vehicle is going rather than where its nose points, so a slide shows. 0 follows the nose only.");
        FEEL.describe("How strongly speed shows. The faster you go, the further the camera drops back and the wider the view, fully at 80% of top speed. The camera also has weight: it lags when you speed up, closes in when you brake and drifts wide in turns. 0 fixes it to the vehicle.");
        PITCH.describe("How far the camera looks down at the road once it has swung in behind.");
    }

    public static void init() {
    }

    public static void reset() {
        active = false;
        tracking = false;
        rested = 0.0f;
        feltBoom = 0.0f;
        fovAdd = 0.0f;
        last = null;
        offX = 0.0f;
        offY = 0.0f;
        yawSpring.speed = 0.0f;
        pitchSpring.speed = 0.0f;
    }

    // Main thread, once a frame while seated in third person.
    public static void update(BaseVehicle vehicle, float dt) {
        active = true;
        vehicle.getForwardVector(forward);
        float h = (float) Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (h > 1.0e-4f) {
            fx = forward.x / h;
            fy = forward.z / h;
        }
        float lastNose = nose;
        VehicleLead.lead(vehicle, 0);
        nose = (float) Math.atan2(fy, fx) - VehicleLead.turn;
        fx = (float) Math.cos(nose);
        fy = (float) Math.sin(nose);
        float turned = wrap(nose - lastNose);
        vehicle.getLinearVelocity(velocity);
        float dvx = velocity.x - vx;
        float dvy = velocity.z - vy;
        vx = velocity.x;
        vy = velocity.z;
        if (vehicle != last) {
            last = vehicle;
            dvx = 0.0f;
            dvy = 0.0f;
            turned = 0.0f;
            offX = 0.0f;
            offY = 0.0f;
            offXSpring.speed = 0.0f;
            offYSpring.speed = 0.0f;
        }
        measure(vehicle);
        float target = DISTANCE.get();
        shown = shown < 0.0f ? target : shown + (target - shown) * ThirdPersonRig.ease(dt, ThirdPersonRig.ZOOM_TIME);
        feel(vehicle, dt);
        // Close behind the vehicle the same sway is a wide swing of the view.
        float near = ThirdPersonRig.held >= 0.0f ? Math.min(boom(), ThirdPersonRig.held) : boom();
        float firm = ThirdPersonRig.firm(near - rear);
        weight(dvx, dvy, firm, dt);

        boolean moved = mouseMoved;
        mouseMoved = false;
        if (!Look.captured || !FOLLOW.get()) {
            tracking = false;
            rested = 0.0f;
            return;
        }
        rested = moved || !tracking ? 0.0f : rested + dt;
        tracking = true;

        float speed = Math.abs(vehicle.getCurrentSpeedKmHour());
        if (speed >= MOVING_KMH && rested >= DELAY.get()) {
            float pace = Math.min(1.0f, Math.max(0.25f, speed / FULL_KMH));
            float time = SWING.get() / pace * ThirdPersonRig.lerp(CLOSE_SWING, 1.0f, firm);
            // Slower driving swings more gently, but a turn is carried along by the share the
            // swing is slower, so the view trails a turn by no more than at speed.
            Look.yaw = wrap(Look.yaw + turned * (1.0f - pace));
            float heading = heading(speed);
            Look.yaw = wrap(heading + yawSpring.step(wrap(Look.yaw - heading), time, dt));
            float down = (float) Math.toRadians(-PITCH.get()) - (float) Math.atan2(Math.max(0.0f, sizeLift), LOOK_AHEAD);
            Look.pitch = down + pitchSpring.step(Look.pitch - down, time, dt);
        } else {
            yawSpring.speed = 0.0f;
            pitchSpring.speed = 0.0f;
        }
    }

    // Where the vehicle is going, blended in from its nose as it gathers speed; never while reversing.
    static float heading(float speed) {
        float hx = fx;
        float hy = fy;
        float v = (float) Math.sqrt(vx * vx + vy * vy);
        float w = SLIDE.get() * ThirdPersonRig.smooth(clamp01((speed - SLIDE_FROM_KMH) / (SLIDE_FULL_KMH - SLIDE_FROM_KMH)));
        if (w > 0.0f && v > 1.0e-3f && vx * hx + vy * hy > 0.0f) {
            hx += (vx / v - hx) * w;
            hy += (vy / v - hy) * w;
        }
        return (float) Math.atan2(hy, hx);
    }

    // The camera keeps its own speed when the vehicle's changes (half of it sideways, scaled by
    // the speed feel), and a spring pulls it back over the vehicle. Moved by the vehicle's speed rather than its
    // position, so it stays clear of the 100 Hz steps the physics moves the vehicle in.
    static void weight(float dvx, float dvy, float firm, float dt) {
        float share = FEEL.get() / FEEL_DEFAULT * firm;
        float along = (dvx * fx + dvy * fy) * share;
        float side = (dvy * fx - dvx * fy) * SIDE_SHARE * share;
        offXSpring.speed -= along * fx - side * fy;
        offYSpring.speed -= along * fy + side * fx;
        offX = offXSpring.step(offX, WEIGHT_TIME, dt);
        offY = offYSpring.step(offY, WEIGHT_TIME, dt);
        float len = (float) Math.sqrt(offX * offX + offY * offY);
        if (len > OFFSET_MAX) {
            offX *= OFFSET_MAX / len;
            offY *= OFFSET_MAX / len;
            offXSpring.speed *= 0.5f;
            offYSpring.speed *= 0.5f;
        }
    }

    // The mouse moves a target; the view follows it. Both threads read the mouse, so this is
    // serialised, and works on the change each read makes rather than on Look.yaw itself,
    // which the swing also writes.
    public static synchronized void beforeRead() {
        readYaw = Look.yaw;
        readPitch = Look.pitch;
    }

    public static synchronized void afterRead() {
        float dy = wrap(Look.yaw - readYaw);
        float dp = Look.pitch - readPitch;
        if (Math.abs(dy) > MOUSE_EPSILON || Math.abs(dp) > MOUSE_EPSILON) mouseMoved = true;
        long now = System.nanoTime();
        float dt = readAt == 0L ? 0.0f : Math.min(0.1f, (now - readAt) / 1.0e9f);
        readAt = now;
        float time = MOUSE.get();
        if (!active || time <= 0.0f) {
            pendingYaw = 0.0f;
            pendingPitch = 0.0f;
            return;
        }
        pendingYaw += dy;
        pendingPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, readPitch + pendingPitch + dp)) - readPitch;
        float k = ThirdPersonRig.ease(dt, time);
        float sy = pendingYaw * k;
        float sp = pendingPitch * k;
        pendingYaw -= sy;
        pendingPitch -= sp;
        Look.yaw = wrap(readYaw + sy);
        Look.pitch = readPitch + sp;
    }

    static void measure(BaseVehicle vehicle) {
        float length = 0.0f;
        float height = 0.0f;
        VehicleScript script = vehicle.getScript();
        if (script != null) {
            length = script.getExtents().z;
            height = script.getExtents().y;
        }
        // What it tows raises the camera if it is taller, but does not push it back.
        BaseVehicle towed = vehicle.getVehicleTowing();
        if (towed != null && towed.getScript() != null) height = Math.max(height, towed.getScript().getExtents().y);
        sizeBoom = length > 0.0f ? (length - REF_LENGTH) * 0.5f : 0.0f;
        rear = (length > 0.0f ? length : REF_LENGTH) * 0.5f;
        sizeLift = height > 0.0f ? (height - REF_HEIGHT) * HEIGHT_LIFT : 0.0f;
    }

    public static void pivot(Frame frame, BaseVehicle vehicle, ThirdPersonRig.Snap snap) {
        VehicleLead.lead(vehicle, 0);
        snap.x = vehicle.getX() + VehicleLead.x + offX;
        snap.y = vehicle.getY() + VehicleLead.y + offY;
        snap.h = frame.camZ * ThirdPersonRig.LEVEL + frame.eyeY + LIFT + sizeLift;
        snap.distance = boom();
        snap.shoulder = 0.0f;
        snap.aheadX = 0.0f;
        snap.aheadY = 0.0f;
        snap.on = true;
    }

    public static void wheel(int wheel) {
        DISTANCE.set(ThirdPersonRig.wheelStep(DISTANCE.get(), NEAREST, FARTHEST, wheel));
    }

    static void feel(BaseVehicle vehicle, float dt) {
        float f = FEEL.get();
        float speed = Math.abs(vehicle.getCurrentSpeedKmHour());
        float top = vehicle.getMaxSpeed() > 0.0f ? vehicle.getMaxSpeed() : TOP_KMH;
        float pace = Math.min(1.0f, speed / (top * FULL_SHARE));
        float want = f * (DISTANCE.get() + sizeBoom) * SPEED_BACK * pace;
        float k = ThirdPersonRig.ease(dt, SETTLE_TIME);
        feltBoom += (want - feltBoom) * k;
        fovAdd += ((float) Math.toRadians(f * SPEED_FOV * pace) - fovAdd) * k;
    }

    public static float boom() {
        return Math.max(NEAREST * 0.5f, (shown < 0.0f ? DISTANCE.get() : shown) + sizeBoom + feltBoom);
    }

    // Room the wall grid needs round the vehicle's centre.
    public static float reach() {
        return boom() + OFFSET_MAX;
    }

    static float clamp01(float t) {
        return Math.max(0.0f, Math.min(1.0f, t));
    }

    static float wrap(float a) {
        a %= TWO_PI;
        return a > Math.PI ? a - TWO_PI : (a < -Math.PI ? a + TWO_PI : a);
    }
}
