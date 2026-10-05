package pzmod.viewpointthirdperson;

import java.lang.reflect.Field;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import viewpoint.core.View;
import viewpoint.input.ThirdPerson;
import zombie.characters.IsoPlayer;
import zombie.core.physics.WorldSimulation;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.vehicles.BaseVehicle;

// The game steps vehicle physics every 10 ms and draws the last step, so at higher frame rates
// the world judders past a camera hung from the vehicle. The seated vehicle, what it tows and
// their occupants are drawn, and the camera hung, moved on by the time not yet stepped. The game
// keeps no turn rate, so it is measured from step to step. Main thread.
public class VehicleLead {
    static final float STEP = 0.01f;
    static final int CHAIN = 3;
    static final Field LOCAL_TIME;
    static final Vector3f velocity = new Vector3f();
    static final Vector3f forward = new Vector3f();
    static final Vector3f angles = new Vector3f();
    static final Matrix4f turned = new Matrix4f();
    static final Matrix4f seat = new Matrix4f();
    static float x, y;
    // About the physics' up axis, which turns the other way from Look.yaw.
    static float turn;

    static final class Rate {
        BaseVehicle vehicle;
        int stepNo;
        float stepYaw;
        float rate;
    }

    static final Rate[] rates = new Rate[CHAIN];

    static {
        Field f = null;
        try {
            f = WorldSimulation.class.getDeclaredField("localTime");
            f.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[ViewpointThirdPerson] vehicles drawn on the physics steps, the game changed: " + e);
        }
        LOCAL_TIME = f;
        for (int i = 0; i < CHAIN; i++) rates[i] = new Rate();
    }

    // link: place in the chain, 0 for the seated vehicle.
    static void lead(BaseVehicle vehicle, int link) {
        x = 0.0f;
        y = 0.0f;
        turn = 0.0f;
        if (LOCAL_TIME == null) return;
        float t;
        try {
            t = Math.max(0.0f, Math.min(STEP, LOCAL_TIME.getFloat(WorldSimulation.instance))) + FrameClock.ahead;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        vehicle.getLinearVelocity(velocity);
        x = velocity.x * t;
        y = velocity.z * t;
        vehicle.getForwardVector(forward);
        float yaw = (float) Math.atan2(forward.x, forward.z);
        WorldSimulation sim = WorldSimulation.instance;
        int no = sim.getBulletFrameNo();
        Rate r = rates[link];
        if (vehicle != r.vehicle) {
            r.vehicle = vehicle;
            r.rate = 0.0f;
        } else if (no != r.stepNo) {
            r.rate = sim.periodSec > 0.0f ? VehicleCamera.wrap(yaw - r.stepYaw) / sim.periodSec : 0.0f;
        }
        r.stepNo = no;
        r.stepYaw = yaw;
        turn = r.rate * t;
    }

    static BaseVehicle seated() {
        IsoPlayer p = IsoPlayer.players[0];
        return View.enabled && ThirdPerson.active && ThirdPersonRig.ok && p != null ? p.getVehicle() : null;
    }

    // Place in the seated vehicle's chain, -1 if not in it.
    static int link(BaseVehicle vehicle) {
        BaseVehicle v = seated();
        for (int i = 0; v != null && i < CHAIN; i++, v = v.getVehicleTowing()) {
            if (v == vehicle) return i;
        }
        return -1;
    }

    public static void shift(ModelSlotRenderData data) {
        BaseVehicle vehicle = seated();
        int link = 0;
        while (vehicle != null && data.object != vehicle
                && !(data.inVehicle && data.character != null && data.character.getVehicle() == vehicle)) {
            vehicle = ++link < CHAIN ? vehicle.getVehicleTowing() : null;
        }
        if (vehicle == null) return;
        lead(vehicle, link);
        data.x += x;
        data.y += y;
        if (turn == 0.0f) return;
        // The game mirrors the physics' rotation in X to draw it, so the turn goes the other way.
        turned.rotationY(-turn);
        if (data.object == vehicle) {
            for (ModelInstanceRenderData part : data.modelData) turned.mul(part.xfrm, part.xfrm);
            turned.mul(data.vehicleTransform, data.vehicleTransform);
        } else {
            turned.mul(vehicle.vehicleTransform, seat).getEulerAnglesZYX(angles);
            data.vehicleAngleX = (float) Math.toDegrees(angles.x);
            data.vehicleAngleY = (float) Math.toDegrees(angles.y);
            data.vehicleAngleZ = (float) Math.toDegrees(angles.z);
        }
    }
}
