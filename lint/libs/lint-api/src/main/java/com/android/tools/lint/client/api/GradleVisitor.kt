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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Location

/**
 * Visitor which can traverse a Gradle file and invoke the various methods
 * on a {@link GradleScanner}.
 *
 * This is only intended to be implemented by lint.
 */
open class GradleVisitor {
    /**
     * Manually visiting the build script. Returns true if it has fully handled
     * the file, otherwise returns true and some of the individual DSL checks below
     * are run.
     */
    open fun visitBuildScript(context: GradleContext, detectors: List<GradleScanner>) {
        // Empty implementation. This class is overridden in modules which have
        // access to Groovy (e.g. the Groovy parser itself from Gradle and the
        // test infrastructure, the Gradle PSI model in the IDE, etc.
    }

    @Deprecated(message = "unused", replaceWith = ReplaceWith(expression = "cookie"))
    open fun getPropertyKeyCookie(cookie: Any): Any = cookie

    @Deprecated(message = "unused", replaceWith = ReplaceWith(expression = "cookie"))
    open fun getPropertyPairCookie(cookie: Any): Any = cookie

    open fun getStartOffset(context: GradleContext, cookie: Any): Int = -1

    open fun createLocation(context: GradleContext, cookie: Any): Location = error("Not supported")
}
