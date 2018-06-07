/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.activity.event;

import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import com.activity.PerfdTestActivity;

public class EventActivity extends PerfdTestActivity {
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

    /** Function called by test to clear the input connection. */
    public void clearConnection() {
        InputMethodManager.getInstance().clearConnectionLocked();
    }
}
