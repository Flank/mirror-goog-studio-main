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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class InstallVariantViaBundleTaskTest {

    @JvmField
    @Rule
    var tmp = TemporaryFolder()

    @Mock
    private lateinit var deviceConnector: DeviceConnector

    private lateinit var params: InstallVariantViaBundleTask.Params

    @Before
    @Throws(Exception::class)
    fun setUpMocks() {
        MockitoAnnotations.initMocks(this)
        `when`(deviceConnector.apiLevel).thenReturn(21)
        `when`(deviceConnector.name).thenReturn("fake_device")
        val project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
        params = object : InstallVariantViaBundleTask.Params() {
            override val adbExe: RegularFileProperty
                get() = project.objects.fileProperty().fileValue(File("adb.exe"))
            override val apkBundle: RegularFileProperty
                get() = project.objects.fileProperty().fileValue(File("bundle.aab"))
            override val timeOutInMs: Property<Int>
                get() = project.objects.property(Int::class.java).value(0)
            override val installOptions: ListProperty<String>
                get() = project.objects.listProperty(String::class.java)
            override val variantName: Property<String>
                get() = project.objects.property(String::class.java).value("variantName")
            override val minApiCodeName: Property<String?>
                get() = project.objects.property(String::class.java)
            override val minSdkVersion: Property<Int>
                get() = project.objects.property(Int::class.java).value(21)
            override val projectPath: Property<String>
                get() = project.objects.property(String::class.java).value("projectName")
            override val taskOwner: Property<String>
                get() = project.objects.property(String::class.java).value("taskOwner")
            override val workerKey: Property<String>
                get() = project.objects.property(String::class.java).value("workerKey")
            override val analyticsService: Property<AnalyticsService>
                get() = FakeGradleProperty(FakeNoOpAnalyticsService())
        }

    }

    @Test
    fun installSingle() {

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )

        val runnable = TestInstallRunnable(params, deviceConnector, outputPath)

        runnable.run()

        verify(deviceConnector, atLeastOnce()).name
        verify(deviceConnector, atLeastOnce()).apiLevel

        verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(outputPath.toFile()),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    @Test
    fun installMultiple() {

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val outputPath2 = Files.createTempFile(
            "extract-apk",
            ""
        )

        val runnable = TestInstallRunnable(params, deviceConnector, outputPath, outputPath2)

        runnable.run()

        verify(deviceConnector, atLeastOnce()).name
        verify(deviceConnector, atLeastOnce()).apiLevel

        verify(deviceConnector).installPackages(
            ArgumentMatchers.eq(listOf(outputPath.toFile(), outputPath2.toFile())),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    private class TestInstallRunnable(
        val params: InstallVariantViaBundleTask.Params,
        private val deviceConnector: DeviceConnector,
        private vararg val outputPaths: Path
    ) : InstallVariantViaBundleTask.InstallRunnable() {

        override fun createDeviceProvider(iLogger: ILogger): DeviceProvider =
            InstallVariantTaskTest.FakeDeviceProvider(ImmutableList.of(deviceConnector))

        override fun getApkFiles(device: DeviceConnector): List<Path> {
            return ImmutableList.copyOf(outputPaths)
        }

        override fun getParameters(): InstallVariantViaBundleTask.Params {
            return params
        }
    }
}
