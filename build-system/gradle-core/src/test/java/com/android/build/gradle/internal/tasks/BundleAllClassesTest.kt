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
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.feature.BundleAllClasses
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.testutils.truth.FileSubject
import com.google.common.collect.ImmutableMap
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class BundleAllClassesTest {

    @Mock private lateinit var scope: VariantScope
    @Mock private lateinit var artifacts: BuildArtifactsHolder
    @Mock private lateinit var fileTree: FileTree
    @Mock private lateinit var globalScope: GlobalScope
    @Mock private lateinit var variantData: BaseVariantData
    @Mock private lateinit var preJavacClasses: FileCollection
    @Mock private lateinit var postJavacClasses: FileCollection
    @Mock private lateinit var extension: BaseExtension
    @Mock private lateinit var aaptOptions: AaptOptions
    @Mock private lateinit var taskContainer: MutableTaskContainer
    @Mock private lateinit var rClasses: FileCollection

    @get:Rule
    val testFolder = TemporaryFolder()

    lateinit var task: BundleAllClasses

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(scope.artifacts).thenReturn(artifacts)
        Mockito.`when`(scope.globalScope).thenReturn(globalScope)
        Mockito.`when`(scope.variantData).thenReturn(variantData)
        Mockito.`when`(scope.name).thenReturn("theVariantName")
        Mockito.`when`(variantData.allPostJavacGeneratedBytecode).thenReturn(postJavacClasses)
        Mockito.`when`(variantData.allPreJavacGeneratedBytecode).thenReturn(preJavacClasses)
        Mockito.`when`(globalScope.extension).thenReturn(extension)
        Mockito.`when`(globalScope.projectOptions).thenReturn(ProjectOptions(ImmutableMap.of<String, Any>(BooleanOption.ENABLE_GRADLE_WORKERS.propertyName, false)))
        Mockito.`when`(extension.aaptOptions).thenReturn(aaptOptions)
        Mockito.`when`(aaptOptions.namespaced).thenReturn(false)
        Mockito.`when`(preJavacClasses.asFileTree).thenReturn(fileTree)
        Mockito.`when`(postJavacClasses.asFileTree).thenReturn(fileTree)

        val project = ProjectBuilder.builder()
            .withName("feature1")
            .withProjectDir(testFolder.newFolder()).build()

        val preBuildTask = project.tasks.register("preBuild")
        Mockito.`when`(scope.taskContainer).thenReturn(taskContainer)
        Mockito.`when`(taskContainer.preBuildTask).thenReturn(preBuildTask)

        task = project.tasks.create("test", BundleAllClasses::class.java)

        Mockito.`when`(globalScope.project).thenReturn(project)
        Mockito.`when`(artifacts.getFinalProductAsFileCollection(InternalArtifactType
            .COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)).thenReturn(
            FakeGradleProvider(rClasses))

        Mockito.`when`(scope.jarCreatorType).thenReturn(JarCreatorType.JAR_FLINGER)

        val configAction = BundleAllClasses.CreationAction(scope)
        configAction.preConfigure(task.name)
        configAction.configure(task)
        task.javacClasses.set(testFolder.newFolder())
        task.outputJar.set(testFolder.newFile("classes.jar"))
    }

    @Test
    fun testBasic() {
        task.doTaskAction()
        FileSubject.assertThat(task.outputJar.get().asFile).exists()
    }
}