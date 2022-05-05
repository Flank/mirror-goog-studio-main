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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.SettingsExtension

internal open class SettingsExtensionImpl: SettingsExtension {

    private var _compileSdk: Int? = null
    override var compileSdk: Int?
        get() = _compileSdk
        set(value) {
            _compileSdk = value
            _compileSdkPreview = null
            addOnName = null
            addOnVendor = null
            addOnApiLevel = null
        }

    private var _compileSdkPreview: String? = null
    override var compileSdkPreview: String?
        get() = _compileSdkPreview
        set(value) {
            _compileSdkPreview = value
            _compileSdk = null
            addOnName = null
            addOnVendor = null
            addOnApiLevel = null
        }

    internal val hasAddOn: Boolean
        get() = addOnName != null && addOnVendor != null && addOnApiLevel != null

    internal var addOnName: String? = null
    internal var addOnVendor: String? = null
    internal var addOnApiLevel: Int? = null

    override fun compileSdkAddon(vendor: String, name: String, version: Int) {
        addOnVendor = vendor
        addOnName = name
        addOnApiLevel = version
        _compileSdk = null
        _compileSdkPreview = null
    }

    private var _minSdk: Int? = null
    override var minSdk: Int?
        get() = _minSdk
        set(value) {
            _minSdk = value
            _minSdkPreview = null
        }

    private var _minSdkPreview: String? = null
    override var minSdkPreview: String?
        get() = _minSdkPreview
        set(value) {
            _minSdkPreview = value
            _minSdk = null
        }
}
