package uz.bekzod.fogmap;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dark fog over the map with soft round holes where you have been. */
public class FogOverlay extends Overlay {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);

    private static final class Res {
        float[] x = new float[0], y = new float[0];
        int n, gen;
        long g, qx0, qx1, qy0, qy1;
        Integer day;
    }

    private final MapView mv;
    private final Context ctx;
    private final Db db;
    private final Paint cut = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Bitmap brush;
    private int brushSoft = -1;
    private volatile Res res;
    private volatile boolean busy = false;
    private volatile int gen = 0;
    private volatile Integer day;

    public FogOverlay(MapView mv, Context ctx, Integer day) {
        super();
        this.mv = mv;
        this.ctx = ctx.getApplicationContext();
        this.db = Db.get(ctx);
        this.day = day;
        cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    public void setDay(Integer d) {
        day = d;
        refresh();
    }

    public void refresh() {
        gen++;
        mv.postInvalidate();
    }

    private void ensureBrush(int soft) {
        if (soft == brushSoft && brush != null) return;
        Bitmap b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float stop = Math.max(0.001f, 1f - soft / 100f);
        p.setShader(new RadialGradient(64, 64, 64, new int[]{0xFF000000, 0xFF000000, 0x00000000},
                new float[]{0f, stop, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(64, 64, 64, p);
        brush = b;
        brushSoft = soft;
    }

    private static boolean same(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public void draw(Canvas c, MapView map, boolean shadow) {
        if (shadow) return;
        Projection p = map.getProjection();
        BoundingBox bb = p.getBoundingBox();
        double north = bb.getLatNorth(), south = bb.getLatSouth(), west = bb.getLonWest(), east = bb.getLonEast();
        double mx0 = Geo.mx(west), mx1 = Geo.mx(east), my0 = Geo.my(north), my1 = Geo.my(south);
        if (!(mx1 > mx0) || !(my1 > my0)) return;
        Point a = p.toPixels(new GeoPoint(north, west), null);
        Point b = p.toPixels(new GeoPoint(south, east), null);
        double sx = (b.x - a.x) / (mx1 - mx0), sy = (b.y - a.y) / (my1 - my0);
        if (!(sx > 0) || !(sy > 0)) return;

        double rM = S.radius(ctx) * Geo.k((north + south) / 2);
        float rpx = (float) (rM * sx);
        long g0 = Math.max(3, Math.round(Math.max(rM * 0.3, 2.0 / sx)));
        long g = 2;
        while (g < g0) g <<= 1;

        long vx0 = (long) Math.floor(mx0 - rM), vx1 = (long) Math.ceil(mx1 + rM);
        long vy0 = (long) Math.floor(my0 - rM), vy1 = (long) Math.ceil(my1 + rM);
        Res r = res;
        Integer d = day;
        if (r == null || r.g != g || r.gen != gen || !same(r.day, d) || vx0 < r.qx0 || vx1 > r.qx1 || vy0 < r.qy0 || vy1 > r.qy1) {
            query(vx0, vx1, vy0, vy1, g, d);
        }

        int alpha = Math.round(S.dens(ctx) * 2.55f);
        ensureBrush(S.soft(ctx));
        int save = c.saveLayer(null, null);
        c.drawColor((alpha << 24) | 0x0E141E);
        if (r != null && same(r.day, d)) {
            for (int i = 0; i < r.n; i++) {
                float X = (float) (a.x + (r.qx0 + r.x[i] - mx0) * sx);
                float Y = (float) (a.y + (r.qy0 + r.y[i] - my0) * sy);
                rect.set(X - rpx, Y - rpx, X + rpx, Y + rpx);
                c.drawBitmap(brush, null, rect, cut);
            }
        }
        c.restoreToCount(save);
    }

    private void query(long x0, long x1, long y0, long y1, long g, Integer d) {
        if (busy) return;
        busy = true;
        final int gn = gen;
        long ex = Math.max(1, (x1 - x0) / 2), ey = Math.max(1, (y1 - y0) / 2);
        final long qx0 = x0 - ex, qx1 = x1 + ex, qy0 = y0 - ey, qy1 = y1 + ey;
        IO.execute(() -> {
            Res r = new Res();
            try {
                load(r, qx0, qx1, qy0, qy1, g, d);
            } catch (Exception e) {
                r.n = 0;
            }
            r.qx0 = qx0; r.qx1 = qx1; r.qy0 = qy0; r.qy1 = qy1;
            r.g = g; r.gen = gn; r.day = d;
            res = r;
            busy = false;
            mv.postInvalidate();
        });
    }

    private void load(Res r, long x0, long x1, long y0, long y1, long g, Integer d) {
        String sql = "SELECT AVG(x), AVG(y) FROM stamps WHERE x BETWEEN ? AND ? AND y BETWEEN ? AND ?"
                + (d != null ? " AND day = " + d.intValue() : "")
                + " GROUP BY x / " + g + ", y / " + g + " LIMIT 400000";
        Cursor c = db.getReadableDatabase().rawQuery(sql, new String[]{
                Long.toString(x0), Long.toString(x1), Long.toString(y0), Long.toString(y1)});
        int cap = 1024, n = 0;
        float[] X = new float[cap], Y = new float[cap];
        try {
            while (c.moveToNext()) {
                if (n == cap) {
                    cap *= 2;
                    X = Arrays.copyOf(X, cap);
                    Y = Arrays.copyOf(Y, cap);
                }
                X[n] = (float) (c.getDouble(0) - x0);
                Y[n] = (float) (c.getDouble(1) - y0);
                n++;
            }
        } finally {
            c.close();
        }
        r.x = X;
        r.y = Y;
        r.n = n;
    }
}
