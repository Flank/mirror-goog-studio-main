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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CoreLibraryDesugarCachingTest {

    @get:Rule
    val project = with(EmptyActivityProjectBuilder()) {
        this.projectName = "project"
        useGradleBuildCache = true
        gradleBuildCacheDir = File("../$GRADLE_BUILD_CACHE")
        build()
    }


    @get:Rule
    val projectCopy = with(EmptyActivityProjectBuilder()) {
        this.projectName = "projectCopy"
        useGradleBuildCache = true
        gradleBuildCacheDir = File("../$GRADLE_BUILD_CACHE")
        build()
    }

    @Before
    fun setUp() {
        for (project in listOf(project, projectCopy)) {
            configureProject(project)
        }
    }

    @Test
    fun testDifferentProjectLocations() {
        val buildCacheDir = File(project.projectDir.parent, GRADLE_BUILD_CACHE)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)

        // http://b/149978740
        val executor = project.executor().with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
        executor
            .withArgument("--build-cache")
            .run("clean", ASSEMBLE_RELEASE)
        assertThat(buildCacheDir).exists()

        val result = executor
            .withArgument("--build-cache")
            .run("clean", ASSEMBLE_RELEASE)
        assertThat(result.getTask(L8_DEX_DESUGAR_LIB)).wasFromCache()
        assertThat(result.getTask(MERGE_DEX)).wasFromCache()
        assertThat(result.getTask(DEX_BUILDER)).wasFromCache()
    }

    private fun configureProject(project: GradleTestProject) {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    defaultConfig {
                        minSdkVersion 22
                        multiDexEnabled true
                    }
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }
                }
                dependencies {
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent())


        FileUtils.join(project.getSubproject("app").mainSrcDir,
            "com/example/helloworld/HelloWorld.java")
            .also { it.parentFile.mkdirs() }
            .writeText(
                """
                    package com.example.helloworld;
                    import java.time.Month;

                    public class HelloWorld {
                        public static Month getTime() {
                            return Month.JUNE;
                        }
                    }
                """.trimIndent())
    }

    companion object {
        private const val GRADLE_BUILD_CACHE = "gradle-build-cache"
        private const val ASSEMBLE_RELEASE = ":app:assembleRelease"
        private const val L8_DEX_DESUGAR_LIB = ":app:l8DexDesugarLibRelease"
        private const val MERGE_DEX = ":app:mergeDexRelease"
        private const val DEX_BUILDER = ":app:dexBuilderRelease"
        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }
}
