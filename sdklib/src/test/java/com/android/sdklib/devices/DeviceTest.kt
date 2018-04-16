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

package com.android.sdklib.devices

import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakeRepoManager
import com.android.repository.testframework.MockFileOp
import com.android.resources.ScreenOrientation
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.NoErrorsOrWarningsLogger
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Dimension
import java.io.File

class DeviceTest {

  private val AVD_LOCATION = "/avd"
  private val SDK_LOCATION = "/sdk"

  private val fileOp = MockFileOp()
  val repoPackages = RepositoryPackages()
  val repoMgr = FakeRepoManager(File(SDK_LOCATION), repoPackages)
  val sdkHandler = AndroidSdkHandler(File(SDK_LOCATION), File(AVD_LOCATION), fileOp, repoMgr)
  val devMgr = DeviceManager.createInstance(sdkHandler, NoErrorsOrWarningsLogger())

  val qvgaPhone = devMgr.getDevice("2.7in QVGA", "Generic")
  val nexus5Phone = devMgr.getDevice("Nexus 5", "Google")
  val nexus5XPhone = devMgr.getDevice("Nexus 5X", "Google")
  val pixelPhone = devMgr.getDevice("pixel", "Google")
  val pixel2Phone = devMgr.getDevice("pixel_2", "Google")
  val pixelXLPhone = devMgr.getDevice("pixel_xl", "Google")

  @Test
  fun testGetDisplayName() {
    assertThat(qvgaPhone?.getDisplayName()).isEqualTo("2.7\" QVGA")
    assertThat(nexus5Phone?.getDisplayName()).isEqualTo("Nexus 5")
    assertThat(nexus5XPhone?.getDisplayName()).isEqualTo("Nexus 5X")
    assertThat(pixelPhone?.getDisplayName()).isEqualTo("Pixel")
    assertThat(pixel2Phone?.getDisplayName()).isEqualTo("Pixel 2")
    assertThat(pixelXLPhone?.getDisplayName()).isEqualTo("Pixel XL")
  }

  @Test
  fun testDeviceHasPlayStore() {
    assertThat(qvgaPhone?.hasPlayStore()).isFalse()
    assertThat(nexus5Phone?.hasPlayStore()).isTrue()
    assertThat(nexus5XPhone?.hasPlayStore()).isTrue()
    assertThat(pixelPhone?.hasPlayStore()).isTrue()
    assertThat(pixel2Phone?.hasPlayStore()).isTrue()
    assertThat(pixelXLPhone?.hasPlayStore()).isFalse()
  }

  @Test
  fun testGetManufacturer() {
    assertThat(qvgaPhone?.manufacturer).isEqualTo("Generic")
    assertThat(nexus5Phone?.manufacturer).isEqualTo("Google")
    assertThat(nexus5XPhone?.manufacturer).isEqualTo("Google")
    assertThat(pixelPhone?.manufacturer).isEqualTo("Google")
    assertThat(pixel2Phone?.manufacturer).isEqualTo("Google")
    assertThat(pixelXLPhone?.manufacturer).isEqualTo("Google")
  }

  @Test
  fun testGetScreenSize() {
    assertThat(qvgaPhone?.getScreenSize(ScreenOrientation.PORTRAIT)).isEqualTo(Dimension(240, 320))
    assertThat(nexus5Phone?.getScreenSize(ScreenOrientation.PORTRAIT)).isEqualTo(Dimension(1080, 1920))
    assertThat(nexus5XPhone?.getScreenSize(ScreenOrientation.PORTRAIT)).isEqualTo(Dimension(1080, 1920))
    assertThat(pixelPhone?.getScreenSize(ScreenOrientation.LANDSCAPE)).isEqualTo(Dimension(1920, 1080))
    assertThat(pixel2Phone?.getScreenSize(ScreenOrientation.LANDSCAPE)).isEqualTo(Dimension(1920, 1080))
    assertThat(pixelXLPhone?.getScreenSize(ScreenOrientation.LANDSCAPE)).isEqualTo(Dimension(2560, 1440))
  }

}
