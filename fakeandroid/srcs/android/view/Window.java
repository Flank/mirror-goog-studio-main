package android.view;

import android.view.accessibility.AccessibilityEvent;

public class Window {
    public interface Callback {
        boolean dispatchKeyEvent(KeyEvent event);

        boolean dispatchKeyShortcutEvent(KeyEvent event);

        boolean dispatchTouchEvent(MotionEvent event);

        boolean dispatchTrackballEvent(MotionEvent event);

        boolean dispatchGenericMotionEvent(MotionEvent event);

        boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event);

        View onCreatePanelView(int featureId);

        boolean onCreatePanelMenu(int featureId, Menu menu);

        boolean onPreparePanel(int featureId, View view, Menu menu);

        boolean onMenuOpened(int featureId, Menu menu);

        boolean onMenuItemSelected(int featureId, MenuItem item);

        void onWindowAttributesChanged(WindowManager.LayoutParams attrs);

        void onContentChanged();

        void onWindowFocusChanged(boolean hasFocus);

        void onAttachedToWindow();

        void onDetachedFromWindow();

        void onPanelClosed(int featureId, Menu menu);

        boolean onSearchRequested();

        boolean onSearchRequested(SearchEvent searchEvent);

        ActionMode onWindowStartingActionMode(ActionMode.Callback callback);

        ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type);

        void onActionModeStarted(ActionMode mode);

        void onActionModeFinished(ActionMode mode);

        default void onPointerCaptureChanged(boolean hasCapture) {};
    }

    private Window.Callback myCallback;

    public Window.Callback getCallback() {
        return myCallback;
    }

    public void setCallback(Window.Callback callback) {
        myCallback = callback;
    }
}
