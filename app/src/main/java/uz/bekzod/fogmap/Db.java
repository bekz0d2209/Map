package uz.bekzod.fogmap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Everything is stored only on the phone. */
public class Db extends SQLiteOpenHelper {
    public static final int PENDING = 0, SAVED = 1, IGNORED = 2;

    public static class Place {
        public long id, t;
        public double lat, lon;
        public int status, visits, minutes;
        public String name, cat;
    }

    public static class DayStat {
        public int day;
        public double meters;
        public long moving, first, last;
    }

    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "fogmap2.db", null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE points (t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, acc REAL, day INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_points_t ON points(t)");
        db.execSQL("CREATE INDEX idx_points_day ON points(day)");
        db.execSQL("CREATE TABLE stamps (x INTEGER NOT NULL, y INTEGER NOT NULL, t INTEGER NOT NULL, day INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_stamps_xy ON stamps(x, y)");
        db.execSQL("CREATE INDEX idx_stamps_day ON stamps(day)");
        db.execSQL("CREATE TABLE cells (x INTEGER NOT NULL, y INTEGER NOT NULL, day INTEGER NOT NULL, PRIMARY KEY (x, y)) WITHOUT ROWID");
        db.execSQL("CREATE INDEX idx_cells_day ON cells(day)");
        db.execSQL("CREATE TABLE day_stats (day INTEGER PRIMARY KEY, meters REAL NOT NULL, moving INTEGER NOT NULL, first_t INTEGER NOT NULL, last_t INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE places (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL NOT NULL, lon REAL NOT NULL, status INTEGER NOT NULL, name TEXT, cat TEXT, visits INTEGER NOT NULL DEFAULT 0, t INTEGER NOT NULL, minutes INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    // ---------- writing ----------

    public void insertPoint(long t, double lat, double lon, float acc, int day) {
        ContentValues v = new ContentValues();
        v.put("t", t);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("acc", acc);
        v.put("day", day);
        getWritableDatabase().insert("points", null, v);
    }

    public void insertStamp(long x, long y, long t, int day) {
        SQLiteStatement st = getWritableDatabase().compileStatement("INSERT INTO stamps (x, y, t, day) VALUES (?, ?, ?, ?)");
        st.bindLong(1, x);
        st.bindLong(2, y);
        st.bindLong(3, t);
        st.bindLong(4, day);
        st.executeInsert();
        st.close();
    }

    public void addCells(Collection<Long> keys, int day) {
        SQLiteDatabase d = getWritableDatabase();
        SQLiteStatement st = d.compileStatement("INSERT OR IGNORE INTO cells (x, y, day) VALUES (?, ?, ?)");
        d.beginTransaction();
        try {
            for (long key : keys) {
                st.bindLong(1, key >> 32);
                st.bindLong(2, key & 0xffffffffL);
                st.bindLong(3, day);
                st.executeInsert();
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
            st.close();
        }
    }

    public void addDayStat(int day, double meters, long movingSec, long t) {
        SQLiteDatabase d = getWritableDatabase();
        d.execSQL("INSERT OR IGNORE INTO day_stats (day, meters, moving, first_t, last_t) VALUES (?, 0, 0, ?, ?)",
                new Object[]{day, t, t});
        d.execSQL("UPDATE day_stats SET meters = meters + ?, moving = moving + ?, last_t = MAX(last_t, ?) WHERE day = ?",
                new Object[]{meters, movingSec, t, day});
    }

    // ---------- reading ----------

    public long totalCells() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "cells");
    }

    public long cellsOfDay(int day) {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "cells", "day = ?", new String[]{String.valueOf(day)});
    }

    private DayStat readDay(Cursor c) {
        DayStat s = new DayStat();
        s.day = c.getInt(0);
        s.meters = c.getDouble(1);
        s.moving = c.getLong(2);
        s.first = c.getLong(3);
        s.last = c.getLong(4);
        return s;
    }

    public DayStat dayStat(int day) {
        Cursor c = getReadableDatabase().rawQuery("SELECT day, meters, moving, first_t, last_t FROM day_stats WHERE day = ?",
                new String[]{String.valueOf(day)});
        try {
            return c.moveToFirst() ? readDay(c) : null;
        } finally {
            c.close();
        }
    }

    public List<DayStat> days(int limit) {
        List<DayStat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT day, meters, moving, first_t, last_t FROM day_stats ORDER BY day DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) out.add(readDay(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** Returns {lat, lon} pairs of one day in time order. */
    public List<double[]> dayPoints(int day) {
        List<double[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT lat, lon FROM points WHERE day = ? ORDER BY t",
                new String[]{String.valueOf(day)});
        try {
            while (c.moveToNext()) out.add(new double[]{c.getDouble(0), c.getDouble(1)});
        } finally {
            c.close();
        }
        return out;
    }

    /** {lat, lon, t} of the newest point, or null. */
    public double[] lastPoint() {
        Cursor c = getReadableDatabase().rawQuery("SELECT lat, lon, t FROM points ORDER BY t DESC LIMIT 1", null);
        try {
            return c.moveToFirst() ? new double[]{c.getDouble(0), c.getDouble(1), c.getLong(2)} : null;
        } finally {
            c.close();
        }
    }

    public double[] firstPoint() {
        Cursor c = getReadableDatabase().rawQuery("SELECT lat, lon FROM points ORDER BY t LIMIT 1", null);
        try {
            return c.moveToFirst() ? new double[]{c.getDouble(0), c.getDouble(1)} : null;
        } finally {
            c.close();
        }
    }

    public boolean hasPointAt(long t) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM points WHERE t = ? LIMIT 1", new String[]{String.valueOf(t)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    // ---------- places ----------

    private Place readPlace(Cursor c) {
        Place p = new Place();
        p.id = c.getLong(0);
        p.lat = c.getDouble(1);
        p.lon = c.getDouble(2);
        p.status = c.getInt(3);
        p.name = c.getString(4);
        p.cat = c.getString(5);
        p.visits = c.getInt(6);
        p.t = c.getLong(7);
        p.minutes = c.getInt(8);
        return p;
    }

    private static final String PLACE_COLS = "id, lat, lon, status, name, cat, visits, t, minutes";

    public List<Place> places() {
        List<Place> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT " + PLACE_COLS + " FROM places ORDER BY id DESC", null);
        try {
            while (c.moveToNext()) out.add(readPlace(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Place place(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT " + PLACE_COLS + " FROM places WHERE id = ?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readPlace(c) : null;
        } finally {
            c.close();
        }
    }

    public Place placeNear(double lat, double lon, double meters) {
        double dLat = meters / 111000.0 * 1.5, dLon = dLat * Geo.k(lat);
        Cursor c = getReadableDatabase().rawQuery("SELECT " + PLACE_COLS + " FROM places WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                new String[]{String.valueOf(lat - dLat), String.valueOf(lat + dLat), String.valueOf(lon - dLon), String.valueOf(lon + dLon)});
        Place best = null;
        double bd = meters;
        try {
            while (c.moveToNext()) {
                Place p = readPlace(c);
                double d = Geo.dist(lat, lon, p.lat, p.lon);
                if (d <= bd) { bd = d; best = p; }
            }
        } finally {
            c.close();
        }
        return best;
    }

    public long insertPlace(double lat, double lon, int status, String name, String cat, int visits, long t, int minutes) {
        ContentValues v = new ContentValues();
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("status", status);
        v.put("name", name);
        v.put("cat", cat);
        v.put("visits", visits);
        v.put("t", t);
        v.put("minutes", minutes);
        return getWritableDatabase().insert("places", null, v);
    }

    public void updatePlace(Place p) {
        ContentValues v = new ContentValues();
        v.put("status", p.status);
        v.put("name", p.name);
        v.put("cat", p.cat);
        v.put("visits", p.visits);
        v.put("minutes", p.minutes);
        getWritableDatabase().update("places", v, "id = ?", new String[]{String.valueOf(p.id)});
    }

    public void incVisits(long id) {
        getWritableDatabase().execSQL("UPDATE places SET visits = visits + 1 WHERE id = ?", new Object[]{id});
    }

    public void setMinutes(long id, int minutes) {
        getWritableDatabase().execSQL("UPDATE places SET minutes = ? WHERE id = ?", new Object[]{minutes, id});
    }

    public void deletePlace(long id) {
        getWritableDatabase().delete("places", "id = ?", new String[]{String.valueOf(id)});
    }

    public int pendingCount() {
        return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), "places", "status = 0", null);
    }

    /** "Дом" if saved, otherwise the very first recorded point. */
    public double[] home() {
        Cursor c = getReadableDatabase().rawQuery("SELECT lat, lon FROM places WHERE status = 1 AND (cat = 'Дом' OR name = 'Дом') ORDER BY visits DESC LIMIT 1", null);
        try {
            if (c.moveToFirst()) return new double[]{c.getDouble(0), c.getDouble(1)};
        } finally {
            c.close();
        }
        return firstPoint();
    }

    public Cursor allPointsCursor() {
        return getReadableDatabase().rawQuery("SELECT t, lat, lon, acc FROM points ORDER BY t", null);
    }
}
