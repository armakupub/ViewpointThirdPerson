package pzmod.viewpointthirdperson;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3f;
import viewpoint.core.Frame;
import viewpoint.render.ModelDraws;
import viewpoint.render.SceneData;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.vehicles.BaseVehicle;

// The main thread captures a frame and Viewpoint's render thread draws it some time later, at
// intervals that follow the captures only loosely; a camera hung from something moving then
// shakes against the world. What it hangs from (the seated vehicle, what that tows, their
// occupants, the body on foot) is moved on in Viewpoint's draw records, and the camera with it,
// by how much later than usual its frame is drawn. The records are Viewpoint's package-private
// layout, read by reflection.
public class DrawTime {
    static final float MOST = 0.015f;
    static final float LATENCY_TIME = 1.0f;
    // ModelDraws: floats per record, where the bounds' centre and the last frame's matrix sit,
    // and the flag for a record that has a last frame's matrix.
    static final int VALUES = 60;
    static final int SPHERE = 24;
    static final int PREVIOUS = 36;
    static final int MOTION = 8;
    static final int ITEMS = 32;
    // Matrix and bounds' centre kept per record, to move from afresh if a frame is drawn twice.
    static final int KEPT = 19;
    static final Field RECORDS, KEYS, FLAGS;
    static volatile boolean broken;

    static final class Item {
        int from, to, blob;
        float vx, vy, rate, cx, cz;
    }

    static final class Shot {
        boolean on;
        long captured;
        float camVX, camVY;
        int count;
        final Item[] items = new Item[ITEMS];
        boolean saved;
        float[] kept = new float[64 * KEPT];
        final float[] blobs = new float[ITEMS * 3];
    }

    static final Map<Frame, Shot> shots = new ConcurrentHashMap<>();
    static final Vector3f velocity = new Vector3f();
    static Item open;

    static {
        Field records = null, keys = null, flags = null;
        try {
            records = ModelDraws.class.getDeclaredField("values");
            keys = ModelDraws.class.getDeclaredField("keys");
            flags = ModelDraws.class.getDeclaredField("flags");
            records.setAccessible(true);
            keys.setAccessible(true);
            flags.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            System.out.println("[ViewpointThirdPerson] vehicles and the body drawn at capture time, Viewpoint changed: " + e);
        }
        RECORDS = records;
        KEYS = keys;
        FLAGS = flags;
    }

    // Main thread, as a frame's capture starts.
    static void begin(Frame frame, long now, boolean on) {
        Shot s = shots.computeIfAbsent(frame, k -> new Shot());
        s.on = on && !broken;
        s.captured = now;
        s.camVX = 0.0f;
        s.camVY = 0.0f;
        s.count = 0;
        s.saved = false;
        open = null;
    }

    static void camera(Frame frame, float vx, float vy) {
        Shot s = shots.get(frame);
        if (s == null) return;
        s.camVX = vx;
        s.camVY = vy;
    }

    static Item start(Frame frame) {
        Shot s = shots.get(frame);
        if (s == null || !s.on || s.count == ITEMS) return null;
        Item it = s.items[s.count];
        if (it == null) it = s.items[s.count] = new Item();
        s.count++;
        it.from = frame.scene.models.count();
        it.to = it.from;
        it.blob = -1;
        it.rate = 0.0f;
        open = it;
        return it;
    }

    static void moving(Item it, BaseVehicle vehicle, int link) {
        vehicle.getLinearVelocity(velocity);
        it.vx = velocity.x;
        it.vy = velocity.z;
        it.rate = VehicleLead.rates[link].vehicle == vehicle ? VehicleLead.rates[link].rate : 0.0f;
    }

    public static void beforeVehicle(Frame frame, ModelSlotRenderData data, BaseVehicle vehicle) {
        open = null;
        int link = VehicleLead.link(vehicle);
        if (link < 0) return;
        Item it = start(frame);
        if (it == null) return;
        moving(it, vehicle, link);
        it.cx = frame.camX - data.x;
        it.cz = frame.camY - data.y;
    }

    // x, y, z: where Viewpoint places the character, also its round shadow on foot.
    public static void beforeCharacter(Frame frame, ModelSlotRenderData data, IsoGameCharacter chr, float x, float y, float z) {
        open = null;
        if (data.inVehicle) {
            BaseVehicle vehicle = chr.getVehicle();
            int link = vehicle == null ? -1 : VehicleLead.link(vehicle);
            if (link < 0) return;
            Item it = start(frame);
            if (it == null) return;
            moving(it, vehicle, link);
            VehicleLead.lead(vehicle, link);
            it.cx = frame.camX - (vehicle.getX() + VehicleLead.x);
            it.cz = frame.camY - (vehicle.getY() + VehicleLead.y);
        } else if (chr == IsoPlayer.players[0] && !ThirdPersonRig.seated) {
            Item it = start(frame);
            if (it == null) return;
            it.vx = ThirdPersonRig.velX;
            it.vy = ThirdPersonRig.velY;
            it.cx = 0.0f;
            it.cz = 0.0f;
            SceneData scene = frame.scene;
            float bx = -(x - frame.camX);
            float by = (z - frame.camZ) * ThirdPersonRig.LEVEL;
            float bz = -(y - frame.camY);
            for (int i = 0; i < scene.blobCount; i++) {
                if (scene.blobs[i * 4] == bx && scene.blobs[i * 4 + 1] == by && scene.blobs[i * 4 + 2] == bz) {
                    it.blob = i;
                    break;
                }
            }
        }
    }

    public static void after(Frame frame) {
        if (open != null) open.to = frame.scene.models.count();
        open = null;
    }

    // Render thread from here on.
    static Thread drawer;
    static Frame drawing;
    static long drawnAt;
    static float latency = Float.NaN, ahead;
    static float camDX, camDY;
    static Map<Object, float[]> drawn = new IdentityHashMap<>();
    static Map<Object, float[]> before = new IdentityHashMap<>();
    static final ArrayDeque<float[]> spare = new ArrayDeque<>();

    // Just before Viewpoint draws a frame; returns how far its things are moved on, in seconds.
    static float draw(Frame frame) {
        long now = System.nanoTime();
        drawer = Thread.currentThread();
        drawing = frame;
        ahead = 0.0f;
        camDX = 0.0f;
        camDY = 0.0f;
        Map<Object, float[]> t = before;
        before = drawn;
        drawn = t;
        for (float[] m : drawn.values()) spare.push(m);
        drawn.clear();
        Shot s = shots.get(frame);
        if (s == null || !s.on || broken) {
            latency = Float.NaN;
            drawnAt = 0L;
            return 0.0f;
        }
        float late = (now - s.captured) / 1.0e9f;
        float dt = drawnAt == 0L ? 0.0f : Math.min(0.1f, (now - drawnAt) / 1.0e9f);
        drawnAt = now;
        latency = Float.isNaN(latency) ? late : latency + (late - latency) * ThirdPersonRig.ease(dt, LATENCY_TIME);
        ahead = Math.max(-MOST, Math.min(MOST, late - latency));
        camDX = s.camVX * ahead;
        camDY = s.camVY * ahead;
        try {
            move(frame, s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            ahead = 0.0f;
            camDX = 0.0f;
            camDY = 0.0f;
            System.out.println("[ViewpointThirdPerson] vehicles and the body drawn at capture time, Viewpoint changed: " + e);
        }
        return ahead;
    }

    static boolean drawing(Frame frame) {
        return frame == drawing && Thread.currentThread() == drawer;
    }

    static void move(Frame frame, Shot s) throws ReflectiveOperationException {
        SceneData scene = frame.scene;
        ModelDraws models = scene.models;
        float[] values = (float[]) RECORDS.get(models);
        Object[] keys = (Object[]) KEYS.get(models);
        int[] flags = (int[]) FLAGS.get(models);
        int count = models.count();
        if (!s.saved) {
            int records = 0;
            for (int i = 0; i < s.count; i++) records += Math.max(0, Math.min(s.items[i].to, count) - s.items[i].from);
            if (s.kept.length < records * KEPT) s.kept = new float[records * KEPT * 2];
            int k = 0;
            for (int i = 0; i < s.count; i++) {
                Item it = s.items[i];
                for (int n = it.from; n < Math.min(it.to, count); n++, k += KEPT) {
                    System.arraycopy(values, n * VALUES, s.kept, k, 16);
                    System.arraycopy(values, n * VALUES + SPHERE, s.kept, k + 16, 3);
                }
                if (it.blob >= 0 && it.blob < scene.blobCount) System.arraycopy(scene.blobs, it.blob * 4, s.blobs, i * 3, 3);
            }
            s.saved = true;
        }
        int k = 0;
        for (int i = 0; i < s.count; i++) {
            Item it = s.items[i];
            // Scene axes: x = camX - world x, z = camY - world y, y up; the turn about the
            // physics' up axis is a turn about the scene's y by the same angle.
            float a = it.rate * ahead;
            float c = (float) Math.cos(a);
            float sn = (float) Math.sin(a);
            float ox = it.cx - (it.cx * c + it.cz * sn) - it.vx * ahead;
            float oz = it.cz - (-it.cx * sn + it.cz * c) - it.vy * ahead;
            for (int n = it.from; n < Math.min(it.to, count); n++, k += KEPT) {
                int r = n * VALUES;
                for (int col = 0; col < 16; col += 4) {
                    float x = s.kept[k + col];
                    float z = s.kept[k + col + 2];
                    float w = s.kept[k + col + 3];
                    values[r + col] = x * c + z * sn + ox * w;
                    values[r + col + 1] = s.kept[k + col + 1];
                    values[r + col + 2] = -x * sn + z * c + oz * w;
                    values[r + col + 3] = w;
                }
                float x = s.kept[k + 16];
                float z = s.kept[k + 18];
                values[r + SPHERE] = x * c + z * sn + ox;
                values[r + SPHERE + 2] = -x * sn + z * c + oz;
                Object key = keys[n];
                if (key == null) continue;
                // Viewpoint's last matrix is the last frame's as captured; motion is measured
                // against the last frame as drawn, whose view is the one drawn too.
                float[] last = before.get(key);
                if (last != null && (flags[n] & MOTION) != 0) System.arraycopy(last, 0, values, r + PREVIOUS, 16);
                float[] m = spare.isEmpty() ? new float[16] : spare.pop();
                System.arraycopy(values, r, m, 0, 16);
                drawn.put(key, m);
            }
            if (it.blob >= 0 && it.blob < scene.blobCount) {
                float x = s.blobs[i * 3];
                float z = s.blobs[i * 3 + 2];
                scene.blobs[it.blob * 4] = x * c + z * sn + ox;
                scene.blobs[it.blob * 4 + 2] = -x * sn + z * c + oz;
            }
        }
    }
}
