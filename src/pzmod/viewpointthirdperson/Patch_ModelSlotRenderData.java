package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

@Patch(className = "zombie.core.skinnedmodel.model.ModelSlotRenderData", methodName = "init")
public class Patch_ModelSlotRenderData {
    @Patch.OnExit
    public static void exit(@Patch.This Object self) {
        VehicleLead.shift((ModelSlotRenderData) self);
    }
}
