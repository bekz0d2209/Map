package uz.bekzod.fogmap;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings. */
public final class S {
    private S() {}

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences("fogmap", Context.MODE_PRIVATE);
    }

    public static int radius(Context c) { return p(c).getInt("radius", 20); }
    public static int soft(Context c) { return p(c).getInt("soft", 55); }
    public static int dens(Context c) { return p(c).getInt("dens", 88); }
    public static int ask(Context c) { return p(c).getInt("ask", 5); }
    public static boolean eco(Context c) { return p(c).getBoolean("eco", false); }
    public static boolean offline(Context c) { return p(c).getBoolean("offline", false); }

    public static void put(Context c, String key, int v) { p(c).edit().putInt(key, v).apply(); }
    public static void putBool(Context c, String key, boolean v) { p(c).edit().putBoolean(key, v).apply(); }
}
