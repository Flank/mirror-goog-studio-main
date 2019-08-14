/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.example.android.basicrenderscript;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for BasicRenderScript sample. */
@RunWith(AndroidJUnit4.class)
public class SampleTests {

    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    /** Test if the test fixture has been set up correctly. */
    @Test
    public void testPreconditions() {
        final MainActivity mainActivity = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        Assert.assertNotNull("mainActivity is null", mainActivity);

        mainActivity.addint(mainActivity.getCacheDir().toString());
    }
}
