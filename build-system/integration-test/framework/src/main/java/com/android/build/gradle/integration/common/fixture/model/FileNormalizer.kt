/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.model

import com.google.gson.JsonElement
import java.io.File

interface FileNormalizer {
    /**
     * Converts the given file path to a normalized string form. If the path is absolute and its
     * prefix matches a known directory, for example, project root, Android SDK, etc, the prefix is
     * replaced by a descriptive placeholder. Hence test assertion does not need to worry about
     * changes introduced by the platform or test session.
     *
     * In addition, the normalized string contains a suffix indicating the presence of the file:
     *
     * - `{!}`: the path does not exist
     * - `{F}`: the path refers to a file
     * - `{D}`: the path refers to a directory
     */
    fun normalize(file: File): String

    /**
     * Normalizes any strings that match the build environment in the given [JsonElement] so that
     * the returned result is invariant across build.
     *
     * Note that this method replace all matched strings and it could return non-sense result if
     * some well-known paths are too common.
     */
    fun normalize(value: JsonElement): JsonElement
}

