/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.sdklib.AndroidVersion
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.logging.Logger
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.internal.verification.VerificationModeFactory.times
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File

class InstallVariantTaskTest {

    @JvmField
    @Rule
    var rule: MockitoRule = MockitoJUnit.rule()

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var kitkatDevice: DeviceConnector

    @Mock
    lateinit var lollipopDevice: DeviceConnector

    @Mock
    lateinit var logger: Logger

    @Before
    fun setUpMocks() {
        Mockito.`when`(lollipopDevice.apiLevel).thenReturn(21)
        Mockito.`when`(lollipopDevice.name).thenReturn("lollipop_device")
        Mockito.`when`(kitkatDevice.apiLevel).thenReturn(19)
        Mockito.`when`(kitkatDevice.name).thenReturn("kitkat_device")
    }

    @Test
    @Throws(Exception::class)
    fun checkPreLSingleApkInstall() {
        checkSingleApk(kitkatDevice)
    }

    @Test
    @Throws(Exception::class)
    fun checkPostKSingleApkInstall() {
        checkSingleApk(lollipopDevice)
    }

    @Test
    @Throws(Exception::class)
    @Ignore // Won't pass because the privacy sandbox sdk .apks file is not set up correctly.
    fun checkDependencyApkInstallation() {
        createMainApkListingFile()
        val listingFile = createDependencyApkListingFile()
        InstallVariantTask.install(
            "project",
            "variant",
            FakeDeviceProvider(ImmutableList.of(lollipopDevice)),
            AndroidVersion(1),
            FakeGradleDirectory(temporaryFolder.root),
            ImmutableSet.of(listingFile),
            ImmutableSet.of(),
            ImmutableList.of(),
            4000,
            logger
        )
        Mockito.verify(logger, times(3)).quiet("Installed on {} {}.", 1, "device")
        Mockito.verify(lollipopDevice, Mockito.atLeastOnce()).name
        Mockito.verify(lollipopDevice, Mockito.atLeastOnce()).apiLevel
        Mockito.verify(lollipopDevice, Mockito.atLeastOnce()).abis
        Mockito.verify(lollipopDevice, Mockito.atLeastOnce()).deviceConfig
        val inOrder = Mockito.inOrder(lollipopDevice)

        inOrder.verify(lollipopDevice).installPackage(
            ArgumentMatchers.eq(temporaryFolder.root.resolve("dependency1.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        inOrder.verify(lollipopDevice).installPackage(
            ArgumentMatchers.eq(temporaryFolder.root.resolve("dependency2.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        inOrder.verify(lollipopDevice).installPackage(
            ArgumentMatchers.eq(temporaryFolder.root.resolve("main.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        Mockito.verifyNoMoreInteractions(lollipopDevice)
    }

    private fun checkSingleApk(deviceConnector: DeviceConnector) {
        createMainApkListingFile()
        InstallVariantTask.install(
            "project",
            "variant",
            FakeDeviceProvider(ImmutableList.of(deviceConnector)),
            AndroidVersion(1),
            FakeGradleDirectory(temporaryFolder.root),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableList.of(),
            4000,
            logger
        )
        Mockito.verify(logger)
            .lifecycle(
                "Installing APK '{}' on '{}' for {}:{}",
                "main.apk",
                deviceConnector.name,
                "project",
                "variant"
            )
        Mockito.verify(logger).quiet("Installed on {} {}.", 1, "device")
        Mockito.verifyNoMoreInteractions(logger)
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).name
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).apiLevel
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).abis
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).deviceConfig
        Mockito.verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(temporaryFolder.root.resolve("main.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        Mockito.verifyNoMoreInteractions(deviceConnector)
    }

    internal class FakeDeviceProvider(private val devices: List<DeviceConnector>) : DeviceProvider() {

        private var state = State.NOT_READY

        override fun init() {
            check(state == State.NOT_READY) { "Can only go to READY from NOT_READY. Current state is $state" }
            state = State.READY
        }

        override fun terminate() {
            check(state == State.READY) { "Can only go to TERMINATED from READY. Current state is $state" }
            state = State.TERMINATED
        }

        override fun getName() = "FakeDeviceProvider"
        override fun getDevices() = devices
        override fun getTimeoutInMs() = 4000
        override fun isConfigured() = true

        private enum class State {
            NOT_READY, READY, TERMINATED
        }
    }

    private fun createMainApkListingFile() {
        val mainOutputFileApk = temporaryFolder.newFile("main.apk")
        temporaryFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText("""
{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "${mainOutputFileApk.name}"
    }
  ]
}""", Charsets.UTF_8)
    }

    private fun createDependencyApkListingFile(): File {
        val dependencyApk1 = temporaryFolder.newFile("dependency1.apk")
        val dependencyApk2 = temporaryFolder.newFile("dependency2.apk")
        return temporaryFolder.newFile("dependencyApkListingFile.txt").also {
            it.writeText("""
[{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test1",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "${dependencyApk1.name}"
    }
  ]
},{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test2",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "${dependencyApk2.name}"
    }
  ]
}]""", Charsets.UTF_8)
        }
    }

}

