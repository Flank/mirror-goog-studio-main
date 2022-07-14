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

import com.android.build.api.dsl.Execution
import com.android.build.api.dsl.SettingsExtension
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

internal open class SettingsExtensionImpl @Inject constructor(objectFactory: ObjectFactory): SettingsExtension {

    private var _compileSdk: Int? = null
    override var compileSdk: Int?
        get() = _compileSdk
        set(value) {
            _compileSdk = value
            _compileSdkPreview = null
            _addOnVendor = null
            _addOnName = null
            _addOnVersion = null
        }

    private var _compileSdkExtension: Int? = null
    override var compileSdkExtension: Int?
        get() = _compileSdkExtension
        set(value) {
            _compileSdkExtension = value
            _compileSdkPreview = null
            _addOnVendor = null
            _addOnName = null
            _addOnVersion = null
        }

    private var _compileSdkPreview: String? = null
    override var compileSdkPreview: String?
        get() = _compileSdkPreview
        set(value) {
            _compileSdkPreview = value
            _compileSdk = null
            _compileSdkExtension = null
            _addOnVendor = null
            _addOnName = null
            _addOnVersion = null
        }


    override fun compileSdkAddon(vendor: String, name: String, version: Int) {
        _addOnVendor = vendor
        _addOnName = name
        _addOnVersion = version
        _compileSdk = null
        _compileSdkExtension = null
        _compileSdkPreview = null
    }

    private var _addOnVendor: String? = null
    private var _addOnName: String? = null
    private var _addOnVersion: Int? = null

    override val addOnVendor: String?
        get() = _addOnVendor
    override val addOnName: String?
        get() = _addOnName
    override val addOnVersion: Int?
        get() = _addOnVersion

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

    override val execution: Execution = objectFactory.newInstance(ExecutionImpl::class.java, objectFactory)

    override fun execution(action: Action<Execution>) {
        action.execute(execution)
    }
    override fun execution(action: Execution.() -> Unit) {
        action.invoke(execution)
    }

    override var ndkVersion: String? = null
    override var ndkPath: String? = null
    override var buildToolsVersion: String? = null
}
