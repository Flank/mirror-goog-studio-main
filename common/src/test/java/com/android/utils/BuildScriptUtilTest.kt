/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.utils

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS
import com.android.utils.FileUtils.writeToFile
import com.google.common.io.Files
import junit.framework.TestCase
import java.io.File

class BuildScriptUtilTest : TestCase() {
  fun testFindGradleBuildFile() {
    val tempDir = Files.createTempDir()
    val buildFilePath = findGradleBuildFile(tempDir)
    assertEquals(File(tempDir, FN_BUILD_GRADLE), buildFilePath)
    writeToFile(buildFilePath, "")
    assertEquals(File(tempDir, FN_BUILD_GRADLE), buildFilePath)
  }

  fun testFindGradleBuildFileKts() {
    val tempDir = createTempDir()
    val ktsBuildFilePath = File(tempDir, FN_BUILD_GRADLE_KTS)
    writeToFile(ktsBuildFilePath, "")
    assertEquals(ktsBuildFilePath, findGradleBuildFile(tempDir))
  }

  fun testFindGradleSettingsFile() {
    val tempDir = Files.createTempDir()
    val buildFilePath = findGradleSettingsFile(tempDir)
    assertEquals(File(tempDir, FN_SETTINGS_GRADLE), buildFilePath)
    writeToFile(buildFilePath, "")
    assertEquals(File(tempDir, FN_SETTINGS_GRADLE), buildFilePath)
  }

  fun testFindGradleSettingsFileKts() {
    val tempDir = createTempDir()
    val ktsBuildFilePath = File(tempDir, FN_SETTINGS_GRADLE_KTS)
    writeToFile(ktsBuildFilePath, "")
    assertEquals(ktsBuildFilePath, findGradleSettingsFile(tempDir))
  }
}