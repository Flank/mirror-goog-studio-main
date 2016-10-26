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

package com.android.build.gradle.integration.common.utils;

import com.android.annotations.NonNull;
import java.io.File;
import java.io.IOException;

/**
 * Class used for changing Jack options in a build file.
 */
public class JackHelper {
    private JackHelper() {}

    /**
     * Enables Jack by adding jack options set to true to the given build file. If enabled = false
     * was specified for any of the flavours, it will override this line.
     *
     * @param buildFile the build.gradle file to which we will add the options
     * @throws IOException if there is a problem writing to buildFile
     */
    public static void enableJack(@NonNull File buildFile) throws IOException {
        TestFileUtils.appendToFile(buildFile,
                "\nandroid.defaultConfig.jackOptions.enabled true\n");

    }

    /**
     * Disables running Jack in process (Jack will run out of process). Also enables Jack by adding
     * "jackOptions.enabled true" to the build file. This is overwritten if there is any other
     * configuration specified for flavours.
     *
     * Note: By default, if there is enough memory available (~2 GB) and Jack is enabled then Jack
     * will run in process. Calling this method will make Jack run out of process regardless of the
     * amount of memory available.
     *
     * @param buildFile the build.gradle file in which we will disable Jack in process
     * @throws IOException if there is a problem writing to buildFile
     */
    public static void disableJackInProcess(@NonNull File buildFile) throws IOException {
        enableJack(buildFile);
        TestFileUtils.appendToFile(buildFile,
                "\nandroid.defaultConfig.jackOptions.jackInProcess false\n");
    }
}
