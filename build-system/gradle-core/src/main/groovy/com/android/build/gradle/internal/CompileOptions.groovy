/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.google.common.base.Charsets
import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion

/**
 * Compilation options.
 */
@CompileStatic
class CompileOptions {
    @Nullable
    private JavaVersion sourceCompatibility

    @Nullable
    private JavaVersion targetCompatibility

    String encoding = Charsets.UTF_8.name()

    /**
     * Default Java version that will be used if the source and target compatibility levels will
     * not be set explicitly.
     */
    JavaVersion defaultJavaVersion = JavaVersion.VERSION_1_6

    boolean ndkCygwinMode = false

    /**
     * Language level of the source code.
     *
     * <p>Formats supported are :
     *      "1.6"
     *      1.6
     *      JavaVersion.Version_1_6
     *      "Version_1_6"
     */
    void setSourceCompatibility(@NonNull Object sourceCompatibility) {
        this.sourceCompatibility = convert(sourceCompatibility)
    }

    /**
     * Language level of the source code.
     *
     * <p>Similar to what <a href="http://www.gradle.org/docs/current/userguide/java_plugin.html">
     * Gradle Java plugin</a> uses.
     */
    @NonNull
    JavaVersion getSourceCompatibility() {
        sourceCompatibility?: defaultJavaVersion
    }

    /**
     * Language level of the target code.
     *
     * <p>Formats supported are :
     *      "1.6"
     *      1.6
     *      JavaVersion.Version_1_6
     *      "Version_1_6"
     */
    void setTargetCompatibility(@NonNull Object targetCompatibility) {
        this.targetCompatibility = convert(targetCompatibility)
    }

    /**
     * Version of the generated Java bytecode.
     *
     * <p>Similar to what <a href="http://www.gradle.org/docs/current/userguide/java_plugin.html">
     * Gradle Java plugin</a> uses.
     */
    @NonNull
    JavaVersion getTargetCompatibility() {
        targetCompatibility?: defaultJavaVersion
    }

    /**
     * Convert all possible supported way of specifying a Java version to {@link JavaVersion}
     * @param version the user provided java version.
     * @return {@link JavaVersion}
     * @throws {@link RuntimeException} if it cannot be converted.
     */
    @NonNull
    private JavaVersion convert(@NonNull Object version) {
        // for backward version reasons, we support setting strings like 'Version_1_6'
        if (version instanceof String
                && version.toString().toUpperCase().startsWith(
                'Version_'.toUpperCase())) {
            version = version.substring("Version".length() + 1).replace("_", ".")
        }
        return JavaVersion.toVersion(version)
    }
}
