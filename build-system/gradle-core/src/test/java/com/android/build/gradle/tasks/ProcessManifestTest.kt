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

import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.AndroidBuilder
import com.android.ide.common.build.ApkData
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.IOException

/**
 * Tests for {@link ProcessManifest}
 */
class ProcessManifestTest {

    @Rule @JvmField var temporaryFolder = TemporaryFolder()

    internal lateinit var task: ProcessManifest

    @Mock lateinit var variantScope: VariantScope
    @Mock lateinit var globalScope: GlobalScope
    @Mock lateinit var outputScope: OutputScope
    @Mock lateinit var variantConfiguration : GradleVariantConfiguration
    @Mock lateinit var buildArtifactsHolder : BuildArtifactsHolder
    @Mock lateinit var variantData : BaseVariantData
    @Mock lateinit var mainSplit: ApkData
    @Mock lateinit var androidBuilder: AndroidBuilder
    @Mock lateinit var mergedFlavor: ProductFlavor

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        `when`(variantScope.globalScope).thenReturn(globalScope)
        `when`(variantScope.outputScope).thenReturn(outputScope)
        `when`(variantScope.variantConfiguration).thenReturn(variantConfiguration)
        `when`(variantScope.artifacts).thenReturn(buildArtifactsHolder)
        `when`(variantScope.variantData).thenReturn(variantData)
        `when`(variantScope.fullVariantName).thenReturn("fullVariantName")
        `when`(variantScope.getTaskName(any(), any())).thenReturn("processManifest")
        `when`(variantScope.getIncrementalDir(anyString())).thenReturn(temporaryFolder.newFolder())
        `when`(variantScope.taskContainer).thenReturn(MutableTaskContainer())

        `when`(globalScope.androidBuilder).thenReturn(androidBuilder)
        `when`(outputScope.mainSplit).thenReturn(mainSplit)
        `when`(variantConfiguration.mergedFlavor).thenReturn(mergedFlavor)
        `when`(mainSplit.fullName).thenReturn("fooRelease")

        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val configAction = ProcessManifest.ConfigAction(variantScope)
        task = project!!.tasks.create(configAction.name, configAction.type, configAction)
    }

    @Test()
    fun testBackwardCompatibility() {
        try {
            @Suppress("DEPRECATION")
            task.manifestOutputFile
            Assert.fail("failed to raise backward incompatible exception")
        } catch(e: RuntimeException) {
            Truth.assertThat(e.message).contains("gradle-plugin-3-0-0-migration.html")
        }
    }

    @Test
    fun testInputsAreAnnotatedCorrectly() {
        assertThat(task.inputs.properties).containsKey("maxSdkVersion")
        assertThat(task.inputs.properties).containsKey("minSdkVersion")
        assertThat(task.inputs.properties).containsKey("targetSdkVersion")
        assertThat(task.inputs.properties).containsKey("versionCode")
        assertThat(task.inputs.properties).containsKey("versionName")
        assertThat(task.inputs.properties).containsKey("manifestPlaceholders")
        assertThat(task.inputs.properties).containsKey("packageOverride")
        assertThat(task.inputs.properties).containsEntry("mainSplitFullName", "fooRelease")
    }
}