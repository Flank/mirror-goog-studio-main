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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.JavaVersion

/** Java compilation options  */
interface CompileOptions : Initializable<CompileOptions> {
    /** @see .getSourceCompatibility
     */
    fun setSourceCompatibility(sourceCompatibility: Any)

    /**
     * Language level of the java source code.
     *
     *
     * Similar to what [
 * Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html) uses. Formats supported are:
     *
     *
     *  * `"1.6"`
     *  * `1.6`
     *  * `JavaVersion.Version_1_6`
     *  * `"Version_1_6"`
     *
     */
    val sourceCompatibility: JavaVersion

    /** @see .getTargetCompatibility
     */
    fun setTargetCompatibility(targetCompatibility: Any)

    /**
     * Version of the generated Java bytecode.
     *
     *
     * Similar to what [
 * Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html) uses. Formats supported are:
     *
     *
     *  * `"1.6"`
     *  * `1.6`
     *  * `JavaVersion.Version_1_6`
     *  * `"Version_1_6"`
     *
     */
    val targetCompatibility: JavaVersion

    /** Java source files encoding.  */
    /** @see .getEncoding
     */
    var encoding: String

    /**
     * Default java version, based on the target SDK. Set by the plugin, not meant to be used in
     * build files by users.
     */
    fun setDefaultJavaVersion(defaultJavaVersion: Any)

    val defaultJavaVersion: JavaVersion

    /**
     * Whether java compilation should use Gradle's new incremental model.
     *
     *
     * This may cause issues in projects that rely on annotation processing etc.
     */
    /** @see .getIncremental
     */
    var incremental: Boolean
}
