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

package com.android.build.api;

import java.io.IOException;

/**
 * Updates the stable API file. Simply run inside Intellij to update the stable API file.
 */
public class StableApiUpdater {

    public static void main(String... args) throws IOException {
        String dirPath =
                "tools/base/build-system/gradle-api/src/test/resources/com/android/build/api";
        StableApiTest.getStableApiTester().updateFile(dirPath);
        StableApiTest.getIncubatingApiTester().updateFile(dirPath);
    }
}
