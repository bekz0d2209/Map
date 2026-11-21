package uz.bekzod.fogmap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

/** Place labels, "?" markers, route start/end dots and your own position. */
public class MarksOverlay extends Overlay {
    private final Context ctx;
    private final float d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path tri = new Path();

    public volatile List<Db.Place> places = new ArrayList<>();
    public volatile double[] me;
    public volatile double[] start, end;

    public MarksOverlay(Context ctx) {
        super();
        this.ctx = ctx.getApplicationContext();
        d = ctx.getResources().getDisplayMetrics().density;
        text.setColor(Ui.WHITE);
        text.setTextSize(12 * d);
        text.setTypeface(Ui.MED);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Ui.WHITE);
    }

    @Override
    public void draw(Canvas c, MapView map, boolean shadow) {
        if (shadow) return;
        Projection p = map.getProjection();
        for (Db.Place pl : places) {
            if (pl.status == Db.IGNORED) continue;
            Point pt = p.toPixels(new GeoPoint(pl.lat, pl.lon), null);
            boolean pending = pl.status == Db.PENDING;
            label(c, pt.x, pt.y, pending ? "?" : pl.name, pending);
        }
        if (start != null) dot(c, p, start, Ui.OK);
        if (end != null) dot(c, p, end, Ui.ACC);
        double[] m = me;
        if (m != null) {
            Point pt = p.toPixels(new GeoPoint(m[0], m[1]), null);
            Point pr = p.toPixels(new GeoPoint(m[0] + S.radius(ctx) / 111320.0, m[1]), null);
            float rpx = Math.max(16 * d, Math.abs(pt.y - pr.y));
            fill.setColor(0x38FF6B4A);
            c.drawCircle(pt.x, pt.y, rpx, fill);
            fill.setColor(Ui.ACC);
            c.drawCircle(pt.x, pt.y, 8 * d, fill);
            stroke.setStrokeWidth(3 * d);
            c.drawCircle(pt.x, pt.y, 8 * d, stroke);
        }
    }

    private void dot(Canvas c, Projection p, double[] ll, int color) {
        Point pt = p.toPixels(new GeoPoint(ll[0], ll[1]), null);
        fill.setColor(color);
        c.drawCircle(pt.x, pt.y, 6 * d, fill);
        stroke.setStrokeWidth(2.5f * d);
        c.drawCircle(pt.x, pt.y, 6 * d, stroke);
    }

    private void label(Canvas c, float x, float y, String s, boolean pending) {
        if (s == null) s = "?";
        float h = 24 * d, w = pending ? h : text.measureText(s) + 22 * d;
        float top = y - h - 10 * d;
        rect.set(x - w / 2, top, x + w / 2, top + h);
        fill.setColor(pending ? Ui.ACC : Ui.SURF);
        c.drawRoundRect(rect, h / 2, h / 2, fill);
        tri.reset();
        tri.moveTo(x - 5 * d, top + h - 1);
        tri.lineTo(x + 5 * d, top + h - 1);
        tri.lineTo(x, y - 4 * d);
        tri.close();
        c.drawPath(tri, fill);
        float base = rect.centerY() - (text.ascent() + text.descent()) / 2;
        c.drawText(s, x, base, text);
    }
}
