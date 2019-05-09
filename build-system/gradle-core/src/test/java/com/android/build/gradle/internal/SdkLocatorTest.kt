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

package com.android.build.gradle.internal

import com.android.SdkConstants
import com.google.common.base.Charsets
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Properties

class SdkLocatorTest {

    @get:Rule
    val testFolder = TemporaryFolder()

    private lateinit var projectRootDir: File
    private lateinit var validSdkDirectory: File
    private lateinit var anotherValidSdkDirectory: File
    private lateinit var missingSdkDirectory: File

    @Before
    fun setUp() {
        projectRootDir = testFolder.newFolder("projectRoot")
        validSdkDirectory = testFolder.newFolder("AndroidSDK")
        anotherValidSdkDirectory = testFolder.newFolder("AnotherSDKDir")
        missingSdkDirectory = testFolder.root.resolve("MissingSDK")
    }

    @After
    fun tearDown() {
        SdkLocator.sdkTestDirectory = null
        SdkLocator.resetCache()
    }

    @Test
    fun noPropertiesSet() {
        val sourceSet = getLocationSourceSet()
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun sdkTestDirectory() {
        val sourceSet = getLocationSourceSet(testSdkDir = validSdkDirectory)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.TEST)
    }

    @Test
    fun sdkTestDirectory_missing() {
        val sourceSet = getLocationSourceSet(testSdkDir = missingSdkDirectory)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun localPropertiesSdkDir() {
        val sourceSet = getLocationSourceSet(localSdkDir = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun localPropertiesSdkDir_relative() {
        val sourceSet = getLocationSourceSet(localSdkDir = validSdkDirectory.relativeTo(projectRootDir).path)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun localPropertiesSdkDir_missing() {
        val sourceSet = getLocationSourceSet(localSdkDir = missingSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun localPropertiesAndroidDir() {
        val sourceSet = getLocationSourceSet(localAndroidDir = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.PLATFORM)
    }

    @Test
    fun localPropertiesAndroidDir_relative() {
        val sourceSet = getLocationSourceSet(localAndroidDir = validSdkDirectory.relativeTo(projectRootDir).path)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.PLATFORM)
    }

    @Test
    fun localPropertiesAndroidDir_missing() {
        val sourceSet = getLocationSourceSet(localAndroidDir = missingSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun envVariablesAndroidHome() {
        val sourceSet = getLocationSourceSet(envAndroidHome = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun envVariablesAndroidHome_relative() {
        val sourceSet = getLocationSourceSet(envAndroidHome = validSdkDirectory.relativeTo(projectRootDir).path)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun envVariablesAndroidHome_missing() {
        val sourceSet = getLocationSourceSet(envAndroidHome = missingSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun envVariablesSdkRoot() {
        val sourceSet = getLocationSourceSet(envSdkRoot = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun envVariablesSdkRoot_relative() {
        val sourceSet = getLocationSourceSet(envSdkRoot = validSdkDirectory.relativeTo(projectRootDir).path)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun envVariablesSdkRoot_missing() {
        val sourceSet = getLocationSourceSet(envSdkRoot = missingSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun systemPropertiesAndroidHome() {
        val sourceSet = getLocationSourceSet(systemAndroidHome = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun systemPropertiesAndroidHome_relative() {
        val sourceSet = getLocationSourceSet(systemAndroidHome = validSdkDirectory.relativeTo(projectRootDir).path)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun systemPropertiesAndroidHome_missing() {
        val sourceSet = getLocationSourceSet(systemAndroidHome = missingSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isNull()
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.MISSING)
    }

    @Test
    fun selectionPreference_sdkDirOverAndroidDir() {
        val sourceSet = getLocationSourceSet(
            localSdkDir = validSdkDirectory.absolutePath,
            localAndroidDir = anotherValidSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun selectionPreference_androidDirOverBadSdkDir() {
        val sourceSet = getLocationSourceSet(
            localSdkDir = missingSdkDirectory.absolutePath,
            localAndroidDir = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.PLATFORM)
    }

    @Test
    fun selectionPreference_androidHomeOverSdkRoot() {
        val sourceSet = getLocationSourceSet(
            envAndroidHome = validSdkDirectory.absolutePath,
            envSdkRoot = anotherValidSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun selectionPreference_sdkRootOverBadAndroidHome() {
        val sourceSet = getLocationSourceSet(
            envAndroidHome = missingSdkDirectory.absolutePath,
            envSdkRoot = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    @Test
    fun cachingBySourceSet() {
        val sourceSet = getLocationSourceSet(localSdkDir = validSdkDirectory.absolutePath)
        val sdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(sdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(sdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)

        validSdkDirectory.delete()
        val cachedSdkLocation = SdkLocator.getSdkLocation(sourceSet)
        assertThat(cachedSdkLocation.directory).isEqualTo(validSdkDirectory)
        assertThat(cachedSdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)

        val newSourceSet = getLocationSourceSet(localSdkDir = anotherValidSdkDirectory.absolutePath)
        val anotherSdkLocation = SdkLocator.getSdkLocation(newSourceSet)
        assertThat(anotherSdkLocation.directory).isEqualTo(anotherValidSdkDirectory)
        assertThat(anotherSdkLocation.type).isEquivalentAccordingToCompareTo(SdkType.REGULAR)
    }

    private fun getLocationSourceSet(
        testSdkDir: File? = null,
        localSdkDir: String? = null,
        localAndroidDir: String? = null,
        envAndroidHome: String? = null,
        envSdkRoot: String? = null,
        systemAndroidHome: String? = null): SdkLocationSourceSet {

        SdkLocator.sdkTestDirectory = testSdkDir

        val localProperties = Properties()
        localSdkDir?.let { localProperties.setProperty(SdkConstants.SDK_DIR_PROPERTY, it) }
        localAndroidDir?.let { localProperties.setProperty(SdkLocator.ANDROID_DIR_PROPERTY, it) }

        val envProperties = Properties()
        envAndroidHome?.let{ envProperties.setProperty(SdkConstants.ANDROID_HOME_ENV, it) }
        envSdkRoot?.let{ envProperties.setProperty(SdkConstants.ANDROID_SDK_ROOT_ENV, it) }

        val systemProperties = Properties()
        systemAndroidHome?.let { systemProperties.setProperty(SdkLocator.ANDROID_HOME_SYSTEM_PROPERTY, systemAndroidHome) }

        return SdkLocationSourceSet(projectRootDir, localProperties, envProperties, systemProperties)
    }

    private fun writeLocalPropertiesFile(properties: Properties) {
        val localProperties = File(projectRootDir, SdkConstants.FN_LOCAL_PROPERTIES)
        OutputStreamWriter(FileOutputStream(localProperties, false), Charsets.UTF_8).use {
            properties.store(it, "Generated by SdkLocatorTest")
        }
    }



}