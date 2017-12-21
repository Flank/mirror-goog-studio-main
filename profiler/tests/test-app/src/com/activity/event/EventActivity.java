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
}
