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

import com.android.build.api.dsl.InitializableObject
import java.io.File

/** DSL object for configuring postprocessing: removing dead code, obfuscating etc.  */
interface PostprocessingFiles {

    /**
     * The ProGuard configuration files.
     *
     * There are 2 default rules files
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     *
     * They are located in the SDK. Using `getDefaultProguardFile(String filename)`
     * will return the full path to the files. They are identical except for enabling optimizations.
     */
    var proguardFiles: MutableList<Any>

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    var testProguardFiles: MutableList<Any>

    /**
     * Specifies proguard rule files to be included in the published AAR.
     *
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    var consumerProguardFiles: MutableList<Any>
}
