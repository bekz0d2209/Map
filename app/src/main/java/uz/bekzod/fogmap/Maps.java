package uz.bekzod.fogmap;

import android.annotation.SuppressLint;
import android.content.Context;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

public final class Maps {
    private Maps() {}

    @SuppressLint("ClickableViewAccessibility")
    public static MapView create(Context c, boolean interactive) {
        MapView m = new MapView(c);
        m.setTileSource(TileSourceFactory.MAPNIK);
        m.setMultiTouchControls(interactive);
        m.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        m.setMinZoomLevel(3.0);
        m.setMaxZoomLevel(20.0);
        m.setTilesScaledToDpi(true);
        m.setDestroyMode(false);
        if (!interactive) m.setOnTouchListener((v, e) -> true);
        return m;
    }
}
