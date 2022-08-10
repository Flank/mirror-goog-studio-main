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
package com.android.sdklib.deviceprovisioner

import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.AndroidVersionException
import com.android.sdklib.devices.Abi

/**
 * Stores various properties about a device, useful for naming the device.
 *
 * This is designed to be extended by subclasses through composition.
 */
interface DeviceProperties {

    val model: String?
    val manufacturer: String?
    val apiLevel: AndroidVersion
    val abi: Abi?

    open class Builder {

        var manufacturer: String? = null
        var model: String? = null
        var apiLevel = AndroidVersion.DEFAULT
        var abi: Abi? = null

        fun readCommonProperties(properties: Map<String, String>) {
            manufacturer =
                properties["ro.product.manufacturer"]
                    ?: properties["ro.manufacturer"]
            model =
                properties["ro.product.model"]
                    ?: properties["ro.model"]
            apiLevel =
                properties["ro.build.version.sdk"]?.let {
                    try {
                        AndroidVersion(it)
                    } catch (e: AndroidVersionException) {
                        null
                    }
                } ?: AndroidVersion.DEFAULT
            abi = properties["ro.product.cpu.abi"]?.let { Abi.getEnum(it) }
        }

        fun buildBase(): DeviceProperties = Impl(manufacturer, model, apiLevel, abi)
    }

    class Impl(
        override val manufacturer: String?,
        override val model: String?,
        override val apiLevel: AndroidVersion,
        override val abi: Abi?
    ) : DeviceProperties

    /**
     * Default implementation of device title; may be overridden.
     */
    fun title(): String =
        when {
            manufacturer.isNullOrBlank() ->
                model ?: "Unknown"
            else -> when {
                model.isNullOrBlank() -> "$manufacturer Device"
                else -> "$manufacturer $model"
            }
        }
}
