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
package com.android.ide.common.gradle.model.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** The "build" folder paths per module. */
public class BuildFolderPaths {
    // Key: Module's Gradle path. Value: Path of the module's 'build' folder.
    @NonNull private final Map<String, File> myBuildFolderPathsByModule = new HashMap<>();

    /**
     * Stores the "build" folder path for the given module.
     *
     * @param moduleGradlePath module's gradle path.
     * @param buildFolder path to the module's build directory.
     */
    public void addBuildFolderMapping(@NonNull String moduleGradlePath, @NonNull File buildFolder) {
        myBuildFolderPathsByModule.put(moduleGradlePath, buildFolder);
    }

    /**
     * Finds the path of the "build" folder for the given module path.
     *
     * @param moduleGradlePath the given module path.
     * @return the path of the "build" folder for the given module path; or {@code null} if the path
     *     is not found or haven't been registered yet.
     */
    @Nullable
    public File findBuildFolderPath(@NonNull String moduleGradlePath) {
        return myBuildFolderPathsByModule.get(moduleGradlePath);
    }
}
