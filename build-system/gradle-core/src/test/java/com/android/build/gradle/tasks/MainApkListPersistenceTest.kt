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
import com.android.build.api.artifact.Operations
import com.android.build.api.variant.VariantConfiguration
import com.android.build.api.variant.impl.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.ApplicationVariantData
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantTypeImpl
import com.android.utils.Pair
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

/**
 * Tests for {@see MainApkListPersistence} task.
 */
open class MainApkListPersistenceTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    var manifestFileFolder = TemporaryFolder()

    @Mock private lateinit var variantScope: VariantScope
    @Mock private lateinit var variantData: ApplicationVariantData
    @Mock private lateinit var globalScope: GlobalScope
    @Mock private lateinit var config: VariantDslInfo
    @Mock private lateinit var artifacts: BuildArtifactsHolder
    @Mock private lateinit var dslScope: DslScope

    private lateinit var outputFactory: OutputFactory
    internal lateinit var project: Project
    internal lateinit var task: MainApkListPersistence
    internal lateinit var testDir: File
    internal lateinit var variantPropertiesImpl: VariantPropertiesImpl

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        Mockito.`when`(variantScope.getTaskName(ArgumentMatchers.any(String::class.java)))
                .thenReturn("taskFoo")
        Mockito.`when`(variantScope.globalScope).thenReturn(globalScope)
        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)
        Mockito.`when`(variantScope.taskContainer).thenReturn(MutableTaskContainer())
        Mockito.`when`(variantScope.fullVariantName).thenReturn("theVariantName")
        Mockito.`when`(config.variantType).thenReturn(VariantTypeImpl.BASE_APK)
        Mockito.`when`(globalScope.projectOptions).thenReturn(ProjectOptions(ImmutableMap.of()))
        Mockito.`when`(variantScope.variantData).thenReturn(variantData)
        Mockito.`when`(variantScope.variantDslInfo).thenReturn(config)
        Mockito.`when`(dslScope.objectFactory).thenReturn(project.objects)
        Mockito.`when`(dslScope.providerFactory).thenReturn(project.providers)


        variantScope.taskContainer.preBuildTask = project.tasks.register("preBuildTask")

        task = project.tasks.register("test", MainApkListPersistence::class.java) {
            task -> task.outputFile.set(project.file(
                temporaryFolder.newFile(SdkConstants.FN_APK_LIST)))
        }.get()
        outputFactory = OutputFactory("foo", config)

        variantPropertiesImpl= VariantPropertiesImpl(dslScope = dslScope,
            variantScope = variantScope, operations = Mockito.mock(Operations::class.java),
            configuration = Mockito.mock(VariantConfiguration::class.java))
        Mockito.`when`(variantData.publicVariantPropertiesApi).thenReturn(variantPropertiesImpl)
        Mockito.`when`(config.getVersionCode(true)).thenReturn(23)
        Mockito.`when`(config.getVersionName(true)).thenReturn("foo")
    }

    @Test
    fun testFullSplitPersistenceNoDisabledState() {
        variantPropertiesImpl.addVariantOutput(outputFactory.addFullSplit(
            ImmutableList.of(Pair.of(VariantOutput.FilterType.ABI, "x86"))))
        variantPropertiesImpl.addVariantOutput(
            outputFactory.addFullSplit(
                ImmutableList.of<Pair<VariantOutput.FilterType, String>>(
                    Pair.of(VariantOutput.FilterType.ABI, "armeabi")
                )
            )
        )

        MainApkListPersistence.CreationAction(variantScope).configure(task)

        assertThat(task.outputFile.get().asFile.absolutePath).startsWith(temporaryFolder.root.absolutePath)

        task.doTaskAction()

        // assert persistence.
        val apkList = ExistingBuildElements.loadApkList(task.outputFile.get().asFile)
        assertThat(apkList).hasSize(2)
        assertThat(apkList.asSequence()
                .filter { apkData -> apkData.type == VariantOutput.OutputType.FULL_SPLIT}
                .map { apkData -> apkData.filters.asSequence().map {
                    filterData -> filterData.identifier }.first() }
                .toList())
                .containsExactly("x86", "armeabi")
    }

    @Test
    fun testFullSplitPersistenceSomeDisabledState() {
        variantPropertiesImpl.addVariantOutput(outputFactory.addFullSplit(
                ImmutableList.of(Pair.of(VariantOutput.FilterType.ABI, "x86"))))
        val variantOutput = variantPropertiesImpl.addVariantOutput(
            outputFactory.addFullSplit(
                ImmutableList.of<Pair<VariantOutput.FilterType, String>>(
                    Pair.of(VariantOutput.FilterType.ABI, "armeabi")
                )
            )
        )
        variantOutput.isEnabled.set(false)

        MainApkListPersistence.CreationAction(variantScope).configure(task)
        assertThat(task.outputFile.get().asFile.absolutePath).startsWith(temporaryFolder.root.absolutePath)

        task.doTaskAction()

        // assert persistence.
        val apkList = ExistingBuildElements.loadApkList(task.outputFile.get().asFile)
        assertThat(apkList).hasSize(1)
        assertThat(apkList.asSequence()
                .filter { apkData -> apkData.type == VariantOutput.OutputType.FULL_SPLIT}
                .map { apkData -> apkData.filters.asSequence().map {
                    filterData -> filterData.identifier }.first() }
                .toList())
                .containsExactly("x86")
    }
}