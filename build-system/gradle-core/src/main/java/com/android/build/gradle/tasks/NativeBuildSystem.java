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

package com.android.build.gradle.tasks;

import static com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_CMAKE;
import static com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_GRADLE;
import static com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_NDK_BUILD;
import static com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_UNKNOWN;

import com.android.annotations.NonNull;

/**
 * Enumeration and descriptive metadata for the different external native build system types.
 */
public enum NativeBuildSystem {
    UNKNOWN(BUILD_SYSTEM_UNKNOWN),
    GRADLE(BUILD_SYSTEM_GRADLE),
    CMAKE(BUILD_SYSTEM_CMAKE),
    NDK_BUILD(BUILD_SYSTEM_NDK_BUILD);

    private final String name;

    NativeBuildSystem(String name) {
        this.name = name;
    }

    /**
     * Returns name of the build system. Not called getName(...) because that conflicts confusingly
     * with Kotlin's Enum::name.
     */
    @NonNull
    public String getTag() {
        return name;
    }
}
