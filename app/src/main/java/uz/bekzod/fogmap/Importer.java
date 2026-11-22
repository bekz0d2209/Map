package uz.bekzod.fogmap;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports history from:
 *  - a FogMap backup (.json),
 *  - Google Timeline / Google Takeout (Records.json, Timeline.json, location-history.json),
 *  - GPX tracks from any app.
 * Every point goes through the normal tracker, so the fog, routes and statistics fill in.
 */
public class Importer {
    public interface Progress {
        void on(String text);
    }

    private static final Pattern ISO = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.(\\d+))?\\s*(Z|[+-]\\d{2}:?\\d{2})?");

    private final Context ctx;
    private final Db db;
    private final Tracker tracker;
    private final Progress progress;
    private final Set<Long> geoKeys = new LinkedHashSet<>();
    private SQLiteDatabase w;
    private int added = 0, seen = 0, inTx = 0;
    private long lastTime = 0;

    public Importer(Context ctx, Progress progress) {
        this.ctx = ctx.getApplicationContext();
        this.db = Db.get(ctx);
        this.progress = progress;
        this.tracker = new Tracker(ctx, new Tracker.Listener() {
            @Override
            public void onNewPlace(long placeId, int minutes) { }

            @Override
            public void onKeptPoint(double lat, double lon, long t) {
                if (geoKeys.size() < 400) geoKeys.add(Passport.key(lat, lon));
            }
        }, true);
    }

    /** Returns a short result text for the user. */
    public String run(InputStream raw) throws Exception {
        BufferedInputStream in = new BufferedInputStream(raw, 65536);
        in.mark(4096);
        byte[] head = new byte[4096];
        int n = in.read(head);
        in.reset();
        String h = n > 0 ? new String(head, 0, n, StandardCharsets.UTF_8).trim() : "";
        w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            if (h.startsWith("<")) gpx(in);
            else if (h.contains("\"app\"") && h.contains("FogMap")) fogmap(in);
            else google(in);
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        if (added == 0) return seen == 0 ? "В файле не нашлось точек" : "Все точки из файла уже были в истории";
        if (!geoKeys.isEmpty() && !S.offline(ctx)) {
            int i = 0;
            for (Long k : geoKeys) {
                i++;
                if (i % 10 == 0) progress.on("Определяю города… " + i + " из " + geoKeys.size());
                double lat = ((k >> 32) + 0.5) / 20.0, lon = ((int) (k & 0xffffffffL) + 0.5) / 20.0;
                Passport.check(ctx, lat, lon, lastTime > 0 ? lastTime : System.currentTimeMillis());
            }
        }
        return "Готово: добавлено точек " + added;
    }

    private void point(long t, double lat, double lon, float acc) {
        if (t <= 0 || Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 85 || Math.abs(lon) > 180) return;
        if (lat == 0 && lon == 0) return;
        seen++;
        if (db.hasPointAt(t)) return;
        tracker.onFix(t, lat, lon, acc > 0 ? acc : 15f, true);
        added++;
        if (++inTx >= 2000) {
            inTx = 0;
            w.setTransactionSuccessful();
            w.endTransaction();
            w.beginTransaction();
        }
        if (seen % 5000 == 0) progress.on("Загружаю историю… " + seen + " точек");
    }

    // ---------- FogMap backup ----------

    private void fogmap(InputStream in) throws Exception {
        JsonReader r = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            if ("points".equals(key)) {
                r.beginArray();
                while (r.hasNext()) {
                    r.beginArray();
                    long t = r.nextLong();
                    double lat = r.nextDouble(), lon = r.nextDouble(), acc = r.nextDouble();
                    while (r.hasNext()) r.skipValue();
                    r.endArray();
                    point(t, lat, lon, (float) acc);
                }
                r.endArray();
            } else if ("places".equals(key)) {
                r.beginArray();
                while (r.hasNext()) {
                    double lat = 0, lon = 0;
                    int status = Db.SAVED, visits = 1;
                    String name = "", cat = "";
                    long t = 0;
                    r.beginObject();
                    while (r.hasNext()) {
                        switch (r.nextName()) {
                            case "lat": lat = r.nextDouble(); break;
                            case "lon": lon = r.nextDouble(); break;
                            case "status": status = r.nextInt(); break;
                            case "name": name = r.nextString(); break;
                            case "cat": cat = r.nextString(); break;
                            case "visits": visits = r.nextInt(); break;
                            case "t": t = r.nextLong(); break;
                            default: r.skipValue();
                        }
                    }
                    r.endObject();
                    if (db.placeNear(lat, lon, 30) == null) db.insertPlace(lat, lon, status, name, cat, visits, t, 0);
                }
                r.endArray();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
    }

    // ---------- GPX ----------

    private void gpx(InputStream in) throws Exception {
        XmlPullParser x = Xml.newPullParser();
        x.setInput(in, null);
        double lat = Double.NaN, lon = Double.NaN;
        long t = 0;
        boolean inPt = false, inTime = false;
        for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
            if (e == XmlPullParser.START_TAG) {
                String name = x.getName();
                if ("trkpt".equals(name) || "rtept".equals(name) || "wpt".equals(name)) {
                    inPt = true;
                    t = 0;
                    try {
                        lat = Double.parseDouble(x.getAttributeValue(null, "lat"));
                        lon = Double.parseDouble(x.getAttributeValue(null, "lon"));
                    } catch (Exception ex) {
                        lat = Double.NaN;
                    }
                } else if (inPt && "time".equals(name)) {
                    inTime = true;
                }
            } else if (e == XmlPullParser.TEXT && inTime) {
                t = parseTime(x.getText().trim());
            } else if (e == XmlPullParser.END_TAG) {
                String name = x.getName();
                if ("time".equals(name)) inTime = false;
                if ("trkpt".equals(name) || "rtept".equals(name) || "wpt".equals(name)) {
                    inPt = false;
                    if (t > 0) {
                        lastTime = t;
                        point(t, lat, lon, 10f);
                    }
                }
            }
        }
    }

    // ---------- Google Timeline / Takeout (any of its formats) ----------

    private void google(InputStream in) throws Exception {
        JsonReader r = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        r.setLenient(true);
        value(r);
    }

    private void value(JsonReader r) throws Exception {
        JsonToken tk = r.peek();
        if (tk == JsonToken.BEGIN_OBJECT) object(r);
        else if (tk == JsonToken.BEGIN_ARRAY) {
            r.beginArray();
            while (r.hasNext()) value(r);
            r.endArray();
        } else r.skipValue();
    }

    private void object(JsonReader r) throws Exception {
        r.beginObject();
        double lat = Double.NaN, lon = Double.NaN;
        long time = 0;
        float acc = -1;
        List<double[]> coords = new ArrayList<>();
        while (r.hasNext()) {
            String k = r.nextName();
            JsonToken tk = r.peek();
            if (tk == JsonToken.STRING || tk == JsonToken.NUMBER) {
                String v = r.nextString();
                switch (k) {
                    case "latitudeE7": lat = num(v) / 1e7; break;
                    case "longitudeE7": lon = num(v) / 1e7; break;
                    case "lat": case "latitude": lat = num(v); break;
                    case "lng": case "lon": case "longitude": lon = num(v); break;
                    case "accuracy": acc = (float) num(v); break;
                    case "timestamp": case "timestampMs": case "time": case "startTime": {
                        long ts = parseTime(v);
                        if (ts > 0) { time = ts; lastTime = ts; }
                        break;
                    }
                    case "endTime": {
                        long ts = parseTime(v);
                        if (ts > 0 && time == 0) lastTime = ts;
                        break;
                    }
                    case "point": case "placeLocation": case "latLng": case "LatLng":
                    case "start": case "end": case "location": {
                        double[] c = coord(v);
                        if (c != null) coords.add(c);
                        break;
                    }
                    default:
                }
            } else if (tk == JsonToken.BEGIN_OBJECT || tk == JsonToken.BEGIN_ARRAY) {
                value(r);
            } else {
                r.skipValue();
            }
        }
        r.endObject();
        long t = time > 0 ? time : lastTime;
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) point(t, lat, lon, acc);
        for (int i = 0; i < coords.size(); i++) point(t + i * 1000L, coords.get(i)[0], coords.get(i)[1], acc);
    }

    private static double num(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return Double.NaN; }
    }

    /** "geo:41.55,60.63" or "41.5512°, 60.6312°". */
    static double[] coord(String s) {
        if (s == null) return null;
        s = s.replace("geo:", "").replace("°", "").trim();
        String[] p = s.split(",");
        if (p.length < 2) return null;
        double a = num(p[0]), b = num(p[1]);
        if (Double.isNaN(a) || Double.isNaN(b)) return null;
        return new double[]{a, b};
    }

    /** ISO-8601 with any fraction and zone, or milliseconds/seconds since 1970. */
    static long parseTime(String s) {
        if (s == null || s.isEmpty()) return 0;
        if (s.matches("\\d{9,}")) {
            long v = Long.parseLong(s);
            return v < 100_000_000_000L ? v * 1000 : v;
        }
        Matcher m = ISO.matcher(s);
        if (!m.find()) return 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), m.group(6) != null ? Integer.parseInt(m.group(6)) : 0);
        long t = c.getTimeInMillis();
        if (m.group(7) != null) {
            String f = (m.group(7) + "000").substring(0, 3);
            t += Integer.parseInt(f);
        }
        String z = m.group(8);
        if (z == null) {
            t -= TimeZone.getDefault().getOffset(t);      // no zone: local time
        } else if (!"Z".equals(z)) {
            int sign = z.startsWith("-") ? -1 : 1;
            String d = z.substring(1).replace(":", "");
            int off = Integer.parseInt(d.substring(0, 2)) * 60 + Integer.parseInt(d.substring(2, 4));
            t -= sign * off * 60000L;
        }
        return t;
    }
}
