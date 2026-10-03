package pzmod.viewpointthirdperson;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "viewpoint.platform.SettingsWindow", methodName = "update")
public class Patch_SettingsWindow {
    @Patch.OnExit
    public static void exit() {
        SettingsMigration.update();
    }
}
