/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.profiler.support.event;

import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * A callback wrapper class that handles window events. This allows us to capture MotionEvents,
 * KeyEvents, and general window operations for us to report back to the Android Studio.
 */
// TODO Have the Window profiler class send events back to Android studio.
public final class WindowProfilerCallback implements Window.Callback {

    private final Window.Callback myRedirectCallback;

    public WindowProfilerCallback(Window.Callback redirectCallback) {
        myRedirectCallback = redirectCallback;
    }

    // Native function to send touch event states via RPC to perfd.
    private native void sendTouchEvent(int state, long downTime);
    private native void sendKeyEvent(String text, long downTime);

    @Override
    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        String keyString = KeyEvent.keyCodeToString(keyEvent.getKeyCode());
        sendKeyEvent(keyString, keyEvent.getDownTime());
        if (myRedirectCallback != null) {
            return myRedirectCallback.dispatchKeyEvent(keyEvent);
        }
        return false;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent keyEvent) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.dispatchKeyShortcutEvent(keyEvent);
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        sendTouchEvent(motionEvent.getAction(), motionEvent.getDownTime());
        if (myRedirectCallback != null) {
            return myRedirectCallback.dispatchTouchEvent(motionEvent);
        }
        return false;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent motionEvent) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.dispatchTrackballEvent(motionEvent);
        }
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent motionEvent) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.dispatchGenericMotionEvent(motionEvent);
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.dispatchPopulateAccessibilityEvent(event);
        }
        return false;
    }

    @Override
    public View onCreatePanelView(int featureId) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onCreatePanelView(featureId);
        }
        return null;
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onCreatePanelMenu(featureId, menu);
        }
        return false;
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onPreparePanel(featureId, view, menu);
        }
        return false;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onMenuOpened(featureId, menu);
        }
        return false;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onMenuItemSelected(featureId, item);
        }
        return false;
    }

    @Override
    public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
        if (myRedirectCallback != null) {
            myRedirectCallback.onWindowAttributesChanged(params);
        }
    }

    @Override
    public void onContentChanged() {
        if (myRedirectCallback != null) {
            myRedirectCallback.onContentChanged();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (myRedirectCallback != null) {
            myRedirectCallback.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public void onAttachedToWindow() {
        if (myRedirectCallback != null) {
            myRedirectCallback.onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (myRedirectCallback != null) {
            myRedirectCallback.onDetachedFromWindow();
        }
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        if (myRedirectCallback != null) {
            myRedirectCallback.onPanelClosed(featureId, menu);
        }
    }

    @Override
    public boolean onSearchRequested() {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onSearchRequested();
        }
        return false;
    }

    @Override
    public boolean onSearchRequested(SearchEvent event) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onSearchRequested(event);
        }
        return false;
    }

    @Override
    public ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onWindowStartingActionMode(callback);
        }
        return null;
    }

    @Override
    public ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback,
            int type) {
        if (myRedirectCallback != null) {
            return myRedirectCallback.onWindowStartingActionMode(callback, type);
        }
        return null;
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        if (myRedirectCallback != null) {
            myRedirectCallback.onActionModeStarted(mode);
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        if (myRedirectCallback != null) {
            myRedirectCallback.onActionModeFinished(mode);
        }
    }
}