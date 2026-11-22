package uz.bekzod.fogmap;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Base64;
import android.util.JsonWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Export to FogMap JSON / GPX. */
public final class Backup {
    private Backup() {}

    // ---------- FogMap JSON (full backup) ----------

    public static void writeJson(Context c, OutputStream os) throws Exception {
        JsonWriter w = new JsonWriter(new OutputStreamWriter(new BufferedOutputStream(os), StandardCharsets.UTF_8));
        Db db = Db.get(c);
        w.beginObject();
        w.name("app").value("FogMap");
        w.name("version").value(2);
        w.name("points").beginArray();
        Cursor cur = db.allPointsCursor();
        try {
            while (cur.moveToNext()) {
                w.beginArray().value(cur.getLong(0)).value(cur.getDouble(1)).value(cur.getDouble(2)).value(cur.getDouble(3)).endArray();
            }
        } finally {
            cur.close();
        }
        w.endArray();
        w.name("places").beginArray();
        for (Db.Place p : db.places()) {
            if (p.status == Db.PENDING) continue;
            w.beginObject();
            w.name("lat").value(p.lat).name("lon").value(p.lon).name("status").value(p.status);
            w.name("name").value(p.name == null ? "" : p.name).name("cat").value(p.cat == null ? "" : p.cat);
            w.name("visits").value(p.visits).name("t").value(p.t);
            w.endObject();
        }
        w.endArray();
        w.name("photos").beginArray();
        for (Db.Photo p : db.photos()) {
            w.beginObject().name("lat").value(p.lat).name("lon").value(p.lon).name("t").value(p.t).endObject();
        }
        w.endArray();
        w.endObject();
        w.close();
    }

    // ---------- GPX (for other apps) ----------

    public static void writeGpx(Context c, OutputStream os) throws Exception {
        Writer w = new OutputStreamWriter(new BufferedOutputStream(os), StandardCharsets.UTF_8);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"FogMap\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        Cursor cur = Db.get(c).allPointsCursor();
        int day = -1;
        try {
            while (cur.moveToNext()) {
                int d = cur.getInt(4);
                if (d != day) {
                    if (day != -1) w.write("</trkseg></trk>\n");
                    w.write("<trk><name>" + Ui.dayShort(d) + "." + (d / 10000) + "</name><trkseg>\n");
                    day = d;
                }
                w.write(String.format(Locale.US, "<trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%s</time></trkpt>\n",
                        cur.getDouble(1), cur.getDouble(2), iso.format(new Date(cur.getLong(0)))));
            }
        } finally {
            cur.close();
        }
        if (day != -1) w.write("</trkseg></trk>\n");
        w.write("</gpx>\n");
        w.close();
    }

}
