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
package android.app;

import android.mock.MockWindowManager;
import android.view.Window;
import android.view.WindowManager;

/** Activity mock for tests */
public class Activity {

    private String myName;

    public Activity(String name) {
        myName = name;
    }

    public String getLocalClassName() {
        return myName;
    }

    public Window getWindow() {
        return new Window();
    }

    public WindowManager getWindowManager() {
        return new MockWindowManager();
    }
}
