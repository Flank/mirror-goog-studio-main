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

import org.gradle.api.Action

/**
 * It is no longer possible to set the Jacoco version in the jacoco {} block. To update the
 * version of Jacoco without updating the android plugin, add a buildscript dependency on a
 * newer version, for example:
 *
 * <pre>
 * buildscript{
 * dependencies {
 * classpath 'org.jacoco:org.jacoco.core:0.7.5.201505241946'
 * }
 * }
</pre> *
 */
@Suppress("DEPRECATION")
@Deprecated("Add a buildScript classpath dependency on jacoco instead")
interface JacocoModule {

    @Deprecated("Add a buildScript classpath dependency on jacoco instead")
    fun jacoco(action: Action<JacocoOptions>)

    /** JaCoCo options.  */
    @Deprecated("Add a buildScript classpath dependency on jacoco instead")
    val jacoco: JacocoOptions

    @Deprecated("Add a buildScript classpath dependency on jacoco instead")
    interface JacocoOptions {
        @Deprecated("Add a buildScript classpath dependency on jacoco instead")
        var version: String
    }
}
