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
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.variant2.createFakeDslScope
import com.android.builder.core.BuilderConstants
import com.android.sdklib.SdkVersionInfo
import com.android.testutils.internal.CopyOfTester
import com.google.common.collect.ImmutableMap
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/** Tests that the build types are properly initialized.  */
class BuildTypeTest {
    private lateinit var project: Project
    private val dslScope: DslScope = createFakeDslScope()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        project = ProjectBuilder.builder().build()
    }

    @Test
    fun testDebug() {
        val type = getBuildTypeWithName(BuilderConstants.DEBUG)
        Assert.assertTrue(type.isDebuggable)
        Assert.assertFalse(type.isJniDebuggable)
        Assert.assertFalse(type.isRenderscriptDebuggable)
        Assert.assertNotNull(type.signingConfig)
        Assert.assertTrue(type.signingConfig!!.isSigningReady)
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
    fun testInitWith() {
        CopyOfTester.assertAllGettersCalled(
            BuildType::class.java,
            BuildType("original", dslScope),
            { original: BuildType ->
                val copy =
                    BuildType(original.name, dslScope)
                copy.initWith(original)
                // Manually call getters that don't need to be copied.
                original.postProcessingConfiguration
                // Covered by original.isDefault
                original.getIsDefault()
                // Uses the private _isDefault
                original.isShrinkResources
                // Covered by _useProguard
                original.isUseProguard
            }
        )
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
