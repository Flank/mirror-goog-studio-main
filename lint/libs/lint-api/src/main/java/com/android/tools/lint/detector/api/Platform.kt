/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.google.common.annotations.Beta
import java.util.EnumSet

/**
 * Represents the different types of platforms that lint checks can apply
 * to. This concept is similar to [Scope], but you can think of [Scope]
 * as referring to the types of files that a lint check considers whereas
 * the [Platform] refers to how those files should be interpreted.
 *
 *
 * **NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.**
 */
@Beta
enum class Platform {
    /**
     * The analysis applies only to Android sources.
     * Issues with this scope will be ignored when lint is running on
     * non-Android sources.
     *
     * An example of where this is relevant is something like the
     * AssertionDetector, which looks for "assert" keywords in Java
     * and flags them as problematic, since on Android, assertions
     * are not enforced (they were unreliable on Dalvik and not
     * implemented on ART). However, when lint is run on on a
     * Java project, we don't want it to flag assertions.
     *
     * Finally note that a detector can always do on the fly
     * checks for this before reporting errors, e.g. via
     * [Project.isAndroidProject], but this scope filtering is
     * intended to limit up front analysis and to filter the
     * available issue list etc. Android specific detectors
     * should still check this in some cases, since in an
     * Android project a module can depend on a non-Android library
     * and when that project is being edited in the IDE, you may
     * or may not want the check to be included; for example,
     * the AssertionDetector should not be, but the API check
     * should (using the minSdkVersion from the main project.)
     */
    ANDROID,

    /**
     * Analysis applies to development with the JDK
     */
    JDK;

    // TODO: Consider other types of platforms here - cloud? web?

    companion object {
        /** Set used for issues which apply to Android */
        @JvmField
        val ANDROID_SET: EnumSet<Platform> = EnumSet.of(ANDROID)

        /** Set used for issues which apply development with JDK */
        @JvmField
        val JDK_SET: EnumSet<Platform> = EnumSet.of(JDK)

        @JvmField
        val UNSPECIFIED: EnumSet<Platform> = EnumSet.noneOf(Platform::class.java)
    }
}
