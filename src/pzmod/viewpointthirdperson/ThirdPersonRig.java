package pzmod.viewpointthirdperson;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import viewpoint.core.CameraSquares;
import viewpoint.core.Frame;
import viewpoint.input.Look;
import viewpoint.input.ThirdPerson;
import viewpoint.platform.LiveSettings;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.iso.IsoGridSquare;
import zombie.inventory.types.HandWeapon;
import zombie.vehicles.BaseVehicle;

public class ThirdPersonRig {
    static final String TAB = "Third person";
    static final String SECTION = TAB + "/On foot";
    static final String COMBAT = TAB + "/Combat";
    static final float LEVEL = 2.4494896f;
    // Viewpoint's ThirdPerson.lift on foot.
    static final float FOOT_LIFT = 0.2f;
    static final float SNAP_DISTANCE = 2.0f;
    static final float SIDE_TIME = 0.15f;
    static final float MIN_DISTANCE = 0.2f;
    static final float MAX_DISTANCE = 10.0f;
    static final float ZOOM_TIME = 0.08f;
    // In quicker than out: indoors the room is close at once, outdoors there is time.
    static final float INDOOR_IN_TIME = 0.12f;
    static final float INDOOR_OUT_TIME = 0.4f;
    static final float SETTLE_IN = 0.15f;
    static final float SETTLE_OUT = 0.25f;
    static final long GRACE_NANOS = 3_000_000_000L;
    static final float VELOCITY_WINDOW = 0.15f;
    static final int TRAIL = 128;
    // Closer than FIRM_FAR the follow lag fades out, gone at FIRM_NEAR: the same lag in metres
    // is a larger angle the nearer the camera hangs.
    static final float FIRM_NEAR = 0.5f;
    static final float FIRM_FAR = 2.0f;

    public static final LiveSettings.Number DISTANCE = LiveSettings.number("thirdPersonCamera.distance", "Distance", SECTION, 0.3f, 8.0f, 0.05f, 2.0f);
    public static final LiveSettings.Number ZOOM_NEAR = LiveSettings.number("thirdPersonCamera.zoomNearest", "Zoom: nearest", SECTION, 0.3f, 8.0f, 0.05f, 1.5f);
    public static final LiveSettings.Number ZOOM_FAR = LiveSettings.number("thirdPersonCamera.zoomFarthest", "Zoom: farthest", SECTION, 0.3f, 8.0f, 0.05f, 4.0f);
    public static final LiveSettings.Number ZOOM_STEPS = LiveSettings.number("thirdPersonCamera.zoomSteps", "Zoom: steps", SECTION, 1.0f, 20.0f, 1.0f, 6.0f);
    public static final LiveSettings.Toggle INDOOR_ZOOM = LiveSettings.toggle("thirdPersonCamera.indoorZoom", "Zoom in indoors", SECTION, true);
    public static final LiveSettings.Number INDOOR_DISTANCE = LiveSettings.number("thirdPersonCamera.indoorDistance", "Distance indoors", SECTION, 0.3f, 8.0f, 0.05f, 0.5f);
    public static final LiveSettings.Number RECOVER = LiveSettings.number("thirdPersonCamera.collisionRecover", "Back out from walls (s)", SECTION, 0.0f, 1.0f, 0.01f, 0.35f);
    public static final LiveSettings.Number HEIGHT = LiveSettings.number("thirdPersonCamera.height", "Height", SECTION, -0.8f, 1.5f, 0.01f, 0.15f);
    public static final LiveSettings.Choice SIDE = LiveSettings.choice("thirdPersonCamera.side", "Shoulder", SECTION, new String[]{"Right", "Left"}, 0);
    public static final LiveSettings.Number SHOULDER = LiveSettings.number("thirdPersonCamera.shoulder", "Shoulder offset", SECTION, 0.0f, 1.5f, 0.01f, 0.3f);
    public static final LiveSettings.Number LOOK_UP = LiveSettings.number("thirdPersonCamera.lookUpCloser", "Closer when looking up", SECTION, 0.0f, 0.9f, 0.05f, 0.5f);
    public static final LiveSettings.Number FOLLOW = LiveSettings.number("thirdPersonCamera.follow", "Follow smoothing (s)", SECTION, 0.0f, 0.5f, 0.01f, 0.06f);
    public static final LiveSettings.Number FOLLOW_HEIGHT = LiveSettings.number("thirdPersonCamera.followHeight", "Height smoothing (s)", SECTION, 0.0f, 0.8f, 0.01f, 0.2f);
    public static final LiveSettings.Number MELEE_DISTANCE = LiveSettings.number("thirdPersonCamera.meleeDistance", "Combat stance: distance outdoors (×)", COMBAT, 0.5f, 2.0f, 0.05f, 1.1f);
    public static final LiveSettings.Number MELEE_INDOORS = LiveSettings.number("thirdPersonCamera.meleeDistanceIndoors", "Combat stance: distance indoors (×)", COMBAT, 0.5f, 2.0f, 0.05f, 0.8f);
    public static final LiveSettings.Number MELEE_SHOULDER = LiveSettings.number("thirdPersonCamera.meleeShoulder", "Combat stance: shoulder offset", COMBAT, 0.0f, 1.5f, 0.01f, 0.2f);
    public static final LiveSettings.Number FIREARM_DISTANCE = LiveSettings.number("thirdPersonCamera.firearmDistance", "Firearm aiming: distance outdoors", COMBAT, 0.3f, 5.0f, 0.05f, 1.6f);
    public static final LiveSettings.Number FIREARM_INDOORS = LiveSettings.number("thirdPersonCamera.firearmDistanceIndoors", "Firearm aiming: distance indoors", COMBAT, 0.3f, 5.0f, 0.05f, 0.6f);
    public static final LiveSettings.Number FIREARM_SHOULDER = LiveSettings.number("thirdPersonCamera.firearmShoulder", "Firearm aiming: shoulder offset", COMBAT, 0.0f, 1.5f, 0.01f, 0.5f);
    public static final LiveSettings.Number FIREARM_FOV = LiveSettings.number("thirdPersonCamera.firearmFovChange", "Firearm aiming: field of view change", COMBAT, -40.0f, 10.0f, 1.0f, -5.0f);
    public static final LiveSettings.Number STANCE_TIME = LiveSettings.number("thirdPersonCamera.stanceTime", "Stance transition (s)", COMBAT, 0.0f, 1.0f, 0.01f, 0.12f);

    static final Set<String> STEADY;
    static final LiveSettings.Number HEAD_MOVEMENT;
    // EyeMotion's own blend into and out of the steady states, per second.
    static final float STEADY_RATE = 5.0f;
    static final MethodHandle REACH;
    static final MethodHandle KEEP_CLEAR;
    public static volatile boolean ok;

    public static float side = 1.0f;
    public static float melee;
    public static float firearm;
    public static volatile float firearmEased;
    static boolean following;
    static float pivotX, pivotY, pivotH;
    static float drawnX, drawnY;
    static float steadyEye;
    static float steadied;
    static float floorH, eyeH;
    static float lagX, lagY, lagZ;
    static float velX, velY;
    static float lastYaw = Float.NaN;
    static float yawRate;
    static float stepAhead;
    static double clock;
    static final double[] trailT = new double[TRAIL];
    static final float[] trailX = new float[TRAIL];
    static final float[] trailY = new float[TRAIL];
    static final float[] trailZ = new float[TRAIL];
    static int trailEnd, trailCount;
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
    static volatile float gridLevelLift = Float.NaN;
    static Method setSide;

    public static final class Snap {
        public volatile boolean on;
        public boolean seated;
        public volatile boolean swingOn;
        public volatile float swingHeading, swingDown, swingTime, swingPace, swingNose, swingDt;
        public volatile long swingFrame;
        public float x, y, h, bodyX, bodyY, distance, shoulder, held;
        public float aheadX, aheadY, clearX, clearY, clearH;
    }

    static final Map<CameraSquares, Snap> snaps = new ConcurrentHashMap<>();

    static {
        DISTANCE.describe("Outdoors. The mouse wheel zooms.");
        INDOOR_ZOOM.describe("Off = same distance as outdoors.");
        INDOOR_DISTANCE.describe("Entering a building zooms in to this.");
        ZOOM_STEPS.describe("Wheel turns from nearest to farthest.");
        SIDE.describe("The Swap shoulder key switches it.");
        LOOK_UP.describe("Keeps the camera off the ground.");
        FOLLOW.describe("0 = rigid.");
        FOLLOW_HEIGHT.describe("Crouching.");
        MELEE_DISTANCE.describe("1 = unchanged.");
        MELEE_INDOORS.describe("1 = unchanged.");
        FIREARM_FOV.describe("Below 0 zooms in.");
        Set<String> steady = null;
        try {
            Field f = Class.forName("viewpoint.input.Controls").getDeclaredField("STEADY_STATES");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) f.get(null);
            steady = set;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] head motion always on, Viewpoint changed: " + e);
        }
        STEADY = steady;
        LiveSettings.Number headMovement = null;
        try {
            Field f = Class.forName("viewpoint.input.Controls").getDeclaredField("HEAD_MOVEMENT");
            f.setAccessible(true);
            headMovement = (LiveSettings.Number) f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] camera steady on the body, Viewpoint changed: " + e);
        }
        HEAD_MOVEMENT = headMovement;
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
        MouseKeyboard.init();
        ControllerLook.init();
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
        // Root motion worked out in one frame moves the body in the next, so this frame's
        // step belongs to the previous frame's time delta.
        float step = stepAhead;
        stepAhead = GameTime.getInstance().getTimeDelta();
        if (ThirdPerson.active && ViewpointThirdPerson.shoulderPressed()) swapSide();

        Snap snap = snaps.computeIfAbsent(frame.cameraSquares, k -> new Snap());
        snap.swingOn = false;
        snap.swingFrame = frame.number;
        BaseVehicle vehicle = chr == null ? null : chr.getVehicle();
        boolean on = ok && ThirdPerson.active && !frame.onCamera && chr != null;
        if (on) FrameClock.tick(frame.number, dt);
        else FrameClock.stop();
        DrawTime.begin(frame, now, on);
        drawnX = 0.0f;
        drawnY = 0.0f;
        if (!on || vehicle != null) {
            if (on) {
                if (!seated) held = -1.0f;
                seated = true;
                VehicleCamera.update(vehicle, dt);
                VehicleCamera.pivot(vehicle, snap, frame.number, dt);
                DrawTime.camera(frame, VehicleCamera.vx, VehicleCamera.vy);
                // Viewpoint puts the wall grid's two levels where the eye is, which climbs a level
                // getting in and out of a tall vehicle; ours start at the vehicle's floor.
                int level = (int) Math.floor(vehicle.jniTransform.origin.y / LEVEL + 0.05f);
                gridLevelLift = (level + 0.5f - frame.camZ) * LEVEL - frame.eyeY;
            } else {
                VehicleCamera.reset();
                gridLevelLift = Float.NaN;
                snap.on = false;
                held = -1.0f;
            }
            LookAround.reset();
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
        LookAround.update(chr, dt);

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
        if (unsettled >= (under ? SETTLE_IN : SETTLE_OUT)) {
            unsettled = 0.0f;
            inside = under;
            if (!inside) {
                leftAt = now;
            } else if (indoorZoom < 0.0f || now - leftAt > GRACE_NANOS) {
                indoorZoom = Math.min(DISTANCE.get(), INDOOR_DISTANCE.get());
            }
        }
        indoor += ((inside ? 1.0f : 0.0f) - indoor) * ease(dt, inside ? INDOOR_IN_TIME : INDOOR_OUT_TIME);
        float base = INDOOR_ZOOM.get() ? lerp(DISTANCE.get(), indoorDistance(), smooth(indoor)) : DISTANCE.get();
        shownDistance = shownDistance < 0.0f ? base : shownDistance + (base - shownDistance) * ease(dt, ZOOM_TIME);
        float roomy = shownDistance;
        side += ((SIDE.get() == 0 ? 1.0f : -1.0f) - side) * ease(dt, SIDE_TIME);

        // Viewpoint's eye follows the head by Head movement in steady states but fully through a
        // swing, a shove or a climb (EyeMotion); the camera follows by Head movement throughout.
        float headMovement = HEAD_MOVEMENT != null ? HEAD_MOVEMENT.get() : 0.0f;
        boolean steady = STEADY == null || STEADY.contains(chr.getCurrentActionContextStateName());
        steadied = !following ? (steady ? 1.0f : 0.0f)
                : steadied + Math.max(-STEADY_RATE * dt, Math.min(STEADY_RATE * dt, (steady ? 1.0f : 0.0f) - steadied));
        float motion = headMovement / Math.max(1.0e-6f, 1.0f - steadied * (1.0f - headMovement));
        if (!following || steady) steadyEye = frame.eyeY;
        float tx = frame.camX - frame.eyeX * motion;
        float ty = frame.camY - frame.eyeZ * motion;
        // The floor height climbs stairs with the body like x and y; only the eye's own height
        // (crouching) is smoothed apart.
        float tz = frame.camZ * LEVEL;
        float te = lerp(steadyEye, frame.eyeY, motion) + HEIGHT.get();
        float dx = tx - pivotX;
        float dy = ty - pivotY;
        if (!following || dx * dx + dy * dy > SNAP_DISTANCE * SNAP_DISTANCE || Math.abs(tz - floorH) > LEVEL) {
            eyeH = te;
            following = true;
            trailCount = 0;
            lagX = 0.0f;
            lagY = 0.0f;
            lagZ = 0.0f;
        } else {
            eyeH += (te - eyeH) * ease(dt, FOLLOW_HEIGHT.get());
        }
        floorH = tz;
        float in = smooth(indoor);
        float meleeShare = lerp(MELEE_DISTANCE.get(), MELEE_INDOORS.get(), in);
        float gunDistance = Math.min(roomy, lerp(FIREARM_DISTANCE.get(), FIREARM_INDOORS.get(), in));
        float distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE,
                lerp(roomy * lerp(1.0f, meleeShare, m), gunDistance, a)));
        float near = held >= 0.0f ? Math.min(distance, held) : distance;
        follow(tx, ty, tz, step, FOLLOW.get() * (1.0f - 0.7f * a) * firm(near));
        // The body is drawn, and the camera follows it, on the frames' clock.
        drawnX = velX * FrameClock.ahead;
        drawnY = velY * FrameClock.ahead;
        tx += drawnX;
        ty += drawnY;
        DrawTime.camera(frame, velX, velY);
        pivotX = tx - lagX;
        pivotY = ty - lagY;
        pivotH = tz - lagZ + eyeH;
        snap.x = pivotX;
        snap.y = pivotY;
        snap.h = pivotH;
        snap.bodyX = tx;
        snap.bodyY = ty;
        snap.aheadX = velX * AHEAD;
        snap.aheadY = velY * AHEAD;
        snap.distance = distance;
        snap.shoulder = lerp(lerp(SHOULDER.get(), MELEE_SHOULDER.get(), m), FIREARM_SHOULDER.get(), a) * side;
        snap.seated = false;
        snap.swingOn = false;
        snap.on = true;
        // Viewpoint's grid starts at the eye's level; a pivot or camera below it, as in a jump, is
        // walled off.
        int eyeLevel = (int) Math.floor(frame.camZ + (frame.eyeY + FOOT_LIFT) / LEVEL);
        float lowest = pivotH - Math.max(0.0f, (float) Math.sin(Look.pitch)) * distance - CLEAR;
        int level = Math.max(eyeLevel - 1, Math.min(eyeLevel, (int) Math.floor(lowest / LEVEL)));
        gridLevelLift = level < eyeLevel ? (level + 0.5f - frame.camZ) * LEVEL - frame.eyeY : Float.NaN;
    }

    static Field drawnFrame;
    static boolean drawBroken;

    // Render thread, with Viewpoint's SceneDrawer about to draw its frame.
    public static void beforeDraw(Object drawer) {
        ControllerLook.draw();
        if (drawBroken) return;
        try {
            if (drawnFrame == null) {
                drawnFrame = drawer.getClass().getDeclaredField("frame");
                drawnFrame.setAccessible(true);
            }
            Frame frame = (Frame) drawnFrame.get(drawer);
            float ahead = DrawTime.draw(frame);
            VehicleCamera.drawSwing(ok ? snaps.get(frame.cameraSquares) : null, ahead);
        } catch (ReflectiveOperationException | RuntimeException e) {
            drawBroken = true;
            System.out.println("[ViewpointThirdPerson] vehicle camera does not swing in, nothing drawn at draw time, Viewpoint changed: " + e);
        }
    }

    public static void shiftBody(ModelSlotRenderData data) {
        if (data.inVehicle || data.object != IsoPlayer.players[0] || (drawnX == 0.0f && drawnY == 0.0f)) return;
        data.x += drawnX;
        data.y += drawnY;
    }

    static float firm(float near) {
        return smooth(Math.max(0.0f, Math.min(1.0f, (near - FIRM_NEAR) / (FIRM_FAR - FIRM_NEAR))));
    }

    // The pivot trails the body by its velocity over a short window, on the game's clock rather
    // than ours: at a steady pace the body then holds still on screen however uneven the frames.
    static void follow(float x, float y, float z, float step, float time) {
        clock += step;
        trailEnd = (trailEnd + 1) % TRAIL;
        trailT[trailEnd] = clock;
        trailX[trailEnd] = x;
        trailY[trailEnd] = y;
        trailZ[trailEnd] = z;
        trailCount = Math.min(trailCount + 1, TRAIL);
        int first = trailEnd;
        for (int i = 1; i < trailCount; i++) {
            first = (trailEnd - i + TRAIL) % TRAIL;
            if (clock - trailT[first] >= VELOCITY_WINDOW) break;
        }
        double span = clock - trailT[first];
        float vx = span > 1.0e-4 ? (float) ((x - trailX[first]) / span) : 0.0f;
        float vy = span > 1.0e-4 ? (float) ((y - trailY[first]) / span) : 0.0f;
        float vz = span > 1.0e-4 ? (float) ((z - trailZ[first]) / span) : 0.0f;
        velX = vx;
        velY = vy;
        float k = ease(step, time);
        lagX += (vx * time - lagX) * k;
        lagY += (vy * time - lagY) * k;
        lagZ += (vz * time - lagZ) * k;
    }

    public static float gridBoom(boolean seated, float boom) {
        if (!ok) return boom;
        if (seated) return VehicleCamera.reach();
        float distance = Math.max(Math.max(DISTANCE.get(), INDOOR_DISTANCE.get()), shownDistance)
                * Math.max(1.0f, Math.max(MELEE_DISTANCE.get(), MELEE_INDOORS.get()));
        return Math.min(MAX_DISTANCE, distance)
                + Math.max(SHOULDER.get(), Math.max(MELEE_SHOULDER.get(), FIREARM_SHOULDER.get())) + 1.0f;
    }

    public static float gridLift(float lift) {
        float l = gridLevelLift;
        return ok && !Float.isNaN(l) ? l : lift;
    }

    public static float fov(float fov) {
        VehicleCamera.baseFov = fov;
        fov += VehicleCamera.fovAdd;
        float a = firearmEased;
        if (a <= 0.0f || !ThirdPerson.active) return fov;
        return fov + (float) Math.toRadians(FIREARM_FOV.get()) * a;
    }

    // A wall the boom will meet as the body moves or the view turns pulls it in beforehand, so it
    // seldom has to snap in.
    static final float AHEAD = 0.2f;
    static final float AHEAD_TIME = 0.1f;
    static final float MAX_TURN_AHEAD = (float) Math.toRadians(45.0);
    static final float CLEAR_TIME = 0.08f;
    static final float HEAD_CLEAR = 0.35f;
    static final float LIFT = 0.2f;
    static final float CLEAR = 0.45f;
    static final float[] captured = new float[3];
    // view() runs on the render thread (SceneDrawer) and the main thread (WorldText) at once.
    static final ThreadLocal<float[]> VIEWED = ThreadLocal.withInitial(() -> new float[3]);

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

    // Same for view(), with the mouse as it is just before drawing.
    public static void afterView(Frame frame, float yaw, float pitch, float[] out) {
        CameraSquares cs = frame.cameraSquares;
        float[] viewed = VIEWED.get();
        float bx = frame.camX - cs.x;
        float by = frame.camY - cs.y;
        float bh = (frame.camZ - cs.level) * LEVEL;
        if (!place(cs, bx - frame.eyeX, by - frame.eyeZ, bh + frame.eyeY, yaw, pitch, viewed, false)) return;
        // Moved on with what it hangs from, by how late the frame is drawn.
        if (DrawTime.drawing(frame)) {
            viewed[0] += DrawTime.camDX;
            viewed[1] += DrawTime.camDY;
        }
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
            float r = reach(cs, s, headX, headY, headH, px, py, ph);
            px = headX + (px - headX) * r;
            py = headY + (py - headY) * r;
            ph = headH + (ph - headH) * r;

            float cy = (float) Math.cos(yaw);
            float sy = (float) Math.sin(yaw);
            float cp = (float) Math.cos(pitch);
            float sp = (float) Math.sin(pitch);

            float sx = px - sy * s.shoulder;
            float sY = py + cy * s.shoulder;
            r = reach(cs, s, px, py, ph, sx, sY, ph);
            sx = px + (sx - px) * r;
            sY = py + (sY - py) * r;

            float d = s.distance * (1.0f - LOOK_UP.get() * Math.max(0.0f, sp));
            // Viewpoint hides the head within HEAD_CLEAR of the camera: swinging between
            // shoulders, or a short boom with little offset, backs off until it is clear.
            // Measured from the body, not the lagging pivot, so the boom does not pump with the
            // lag of each step.
            float vx = s.bodyX - cs.x - sy * s.shoulder - headX;
            float vy = s.bodyY - cs.y + cy * s.shoulder - headY;
            float vh = ph - headH - LIFT;
            float along = vx * cy * cp + vy * sy * cp + vh * sp;
            float disc = along * along - (vx * vx + vy * vy + vh * vh) + CLEAR * CLEAR;
            if (disc > 0.0f) d = Math.max(d, along + (float) Math.sqrt(disc));
            float bx = sx - cy * cp * d;
            float by = sY - sy * cp * d;
            float bh = ph - sp * d;
            r = reach(cs, s, sx, sY, ph, bx, by, bh);
            float len = d * r;
            if (capture) {
                float ahead = ahead(cs, s, sx, sY, ph, yaw, cp, sp, d);
                float target = Math.min(len, ahead);
                // In at once, out slowly; but a boom that was not pushed in follows the zoom freely.
                if (held < 0.0f || len <= held) {
                    held = len;
                } else if (target < held) {
                    held += (target - held) * ease(lastDt, AHEAD_TIME);
                } else if (r >= 1.0f && ahead >= d - 1.0e-3f && held >= heldFull - 1.0e-3f) {
                    held = len;
                } else {
                    held += (target - held) * ease(lastDt, RECOVER.get());
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
            if (capture) {
                float x = out[0], y = out[1], h = out[2];
                // keepClear would hold a seated camera under the sky as under a ceiling.
                if (!(s.seated && h > CameraSquares.LEVELS * LEVEL - CLEAR)) KEEP_CLEAR.invokeExact(cs, out);
                float k = ease(lastDt, CLEAR_TIME);
                s.clearX += (out[0] - x - s.clearX) * k;
                s.clearY += (out[1] - y - s.clearY) * k;
                s.clearH += (out[2] - h - s.clearH) * k;
                out[0] = x;
                out[1] = y;
                out[2] = h;
            }
            out[0] += s.clearX;
            out[1] += s.clearY;
            out[2] += s.clearH;
            return true;
        } catch (Throwable t) {
            ok = false;
            System.out.println("[ViewpointThirdPerson] third-person rig off after error: " + t);
            return false;
        }
    }

    static float ahead(CameraSquares cs, Snap s, float sx, float sY, float ph, float yaw, float cp, float sp, float d) throws Throwable {
        if (!Float.isNaN(lastYaw) && lastDt > 0.0f) {
            float turn = (float) Math.atan2(Math.sin(yaw - lastYaw), Math.cos(yaw - lastYaw));
            yawRate += (turn / lastDt - yawRate) * ease(lastDt, VELOCITY_WINDOW);
        }
        lastYaw = yaw;
        float turnAhead = Math.max(-MAX_TURN_AHEAD, Math.min(MAX_TURN_AHEAD, yawRate * AHEAD));
        if (Math.abs(turnAhead) < 1.0e-3f && s.aheadX * s.aheadX + s.aheadY * s.aheadY < 1.0e-6f) return d;
        float r = reach(cs, s, sx, sY, ph, sx + s.aheadX, sY + s.aheadY, ph);
        float qx = sx + s.aheadX * r;
        float qy = sY + s.aheadY * r;
        float y = yaw + turnAhead;
        float cy = (float) Math.cos(y);
        float sy = (float) Math.sin(y);
        return d * reach(cs, s, qx, qy, ph, qx - cy * cp * d, qy - sy * cp * d, ph - sp * d);
    }

    // Above the grid's two levels Viewpoint sees a wall. Seated, what lies above them is open
    // sky, so a ray that leaves through the top before meeting a wall runs its full length.
    static float reach(CameraSquares cs, Snap s, float x0, float y0, float h0, float x1, float y1, float h1) throws Throwable {
        float top = CameraSquares.LEVELS * LEVEL;
        if (s.seated && h0 >= top) return 1.0f;
        float r = (float) REACH.invokeExact(cs, x0, y0, h0, x1, y1, h1);
        if (s.seated && h1 > top && r >= (top - h0) / (h1 - h0) - 1.0e-3f) return 1.0f;
        return r;
    }

    // Outdoors the wheel moves Distance itself, so the window shows and keeps where it stands;
    // indoors it moves a distance of its own, set afresh on entering.
    public static void wheel(int wheel) {
        float near = wheelNear();
        float far = Math.max(ZOOM_NEAR.get(), ZOOM_FAR.get());
        if (far - near < 1.0e-3f) return;
        float d = wheelStep(zoomed(), near, far, wheel);
        if (zoomedIndoors()) indoorZoom = d;
        else DISTANCE.set(d);
    }

    static boolean zoomedAllIn() {
        return zoomed() <= wheelNear() + 0.05f;
    }

    static void zoomAllIn() {
        if (zoomedIndoors()) indoorZoom = wheelNear();
        else DISTANCE.set(wheelNear());
    }

    // Indoors the wheel must reach back in to where entering put the camera.
    private static float wheelNear() {
        float near = Math.min(ZOOM_NEAR.get(), ZOOM_FAR.get());
        return zoomedIndoors() ? Math.min(near, INDOOR_DISTANCE.get()) : near;
    }

    private static boolean zoomedIndoors() {
        return INDOOR_ZOOM.get() && inside && indoorZoom >= 0.0f;
    }

    private static float zoomed() {
        return zoomedIndoors() ? indoorZoom : DISTANCE.get();
    }

    // Steps evenly spaced on a log scale, so they feel alike; a distance between two steps
    // (set by hand, or the indoor distance) goes to the next one in the wheel's direction.
    // Distances are stored rounded to their 0.05 slider step, so a step reads back off by that.
    static float wheelStep(float d, float near, float far, int wheel) {
        int steps = Math.max(1, Math.round(ZOOM_STEPS.get()));
        double size = Math.log(far / near) / steps;
        double at = Math.log(Math.max(near, Math.min(far, d)) / near) / size;
        int nearest = (int) Math.round(at);
        boolean onStep = Math.abs(near * Math.exp(nearest * size) - d) <= 0.05;
        int step = onStep ? nearest + (wheel > 0 ? -1 : 1) : (int) (wheel > 0 ? Math.floor(at) : Math.ceil(at));
        return (float) (near * Math.exp(Math.max(0, Math.min(steps, step)) * size));
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
