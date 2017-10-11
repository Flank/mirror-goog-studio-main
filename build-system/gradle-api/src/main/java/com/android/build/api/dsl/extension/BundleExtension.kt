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

package com.android.build.api.dsl.extension

import org.gradle.api.Incubating

/**
 * Extension for bundle plugin.
 *
 * Allows developers to indicate which modules to bundle
 */
@Incubating
interface BundleExtension: AndroidExtension {

    /**
     * Bundles the given module.
     * @param moduleName the name of the module in the bundle
     * @param projectPath the path of the Gradle project
     * @param variantName the variant of the project to bundle
     */
    fun bundle(moduleName: String, projectPath: String, variantName: String)
}