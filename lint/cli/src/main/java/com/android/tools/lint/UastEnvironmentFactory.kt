/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint

object UastEnvironmentFactory {

    /**
     * Creates a new [UastEnvironment.Configuration] that specifies
     * project structure, classpath, compiler flags, etc.
     */
    fun createConfiguration(): UastEnvironment.Configuration {
        return Fe10UastEnvironment.Configuration.create()
    }

    /**
     * Creates a new [UastEnvironment] suitable for analyzing both
     * Java and Kotlin code. You must still call [analyzeFiles]
     * before doing anything with PSI/UAST. When finished using the
     * environment, call [dispose].
     */
    fun create(
        config: UastEnvironment.Configuration
    ): UastEnvironment {
       return Fe10UastEnvironment.create(config)
    }
}
