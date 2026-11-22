package uz.bekzod.fogmap;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** Checks and shortcuts for everything the phone needs to let FogMap work non-stop. */
public final class Perms {
    private Perms() {}

    public static boolean fine(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean background(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean battery(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** Starting tracking without the app on screen needs "Allow all the time". */
    public static boolean canTrack(Context c) {
        return fine(c) && background(c);
    }

    public static void openAppSettings(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    public static void askBattery(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            openAppSettings(c);
        }
    }

    /** Opens the maker's "Autostart" screen (Xiaomi, Huawei, Oppo, Vivo…), or the app settings. */
    public static void openAutostart(Context c) {
        String[][] screens = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
        };
        for (String[] s : screens) {
            try {
                Intent i = new Intent().setComponent(new ComponentName(s[0], s[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (c.getPackageManager().resolveActivity(i, 0) != null) {
                    c.startActivity(i);
                    return;
                }
            } catch (Exception ignored) { }
        }
        openAppSettings(c);
    }
}
