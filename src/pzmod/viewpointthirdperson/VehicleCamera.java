package pzmod.viewpointthirdperson;

import java.util.IdentityHashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Vector3f;
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
    // Above the roof, where the old eye-based pivot sat over vanilla cars, vans and step vans alike.
    static final float ROOF_CLEAR = 0.64f;
    // A plain sedan (CarNormal): the size Distance is meant for.
    static final float REF_LENGTH = 4.74f;
    static final float REF_ROOF = 1.41f;
    // A raised camera tilts down to look at the road as far ahead as it does over a sedan.
    static final float LOOK_AHEAD = 12.0f;
    static final float SIDE_SHARE = 0.5f;
    static final float OFFSET_MAX = 2.5f;
    static final float WEIGHT_TIME = 0.8f;
    static final float FEEL_DEFAULT = 0.5f;
    // Above its default, the weight takes up a change of speed over up to this long, so the dip
    // of a gear change while setting off evens out instead of rocking the camera.
    static final float GATHER_TIME = 0.3f;
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
    static float roofUp;
    static final Map<VehicleScript, float[]> roofs = new IdentityHashMap<>();
    static final Matrix3f turnShape = new Matrix3f();
    static float rear;
    static BaseVehicle last;
    static float vx, vy;
    static float fx = 1.0f, fy;
    static float nose;
    static boolean swingOn;
    static float swingHeading, swingDown, swingTime, swingPace;
    static long drawnFrame = Long.MIN_VALUE;
    static float drawnNose = Float.NaN;
    static float swung = Float.NaN;
    static long swungFrame;
    // What the last frame drawn was turned on by, for being drawn late; taken back before the next.
    static float leadYaw;
    static float offX, offY;
    static float gatherX, gatherY;
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
        swingOn = false;
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
        VehicleLead.lead(vehicle, 0);
        nose = (float) Math.atan2(fy, fx) - VehicleLead.turn;
        fx = (float) Math.cos(nose);
        fy = (float) Math.sin(nose);
        vehicle.getLinearVelocity(velocity);
        float dvx = velocity.x - vx;
        float dvy = velocity.z - vy;
        vx = velocity.x;
        vy = velocity.z;
        if (vehicle != last) {
            last = vehicle;
            dvx = 0.0f;
            dvy = 0.0f;
            offX = 0.0f;
            offY = 0.0f;
            offXSpring.speed = 0.0f;
            offYSpring.speed = 0.0f;
            gatherX = 0.0f;
            gatherY = 0.0f;
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
        swingOn = false;
        if (!Look.captured || !FOLLOW.get()) {
            tracking = false;
            rested = 0.0f;
            return;
        }
        rested = moved || !tracking ? 0.0f : rested + dt;
        tracking = true;

        float speed = Math.abs(vehicle.getCurrentSpeedKmHour());
        swingOn = speed >= MOVING_KMH && rested >= DELAY.get();
        if (swingOn) {
            swingPace = Math.min(1.0f, Math.max(0.25f, speed / FULL_KMH));
            swingTime = SWING.get() / swingPace * ThirdPersonRig.lerp(CLOSE_SWING, 1.0f, firm);
            swingHeading = heading(speed);
            swingDown = (float) Math.toRadians(-PITCH.get()) - (float) Math.atan2(Math.max(0.0f, sizeLift), LOOK_AHEAD);
        }
    }

    // The render thread reads Look.yaw just before drawing a frame, while this thread is already
    // working out the next: a swing written here would turn the view of a frame drawn with the
    // vehicle of the one before. So each frame keeps its swing, and the render thread turns the
    // view by it just before drawing that frame.
    static void keepSwing(ThirdPersonRig.Snap snap, long frame, float dt) {
        snap.swingFrame = frame;
        snap.swingDt = dt;
        snap.swingOn = swingOn && tracking;
        snap.swingHeading = swingHeading;
        snap.swingDown = swingDown;
        snap.swingTime = swingTime;
        snap.swingPace = swingPace;
        snap.swingNose = nose;
    }

    // Render thread, before drawing a frame. The swing moves on by the time that frame moved the
    // vehicle on, not by the render thread's own clock. Slower driving swings more gently, but a
    // turn is carried along by the share the swing is slower, so the view trails a turn by no
    // more than at speed. The main thread may be inside Look.read meanwhile: the swing is moved
    // into what its afterRead counts from, not the mouse. The view turns on with the vehicle by
    // ahead, how much later than usual the frame is drawn (DrawTime).
    public static synchronized void drawSwing(ThirdPersonRig.Snap snap, float ahead) {
        if (snap != null && snap.swingFrame == drawnFrame) return;
        drawnFrame = snap == null ? Long.MIN_VALUE : snap.swingFrame;
        if (leadYaw != 0.0f) {
            Look.yaw = wrap(Look.yaw - leadYaw);
            readYaw = wrap(readYaw - leadYaw);
            leadYaw = 0.0f;
        }
        if (snap == null || !snap.on || !snap.seated || !snap.swingOn) {
            drawnNose = Float.NaN;
            swung = Float.NaN;
            yawSpring.speed = 0.0f;
            pitchSpring.speed = 0.0f;
            return;
        }
        float carried = Float.isNaN(drawnNose) ? 0.0f : wrap(snap.swingNose - drawnNose) * (1.0f - snap.swingPace);
        drawnNose = snap.swingNose;
        float yaw = Look.yaw;
        float pitch = Look.pitch;
        float to = wrap(yaw + carried);
        to = wrap(snap.swingHeading + yawSpring.step(wrap(to - snap.swingHeading), snap.swingTime, snap.swingDt));
        float toPitch = snap.swingDown + pitchSpring.step(pitch - snap.swingDown, snap.swingTime, snap.swingDt);
        boolean next = snap.swingFrame == swungFrame + 1 && !Float.isNaN(swung) && snap.swingDt > 0.0f;
        float rate = next ? wrap(to - swung) / snap.swingDt : 0.0f;
        swung = to;
        swungFrame = snap.swingFrame;
        leadYaw = rate * ahead;
        to = wrap(to + leadYaw);
        Look.yaw = to;
        Look.pitch = toPitch;
        readYaw = wrap(readYaw + wrap(to - yaw));
        readPitch += toPitch - pitch;
    }

    // A turn of the view by the render thread that the mouse smoothing is not to count.
    static synchronized void turnView(float yaw, float pitch) {
        float p = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, Look.pitch + pitch));
        Look.yaw = wrap(Look.yaw + yaw);
        readYaw = wrap(readYaw + yaw);
        readPitch += p - Look.pitch;
        Look.pitch = p;
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
        float share = feel() * firm;
        float along = (dvx * fx + dvy * fy) * share;
        float side = (dvy * fx - dvx * fy) * SIDE_SHARE * share;
        gatherX += along * fx - side * fy;
        gatherY += along * fy + side * fx;
        float k = ThirdPersonRig.ease(dt, GATHER_TIME * clamp01((FEEL.get() - FEEL_DEFAULT) / (1.0f - FEEL_DEFAULT)));
        offXSpring.speed -= gatherX * k;
        offYSpring.speed -= gatherY * k;
        gatherX -= gatherX * k;
        gatherY -= gatherY * k;
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
        float tall = REF_ROOF;
        float top = REF_ROOF;
        VehicleScript script = vehicle.getScript();
        if (script != null) {
            float[] roof = roof(script);
            length = script.getExtents().z;
            top = roof[0];
            tall = roof[0] - roof[1];
        }
        // What it tows raises the camera if it is taller, but does not push it back.
        BaseVehicle towed = vehicle.getVehicleTowing();
        if (towed != null && towed.getScript() != null) {
            float[] roof = roof(towed.getScript());
            if (roof[0] - roof[1] > tall) {
                top += roof[0] - roof[1] - tall;
                tall = roof[0] - roof[1];
            }
        }
        sizeBoom = length > 0.0f ? (length - REF_LENGTH) * 0.5f : 0.0f;
        rear = (length > 0.0f ? length : REF_LENGTH) * 0.5f;
        sizeLift = tall - REF_ROOF;
        roofUp = top;
    }

    // Roof and ground above and below the vehicle's origin, from its collision boxes and wheels;
    // the model's mesh keeps no bounds. Scripts are scaled once loaded.
    static float[] roof(VehicleScript script) {
        float[] roof = roofs.get(script);
        if (roof != null) return roof;
        Vector3f com = script.getCenterOfMassOffset();
        Vector3f ext = script.getExtents();
        float top = com.y + ext.y * 0.5f;
        for (int i = 0; i < script.getPhysicsShapeCount(); i++) {
            VehicleScript.PhysicsShape shape = script.getPhysicsShape(i);
            if (shape.type == 1) {
                turnShape.rotationXYZ((float) Math.toRadians(shape.rotate.x), (float) Math.toRadians(shape.rotate.y),
                        (float) Math.toRadians(shape.rotate.z));
                top = Math.max(top, shape.offset.y + 0.5f * (Math.abs(turnShape.m01) * shape.extents.x
                        + Math.abs(turnShape.m11) * shape.extents.y + Math.abs(turnShape.m21) * shape.extents.z));
            } else if (shape.type == 2) {
                top = Math.max(top, shape.offset.y + shape.radius);
            }
        }
        float ground = com.y - ext.y * 0.5f;
        if (script.getWheelCount() > 0) {
            float model = script.getModel() != null ? script.getModel().getOffset().y : 0.0f;
            ground = Float.MAX_VALUE;
            for (int i = 0; i < script.getWheelCount(); i++) {
                VehicleScript.Wheel wheel = script.getWheel(i);
                ground = Math.min(ground, wheel.offset.y + model - wheel.radius);
            }
        }
        roof = new float[]{top, ground};
        roofs.put(script, roof);
        return roof;
    }

    public static void pivot(BaseVehicle vehicle, ThirdPersonRig.Snap snap, long frame, float dt) {
        VehicleLead.lead(vehicle, 0);
        snap.x = vehicle.getX() + VehicleLead.x + offX;
        snap.y = vehicle.getY() + VehicleLead.y + offY;
        snap.h = vehicle.jniTransform.origin.y + roofUp + ROOF_CLEAR;
        snap.seated = true;
        keepSwing(snap, frame, dt);
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
        float f = feel() * FEEL_DEFAULT;
        float speed = Math.abs(vehicle.getCurrentSpeedKmHour());
        float top = vehicle.getMaxSpeed() > 0.0f ? vehicle.getMaxSpeed() : TOP_KMH;
        float pace = Math.min(1.0f, speed / (top * FULL_SHARE));
        float want = f * (DISTANCE.get() + sizeBoom) * SPEED_BACK * pace;
        float k = ThirdPersonRig.ease(dt, SETTLE_TIME);
        feltBoom += (want - feltBoom) * k;
        fovAdd += ((float) Math.toRadians(f * SPEED_FOV * pace) - fovAdd) * k;
    }

    // Speed feel as a share of its default: linear up to the default, then flattening out at
    // 1.5 at full rather than going on to 2.
    static float feel() {
        float f = FEEL.get();
        if (f <= FEEL_DEFAULT) return f / FEEL_DEFAULT;
        float u = f - FEEL_DEFAULT;
        return 1.0f + 2.0f * u - 2.0f * u * u;
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
