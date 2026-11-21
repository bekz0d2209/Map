package uz.bekzod.fogmap;

import java.util.Calendar;

/** Web-Mercator helpers. x grows east, y grows south (like the screen); both are positive. */
public final class Geo {
    public static final double R = 6378137.0;
    public static final double HALF = 20037508.342789244;
    /** Area grid cell in Mercator metres: ≈ 4×4 m on the ground at ~41°N (Uzbekistan). */
    public static final double CELL = 5.34;

    private Geo() {}

    public static double mx(double lon) {
        return Math.toRadians(lon) * R + HALF;
    }

    public static double my(double lat) {
        lat = Math.max(-85.0, Math.min(85.0, lat));
        return HALF - R * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
    }

    /** Mercator metres per real metre at this latitude. */
    public static double k(double lat) {
        return 1.0 / Math.cos(Math.toRadians(lat));
    }

    public static double dist(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Local calendar day as yyyymmdd. */
    public static int day(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    public static int today() {
        return day(System.currentTimeMillis());
    }

    public static Calendar cal(int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(day / 10000, (day / 100) % 100 - 1, day % 100, 12, 0, 0);
        return c;
    }

    public static int addDays(int day, int n) {
        Calendar c = cal(day);
        c.add(Calendar.DAY_OF_MONTH, n);
        return day(c.getTimeInMillis());
    }

    /** Monday of the week containing this day. */
    public static int weekStart(int day) {
        Calendar c = cal(day);
        int shift = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DAY_OF_MONTH, -shift);
        return day(c.getTimeInMillis());
    }
}
