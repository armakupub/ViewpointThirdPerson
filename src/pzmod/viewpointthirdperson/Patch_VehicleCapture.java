package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.vehicles.BaseVehicle;

// Main thread: which of Viewpoint's draw records a vehicle adds.
@Patch(className = "viewpoint.models.VehicleCapture", methodName = "capture")
public class Patch_VehicleCapture {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Frame frame, @Patch.Argument(1) ModelSlotRenderData data,
                             @Patch.Argument(2) BaseVehicle vehicle) {
        DrawTime.beforeVehicle(frame, data, vehicle);
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Frame frame) {
        DrawTime.after(frame);
    }
}
