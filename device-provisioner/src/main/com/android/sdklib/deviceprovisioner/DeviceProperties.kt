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
import com.android.sdklib.devices.Abi

/**
 * Stores various properties about a device, useful for naming the device.
 *
 * This is designed to be extended by subclasses through composition.
 */
interface DeviceProperties {

  val model: String?
  val manufacturer: String?
  val abi: Abi?
  /**
   * The Android API level. May include a codename if not a release version.
   *
   * This should be set for any device we can read properties from; if this is null, the device is
   * probably offline.
   */
  val androidVersion: AndroidVersion?
  /** The user-visible version of Android, like "7.1" or "11". */
  val androidRelease: String?

  /**
   * A string ideally unique to the device instance (e.g. serial number or emulator console port),
   * used for disambiguating this device from others with similar properties.
   */
  val disambiguator: String?

  open class Builder {

    var manufacturer: String? = null
    var model: String? = null
    var abi: Abi? = null
    var androidVersion: AndroidVersion? = null
    var androidRelease: String? = null
    var disambiguator: String? = null

    fun readCommonProperties(properties: Map<String, String>) {
      manufacturer = properties["ro.product.manufacturer"] ?: properties["ro.manufacturer"]
      model = properties["ro.product.model"] ?: properties["ro.model"]
      androidVersion =
        properties["ro.build.version.sdk"]?.let { it.toIntOrNull() }?.let { sdk ->
          AndroidVersion(sdk, properties["ro.build.version.codename"])
        }
      abi = properties["ro.product.cpu.abi"]?.let { Abi.getEnum(it) }
      androidRelease = properties["ro.build.version.release"]
    }

    fun buildBase(): DeviceProperties =
      Impl(manufacturer, model, androidVersion, abi, androidRelease, disambiguator)
  }

  class Impl(
    override val manufacturer: String?,
    override val model: String?,
    override val androidVersion: AndroidVersion?,
    override val abi: Abi?,
    override val androidRelease: String?,
    override val disambiguator: String?
  ) : DeviceProperties

  /** Default implementation of device title; may be overridden. */
  fun title(): String =
    when {
      manufacturer.isNullOrBlank() -> model ?: "Unknown"
      else ->
        when {
          model.isNullOrBlank() -> "$manufacturer Device"
          else -> "$manufacturer $model"
        }
    }
}
