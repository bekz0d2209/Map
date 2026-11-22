package uz.bekzod.fogmap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Passport": the first visit to every city, region and country.
 * Names come from the phone's built-in geocoder (needs internet), asked at most once per ~5 km square.
 */
public final class Passport {
    public static final String CITY = "city", REGION = "region", COUNTRY = "country";

    private Passport() {}

    public static long key(double lat, double lon) {
        long a = (long) Math.floor(lat * 20), b = (long) Math.floor(lon * 20);
        return (a << 32) | (b & 0xffffffffL);
    }

    /** Looks the spot up if its square is new. Returns newly visited names ("Город Хива", …). Call off the main thread. */
    @SuppressWarnings("deprecation")
    public static List<String> check(Context c, double lat, double lon, long t) {
        List<String> fresh = new ArrayList<>();
        if (S.offline(c) || !Geocoder.isPresent()) return fresh;
        Db db = Db.get(c);
        long k = key(lat, lon);
        if (!db.claimGeoCell(k)) return fresh;
        try {
            List<Address> list = new Geocoder(c, new Locale("ru")).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return fresh;
            Address a = list.get(0);
            String country = a.getCountryName();
            String region = a.getAdminArea();
            String city = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
            if (country != null && db.addVisit(COUNTRY, country, country, t)) fresh.add("Новая страна: " + country);
            if (region != null && db.addVisit(REGION, region, country, t)) fresh.add("Новый регион: " + region);
            if (city != null && db.addVisit(CITY, city, country, t)) fresh.add("Новый город: " + city);
        } catch (Exception e) {
            db.releaseGeoCell(k);   // no internet now: try this square again later
        }
        return fresh;
    }

    public static void notify(Context c, List<String> fresh) {
        if (fresh.isEmpty()) return;
        try {
            Intent i = new Intent(c, MainActivity.class).putExtra("tab", 3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 3, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String title = fresh.get(fresh.size() - 1);
            StringBuilder text = new StringBuilder();
            for (String s : fresh) text.append(text.length() > 0 ? " · " : "").append(s);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, TrackerService.CH_NEWS) : new Notification.Builder(c);
            Notification n = nb.setContentTitle(title)
                    .setContentText(text.toString())
                    .setSmallIcon(R.drawable.ic_pin)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            c.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000) + 5000, n);
        } catch (Exception e) {
            App.log("Passport.notify", e);
        }
    }
}
