/*
 * Copyright (C) 2015 The Android Open Source Project
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

@Suppress("DEPRECATION")
/** 'android' extension 'com.android.test' projects.  */
interface TestExtension : BuildProperties, VariantOrExtensionProperties, VariantAwareProperties, OnDeviceTestProperties, ApkProperties, AndroidExtension {

    /** The Gradle path of the project that this test project tests.  */
    var targetProjectPath: String?

    // --- DEPRECATED

    @Deprecated("This is deprecated, test module can now test all flavors.")
    var targetVariant: String?
}
