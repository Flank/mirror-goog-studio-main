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

  fun testIsGradleBuildFile() {
    val tempDir = Files.createTempDir()
    val buildFilePath = File(tempDir, FN_BUILD_GRADLE)
    assertFalse(isGradleScript(buildFilePath))
    writeToFile(buildFilePath, "")
    assertTrue(isGradleScript(buildFilePath))

    val buildFilePathKts = File(tempDir, FN_BUILD_GRADLE_KTS)
    assertFalse(isGradleScript(buildFilePathKts))
    writeToFile(buildFilePathKts, "")
    assertTrue(isGradleScript(buildFilePathKts))

    val renamedBuildFilePath = File(tempDir, "app.gradle")
    assertFalse(isGradleScript(renamedBuildFilePath))
    writeToFile(renamedBuildFilePath, "")
    assertTrue(isGradleScript(renamedBuildFilePath))

    val otherFilePath = File(tempDir, "some_file.txt")
    assertFalse(isGradleScript(otherFilePath))
    writeToFile(otherFilePath, "")
    assertFalse(isGradleScript(otherFilePath))
  }

  fun testIsDefaultGradleBuildFile() {
    val tempDir = Files.createTempDir()
    val buildFilePath = File(tempDir, FN_BUILD_GRADLE)
    assertFalse(isDefaultGradleBuildFile(buildFilePath))
    writeToFile(buildFilePath, "")
    assertTrue(isDefaultGradleBuildFile(buildFilePath))

    val buildFilePathKts = File(tempDir, FN_BUILD_GRADLE_KTS)
    assertFalse(isDefaultGradleBuildFile(buildFilePathKts))
    writeToFile(buildFilePathKts, "")
    assertTrue(isDefaultGradleBuildFile(buildFilePathKts))

    val renamedBuildFilePath = File(tempDir, "app.gradle")
    assertFalse(isDefaultGradleBuildFile(renamedBuildFilePath))
    writeToFile(renamedBuildFilePath, "")
    assertFalse(isDefaultGradleBuildFile(renamedBuildFilePath))

    val otherFilePath = File(tempDir, "some_file.txt")
    assertFalse(isDefaultGradleBuildFile(otherFilePath))
    writeToFile(otherFilePath, "")
    assertFalse(isDefaultGradleBuildFile(otherFilePath))
  }

  fun testIsGradleSettingsFile() {
    val tempDir = Files.createTempDir()
    val buildFilePath = File(tempDir, FN_SETTINGS_GRADLE)
    assertFalse(isGradleSettingsFile(buildFilePath))
    writeToFile(buildFilePath, "")
    assertTrue(isGradleSettingsFile(buildFilePath))

    val buildFilePathKts = File(tempDir, FN_SETTINGS_GRADLE_KTS)
    assertFalse(isGradleSettingsFile(buildFilePathKts))
    writeToFile(buildFilePathKts, "")
    assertTrue(isGradleSettingsFile(buildFilePathKts))

    val renamedBuildFilePath = File(tempDir, "app.gradle")
    assertFalse(isGradleSettingsFile(renamedBuildFilePath))
    writeToFile(renamedBuildFilePath, "")
    assertFalse(isGradleSettingsFile(renamedBuildFilePath))

    val otherFilePath = File(tempDir, "some_file.txt")
    assertFalse(isGradleSettingsFile(otherFilePath))
    writeToFile(otherFilePath, "")
    assertFalse(isGradleSettingsFile(otherFilePath))
  }
}