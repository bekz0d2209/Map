package uz.bekzod.fogmap;

/** Web-Mercator grid helpers. One cell ≈ 2×2 m on the ground at Uzbekistan latitudes (~41°N). */
public final class Geo {
    public static final double R = 6378137.0;
    public static final double HALF = 20037508.342789244;
    /** Cell size in Mercator metres: 2 m / cos(41.5°) ≈ 2.67 → 2×2 m real at ~41°N. */
    public static final double CELL = 2.67;

    private Geo() {}

    public static double mercX(double lon) {
        return Math.toRadians(lon) * R;
    }

    public static double mercY(double lat) {
        lat = Math.max(-85.0, Math.min(85.0, lat));
        return R * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
    }

    /** Continuous cell coordinate (x grows east). */
    public static double cellX(double lon) {
        return (mercX(lon) + HALF) / CELL;
    }

    /** Continuous cell coordinate (y grows south, like the screen). */
    public static double cellY(double lat) {
        return (HALF - mercY(lat)) / CELL;
    }

    public static long key(double lat, double lon) {
        long x = (long) Math.floor(cellX(lon));
        long y = (long) Math.floor(cellY(lat));
        return (x << 32) | (y & 0xffffffffL);
    }

    /** Distance in metres. */
    public static double dist(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
