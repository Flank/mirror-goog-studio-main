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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class IncrementalGlobalSyntheticsTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject("app", MinimalSubProject.app("com.example.app"))
                .build())
        .create()

    private lateinit var app: GradleTestProject

    private lateinit var changedSourceInApp: File
    private lateinit var changedGlobalInApp: File
    private lateinit var changedDexInApp: File
    private lateinit var unchangedDexInApp: File

    @Before
    fun setUp() {
        app = project.getSubproject("app")

        changedSourceInApp = app.mainSrcDir.resolve("com/example/app/IllformedLocaleExceptionUsage.java")
        changedGlobalInApp = app.buildDir.resolve("intermediates/global_synthetics_project/debug/out/com/example/app/IllformedLocaleExceptionUsage.globals")
        changedDexInApp = app.buildDir.resolve("intermediates/project_dex_archive/debug/out/com/example/app/IllformedLocaleExceptionUsage.dex")
        unchangedDexInApp = app.buildDir.resolve("intermediates/project_dex_archive/debug/out/com/example/app/Main.dex")

        TestFileUtils.appendToFile(
            app.buildFile,
            """

                android.defaultConfig.minSdkVersion  21
            """.trimIndent()
        )
        app.mainSrcDir.resolve("com/example/app/Main.java").also {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.writeText(
                """
                    package com.example.app;

                    public class Main {
                        public void function() {}
                    }
                """.trimIndent()
            )
        }
    }

    @Test
    fun testAddOrRemoveDexFileInTask() {
        executor().run("assembleDebug")

        Truth.assertThat(changedGlobalInApp.exists()).isFalse()
        Truth.assertThat(changedDexInApp.exists()).isFalse()
        Truth.assertThat(unchangedDexInApp.exists()).isTrue()

        val unchangedTimeStamp = Files.getLastModifiedTime(unchangedDexInApp.toPath())

        changedSourceInApp.let {
            it.createNewFile()
            it.writeText(
                """
                    package com.example.app;

                    public class IllformedLocaleExceptionUsage {
                        public void function() {
                            try {
                                throw new android.icu.util.IllformedLocaleException();
                            } catch (android.icu.util.IllformedLocaleException e) {}
                        }
                    }
                """.trimIndent()
            )
        }
        executor().run("assembleDebug")

        Truth.assertThat(changedGlobalInApp.exists()).isTrue()
        Truth.assertThat(changedDexInApp.exists()).isTrue()
        checkFileIsUnchanged(unchangedTimeStamp, unchangedDexInApp)

        FileUtils.deleteIfExists(changedSourceInApp)
        executor().run("assembleDebug")

        Truth.assertThat(changedGlobalInApp.exists()).isFalse()
        Truth.assertThat(changedDexInApp.exists()).isFalse()
        checkFileIsUnchanged(unchangedTimeStamp, unchangedDexInApp)
    }

    @Test
    fun testModifyDexFileInTask() {
        changedSourceInApp.let {
            it.createNewFile()
            it.writeText(
                """
                    package com.example.app;

                    public class IllformedLocaleExceptionUsage {
                        public void function() {
                            try {
                                throw new android.icu.util.IllformedLocaleException();
                            } catch (android.icu.util.IllformedLocaleException e) {}
                        }
                    }
                """.trimIndent()
            )
        }
        executor().run("assembleDebug")

        Truth.assertThat(changedGlobalInApp.exists()).isTrue()
        Truth.assertThat(changedDexInApp.exists()).isTrue()
        Truth.assertThat(unchangedDexInApp.exists()).isTrue()
        val unchangedTimeStamp = Files.getLastModifiedTime(unchangedDexInApp.toPath())
        var changedDexTimeStamp = Files.getLastModifiedTime(changedDexInApp.toPath())

        TestFileUtils.searchAndReplace(
            changedSourceInApp,
            "throw new android.icu.util.IllformedLocaleException();",
            "//comment out"
        )
        executor().run("assembleDebug")

        Truth.assertThat(changedGlobalInApp.exists()).isFalse()
        checkFileIsChanged(changedDexTimeStamp, changedDexInApp)
        checkFileIsUnchanged(unchangedTimeStamp, unchangedDexInApp)

        changedDexTimeStamp = Files.getLastModifiedTime(changedDexInApp.toPath())

        TestFileUtils.searchAndReplace(
            changedSourceInApp,
            "//comment out",
            "throw new android.icu.util.IllformedLocaleException();"
        )
        executor().run("assembleDebug")

        Truth.assertThat(changedGlobalInApp.exists()).isTrue()
        checkFileIsChanged(changedDexTimeStamp, changedDexInApp)
        checkFileIsUnchanged(unchangedTimeStamp, unchangedDexInApp)
    }

    private fun checkFileIsUnchanged(oldTimeStamp: FileTime, file: File) {
        Truth.assertThat(file.exists()).isTrue()
        Truth.assertThat(oldTimeStamp == Files.getLastModifiedTime(file.toPath()))
    }

    private fun checkFileIsChanged(oldTimeStamp: FileTime, file: File) {
        Truth.assertThat(file.exists()).isTrue()
        Truth.assertThat(oldTimeStamp != Files.getLastModifiedTime(file.toPath()))
    }

    private fun executor() = app.executor().with(BooleanOption.ENABLE_GLOBAL_SYNTHETICS, true)
}
