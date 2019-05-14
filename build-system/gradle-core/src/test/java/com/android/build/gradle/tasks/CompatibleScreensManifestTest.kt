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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.ProductFlavor
import com.android.utils.FileUtils
import com.android.utils.Pair
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Tests for the [CompatibleScreensManifest] class  */
class CompatibleScreensManifestTest {

    @get:Rule var projectFolder = TemporaryFolder()
    @get:Rule var temporaryFolder = TemporaryFolder()

    @Mock internal lateinit var scope: VariantScope
    @Mock private lateinit var outputScope: OutputScope
    @Mock private lateinit var variantConfiguration: GradleVariantConfiguration
    @Mock private lateinit var productFlavor: ProductFlavor
    @Mock private lateinit var buildArtifactsHolder: BuildArtifactsHolder
    @Mock private lateinit var taskContainer: MutableTaskContainer

    private lateinit var task: CompatibleScreensManifest

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = projectFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        task = project.tasks.create("test", CompatibleScreensManifest::class.java)

        MockitoAnnotations.initMocks(this)
        `when`(scope.fullVariantName).thenReturn("fullVariantName")
        `when`(scope.variantConfiguration).thenReturn(variantConfiguration)
        `when`(scope.outputScope).thenReturn(outputScope)
        `when`(scope.artifacts).thenReturn(buildArtifactsHolder)
        `when`(scope.taskContainer).thenReturn(taskContainer)
        `when`(taskContainer.preBuildTask).thenReturn(project.tasks.register("preBuildTask"))
        task.outputFolder.set(temporaryFolder.root)
        `when`<ApiVersion>(productFlavor.minSdkVersion).thenReturn(DefaultApiVersion(21))
        `when`<ProductFlavor>(variantConfiguration.mergedFlavor).thenReturn(productFlavor)
        `when`(variantConfiguration.baseName).thenReturn("baseName")
        `when`(variantConfiguration.fullName).thenReturn("fullName")
        `when`(variantConfiguration.type).thenReturn(VariantTypeImpl.BASE_APK)
    }

    @Test
    fun testConfigAction() {

        val configAction = CompatibleScreensManifest.CreationAction(
                scope, setOf("xxhpi", "xxxhdpi")
        )

        configAction.configure(task)

        assertThat(task.variantName).isEqualTo("fullVariantName")
        assertThat(task.name).isEqualTo("test")
        assertThat(task.minSdkVersion.get()).isEqualTo("21")
        assertThat(task.screenSizes).containsExactly("xxhpi", "xxxhdpi")
        assertThat(task.outputFolder.get().asFile).isEqualTo(temporaryFolder.root)
    }

    @Test
    fun testNoSplit() {

        val outputFactory = OutputFactory(PROJECT, variantConfiguration)
        val mainApk = outputFactory.addMainApk()
        writeApkList(listOf(mainApk))

        task.variantName = "variant"
        task.minSdkVersion = task.project.provider { "22" }
        task.screenSizes = ImmutableSet.of("mdpi", "xhdpi")

        task.taskAction()
        val buildElements = ExistingBuildElements.from(
            InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            temporaryFolder.root
        )
        assertThat(buildElements).isEmpty()
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithMinSdkVersion() {

        val outputFactory = OutputFactory(PROJECT, variantConfiguration)
        val splitApk = outputFactory.addFullSplit(
                ImmutableList.of<Pair<VariantOutput.FilterType, String>>(
                        Pair.of<VariantOutput.FilterType, String>(
                                VariantOutput.FilterType.DENSITY,
                                "xhdpi"
                        )
                )
        )
        writeApkList(listOf(splitApk))

        task.variantName = "variant"
        task.minSdkVersion = task.project.provider { "22" }
        task.screenSizes = ImmutableSet.of("xhdpi")

        task.taskAction()

        val xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"22\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains(
                    "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />"
            )
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithoutMinSdkVersion() {

        val outputFactory = OutputFactory(PROJECT, variantConfiguration)
        val splitApk = outputFactory.addFullSplit(
                ImmutableList.of<Pair<VariantOutput.FilterType, String>>(
                        Pair.of<VariantOutput.FilterType, String>(
                                VariantOutput.FilterType.DENSITY,
                                "xhdpi"
                        )
                )
        )
        writeApkList(listOf(splitApk))

        task.variantName = "variant"
        task.minSdkVersion = task.project.provider { null }
        task.screenSizes = ImmutableSet.of("xhdpi")

        task.taskAction()

        val xml = Joiner.on("\n")
            .join(
                    Files.readAllLines(
                            findManifest(temporaryFolder.root, "xhdpi").toPath()
                    )
            )
        assertThat(xml).doesNotContain("<uses-sdk")
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleSplitsWithMinSdkVersion() {

        val outputFactory = OutputFactory(PROJECT, variantConfiguration)
        val xhdpiSplit = outputFactory.addFullSplit(
                ImmutableList.of<Pair<VariantOutput.FilterType, String>>(
                        Pair.of<VariantOutput.FilterType, String>(
                                VariantOutput.FilterType.DENSITY,
                                "xhdpi"
                        )
                )
        )
        val xxhdpiSplit = outputFactory.addFullSplit(
                ImmutableList.of<Pair<VariantOutput.FilterType, String>>(
                        Pair.of<VariantOutput.FilterType, String>(
                                VariantOutput.FilterType.DENSITY,
                                "xxhdpi"
                        )
                )
        )
        writeApkList(listOf(xhdpiSplit, xxhdpiSplit))

        task.variantName = "variant"
        task.minSdkVersion = task.project.provider { "23" }
        task.screenSizes = ImmutableSet.of("xhdpi", "xxhdpi")

        task.taskAction()

        var xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains(
                    "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />"
            )

        xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xxhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains("<screen android:screenSize=\"xxhdpi\" android:screenDensity=\"480\" />")
    }

    private fun writeApkList(apkDataList: List<ApkData>) {
        val apkList = temporaryFolder.newFile("apk_list.json")
        FileUtils.writeToFile(apkList,ExistingBuildElements.persistApkList(apkDataList))
        task.apkList.set(apkList)
    }

    companion object {
        private const val PROJECT = "project"

        private fun findManifest(taskOutputDir: File, splitName: String): File {
            val splitDir = File(taskOutputDir, splitName)
            assertThat(splitDir.exists()).isTrue()
            val manifestFile = File(splitDir, SdkConstants.ANDROID_MANIFEST_XML)
            assertThat(manifestFile.exists()).isTrue()
            return manifestFile
        }
    }
}
