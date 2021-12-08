package com.codeages.livecloudsdk;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import java.util.List;

public class KeyboardHeightProvider extends PopupWindow implements ViewTreeObserver.OnGlobalLayoutListener {
    private final Activity mActivity;
    private final View rootView;
    private HeightListener listener;
    private float density = 1;

    public KeyboardHeightProvider(Activity activity) {
        super(activity);
        this.mActivity = activity;

        // Basic configuration
        rootView = new View(activity);
        setContentView(rootView);

        // Monitor global Layout changes
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        setBackgroundDrawable(new ColorDrawable(0));

        // Set width to 0 and height to full screen
        setWidth(0);
        setHeight(FrameLayout.LayoutParams.MATCH_PARENT);

        // Set keyboard pop-up mode
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);

        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        density = metrics.density;
    }

    public KeyboardHeightProvider init() {
        if (!isShowing()) {
            final View view = mActivity.getWindow().getDecorView();
            // Delay loading popupWindow, if not, error will be reported
            view.post(() -> showAtLocation(view, Gravity.NO_GRAVITY, 0, 0));
        }
        return this;
    }

    public KeyboardHeightProvider setHeightListener(HeightListener listener) {
        this.listener = listener;
        return this;
    }

    private int getScreenHeight() {
        Rect rect = new Rect();
        mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect.height();
    }

    private List<Rect> getCutoutInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WindowInsets windowInsets = mActivity.getWindow().getDecorView().getRootWindowInsets();
                if (windowInsets != null) {
                    return windowInsets.getDisplayCutout().getBoundingRects();
                } else {
                    return null;
                }
            } catch (Exception e) {
//                Log.e(TAG_CUTOUT, "error:" + e.toString());
            }
        }
        return null;
    }

    @Override
    public void onGlobalLayout() {
        Rect rect = new Rect();
        rootView.getWindowVisibleDisplayFrame(rect);

        int keyboardHeight = getScreenHeight() - rect.height();
        if (listener != null) {
            listener.onHeightChanged(keyboardHeight, density, getCutoutInfo());
        }
    }

    public interface HeightListener {
        void onHeightChanged(int height, float density, List<Rect> cutout);
    }
}
