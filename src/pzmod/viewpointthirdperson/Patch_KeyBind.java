package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.platform.KeyBind;

// KeyChord is keyboard-only, so the cursor key's default, Viewpoint's hardcoded middle mouse
// button, is shown in place of "None". Viewpoint calls these while building its own keys, so
// they compare against a reference ViewpointKeys hands over instead of loading it.
public class Patch_KeyBind {
    public static final String MIDDLE = "Middle mouse button";
    public static volatile KeyBind cursor;

    @Patch(className = "viewpoint.platform.KeyBind", methodName = "bound")
    public static class Patch_bound {
        @Patch.OnExit
        public static void exit(@Patch.This Object self, @Patch.Return(readOnly = false) boolean ret) {
            if (!ret && self == cursor) ret = true;
        }
    }

    @Patch(className = "viewpoint.platform.KeyBind", methodName = "windowText")
    public static class Patch_windowText {
        @Patch.OnExit
        public static void exit(@Patch.This Object self, @Patch.Return(readOnly = false) String ret) {
            KeyBind c = cursor;
            if (self == c && c.chords().isEmpty()) ret = MIDDLE;
        }
    }
}
