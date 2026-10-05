package pzmod.viewpointthirdperson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import viewpoint.platform.LiveSettings;
import zombie.ZomboidFileSystem;

// Viewpoint saves every setting, defaults too, so a changed default never reaches someone who
// played before. The version is kept in a file of our own: Viewpoint drops unknown keys on save.
public class SettingsMigration {
    static final String FILE = "ViewpointThirdPerson.ini";
    static final String VERSION = "settingsVersion";
    static final String PREFIX = "thirdPersonCamera.";
    static final float SAME = 1.0e-4f;

    static final class Change {
        final String from;
        final LiveSettings.Number setting;
        final float old;

        Change(LiveSettings.Number setting, float old) {
            this(null, setting, old);
        }

        // Replaces the setting saved under from; a changed value carries over as its offset from old.
        Change(String from, LiveSettings.Number setting, float old) {
            this.from = from;
            this.setting = setting;
            this.old = old;
        }
    }

    // STEPS[i] takes the settings from version i + 1 to i + 2; version 1 is 0.2.0 and before.
    static final Change[][] STEPS = {
            {
                    new Change(ThirdPersonRig.INDOOR_DISTANCE, 2.0f),
                    new Change(ThirdPersonRig.MELEE_SHOULDER, 0.15f),
                    new Change(ThirdPersonRig.FIREARM_DISTANCE, 2.0f),
                    new Change(VehicleCamera.PITCH, 8.0f),
            },
            {
                    new Change(VehicleCamera.DELAY, 1.0f),
            },
            {
                    new Change(PREFIX + "vehicleDistance", VehicleCamera.DISTANCE, 5.0f),
            },
    };
    static final int CURRENT = STEPS.length + 1;

    static boolean done;

    // After Viewpoint's settings window has run, which loads the settings the first time.
    public static void update() {
        if (done) return;
        try {
            if (!loaded()) return;
            done = true;
            migrate();
        } catch (ReflectiveOperationException | RuntimeException | java.io.IOException e) {
            done = true;
            System.out.println("[ViewpointThirdPerson] settings not migrated: " + e);
        }
    }

    static void migrate() throws ReflectiveOperationException, java.io.IOException {
        File file = new File(ZomboidFileSystem.instance.getCacheDir(), FILE);
        Properties ours = new Properties();
        if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                ours.load(in);
            }
        }
        Properties saved = saved();
        int version;
        try {
            version = Integer.parseInt(ours.getProperty(VERSION, "").trim());
        } catch (NumberFormatException e) {
            version = played(saved) ? 1 : CURRENT;
        }
        if (version >= CURRENT) {
            if (!file.isFile()) write(file, ours);
            return;
        }
        Field key = LiveSettings.Setting.class.getDeclaredField("key");
        Field fallback = LiveSettings.Number.class.getDeclaredField("fallback");
        key.setAccessible(true);
        fallback.setAccessible(true);
        int moved = 0;
        for (int step = version - 1; step < STEPS.length; step++) {
            for (Change c : STEPS[step]) {
                if (c.from != null) {
                    float was = number(saved.getProperty(c.from));
                    if (Float.isNaN(was) || Math.abs(was - c.old) <= SAME) continue;
                    c.setting.set(was - c.old);
                    moved++;
                    continue;
                }
                if (saved.getProperty((String) key.get(c.setting)) == null) continue;
                if (Math.abs(c.setting.get() - c.old) > SAME) continue;
                c.setting.set(fallback.getFloat(c.setting));
                moved++;
            }
        }
        if (moved > 0) {
            Method save = LiveSettings.class.getDeclaredMethod("save");
            save.setAccessible(true);
            save.invoke(null);
        }
        write(file, ours);
        System.out.println("[ViewpointThirdPerson] settings from version " + version + " to " + CURRENT + ", " + moved + " moved to new defaults");
    }

    static float number(String s) {
        if (s == null) return Float.NaN;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    static boolean played(Properties saved) {
        for (String k : saved.stringPropertyNames()) if (k.startsWith(PREFIX)) return true;
        return false;
    }

    static void write(File file, Properties ours) throws java.io.IOException {
        ours.setProperty(VERSION, Integer.toString(CURRENT));
        try (FileOutputStream out = new FileOutputStream(file)) {
            ours.store(out, "Third Person Camera for Project Viewpoint");
        }
    }

    static boolean loaded() throws ReflectiveOperationException {
        Field f = LiveSettings.class.getDeclaredField("loaded");
        f.setAccessible(true);
        return f.getBoolean(null);
    }

    static Properties saved() throws ReflectiveOperationException {
        Field f = LiveSettings.class.getDeclaredField("saved");
        f.setAccessible(true);
        Properties p = (Properties) f.get(null);
        return p != null ? p : new Properties();
    }
}
