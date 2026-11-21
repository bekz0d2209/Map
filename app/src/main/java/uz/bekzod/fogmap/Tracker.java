package uz.bekzod.fogmap;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;

/**
 * Turns GPS fixes into: points (for daily routes), stamps (for the fog),
 * area cells (for statistics) and "where are we?" questions when you stay in one place.
 * Not thread-safe: call it from one thread.
 */
public class Tracker {
    public interface Listener {
        void onNewPlace(long placeId, int minutes);
    }

    private static final float MAX_ACC_TRACK = 25f;
    private static final float MAX_ACC_STAY = 80f;
    private static final double STAY_RADIUS = 50.0;

    private final Context ctx;
    private final Db db;
    private final Listener listener;
    private final boolean importing;
    private final SharedPreferences sp;

    private double lastLat = Double.NaN, lastLon = Double.NaN;
    private long lastT = 0;
    private double stampX = Double.NaN, stampY = Double.NaN;

    private double aLat = Double.NaN, aLon = Double.NaN;
    private long aT = 0;
    private boolean asked = false;
    private long stayPlace = -1;
    private long lastFixSaved = 0;

    public Tracker(Context ctx, Listener listener, boolean importing) {
        this.ctx = ctx.getApplicationContext();
        this.db = Db.get(ctx);
        this.listener = listener;
        this.importing = importing;
        this.sp = S.p(ctx);
    }

    public void onFix(long t, double lat, double lon, float acc, boolean gps) {
        if (!importing) {
            if (acc <= MAX_ACC_STAY) stay(t, lat, lon);
            if (t - lastFixSaved > 10000) {
                lastFixSaved = t;
                sp.edit().putLong("fix_t", t).apply();
            }
        }
        if (gps && acc <= MAX_ACC_TRACK) track(t, lat, lon, acc);
    }

    // ---------- staying in one place ----------

    private void stay(long t, double lat, double lon) {
        if (Double.isNaN(aLat) || Geo.dist(aLat, aLon, lat, lon) > STAY_RADIUS) {
            aLat = lat;
            aLon = lon;
            aT = t;
            asked = false;
            stayPlace = -1;
            publish();
        } else {
            checkStay(t);
        }
    }

    /** Also called every 30 s by the service, so standing indoors without GPS still counts. */
    public void checkStay(long now) {
        if (importing || Double.isNaN(aLat)) return;
        long ms = now - aT;
        int askMin = S.ask(ctx);
        if (!asked && ms >= askMin * 60000L) {
            asked = true;
            Db.Place p = db.placeNear(aLat, aLon, 60);
            if (p == null) {
                stayPlace = db.insertPlace(aLat, aLon, Db.PENDING, null, null, 0, aT, (int) (ms / 60000));
                if (listener != null) listener.onNewPlace(stayPlace, (int) (ms / 60000));
            } else if (p.status == Db.SAVED) {
                db.incVisits(p.id);
                stayPlace = -1;
            } else if (p.status == Db.PENDING) {
                stayPlace = p.id;
            } else {
                stayPlace = -1;
            }
        }
        if (asked && stayPlace > 0) db.setMinutes(stayPlace, (int) (ms / 60000));
    }

    private void publish() {
        sp.edit().putLong("stay_t", aT).putFloat("stay_lat", (float) aLat).putFloat("stay_lon", (float) aLon).apply();
    }

    // ---------- route, fog, area ----------

    private void track(long t, double lat, double lon, float acc) {
        int day = Geo.day(t);
        boolean cont = !Double.isNaN(lastLat) && t - lastT <= 120000L && t > lastT && Geo.day(lastT) == day;
        double d = cont ? Geo.dist(lastLat, lastLon, lat, lon) : 0;
        if (cont && d < 3.0) {          // standing still: ignore GPS jitter
            lastT = t;
            return;
        }
        db.insertPoint(t, lat, lon, acc, day);
        if (cont && d <= 150.0) {
            long dtSec = Math.max(1, (t - lastT) / 1000);
            long moving = d / dtSec > 0.4 ? dtSec : 0;
            db.addDayStat(day, d, moving, t);
            int steps = Math.max(1, (int) Math.ceil(d));
            for (int i = 1; i <= steps; i++) {
                double f = (double) i / steps;
                stamp(lastLat + (lat - lastLat) * f, lastLon + (lon - lastLon) * f, t, day);
            }
        } else {
            db.addDayStat(day, 0, 0, t);
            stampX = Double.NaN;
            stamp(lat, lon, t, day);
        }
        lastLat = lat;
        lastLon = lon;
        lastT = t;
    }

    private void stamp(double lat, double lon, long t, int day) {
        double x = Geo.mx(lon), y = Geo.my(lat), k = Geo.k(lat);
        if (!Double.isNaN(stampX) && Math.hypot(x - stampX, y - stampY) < 4.0 * k) return;
        stampX = x;
        stampY = y;
        db.insertStamp(Math.round(x), Math.round(y), t, day);
        double r = S.radius(ctx) * 0.72 * k;
        int n = (int) Math.ceil(r / Geo.CELL);
        long cx = (long) Math.floor(x / Geo.CELL), cy = (long) Math.floor(y / Geo.CELL);
        HashSet<Long> keys = new HashSet<>();
        for (long i = cx - n; i <= cx + n; i++) {
            for (long j = cy - n; j <= cy + n; j++) {
                double dx = (i + 0.5) * Geo.CELL - x, dy = (j + 0.5) * Geo.CELL - y;
                if (dx * dx + dy * dy <= r * r) keys.add((i << 32) | (j & 0xffffffffL));
            }
        }
        db.addCells(keys, day);
    }
}
