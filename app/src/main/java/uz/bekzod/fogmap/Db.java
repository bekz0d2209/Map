package uz.bekzod.fogmap;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/** Local-only storage: GPS points (for daily routes) and opened 2×2 m cells (for the fog). */
public class Db extends SQLiteOpenHelper {
    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "fogmap.db", null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE points (t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, acc REAL)");
        db.execSQL("CREATE INDEX idx_points_t ON points(t)");
        db.execSQL("CREATE TABLE cells (x INTEGER NOT NULL, y INTEGER NOT NULL, t INTEGER NOT NULL, PRIMARY KEY (x, y)) WITHOUT ROWID");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    public void insertPoint(long t, double lat, double lon, float acc) {
        ContentValues v = new ContentValues();
        v.put("t", t);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("acc", acc);
        getWritableDatabase().insert("points", null, v);
    }

    public void addCells(Iterable<Long> keys, long t) {
        SQLiteDatabase d = getWritableDatabase();
        SQLiteStatement st = d.compileStatement("INSERT OR IGNORE INTO cells (x, y, t) VALUES (?, ?, ?)");
        d.beginTransaction();
        try {
            for (long key : keys) {
                st.bindLong(1, key >> 32);
                st.bindLong(2, key & 0xffffffffL);
                st.bindLong(3, t);
                st.executeInsert();
            }
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
            st.close();
        }
    }

    public long cellCount() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), "cells");
    }
}
