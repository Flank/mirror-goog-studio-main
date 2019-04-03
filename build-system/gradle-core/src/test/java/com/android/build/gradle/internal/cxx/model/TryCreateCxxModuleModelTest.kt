/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.model

import com.android.SdkConstants.NDK_SYMLINK_DIR
import com.android.build.gradle.internal.cxx.logging.RecordingLoggingEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.doReturn
import java.io.File

class TryCreateCxxModuleModelTest {

    @Test
    fun `no native build`() {
        BasicModuleModelMock().let {
            assertThat(tryCreateCxxModuleModel(it.global)).isNull()
        }
    }

    @Test
    fun `simplest cmake`() {
        BasicModuleModelMock().let {
            doReturn(File("./CMakeLists.txt")).`when`(it.cmake).path
            assertThat(tryCreateCxxModuleModel(it.global)).isNotNull()
        }
    }

    @Test
    fun `simplest ndk-build`() {
        BasicModuleModelMock().let {
            doReturn(File("./Android.mk")).`when`(it.ndkBuild).path
            assertThat(tryCreateCxxModuleModel(it.global)).isNotNull()
        }
    }

    @Test
    fun `fully exercise model expect no exceptions`() {
        BasicCmakeMock().let {
            // Walk all vals in the model and invoke them
            val module = tryCreateCxxModuleModel(it.global)!!
            CxxModuleModel::class.java.methods.toList().onEach { method ->
                if (!method.toGenericString().contains("NdkFolder") &&
                    !method.toGenericString().contains("NdkVersion")) {
                    method.invoke(module)
                }
            }
        }
    }

    @Test
    fun `both cmake and ndk-build`() {
        BasicCmakeMock().let {
            doReturn(it.tempFolder.newFile("Android.mk")).`when`(it.ndkBuild).path
            RecordingLoggingEnvironment().use { logEnvironment ->
                assertThat(tryCreateCxxModuleModel(it.global)).isNull()
                assertThat(logEnvironment.errors).hasSize(1)
                assertThat(logEnvironment.errors[0]).contains("More than one")
            }
        }
    }

    @Test
    fun `remap of buildStagingDirectory`() {
        BasicCmakeMock().let {
            RecordingLoggingEnvironment().use { logEnvironment ->
                doReturn(File("my-build-staging-directory"))
                    .`when`(it.cmake).buildStagingDirectory
                val module = tryCreateCxxModuleModel(it.global)!!
                val finalStagingDir = module.cxxFolder
                assertThat(logEnvironment.errors).hasSize(0)
                assertThat(finalStagingDir.path).contains("my-build-staging-directory")
            }
        }
    }

    @Test
    fun `remap of buildStagingDirectory into build folder`() {
        BasicCmakeMock().let {
            RecordingLoggingEnvironment().use { logEnvironment ->
                doReturn(File(it.project.buildDir, "my-build-staging-directory"))
                    .`when`(it.cmake).buildStagingDirectory
                val module = tryCreateCxxModuleModel(it.global)!!
                assertThat(logEnvironment.errors).hasSize(0)
                val finalStagingDir = module.cxxFolder
                assertThat(logEnvironment.errors).hasSize(1)
                assertThat(logEnvironment.errors[0])
                    .contains("The build staging directory you specified")
                assertThat(finalStagingDir.path).doesNotContain("build-dir")
                assertThat(finalStagingDir.path).contains(".cxx")
            }
        }
    }
}