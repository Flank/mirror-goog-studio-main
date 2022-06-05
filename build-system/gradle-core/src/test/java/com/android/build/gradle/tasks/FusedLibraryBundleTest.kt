/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.artifact.impl.SingleInitialProviderRequestImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScopeImpl
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

internal class FusedLibraryBundleTest {
    @Rule
    @JvmField var temporaryFolder = TemporaryFolder()

    val build: File by lazy {
        temporaryFolder.newFolder("build")
    }

    @Test
    fun testJarClasses() {
        testCreationConfig<FusedLibraryBundleClasses, FusedLibraryBundleClasses.CreationAction>(
            "classes.jar"
        )
    }

    @Test
    fun testAarBundle() {
        testCreationConfig<FusedLibraryBundleAar, FusedLibraryBundleAar.CreationAction>(
            "bundle.aar"
        )
    }

    inline fun <reified T: FusedLibraryBundle, reified U: FusedLibraryBundle.CreationAction<T>> testCreationConfig(
        archiveFileName: String,
    ) {
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("bundle", T::class.java)

        val variantScope = Mockito.mock(FusedLibraryVariantScopeImpl::class.java)
        val artifacts = Mockito.mock(ArtifactsImpl::class.java)
        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)

        Mockito.`when`(variantScope.layout).thenReturn(project.layout)

        @Suppress("UNCHECKED_CAST")
        val request = Mockito.mock(SingleInitialProviderRequestImpl::class.java)
                as SingleInitialProviderRequestImpl<T, RegularFile>

        Mockito.`when`(artifacts.setInitialProvider(taskProvider, FusedLibraryBundle::outputFile))
                .thenReturn(request)

        val creationAction = U::class.java.getDeclaredConstructor(FusedLibraryVariantScope::class.java)
            .newInstance(variantScope)
        creationAction.handleProvider(taskProvider)

        val task = taskProvider.get()
        creationAction.configure(task)

        Truth.assertThat(task.destinationDirectory.get().asFile.absolutePath).isEqualTo(
            project.layout.buildDirectory.dir(task.name).get().asFile.absolutePath
        )
        Truth.assertThat(task.archiveFileName.get()).isEqualTo(archiveFileName)
    }
}
