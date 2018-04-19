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

package com.android.build.gradle.internal.variant;

import com.android.annotations.Nullable;
import org.gradle.api.Task;

/** Provider of tasks reference. This allow for simpler dependencies that the whole VariantScope */
public interface TaskContainer {

    enum TaskKind {
        // Task the process all variant's outputs.
        PROCESS_ANDROID_RESOURCES,
        // Task the process all variant's outputs.
        PROCESS_MANIFEST,
        // Task to package all variant's outputs.
        PACKAGE_ANDROID_ARTIFACT,
        // Task to assemble the variant and all its output.
        ASSEMBLE,
        // Task to assemble the variant and all its output.
        BUNDLE
    }

    void addTask(TaskKind taskKind, Task task);

    @Nullable
    Task getTaskByKind(TaskKind name);

    @Nullable
    <T extends Task> T getTaskByType(Class<T> taskType);
}
