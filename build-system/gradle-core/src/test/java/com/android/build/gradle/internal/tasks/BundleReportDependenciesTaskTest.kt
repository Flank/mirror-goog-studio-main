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

import com.android.build.gradle.internal.tasks.bundle.ADBuilder
import com.android.build.gradle.internal.tasks.bundle.appDependencies
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileCollection
import com.android.tools.build.libraries.metadata.AppDependencies
import com.android.tools.build.libraries.metadata.Library
import com.android.tools.build.libraries.metadata.LibraryDependencies
import com.android.tools.build.libraries.metadata.ModuleDependencies
import com.android.tools.build.libraries.metadata.Repository
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import com.google.common.collect.ImmutableSet
import com.google.protobuf.Int32Value

class BundleReportDependenciesTaskTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    internal lateinit var project: Project
    lateinit var task: BundleReportDependenciesTask
    lateinit var dependenciesFile : File
    lateinit var baseDepsFile : File
    lateinit var feature1File : File
    lateinit var feature2File : File
    lateinit var featureDepsFiles : Set<File>
    lateinit var baseDepsFiles : Set<File>

    @Mock private lateinit var featureDeps: FileCollection

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        dependenciesFile = temporaryFolder.newFile()
        baseDepsFile = temporaryFolder.newFile()
        feature1File = temporaryFolder.newFile()
        feature2File = temporaryFolder.newFile()
        featureDepsFiles = ImmutableSet.of(feature1File, feature2File)
        baseDepsFiles = ImmutableSet.of(baseDepsFile)

        Mockito.`when`(featureDeps.files).thenReturn(featureDepsFiles)

        val testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        task = project.tasks.create("test", BundleReportDependenciesTask::class.java)
        task.dependenciesList.set(dependenciesFile)
    }

    @Test
    fun combineDepsTest() {
        val addLib1 = fun ADBuilder.() = run { addLibrary("foo", "baz", "1.2") }
        val addLib2 = fun ADBuilder.() = run { addLibrary("bar", "beef", "1.1") }
        val addLib3 = fun ADBuilder.() = run { addLibrary("dead", "beef", "1.1") }
        val baseAppDeps = appDependencies {
            addLib1().setRepoIndex(0)
            addLib2()
            addModuleDeps("base",0)
            addLibraryDeps(0,1)
            addLibraryDeps(1)
            addMavenRepository("fakeUrl1")
        }
        val featureDep1 = appDependencies {
            addLib2()
            addModuleDeps("feature1", 0)
            addLibraryDeps(0)
        }
        val featureDep2 = appDependencies {
            addLib3().setRepoIndex(0)
            addLib2()
            addModuleDeps("feature2", 0)
            addLibraryDeps(0, 1)
            addLibraryDeps(1)
            addIvyRepository("fakeUrl2")
        }
        FileOutputStream(baseDepsFile).use { baseAppDeps.writeTo(it) }
        FileOutputStream(feature1File).use { featureDep1.writeTo(it) }
        FileOutputStream(feature2File).use { featureDep2.writeTo(it) }
        val expected = appDependencies {
            addLib1().setRepoIndex(0)
            addLib2()
            addLib3().setRepoIndex(1)
            addModuleDeps("base", 0)
            addModuleDeps("feature1", 1)
            addModuleDeps("feature2", 2)
            addLibraryDeps(0, 1)
            addLibraryDeps(1)
            addLibraryDeps(2, 1)
            addMavenRepository("fakeUrl1")
            addIvyRepository("fakeUrl2")
        }

        task.baseDeps.set(baseDepsFile)
        task.featureDeps = featureDeps

        task.doTaskAction()
        val allDeps = AppDependencies.parseFrom(FileInputStream(task.dependenciesList.get().asFile))
        assertThat(allDeps).isEqualTo(expected)
    }
}
