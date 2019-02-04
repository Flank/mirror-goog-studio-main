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
package com.android.tools.usb

import com.android.tools.usb.parser.EmptyParser
import com.android.tools.usb.parser.LinuxParser
import com.android.tools.usb.parser.MacParser
import com.android.tools.usb.parser.OutputParser
import com.android.tools.usb.parser.WindowsParser

/**
 * Represents OS and holds information to support that platform.
 */
enum class Platform(val supported: Boolean, val command: String?) {
  Windows(true, "${System.getenv("WINDIR")}\\system32\\wbem\\wmic path CIM_LogicalDevice where \"DeviceID like 'USB\\\\%'\" get /value"),
  Linux(true, "lsusb -v"),
  Mac(true, "system_profiler SPUSBDataType -detailLevel mini"),
  Unknown(false, null);

  companion object Factory {
    fun currentOS(): Platform = currentOS(System.getProperty("os.name"))

    fun currentOS(os: String): Platform = when {
      os.startsWith("Windows") -> Windows
      os.startsWith("Linux") -> Linux
      os.startsWith("Mac") -> Mac
      else -> Unknown
    }
  }

  fun parser(): OutputParser = when (this) {
    Windows -> WindowsParser()
    Linux -> LinuxParser()
    Mac -> MacParser()
    else -> EmptyParser()
  }
}
