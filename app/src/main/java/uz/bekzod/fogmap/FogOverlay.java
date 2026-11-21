package uz.bekzod.fogmap;

import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dark fog over the whole map with holes cut out where opened cells are. */
public class FogOverlay extends Overlay {
    private static final int FOG = 0xE60F1620;

    private static final class Runs {
        int[] x, y, w;
        int n, k, gen;
        long x0, x1, y0, y1;
    }

    private final MapView mv;
    private final Db db;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Paint clear = new Paint();
    private volatile Runs runs;
    private volatile boolean busy = false;
    private volatile int gen = 0;

    public FogOverlay(MapView mv, Db db) {
        super();
        this.mv = mv;
        this.db = db;
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clear.setAntiAlias(false);
        clear.setStyle(Paint.Style.FILL);
    }

    /** Re-read cells from the database (new ones may have been recorded). */
    public void refresh() {
        gen++;
        mv.postInvalidate();
    }

    @Override
    public void draw(Canvas c, MapView map, boolean shadow) {
        if (shadow) return;
        Projection p = map.getProjection();
        BoundingBox bb = p.getBoundingBox();
        double north = bb.getLatNorth(), south = bb.getLatSouth();
        double west = bb.getLonWest(), east = bb.getLonEast();
        double cx0 = Geo.cellX(west), cx1 = Geo.cellX(east);
        double cy0 = Geo.cellY(north), cy1 = Geo.cellY(south);
        if (!(cx1 > cx0) || !(cy1 > cy0)) return;

        Point a = p.toPixels(new GeoPoint(north, west), null);
        Point b = p.toPixels(new GeoPoint(south, east), null);
        double sx = (b.x - a.x) / (cx1 - cx0);
        double sy = (b.y - a.y) / (cy1 - cy0);
        if (!(sx > 0) || !(sy > 0)) return;

        // When zoomed out, merge cells into blocks so each block is at least ~2 px.
        int k = 1;
        while (k * sx < 2.0 && k < (1 << 20)) k <<= 1;

        long vx0 = (long) Math.floor(cx0), vx1 = (long) Math.ceil(cx1);
        long vy0 = (long) Math.floor(cy0), vy1 = (long) Math.ceil(cy1);
        Runs r = runs;
        if (r == null || r.k != k || r.gen != gen || vx0 < r.x0 || vx1 > r.x1 || vy0 < r.y0 || vy1 > r.y1) {
            query(vx0, vx1, vy0, vy1, k);
        }

        int save = c.saveLayer(null, null);
        c.drawColor(FOG);
        if (r != null) {
            double kk = r.k;
            for (int i = 0; i < r.n; i++) {
                float l = (float) (a.x + (r.x[i] * kk - cx0) * sx);
                float t = (float) (a.y + (r.y[i] * kk - cy0) * sy);
                float rr = (float) (l + r.w[i] * kk * sx);
                float bt = (float) (t + kk * sy);
                c.drawRect(l, t, rr, bt, clear);
            }
        }
        c.restoreToCount(save);
    }

    private void query(long vx0, long vx1, long vy0, long vy1, int k) {
        if (busy) return;
        busy = true;
        final int g = gen;
        long mx = Math.max(1, (vx1 - vx0) / 2), my = Math.max(1, (vy1 - vy0) / 2);
        final long x0 = vx0 - mx, x1 = vx1 + mx, y0 = vy0 - my, y1 = vy1 + my;
        ex.execute(() -> {
            Runs r;
            try {
                r = load(x0, x1, y0, y1, k);
            } catch (Exception e) {
                r = new Runs();
                r.x = new int[0]; r.y = new int[0]; r.w = new int[0];
                r.k = k; r.x0 = x0; r.x1 = x1; r.y0 = y0; r.y1 = y1;
            }
            r.gen = g;
            runs = r;
            busy = false;
            mv.postInvalidate();
        });
    }

    private Runs load(long x0, long x1, long y0, long y1, int k) {
        int cap = 4096, n = 0;
        int[] X = new int[cap], Y = new int[cap], W = new int[cap];
        String sql = "SELECT x/" + k + " AS gx, y/" + k + " AS gy FROM cells"
                + " WHERE x BETWEEN ? AND ? AND y BETWEEN ? AND ?"
                + " GROUP BY gx, gy ORDER BY gy, gx LIMIT 600000";
        Cursor cur = db.getReadableDatabase().rawQuery(sql, new String[]{
                Long.toString(x0), Long.toString(x1), Long.toString(y0), Long.toString(y1)});
        try {
            while (cur.moveToNext()) {
                int gx = cur.getInt(0), gy = cur.getInt(1);
                if (n > 0 && Y[n - 1] == gy && X[n - 1] + W[n - 1] == gx) {
                    W[n - 1]++;   // merge horizontal neighbours into one rectangle
                    continue;
                }
                if (n == cap) {
                    cap *= 2;
                    X = Arrays.copyOf(X, cap);
                    Y = Arrays.copyOf(Y, cap);
                    W = Arrays.copyOf(W, cap);
                }
                X[n] = gx; Y[n] = gy; W[n] = 1;
                n++;
            }
        } finally {
            cur.close();
        }
        Runs r = new Runs();
        r.x = X; r.y = Y; r.w = W; r.n = n; r.k = k;
        r.x0 = x0; r.x1 = x1; r.y0 = y0; r.y1 = y1;
        return r;
    }
}
