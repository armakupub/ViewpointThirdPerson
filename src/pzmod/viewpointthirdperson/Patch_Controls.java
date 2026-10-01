package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.input.Controls", methodName = "middlePressed")
public class Patch_Controls {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly = false) boolean ret) {
        if (ViewpointKeys.CURSOR != null && !ViewpointKeys.CURSOR.chords().isEmpty()) ret = ViewpointThirdPerson.cursorPressed();
    }
}
