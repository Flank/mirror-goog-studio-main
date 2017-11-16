/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.flags.junit;

import com.android.annotations.Nullable;
import com.android.flags.Flag;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

/**
 * {@link Rule} which will ensure that a target {@link Flag} is reset back to its default value
 * after every test.
 *
 * <p>If you have code behind a flag, then that means your code can exhibit different behavior
 * depending on the flag's value. The easiest way to test all paths is to have multiple tests, with
 * each test changing the flag's value at the beginning of it.
 *
 * <pre>
 *   public class MyTest {
 *     @Rule
 *     public RestoreFlagRule{Boolean} myRestoreFlag = new RestoreFlagRule{}(StudioFlags.MY_FLAG);
 *
 *     @Test
 *     public void testFirstBranch() throws Exception {
 *       StudioFlags.MY_FLAG.override(false);
 *       ...
 *     }
 *
 *     @Test
 *     public void testSecondBranch() throws Exception {
 *       StudioFlags.MY_FLAG.override(true);
 *       ...
 *     }
 *   }
 * </pre>
 */
public class RestoreFlagRule<T> extends ExternalResource {
    private final Flag<T> myFlag;
    @Nullable private T myOriginalValue = null;

    public RestoreFlagRule(Flag<T> flag) {
        myFlag = flag;
    }

    @Override
    protected void before() throws Throwable {
        if (myFlag.isOverridden()) {
            myOriginalValue = myFlag.get();
        }
    }

    @Override
    protected void after() {
        if (myOriginalValue != null) {
            myFlag.override(myOriginalValue);
        } else {
            // If myOriginalValue was null, it meant the flag was not overridden in the first place.
            // Therefore, clear it here, just in case any test happened to set it.
            myFlag.clearOverride();
        }
    }
}
