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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.core.VariantTypeImpl
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.bouncycastle.util.io.Streams
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile

class PerModuleBundleTaskTest {

    @Mock private lateinit var dexFiles: FileCollection
    @Mock private lateinit var javaResProvider: Provider<RegularFile>
    @Mock private lateinit var javaResFiles: FileCollection
    @Mock private lateinit var nativeLibsProvider: Provider<Directory>
    @Mock private lateinit var nativeLibsFiles: ConfigurableFileCollection
    @Mock private lateinit var variantScope: VariantScope
    @Mock private lateinit var artifacts: BuildArtifactsHolder
    @Mock private lateinit var featureSetMetadata: FileCollection
    @Mock private lateinit var transformManager: TransformManager
    @Mock private lateinit var variantConfiguration: GradleVariantConfiguration
    @Mock private lateinit var globalScope: GlobalScope
    @Mock private lateinit var project: Project
    @Mock private lateinit var taskContainer: MutableTaskContainer
    @Mock private lateinit var preBuildTask: TaskProvider<out Task>
    @Mock private lateinit var projectLayout: ProjectLayout
    @Mock private lateinit var projectFiles: ConfigurableFileCollection
    @Mock private lateinit var extension: BaseExtension
    @Mock private lateinit var compileOptions: CompileOptions

    @get:Rule
    val testFolder = TemporaryFolder()

    lateinit var task: PerModuleBundleTask

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(variantScope.fullVariantName).thenReturn("variant")
        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)
        Mockito.`when`(variantScope.type).thenReturn(VariantTypeImpl.FEATURE)

        Mockito.`when`(variantScope.variantConfiguration).thenReturn(variantConfiguration)
        Mockito.`when`(variantConfiguration.supportedAbis).thenReturn(setOf())

        Mockito.`when`(variantScope.needsMergedJavaResStream).thenReturn(false)
        Mockito.`when`(artifacts.getFinalProduct<RegularFile>(InternalArtifactType.MERGED_JAVA_RES))
            .thenReturn(javaResProvider)
        Mockito.`when`(project.layout).thenReturn(projectLayout)
        Mockito.`when`(projectLayout.files(javaResProvider)).thenReturn(javaResFiles)

        Mockito.`when`(variantScope.transformManager).thenReturn(transformManager)
        Mockito.`when`(transformManager.getPipelineOutputAsFileCollection(StreamFilter.DEX))
            .thenReturn(dexFiles)
        Mockito.`when`(project.files()).thenReturn(projectFiles)
        Mockito.`when`(artifacts.getFinalProduct<Directory>(STRIPPED_NATIVE_LIBS))
            .thenReturn(nativeLibsProvider)
        Mockito.`when`(projectFiles.from(nativeLibsProvider)).thenReturn(nativeLibsFiles)

        Mockito.`when`(variantScope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.FEATURE_SET_METADATA))
            .thenReturn(featureSetMetadata)


        Mockito.`when`(variantScope.globalScope).thenReturn(globalScope)
        Mockito.`when`(globalScope.project).thenReturn(project)
        Mockito.`when`(project.path).thenReturn(":foo:bar")
        Mockito.`when`(variantScope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.FEATURE_DEX,
            mapOf(MODULE_PATH to ":foo:bar")))
            .thenReturn(dexFiles)

        Mockito.`when`(variantScope.taskContainer).thenReturn(taskContainer)
        Mockito.`when`(taskContainer.preBuildTask).thenReturn(preBuildTask)

        Mockito.`when`(variantScope.jarCreatorType).thenReturn(JarCreatorType.JAR_FLINGER)
        Mockito.`when`(globalScope.extension).thenReturn(extension)
        Mockito.`when`(extension.compileOptions).thenReturn(compileOptions)
        Mockito.`when`(dexFiles.plus(projectFiles)).thenReturn(dexFiles)

        val testFiles = testFolder.newFolder("test_files")
        val featureMetadata = File(testFiles, "feature-metadata.json")
        FileUtils.writeToFile(
            featureMetadata,
            "[{\"modulePath\":\":feature2\",\"featureName\":\"feature2\",\"resOffset\":129}," +
                    "{\"modulePath\":\":feature1\",\"featureName\":\"feature1\",\"resOffset\":128}]")

        val project = ProjectBuilder.builder().withProjectDir(testFolder.newFolder()).build()
        task = project.tasks.create("test", PerModuleBundleTask::class.java) {
            task -> task.assetsFiles.set(testFolder.newFolder("assets"))
        }

        Mockito.`when`(featureSetMetadata.singleFile).thenReturn(featureMetadata)

        val resFile = testFolder.newFile("res")
        createRes(resFile)
        task.resFiles.set(resFile)
        task.outputDir.set(testFolder.newFolder("out"))


        val configAction = PerModuleBundleTask.CreationAction(variantScope, false)
        configAction.preConfigure(task.name)
        configAction.configure(task)
    }

    @Test
    fun testSingleDexFiles() {
        val dexFolder = testFolder.newFolder("dex_files")
        Mockito.`when`(dexFiles.files).thenReturn(
            setOf(createDex(dexFolder, "classes.dex")))
        task.doTaskAction()
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 1)
    }

    @Test
    fun testNoDuplicateDexFiles() {
        val dexFolder = testFolder.newFolder("dex_files")
        Mockito.`when`(dexFiles.files).thenReturn(
            setOf(
                createDex(dexFolder, "classes.dex"),
                createDex(dexFolder, "classes2.dex"),
                createDex(dexFolder, "classes3.dex")))
        task.doTaskAction()
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 3)
    }

    @Test
    fun testDuplicateDexFiles() {
        val dexFolder0 = testFolder.newFolder("0")
        val dexFolder1 = testFolder.newFolder("1")
        Mockito.`when`(dexFiles.files).thenReturn(
            setOf(
                createDex(dexFolder0, "classes.dex"),
                createDex(dexFolder0, "classes2.dex"),
                createDex(dexFolder0, "classes3.dex"),
                createDex(dexFolder1, "classes.dex"),
                createDex(dexFolder1, "classes2.dex")))
        task.doTaskAction()

        // verify naming and shuffling of names.
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 5)
    }

    @Test
    fun testMainDexNotRenamedFiles() {
        val dexFolder0 = testFolder.newFolder("0")
        Mockito.`when`(dexFiles.files).thenReturn(
            setOf(
                createDex(dexFolder0, "classes2.dex"),
                createDex(dexFolder0, "classes.dex"),
                createDex(dexFolder0, "classes3.dex")))
        task.doTaskAction()

        // verify classes.dex has not been renamed.
        verifyOutputZip(task.outputDir.get().asFileTree.singleFile, 3)
    }

    private fun verifyOutputZip(zipFile: File, expectedNumberOfDexFiles: Int) {
        assertThat(expectedNumberOfDexFiles).isGreaterThan(0)
        assertThat(zipFile.exists())
        ZipFileSubject.assertThatZip(zipFile).use {
            it.contains("dex/classes.dex")
            for (index in 2..expectedNumberOfDexFiles) {
                it.contains("dex/classes$index.dex")
            }
            it.doesNotContain("dex/classes" + (expectedNumberOfDexFiles + 1) + ".dex")
        }
        verifyClassesDexNotRenamed(zipFile)
    }

    private fun verifyClassesDexNotRenamed(zipFile: File) {
        val outputZip = ZipFile(zipFile)
        outputZip.getInputStream(outputZip.getEntry("dex/classes.dex")).use {
            val bytes = ByteArray(128)
            Streams.readFully(it, bytes)
            assertThat(bytes.toString(Charset.defaultCharset())).startsWith("Dex classes.dex")
        }
    }

    private fun createDex(folder: File, id: String): File {
        val dexFile = File(folder, id)
        FileUtils.createFile(dexFile, "Dex $id")
        return dexFile
    }

    private fun createRes(resFile: File) {
        JarOutputStream(BufferedOutputStream(FileOutputStream(resFile))).use {
            it.putNextEntry(JarEntry("foo"))
            it.writer(Charsets.UTF_8).append("foo")
            it.closeEntry()
        }
    }
}