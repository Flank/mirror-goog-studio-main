package com.android.tools.profiler.support.event;

import android.view.Window;

/**
 * Wrapper function around the window set and get callback funcitons.
 * <p>
 * This function stores off the callback the user, or activity sets on the window. This callback is then called after
 * the profiler has properly recorded the event.
 */
public final class EventWrapper {
    // Callback events are to be forwarded to
    private static Window.Callback mWrappedCallback;

    /**
     * Method to get wrapped callback, used by the applicaiton via code injection, and the profiler listener.
     *
     */
    public static Window.Callback getCallback() {
        return mWrappedCallback;
    }

    /**
     * Method to set the callback we are wrapping. Each place in the users application setCallback is called we inject
     * a call to this function instead so we can capture the callback as a redirect.
     *
     */
    public static void setCallback(Window.Callback callback) {
        mWrappedCallback = callback;
    }
}
