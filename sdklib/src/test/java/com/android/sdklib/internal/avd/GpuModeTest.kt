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

package com.android.sdklib.internal.avd

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// These are pretty trivial tests, but they verify an
// interface between Studio and the emulator, so maybe
// they can catch a disconnect.

class GpuModeTest {

  @Test
  fun testGetGpuSetting() {
    assertThat(GpuMode.AUTO.gpuSetting).isEqualTo("auto")
    assertThat(GpuMode.HOST.gpuSetting).isEqualTo("host")
    assertThat(GpuMode.SWIFT.gpuSetting).isEqualTo("software")
    assertThat(GpuMode.OFF.gpuSetting).isEqualTo("off")
  }

  @Test
  fun testFromGpuSetting() {
    assertThat(GpuMode.fromGpuSetting("auto")).isEqualTo(GpuMode.AUTO)
    assertThat(GpuMode.fromGpuSetting("host")).isEqualTo(GpuMode.HOST)
    assertThat(GpuMode.fromGpuSetting("software")).isEqualTo(GpuMode.SWIFT)
    assertThat(GpuMode.fromGpuSetting("off")).isEqualTo(GpuMode.OFF)
    assertThat(GpuMode.fromGpuSetting("bogus")).isEqualTo(GpuMode.OFF)
  }
}
