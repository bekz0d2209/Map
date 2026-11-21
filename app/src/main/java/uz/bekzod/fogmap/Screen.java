package uz.bekzod.fogmap;

import android.view.View;

public interface Screen {
    View view();
    void onShow();
    void onHide();
    /** Called every few seconds while the screen is visible. */
    void refresh();
}
