package uz.bekzod.fogmap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;

import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class Maps {
    public static final int SCHEME = 0, SATELLITE = 1, HYBRID = 2;
    public static final String[] NAMES = {"Схема", "Спутник", "Гибрид"};

    private static final Map<MapView, Integer> applied = new WeakHashMap<>();

    private Maps() {}

    /** Esri tiles use the {z}/{y}/{x} order. */
    static class Esri extends OnlineTileSourceBase {
        Esri(String name, String path, String ext, int maxZoom) {
            super(name, 0, maxZoom, 256, ext,
                    new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/" + path + "/MapServer/tile/"});
        }

        @Override
        public String getTileURLString(long index) {
            return getBaseUrl() + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index);
        }
    }

    // Sharp satellite photos for Uzbekistan exist only up to zoom 17; closer we enlarge zoom-17 photos
    // instead of asking for pictures that don't exist (that was the white screen).
    private static final ITileSource IMAGERY = new Esri("EsriImagery", "World_Imagery", ".jpg", 17);
    private static final ITileSource ROADS = new Esri("EsriRoads", "Reference/World_Transportation", ".png", 18);
    private static final ITileSource LABELS = new Esri("EsriLabels", "Reference/World_Boundaries_and_Places", ".png", 18);

    public static int type(Context c) {
        return S.p(c).getInt("map_type", SCHEME);
    }

    public static String credit(Context c) {
        return type(c) == SCHEME ? "© OpenStreetMap" : "© Esri, Maxar, Earthstar Geographics";
    }

    @SuppressLint("ClickableViewAccessibility")
    public static MapView create(Context c, boolean interactive) {
        MapView m = new MapView(c);
        m.setMultiTouchControls(interactive);
        m.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        m.setMinZoomLevel(3.0);
        m.setMaxZoomLevel(20.0);
        m.setTilesScaledToDpi(true);
        m.setDestroyMode(false);
        if (!interactive) m.setOnTouchListener((v, e) -> true);
        apply(m, c);
        return m;
    }

    /** Switches the map to the chosen look (scheme / satellite / hybrid). Safe to call often. */
    public static void apply(MapView m, Context c) {
        int t = type(c);
        Integer cur = applied.get(m);
        if (cur != null && cur == t) return;
        applied.put(m, t);

        List<Overlay> old = new ArrayList<>();
        for (Overlay o : m.getOverlays()) if (o instanceof TilesOverlay) old.add(o);
        for (Overlay o : old) {
            m.getOverlays().remove(o);
            try { o.onDetach(m); } catch (Exception ignored) { }
        }

        m.setTileSource(t == SCHEME ? TileSourceFactory.MAPNIK : IMAGERY);
        // While pictures load, show a dark background instead of a white grid.
        m.getOverlayManager().getTilesOverlay().setLoadingBackgroundColor(t == SCHEME ? 0xFFE8E4DC : 0xFF1A1F26);
        m.getOverlayManager().getTilesOverlay().setLoadingLineColor(t == SCHEME ? 0xFFE8E4DC : 0xFF1A1F26);
        if (t == HYBRID) {
            m.getOverlays().add(0, labels(c, LABELS));
            m.getOverlays().add(0, labels(c, ROADS));
        }
        m.invalidate();
    }

    private static TilesOverlay labels(Context c, ITileSource src) {
        TilesOverlay o = new TilesOverlay(new MapTileProviderBasic(c.getApplicationContext(), src), c.getApplicationContext());
        o.setLoadingBackgroundColor(Color.TRANSPARENT);
        o.setLoadingLineColor(Color.TRANSPARENT);
        return o;
    }
}
