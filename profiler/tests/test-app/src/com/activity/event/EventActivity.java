package com.activity.event;

import android.app.Activity;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;

public class EventActivity extends Activity {
    public EventActivity() {
        super("EventActivity");
    }

    /** Function called by test to start capturing input and reporting events. */
    public void acceptInput() {
        InputMethodManager.getInstance().setIsAcceptingText(true);
    }

    /** Function called by test to stop capturing input and clear input connection. */
    public void blockInput() {
        InputMethodManager.getInstance().setIsAcceptingText(false);
    }

    /** Function called by test to print current status of connection. */
    public void printConnection() {
        InputConnection connection =
                InputMethodManager.getInstance().getConnectionWrapper().getConnection();
        System.out.println("Connection: " + connection);
        if (connection instanceof InputConnectionWrapper) {
            System.out.println(
                    "WrapperConnection: " + ((InputConnectionWrapper) connection).getConnection());
        }
    }

    /**
     * This function is called by the EventProfilerTest via refelection to assert the
     * InputConnectionWrapper is only wrapped a single time by the EventProfiler
     */
    public void printInputConnectionTreeDepth() {
        InputConnection connection =
                InputMethodManager.getInstance().getConnectionWrapper().getConnection();
        int depth = 0;
        while (connection instanceof InputConnectionWrapper && depth < 1000) {
            InputConnectionWrapper wrapper = (InputConnectionWrapper) connection;
            connection = wrapper.getConnection();
            depth++;
        }
        depth++;
        System.out.println("InputConnectionTree Depth: " + depth);
    }
}
