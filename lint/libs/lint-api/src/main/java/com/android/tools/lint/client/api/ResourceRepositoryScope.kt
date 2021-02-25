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

package com.android.tools.lint.client.api

import com.android.ide.common.resources.ResourceRepository

/**
 * The various types or scopes of a [ResourceRepository] return from
 * [LintClient.getResources]
 */
enum class ResourceRepositoryScope {
    /**
     * Resources in the Android SDK (e.g. in the android: namespace)
     */
    ANDROID,

    /** Only resources in the current project. */
    PROJECT_ONLY,

    /**
     * Resources in the current project as well as resources in local
     * projects this project depends on (e.g. project + projects it
     * depends on)
     */
    LOCAL_DEPENDENCIES,

    /**
     * Like [LOCAL_DEPENDENCIES], but also includes resources from
     * remote libraries, e.g. in AAR files (e.g. project + projects it
     * depends on + any libraries these projects depend on). (Note that
     * the Android SDK resources are not included since these are in a
     * different namespace.)
     */
    ALL_DEPENDENCIES;

    /** Whether this scope includes local project dependencies. */
    fun includesDependencies(): Boolean {
        return this >= LOCAL_DEPENDENCIES
    }

    /** Whether this scope includes remote/AAR libraries. */
    fun includesLibraries(): Boolean {
        return this >= ALL_DEPENDENCIES
    }
}
