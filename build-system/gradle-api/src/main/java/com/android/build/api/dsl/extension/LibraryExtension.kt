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

import com.android.build.api.dsl.variant.LibraryVariant
import org.gradle.api.DomainObjectSet

/** 'android' extension 'com.android.library' projects.  */
interface LibraryExtension : BuildProperties, VariantAwareProperties, EmbeddedTestProperties, OnDeviceTestProperties, AndroidExtension {

    /** Name of the variant to publish.  */
    var defaultPublishConfig: String

    /** Aidl files to package in the aar.  */
    var aidlPackageWhiteList: Collection<String>

    /**
     * Returns the list of library variants. Since the collections is built after evaluation, it
     * should be used with [DomainObjectSet.all] to process future items.
     */
    @Deprecated("Use variants property")
    val libraryVariants: DomainObjectSet<LibraryVariant>

    // --- DEPRECATED

    @Deprecated("This always return false ")
    var packageBuildConfig: Boolean
}
