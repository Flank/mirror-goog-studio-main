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
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.VariantTypeImpl
import com.android.builder.profile.NameAnonymizer
import com.android.builder.profile.NameAnonymizerSerializer
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Base64

/** Tests for the [CompatibleScreensManifest] class  */
class CompatibleScreensManifestTest {

    @get:Rule var projectFolder = TemporaryFolder()
    @get:Rule var temporaryFolder = TemporaryFolder()

    @Mock internal lateinit var scope: VariantScope
    @Mock internal lateinit var globalScope: GlobalScope
    @Mock private lateinit var variantDslInfo: VariantDslInfo
    @Suppress("DEPRECATION")
    @Mock private lateinit var artifacts: ArtifactsImpl
    @Mock private lateinit var taskContainer: MutableTaskContainer
    @Mock private lateinit var variantData: BaseVariantData
    @Mock private lateinit var appVariant: ApplicationVariantImpl

    private lateinit var task: CompatibleScreensManifest

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = projectFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java),
            AnalyticsService::class.java
        ) {
            val profile = GradleBuildProfile.newBuilder().build().toByteArray()
            it.parameters.profile.set(Base64.getEncoder().encodeToString(profile))
            it.parameters.anonymizer.set(NameAnonymizerSerializer().toJson(NameAnonymizer()))
            it.parameters.projects.set(mutableMapOf())
            it.parameters.enableProfileJson.set(true)
            it.parameters.taskMetadata.set(mutableMapOf())
            it.parameters.rootProjectPath.set("/path")
        }
        task = project.tasks.create("test", CompatibleScreensManifest::class.java)

        val services = createTaskCreationServices(
            createProjectServices(project)
        )

        MockitoAnnotations.initMocks(this)
        `when`(appVariant.name).thenReturn("fullVariantName")
        `when`(appVariant.baseName).thenReturn("baseName")
        `when`(appVariant.variantDslInfo).thenReturn(variantDslInfo)
        `when`(appVariant.globalScope).thenReturn(globalScope)
        `when`(appVariant.artifacts).thenReturn(artifacts)
        `when`(appVariant.taskContainer).thenReturn(taskContainer)
        `when`(appVariant.variantScope).thenReturn(scope)
        `when`(appVariant.variantType).thenReturn(VariantTypeImpl.BASE_APK)
        `when`(appVariant.variantData).thenReturn(variantData)
        `when`(appVariant.services).thenReturn(services)
        `when`<AndroidVersion>(appVariant.minSdkVersion).thenReturn(AndroidVersionImpl(21))


        `when`(taskContainer.preBuildTask).thenReturn(project.tasks.register("preBuildTask"))
        task.outputFolder.set(temporaryFolder.root)
        `when`(variantDslInfo.variantType).thenReturn(VariantTypeImpl.BASE_APK)
        `when`(variantDslInfo.componentIdentity).thenReturn(
            ComponentIdentityImpl(
                "fullVariantName",
                "flavorName",
                "debug"
            )
        )
        val applicationId = project.objects.property(String::class.java)
        applicationId.set("com.foo")
        `when`(appVariant.applicationId).thenReturn(applicationId)
    }

    @Test
    fun testConfigAction() {
        val configAction = CompatibleScreensManifest.CreationAction(
                appVariant, setOf("xxhpi", "xxxhdpi")
        )
        val variantOutputList = VariantOutputList(
            listOf(
                VariantOutputImpl(
                    FakeGradleProperty(value = 0),
                    FakeGradleProperty(value =""),
                    FakeGradleProperty(value =true),
                    Mockito.mock(VariantOutputConfigurationImpl::class.java),
                    "base_name",
                    "main_full_name",
                    FakeGradleProperty(value = "output_file_name"))))
        `when`(appVariant.outputs).thenReturn(variantOutputList)

        configAction.configure(task)

        assertThat(task.variantName).isEqualTo("fullVariantName")
        assertThat(task.name).isEqualTo("test")
        assertThat(task.minSdkVersion.get()).isEqualTo("21")
        assertThat(task.screenSizes).containsExactly("xxhpi", "xxxhdpi")
        assertThat(task.outputFolder.get().asFile).isEqualTo(temporaryFolder.root)
        assertThat(task.applicationId.get()).isEqualTo("com.foo")
        assertThat(task.variantType.get()).isEqualTo(VariantTypeImpl.BASE_APK.toString())
        assertThat(task.analyticsService.get()).isInstanceOf(AnalyticsService::class.java)
    }

    @Test
    fun testNoSplit() {

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
            FakeGradleProperty("version_name"),
            FakeGradleProperty(true),
            VariantOutputConfigurationImpl(false, listOf()),
                "base_name",
                "main_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantName = "variant"
        task.minSdkVersion.set("22" )
        task.screenSizes = ImmutableSet.of("mdpi", "xhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())
        task.analyticsService.set(FakeNoOpAnalyticsService())

        task.taskAction()
        val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(
            temporaryFolder.root)
        assertThat(buildElements?.elements).isEmpty()
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithMinSdkVersion() {

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xhdpi"))),
                "base_name",

                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantName = "variant"
        task.minSdkVersion.set("22")
        task.screenSizes = ImmutableSet.of("xhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())
        task.analyticsService.set(FakeNoOpAnalyticsService())

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

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xhdpi"))),
                "base_name",
                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantName = "variant"
        task.minSdkVersion.set(task.project.provider { null })
        task.screenSizes = ImmutableSet.of("xhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())
        task.analyticsService.set(FakeNoOpAnalyticsService())

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

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xhdpi"))),
                "base_name",
                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))

        task.variantOutputs.add(
            VariantOutputImpl(FakeGradleProperty(5),
                FakeGradleProperty("version_name"),
                FakeGradleProperty(true),
                VariantOutputConfigurationImpl(false,
                    listOf(FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xxhdpi"))),
                "base_name",

                "split_full_name",
                FakeGradleProperty(value = "output_file_name")))


        task.variantName = "variant"
        task.minSdkVersion.set("23")
        task.screenSizes = ImmutableSet.of("xhdpi", "xxhdpi")
        task.applicationId.set("com.foo")
        task.variantType.set(VariantTypeImpl.BASE_APK.toString())
        task.analyticsService.set(FakeNoOpAnalyticsService())

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

    companion object {
        private fun findManifest(taskOutputDir: File, splitName: String): File {
            val splitDir = File(taskOutputDir, splitName)
            assertThat(splitDir.exists()).isTrue()
            val manifestFile = File(splitDir, SdkConstants.ANDROID_MANIFEST_XML)
            assertThat(manifestFile.exists()).isTrue()
            return manifestFile
        }
    }
}
