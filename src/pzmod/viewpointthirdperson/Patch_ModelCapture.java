package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.core.Frame;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

// Main thread: which of Viewpoint's draw records a character adds.
@Patch(className = "viewpoint.models.ModelCapture", methodName = "capture")
public class Patch_ModelCapture {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) Frame frame, @Patch.Argument(1) ModelSlotRenderData data,
                             @Patch.Argument(2) IsoGameCharacter chr, @Patch.Argument(5) float x,
                             @Patch.Argument(6) float y, @Patch.Argument(7) float z) {
        DrawTime.beforeCharacter(frame, data, chr, x, y, z);
    }

    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Frame frame) {
        DrawTime.after(frame);
    }
}
