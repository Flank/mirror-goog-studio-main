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
package com.android.build.gradle.internal.dsl

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.services.createDslServices
import com.android.builder.core.AbstractBuildType
import com.android.builder.core.BuilderConstants
import com.android.sdklib.SdkVersionInfo
import com.android.testutils.internal.CopyOfTester
import com.android.testutils.truth.PathSubject
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/** Tests that the build types are properly initialized.  */
class BuildTypeTest {
    private lateinit var project: Project
    private val dslServices = createDslServices()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        project = ProjectBuilder.builder().build()
        TestProjects.prepareProject(project, ImmutableMap.of())
    }

    @Test
    fun testDebug() {
        val type = getBuildTypeWithName(BuilderConstants.DEBUG)
        Assert.assertTrue(type.isDebuggable)
        Assert.assertFalse(type.isJniDebuggable)
        Assert.assertFalse(type.isRenderscriptDebuggable)
        type as AbstractBuildType
        Assert.assertNotNull(type.signingConfig)
        Assert.assertTrue(type.isZipAlignEnabled)
    }

    @Test
    fun testRelease() {
        val type =
            getBuildTypeWithName(BuilderConstants.RELEASE)
        Assert.assertFalse(type.isDebuggable)
        Assert.assertFalse(type.isJniDebuggable)
        Assert.assertFalse(type.isRenderscriptDebuggable)
        Assert.assertTrue(type.isZipAlignEnabled)
    }

    @Test
    fun testBuildConfigOverride() {
        val debugBuildType = dslServices.newDecoratedInstance(BuildType::class.java, "someBuildType", dslServices)

        Truth.assertThat(debugBuildType).isNotNull()
        debugBuildType.buildConfigField("String", "name", "sensitiveValue")
        debugBuildType.buildConfigField("String", "name", "sensitiveValue")
        val messages = (dslServices.issueReporter as FakeSyncIssueReporter).messages
        Truth.assertThat(messages).hasSize(1)
        Truth.assertThat(messages[0]).doesNotContain("sensitiveValue")
    }

    @Test
    fun testResValueOverride() {
        val debugBuildType = dslServices.newDecoratedInstance(BuildType::class.java, "someBuildType", dslServices)

        Truth.assertThat(debugBuildType).isNotNull()
        debugBuildType.resValue("String", "name", "sensitiveValue")
        debugBuildType.resValue("String", "name", "sensitiveValue")
        val messages = (dslServices.issueReporter as FakeSyncIssueReporter).messages
        Truth.assertThat(messages).hasSize(1)
        Truth.assertThat(messages[0]).doesNotContain("sensitiveValue")
    }

    @Test
    fun testInitWith() {
        CopyOfTester.assertAllGettersCalled(
            BuildType::class.java,
            dslServices.newDecoratedInstance(BuildType::class.java, "original", dslServices),
            listOf(
                // Extensions are not copied as AGP doesn't manage them
                "getExtensions",
                "isZipAlignEnabled\$annotations"
            )
        ) { original: BuildType ->
            val copy = dslServices.newDecoratedInstance(BuildType::class.java, original.name, dslServices)
            copy.initWith(original)
            // Ndk and ndkConfig refer to the same object
            original.ndk
            // Manually call getters that don't need to be copied.
            original.postProcessingConfiguration
            // Covered by original.isDefault
            original.getIsDefault()
            // Uses the private _isDefault
            original.isShrinkResources
            // Covered by _useProguard
            original.isUseProguard
            // Covered by externalNativeBuildOptions
            original.externalNativeBuild
        }
    }

    @Test
    fun setProguardFilesTest() {
        val buildType : com.android.build.api.dsl.BuildType =
            dslServices.newDecoratedInstance(BuildType::class.java, "someBuildType", dslServices)
        buildType.apply {
            // Check set replaces
            proguardFiles += dslServices.file("replaced")
            setProguardFiles(listOf("test"))
            Truth.assertThat(proguardFiles).hasSize(1)
            PathSubject.assertThat(proguardFiles.single()).hasName("test")
            // Check set self doesn't clear
            setProguardFiles(proguardFiles)
            PathSubject.assertThat(proguardFiles.single()).hasName("test")
        }
    }

    private fun getBuildTypeWithName(name: String): com.android.builder.model.BuildType {
        project.apply(
            ImmutableMap.of<String, String?>(
                "plugin",
                "com.android.application"
            )
        )
        project.extensions
            .getByType(AppExtension::class.java)
            .compileSdkVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
        val buildTypeData =
            project.plugins
                .getPlugin(AppPlugin::class.java)
                .variantInputModel
                .buildTypes[name] ?: error("Build type not found")
        return buildTypeData.buildType
    }
}
