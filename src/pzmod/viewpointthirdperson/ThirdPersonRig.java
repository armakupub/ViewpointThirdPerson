package pzmod.viewpointthirdperson;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import viewpoint.core.CameraSquares;
import viewpoint.core.Frame;
import viewpoint.input.Look;
import viewpoint.input.ThirdPerson;
import viewpoint.platform.LiveSettings;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.inventory.types.HandWeapon;
import zombie.vehicles.BaseVehicle;

public class ThirdPersonRig {
    static final String TAB = "Third person";
    static final String SECTION = TAB + "/On foot";
    static final String COMBAT = TAB + "/Combat";
    static final float LEVEL = 2.4494896f;
    static final float SNAP_DISTANCE = 2.0f;
    static final float SIDE_TIME = 0.15f;
    static final float MIN_DISTANCE = 0.2f;
    static final float MAX_DISTANCE = 10.0f;
    static final float ZOOM_TIME = 0.08f;
    static final float INDOOR_TIME = 0.4f;
    static final float SETTLE = 0.5f;
    static final long GRACE_NANOS = 3_000_000_000L;

    public static final LiveSettings.Number DISTANCE = LiveSettings.number("thirdPersonCamera.distance", "Distance", SECTION, 0.3f, 8.0f, 0.05f, 2.0f);
    public static final LiveSettings.Number ZOOM_NEAR = LiveSettings.number("thirdPersonCamera.zoomNearest", "Mouse wheel: nearest", SECTION, 0.3f, 8.0f, 0.05f, 1.5f);
    public static final LiveSettings.Number ZOOM_FAR = LiveSettings.number("thirdPersonCamera.zoomFarthest", "Mouse wheel: farthest", SECTION, 0.3f, 8.0f, 0.05f, 4.0f);
    public static final LiveSettings.Number INDOOR_DISTANCE = LiveSettings.number("thirdPersonCamera.indoorDistance", "Indoors: zoom in to", SECTION, 0.3f, 8.0f, 0.05f, 2.0f);
    public static final LiveSettings.Number RECOVER = LiveSettings.number("thirdPersonCamera.collisionRecover", "Collision: backing out (s)", SECTION, 0.0f, 1.0f, 0.01f, 0.35f);
    public static final LiveSettings.Number HEIGHT = LiveSettings.number("thirdPersonCamera.height", "Height above the eyes", SECTION, -0.8f, 1.5f, 0.01f, 0.15f);
    public static final LiveSettings.Choice SIDE = LiveSettings.choice("thirdPersonCamera.side", "Shoulder", SECTION, new String[]{"Right", "Left"}, 0);
    public static final LiveSettings.Number SHOULDER = LiveSettings.number("thirdPersonCamera.shoulder", "Shoulder offset", SECTION, 0.0f, 1.5f, 0.01f, 0.3f);
    public static final LiveSettings.Number LOOK_UP = LiveSettings.number("thirdPersonCamera.lookUpCloser", "Closer when looking up", SECTION, 0.0f, 0.9f, 0.05f, 0.5f);
    public static final LiveSettings.Number FOLLOW = LiveSettings.number("thirdPersonCamera.follow", "Follow smoothing (s)", SECTION, 0.0f, 0.5f, 0.01f, 0.06f);
    public static final LiveSettings.Number FOLLOW_HEIGHT = LiveSettings.number("thirdPersonCamera.followHeight", "Height smoothing (s)", SECTION, 0.0f, 0.8f, 0.01f, 0.2f);
    public static final LiveSettings.Number MELEE_DISTANCE = LiveSettings.number("thirdPersonCamera.meleeDistance", "Combat stance: distance (share of Distance)", COMBAT, 0.5f, 2.0f, 0.05f, 1.1f);
    public static final LiveSettings.Number MELEE_SHOULDER = LiveSettings.number("thirdPersonCamera.meleeShoulder", "Combat stance: shoulder offset", COMBAT, 0.0f, 1.5f, 0.01f, 0.15f);
    public static final LiveSettings.Number FIREARM_DISTANCE = LiveSettings.number("thirdPersonCamera.firearmDistance", "Firearm aiming: distance", COMBAT, 0.3f, 5.0f, 0.05f, 2.0f);
    public static final LiveSettings.Number FIREARM_SHOULDER = LiveSettings.number("thirdPersonCamera.firearmShoulder", "Firearm aiming: shoulder offset", COMBAT, 0.0f, 1.5f, 0.01f, 0.5f);
    public static final LiveSettings.Number FIREARM_FOV = LiveSettings.number("thirdPersonCamera.firearmFovChange", "Firearm aiming: field of view change", COMBAT, -40.0f, 10.0f, 1.0f, -5.0f);
    public static final LiveSettings.Number STANCE_TIME = LiveSettings.number("thirdPersonCamera.stanceTime", "Stance blend (s)", COMBAT, 0.0f, 1.0f, 0.01f, 0.12f);

    static final MethodHandle REACH;
    static final MethodHandle KEEP_CLEAR;
    public static volatile boolean ok;

    public static float side = 1.0f;
    public static float melee;
    public static float firearm;
    public static volatile float firearmEased;
    static boolean following;
    static float pivotX, pivotY, pivotH;
    static long last;
    static float shownDistance = -1.0f;
    static float indoor;
    static volatile boolean inside;
    static volatile float indoorZoom = -1.0f;
    static float unsettled;
    static long leftAt;
    static float lastDt;
    static float held = -1.0f;
    static float heldFull;
    static boolean seated;
    static Method setSide;

    public static final class Snap {
        public volatile boolean on;
        public float x, y, h, distance, shoulder, held;
    }

    static final Map<CameraSquares, Snap> snaps = new ConcurrentHashMap<>();

    static {
        DISTANCE.describe("How far behind the shoulder the camera hangs outdoors. The mouse wheel changes it, between the two limits below.");
        ZOOM_NEAR.describe("The nearest the mouse wheel brings the camera to the shoulder.");
        INDOOR_DISTANCE.describe("Going under a roof or into a room brings the camera in to this distance, if it was further out; the wheel then zooms freely. Back outside, it returns to Distance.");
        RECOVER.describe("After a wall pushed the camera in, how long it takes to back out again. It always moves in at once. On foot and in vehicles.");
        ZOOM_FAR.describe("The farthest the mouse wheel takes the camera from the shoulder.");
        HEIGHT.describe("Where the camera pivots, above (or below) the eyes. Follows a crouch, not the bob of each step.");
        SIDE.describe("Which shoulder the camera looks over. The swap shoulder key (Keys, Third person camera) flips it.");
        SHOULDER.describe("How far beside the body the camera sits while walking about.");
        LOOK_UP.describe("Looking up pulls the camera in by this share of its distance, so it stays off the ground.");
        FOLLOW.describe("How long the camera takes to catch up with the body: 0 is fixed to it.");
        FOLLOW_HEIGHT.describe("How long the camera takes to follow the body up and down: crouching, stairs.");
        MELEE_DISTANCE.describe("Distance in the combat stance without a firearm, as a share of Distance: above 1 backs off to see more round the body.");
        MELEE_SHOULDER.describe("Shoulder offset in the combat stance without a firearm: low keeps both flanks in view.");
        FIREARM_DISTANCE.describe("Distance while aiming a firearm.");
        FIREARM_SHOULDER.describe("Shoulder offset while aiming a firearm.");
        FIREARM_FOV.describe("Degrees the third-person field of view narrows (below 0) or widens while aiming a firearm. A small change keeps your surroundings in view.");
        STANCE_TIME.describe("How long the camera takes to move into and out of the combat stance or firearm aiming.");
        MethodHandle reach = null;
        MethodHandle keepClear = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Method m = ThirdPerson.class.getDeclaredMethod("reach", CameraSquares.class,
                    float.class, float.class, float.class, float.class, float.class, float.class);
            m.setAccessible(true);
            reach = lookup.unreflect(m);
            m = ThirdPerson.class.getDeclaredMethod("keepClear", CameraSquares.class, float[].class);
            m.setAccessible(true);
            keepClear = lookup.unreflect(m);
            ok = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] third-person rig off, Viewpoint changed: " + e);
        }
        REACH = reach;
        KEEP_CLEAR = keepClear;
    }

    public static void init() {
        VehicleCamera.init();
    }

    // Choice.set is package-private; through it the window shows the side the key chose.
    static void swapSide() {
        try {
            if (setSide == null) {
                setSide = LiveSettings.Choice.class.getDeclaredMethod("set", int.class);
                setSide.setAccessible(true);
            }
            setSide.invoke(SIDE, 1 - SIDE.get());
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] cannot swap the shoulder: " + e);
        }
    }

    static float ease(float dt, float time) {
        return time <= 0.0f ? 1.0f : 1.0f - (float) Math.exp(-dt / time);
    }

    public static void beforeCapture(Frame frame, IsoGameCharacter chr) {
        long now = System.nanoTime();
        float dt = last == 0L ? 0.0f : Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        lastDt = dt;
        if (ThirdPerson.active && ViewpointThirdPerson.shoulderPressed()) swapSide();

        Snap snap = snaps.computeIfAbsent(frame.cameraSquares, k -> new Snap());
        BaseVehicle vehicle = chr == null ? null : chr.getVehicle();
        boolean on = ok && ThirdPerson.active && !frame.onCamera && chr != null;
        if (!on || vehicle != null) {
            if (on) {
                if (!seated) held = -1.0f;
                seated = true;
                VehicleCamera.update(vehicle, dt);
                VehicleCamera.pivot(frame, vehicle, snap);
            } else {
                VehicleCamera.reset();
                snap.on = false;
                held = -1.0f;
            }
            following = false;
            melee = 0.0f;
            firearm = 0.0f;
            firearmEased = 0.0f;
            indoor = 0.0f;
            return;
        }
        if (seated) held = -1.0f;
        seated = false;
        VehicleCamera.reset();

        boolean aiming = chr.isAiming();
        HandWeapon weapon = aiming ? chr.getUseHandWeapon() : null;
        boolean gun = weapon != null && weapon.isAimedFirearm();
        float k0 = ease(dt, STANCE_TIME.get());
        melee += ((aiming && !gun ? 1.0f : 0.0f) - melee) * k0;
        firearm += ((gun ? 1.0f : 0.0f) - firearm) * k0;
        float m = smooth(melee);
        float a = smooth(firearm);
        firearmEased = a;
        IsoGridSquare sq = chr.getCurrentSquare();
        boolean under = sq != null && (sq.isInARoom() || !sq.isOutside());
        unsettled = under == inside ? 0.0f : unsettled + dt;
        if (unsettled >= SETTLE) {
            unsettled = 0.0f;
            inside = under;
            if (!inside) {
                leftAt = now;
            } else if (indoorZoom < 0.0f || now - leftAt > GRACE_NANOS) {
                indoorZoom = Math.min(DISTANCE.get(), INDOOR_DISTANCE.get());
            }
        }
        indoor += ((inside ? 1.0f : 0.0f) - indoor) * ease(dt, INDOOR_TIME);
        float base = lerp(DISTANCE.get(), indoorDistance(), smooth(indoor));
        shownDistance = shownDistance < 0.0f ? base : shownDistance + (base - shownDistance) * ease(dt, ZOOM_TIME);
        float roomy = shownDistance;
        side += ((SIDE.get() == 0 ? 1.0f : -1.0f) - side) * ease(dt, SIDE_TIME);

        float tx = frame.camX;
        float ty = frame.camY;
        float th = frame.camZ * LEVEL + frame.eyeY + HEIGHT.get();
        float dx = tx - pivotX;
        float dy = ty - pivotY;
        if (!following || dx * dx + dy * dy > SNAP_DISTANCE * SNAP_DISTANCE || Math.abs(th - pivotH) > LEVEL) {
            pivotX = tx;
            pivotY = ty;
            pivotH = th;
            following = true;
        } else {
            float k = ease(dt, FOLLOW.get() * (1.0f - 0.7f * a));
            pivotX += dx * k;
            pivotY += dy * k;
            pivotH += (th - pivotH) * ease(dt, FOLLOW_HEIGHT.get());
        }

        snap.x = pivotX;
        snap.y = pivotY;
        snap.h = pivotH;
        float distance = lerp(roomy * lerp(1.0f, MELEE_DISTANCE.get(), m), FIREARM_DISTANCE.get(), a);
        snap.distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
        snap.shoulder = lerp(lerp(SHOULDER.get(), MELEE_SHOULDER.get(), m), FIREARM_SHOULDER.get(), a) * side;
        snap.on = true;
    }

    public static float gridBoom(boolean seated, float boom) {
        if (!ok) return boom;
        if (seated) return VehicleCamera.reach();
        float distance = Math.max(Math.max(Math.max(DISTANCE.get(), INDOOR_DISTANCE.get()), shownDistance) * Math.max(1.0f, MELEE_DISTANCE.get()), FIREARM_DISTANCE.get());
        return Math.min(MAX_DISTANCE, distance)
                + Math.max(SHOULDER.get(), Math.max(MELEE_SHOULDER.get(), FIREARM_SHOULDER.get())) + 1.0f;
    }

    public static float fov(float fov) {
        fov += VehicleCamera.fovAdd;
        float a = firearmEased;
        if (a <= 0.0f || !ThirdPerson.active) return fov;
        return fov + (float) Math.toRadians(FIREARM_FOV.get()) * a;
    }

    static final float HEAD_CLEAR = 0.35f;
    static final float LIFT = 0.2f;
    static final float CLEAR = 0.45f;
    static final float[] captured = new float[3];
    static final float[] viewed = new float[3];

    // Viewpoint's capture() placed the camera already; this redoes it from our pivot and
    // overwrites what capture() publishes. Main thread.
    public static void afterCapture(Frame frame) {
        CameraSquares cs = frame.cameraSquares;
        if (!cs.on) return;
        float hx = frame.camX - cs.x - frame.eyeX;
        float hy = frame.camY - cs.y - frame.eyeZ;
        float hh = (frame.camZ - cs.level) * LEVEL + frame.eyeY;
        if (!place(cs, hx, hy, hh, Look.yaw, Look.pitch, captured, true)) return;
        ThirdPerson.offsetX = captured[0] - hx;
        ThirdPerson.offsetY = captured[1] - hy;
        ThirdPerson.offsetUp = captured[2] - hh;
        float up = ThirdPerson.offsetUp - LIFT;
        ThirdPerson.headShown = ThirdPerson.offsetX * ThirdPerson.offsetX + ThirdPerson.offsetY * ThirdPerson.offsetY
                + up * up >= HEAD_CLEAR * HEAD_CLEAR;
    }

    // Same for view(), with the mouse as it is just before drawing. Render thread.
    public static void afterView(Frame frame, float yaw, float pitch, float[] out) {
        CameraSquares cs = frame.cameraSquares;
        float bx = frame.camX - cs.x;
        float by = frame.camY - cs.y;
        float bh = (frame.camZ - cs.level) * LEVEL;
        if (!place(cs, bx - frame.eyeX, by - frame.eyeZ, bh + frame.eyeY, yaw, pitch, viewed, false)) return;
        out[0] = bx - viewed[0];
        out[1] = viewed[2] - bh;
        out[2] = by - viewed[1];
    }

    // Same chain as ThirdPerson.place (pivot, shoulder, boom, keepClear), from a pivot that
    // is smoothed and kept apart from the head bone; seated, from VehicleCamera's pivot.
    public static boolean place(CameraSquares cs, float headX, float headY, float headH,
                                float yaw, float pitch, float[] out, boolean capture) {
        Snap s = snaps.get(cs);
        if (!ok || s == null || !s.on) {
            return false;
        }
        try {
            float px = s.x - cs.x;
            float py = s.y - cs.y;
            float ph = s.h - cs.level * LEVEL;
            // The lagging pivot must not trail through a wall the body just passed.
            float r = reach(cs, headX, headY, headH, px, py, ph);
            px = headX + (px - headX) * r;
            py = headY + (py - headY) * r;
            ph = headH + (ph - headH) * r;

            float cy = (float) Math.cos(yaw);
            float sy = (float) Math.sin(yaw);
            float cp = (float) Math.cos(pitch);
            float sp = (float) Math.sin(pitch);

            float sx = px - sy * s.shoulder;
            float sY = py + cy * s.shoulder;
            r = reach(cs, px, py, ph, sx, sY, ph);
            sx = px + (sx - px) * r;
            sY = py + (sY - py) * r;

            float d = s.distance * (1.0f - LOOK_UP.get() * Math.max(0.0f, sp));
            // Viewpoint hides the head within HEAD_CLEAR of the camera: swinging between
            // shoulders, or a short boom with little offset, backs off until it is clear.
            float vx = sx - headX;
            float vy = sY - headY;
            float vh = ph - headH - LIFT;
            float along = vx * cy * cp + vy * sy * cp + vh * sp;
            float disc = along * along - (vx * vx + vy * vy + vh * vh) + CLEAR * CLEAR;
            if (disc > 0.0f) d = Math.max(d, along + (float) Math.sqrt(disc));
            float bx = sx - cy * cp * d;
            float by = sY - sy * cp * d;
            float bh = ph - sp * d;
            r = reach(cs, sx, sY, ph, bx, by, bh);
            float len = d * r;
            if (capture) {
                // In at once, out slowly; but a boom that was not pushed in follows the zoom freely.
                if (held < 0.0f || len <= held || (r >= 1.0f && held >= heldFull - 1.0e-3f)) {
                    held = len;
                } else {
                    held += (len - held) * ease(lastDt, RECOVER.get());
                }
                heldFull = d;
                s.held = held;
                len = held;
            } else {
                len = Math.min(len, s.held);
            }
            out[0] = sx - cy * cp * len;
            out[1] = sY - sy * cp * len;
            out[2] = ph - sp * len;
            KEEP_CLEAR.invokeExact(cs, out);
            return true;
        } catch (Throwable t) {
            ok = false;
            System.out.println("[ViewpointThirdPerson] third-person rig off after error: " + t);
            return false;
        }
    }

    static float reach(CameraSquares cs, float x0, float y0, float h0, float x1, float y1, float h1) throws Throwable {
        return (float) REACH.invokeExact(cs, x0, y0, h0, x1, y1, h1);
    }

    // Outdoors the wheel moves Distance itself, so the window shows and keeps where it stands;
    // indoors it moves a distance of its own, set afresh on entering.
    public static void wheel(int wheel) {
        float near = Math.min(ZOOM_NEAR.get(), ZOOM_FAR.get());
        float far = Math.max(ZOOM_NEAR.get(), ZOOM_FAR.get());
        boolean in = inside && indoorZoom >= 0.0f;
        float d = in ? indoorZoom : DISTANCE.get();
        d = Math.max(near, Math.min(far, wheel > 0 ? d / ThirdPersonZoom.STEP : d * ThirdPersonZoom.STEP));
        if (in) indoorZoom = d;
        else DISTANCE.set(d);
    }

    static float indoorDistance() {
        return indoorZoom >= 0.0f ? indoorZoom : Math.min(DISTANCE.get(), INDOOR_DISTANCE.get());
    }

    static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
