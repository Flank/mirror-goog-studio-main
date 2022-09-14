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
import com.android.builder.testing.api.DeviceConfigProviderImpl
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
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

    private lateinit var project: Project

    @Before
    @Throws(Exception::class)
    fun setUpMocks() {
        MockitoAnnotations.initMocks(this)
        `when`(deviceConnector.apiLevel).thenReturn(21)
        `when`(deviceConnector.name).thenReturn("fake_device")
        project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
    }

    private fun getParams(privacySandboxSdkApksFiles: List<File> = emptyList()) =
        object : InstallVariantViaBundleTask.Params() {
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
            override val privacySandboxSdkApksFiles: ListProperty<File>
                get() = project.objects.listProperty(File::class.java)
                    .also { it.addAll(privacySandboxSdkApksFiles) }

        }

    @Test
    fun installSingle() {

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )

        val runnable = TestInstallRunnable(getParams(), deviceConnector, outputPath)

        runnable.run()

        verify(deviceConnector, atLeastOnce()).name
        verify(deviceConnector, atLeastOnce()).apiLevel
        verify(deviceConnector).deviceConfig

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

        val runnable = TestInstallRunnable(getParams(), deviceConnector, outputPath, outputPath2)

        runnable.run()

        verify(deviceConnector, atLeastOnce()).name
        verify(deviceConnector, atLeastOnce()).apiLevel
        verify(deviceConnector).deviceConfig

        verify(deviceConnector).installPackages(
            ArgumentMatchers.eq(listOf(outputPath.toFile(), outputPath2.toFile())),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    @Test
    fun installPrivacySandboxSdkApks_oneToOne() {

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val sdk1Apks = File("sdk1.apks")
        val sdk1ExtractedApk = File("sdk1-extracted.apk")
        val runnable = TestInstallRunnable(
            getParams(listOf(sdk1Apks)),
            deviceConnector,
            outputPath,
            privacySandboxSdkApkMapping = mapOf(
                sdk1Apks to listOf(sdk1ExtractedApk)
            )
        )

        runnable.run()

        verify(deviceConnector, atLeastOnce()).name
        verify(deviceConnector, atLeastOnce()).apiLevel
        verify(deviceConnector).deviceConfig

        verify(deviceConnector).installPackages(
            ArgumentMatchers.eq(listOf(sdk1ExtractedApk)),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )

        verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(outputPath.toFile()),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    @Test
    fun installPrivacySandboxSdkApks_manyToMany() {

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val sdk1Apks = File("sdk1.apks")
        val sdk1ExtractedApk1 = File("sdk1-extracted1.apk")
        val sdk1ExtractedApk2 = File("sdk1-extracted2.apk")

        val sdk2Apks = File("sdk2.apks")
        val sdk2ExtractedApk1 = File("sdk2-extracted1.apk")
        val sdk2ExtractedApk2 = File("sdk2-extracted2.apk")
        val runnable = TestInstallRunnable(
            getParams(listOf(sdk1Apks, sdk2Apks)),
            deviceConnector,
            outputPath,
            privacySandboxSdkApkMapping = mapOf(
                sdk1Apks to listOf(sdk1ExtractedApk1, sdk1ExtractedApk2),
                sdk2Apks to listOf(sdk2ExtractedApk1, sdk2ExtractedApk2)
            )
        )

        runnable.run()

        verify(deviceConnector, atLeastOnce()).name
        verify(deviceConnector, atLeastOnce()).apiLevel
        verify(deviceConnector).deviceConfig

        verify(deviceConnector).installPackages(
            ArgumentMatchers.eq(listOf(sdk1ExtractedApk1, sdk1ExtractedApk2)),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )

        verify(deviceConnector).installPackages(
            ArgumentMatchers.eq(listOf(sdk2ExtractedApk1, sdk2ExtractedApk2)),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )

        verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(outputPath.toFile()),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        verifyNoMoreInteractions(deviceConnector)
    }

    private class TestInstallRunnable(
        val params: InstallVariantViaBundleTask.Params,
        private val deviceConnector: DeviceConnector,
        private vararg val outputPaths: Path,
        private val privacySandboxSdkApkMapping: Map<File, List<File>> = mapOf(),
    ) : InstallVariantViaBundleTask.InstallRunnable() {

        override fun createDeviceProvider(iLogger: ILogger): DeviceProvider =
            InstallVariantTaskTest.FakeDeviceProvider(ImmutableList.of(deviceConnector))

        override fun getApkFiles(
            device: DeviceConnector,
            deviceConfigProvider: DeviceConfigProviderImpl
        ): List<Path> {
            return ImmutableList.copyOf(outputPaths)
        }

        override fun getPrivacySandboxSdkApkFiles(apk: Path) =
            privacySandboxSdkApkMapping[apk.fileName.toFile()]!!

        override fun getParameters(): InstallVariantViaBundleTask.Params {
            return params
        }
    }
}
