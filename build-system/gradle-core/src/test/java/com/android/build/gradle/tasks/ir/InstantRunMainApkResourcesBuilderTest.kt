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

package com.android.build.gradle.tasks.ir

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.incremental.InstantRunBuildContext
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.utils.FileCache
import com.android.ide.common.build.ApkInfo
import com.android.utils.ILogger
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

/**
 * Tests for {@link InstantRunMainApkResourcesBuilder}
 */
open class InstantRunMainApkResourcesBuilderTest {

    @Rule @JvmField
    var temporaryFolder = TemporaryFolder()

    @Rule @JvmField
    var manifestFileFolder = TemporaryFolder()

    @Rule @JvmField
    var emptyFolder = TemporaryFolder()

    @Mock private lateinit var resources: BuildableArtifact
    @Mock private lateinit var fileCollection: FileCollection
    private lateinit var manifestFiles: Provider<Directory>
    @Mock internal lateinit var fileTree: FileTree
    @Mock internal var buildContext: InstantRunBuildContext? = null
    @Mock internal var logger: ILogger? = null
    @Mock private lateinit var variantConfiguration: GradleVariantConfiguration
    @Mock private lateinit var buildArtifactsHolder: BuildArtifactsHolder
    @Mock private lateinit var variantScope: VariantScope
    @Mock private lateinit var globalScope: GlobalScope
    @Mock private lateinit var fileCache: FileCache
    @Mock private lateinit var dslScope: DslScope
    @Mock private lateinit var issueReporter: EvalIssueReporter
    @Mock private lateinit var projectOptions: ProjectOptions
    private val outputScope = OutputScope()

    internal lateinit var project: Project
    internal lateinit var task: InstantRunMainApkResourcesBuilder
    internal lateinit var testDir: File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        `when`(variantScope.fullVariantName).thenReturn("variantName")
        `when`(variantConfiguration.baseName).thenReturn("variantName")
        `when`(globalScope.buildCache).thenReturn(fileCache)
        `when`(globalScope.project).thenReturn(project)
        `when`(globalScope.projectOptions).thenReturn(projectOptions)
        `when`(variantScope.getTaskName(any(String::class.java))).thenReturn("taskFoo")
        `when`(variantScope.globalScope).thenReturn(globalScope)
        `when`(variantScope.outputScope).thenReturn(outputScope)
        `when`(variantScope.artifacts).thenReturn(buildArtifactsHolder)
        `when`(variantConfiguration.type).thenReturn(VariantTypeImpl.BASE_APK)

        `when`(variantScope.taskContainer).thenReturn(MutableTaskContainer())
        variantScope.taskContainer.preBuildTask = project.tasks.register("preBuildTask")

        `when`(buildArtifactsHolder.getFinalArtifactFiles(InternalArtifactType.MERGED_RES))
                .thenReturn(resources)
        `when`(dslScope.issueReporter).thenReturn(issueReporter)
        `when`(resources.get()).thenReturn(fileCollection)
        `when`(fileCollection.asFileTree).thenReturn(fileTree)
    }

    @Test
    fun testConfigAction() {
        task = project.tasks.create("test", InstantRunMainApkResourcesBuilder::class.java)
        val configAction = InstantRunMainApkResourcesBuilder.CreationAction(
                variantScope,
                InternalArtifactType.MERGED_RES)

        val outDir = temporaryFolder.newFolder()
        manifestFiles = project.layout.buildDirectory.dir(emptyFolder.root.absolutePath)
        `when`(buildArtifactsHolder.getFinalProduct<Directory>(
            InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS)).thenReturn(manifestFiles)
        `when`(buildArtifactsHolder.appendArtifact(
            InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES,
            task.name,
            "out")).thenReturn(outDir)

        configAction.preConfigure(task.name)
        configAction.configure(task)
        assertThat(task.resourceFiles).isEqualTo(resources)
        assertThat(task.manifestFiles).isEqualTo(manifestFiles)
        assertThat(task.outputDirectory).isEqualTo(outDir)
    }

    // subclass the task to not have to create an AndroidBuilder and aapt mock. those parts
    // should be tested elsewhere.
    open class InstantRunMainApkResourcesBuilderForTest: InstantRunMainApkResourcesBuilder() {
        @Throws(IOException::class)
        override fun processSplit(apkData: ApkInfo, manifestFile: File?): File? {
            return File(outputDirectory, apkData.baseName)
        }
    }

    @Test
    fun testSingleApkData() {
        task = project.tasks.create("test", InstantRunMainApkResourcesBuilderForTest::class.java)

        val configAction = InstantRunMainApkResourcesBuilder.CreationAction(
                variantScope,
                InternalArtifactType.MERGED_RES)

        val outDir = temporaryFolder.newFolder()

        val manifestFile = manifestFileFolder.newFile()
        FileUtils.write(manifestFile,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "      package=\"com.example.spollom.myapplication\"\n" +
                "      android:versionCode=\"1\"\n" +
                "      android:versionName=\"1.0\">\n" +
                "</manifest>\n")

        BuildOutput(InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS,
                OutputFactory("test", variantConfiguration, outputScope).addMainApk(),
                manifestFile)
                .save(manifestFileFolder.root)

        manifestFiles = project.layout.buildDirectory.dir(project.provider { manifestFileFolder.root.absolutePath })
        `when`(buildArtifactsHolder.getFinalProduct<Directory>(
            InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS)).thenReturn(manifestFiles)
        `when`(buildArtifactsHolder.appendArtifact(
            InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES,
            task.name,
            "out")).thenReturn(outDir)

        configAction.preConfigure(task.name)
        configAction.configure(task)

        task.doFullTaskAction()
        val resultingFiles = outDir.listFiles()
        assertThat(resultingFiles).hasLength(1)
        assertThat(resultingFiles[0].name).isEqualTo("output.json")
        val buildArtifacts = ExistingBuildElements.from(
                InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES, outDir)
        assertThat(buildArtifacts.size()).isEqualTo(1)
        val buildArtifact = Iterables.getOnlyElement(buildArtifacts)
        assertThat(buildArtifact.type).isEqualTo(
                InternalArtifactType.INSTANT_RUN_MAIN_APK_RESOURCES)
        assertThat(buildArtifact.apkInfo.type.toString()).isEqualTo("MAIN")
    }
}