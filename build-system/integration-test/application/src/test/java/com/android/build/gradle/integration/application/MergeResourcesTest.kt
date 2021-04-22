/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.builder.internal.packaging.ApkCreatorType
import com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER
import com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR
import com.android.testutils.apk.Apk
import com.android.testutils.apk.Zip
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files

@RunWith(FilterableParameterized::class)
class MergeResourcesTest(val apkCreatorType: ApkCreatorType) {

    companion object {
        @Parameterized.Parameters(name = "apkCreatorType_{0}")
        @JvmStatic
        fun params() = listOf(APK_Z_FILE_CREATOR, APK_FLINGER)
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("projectWithModules")
        .setApkCreatorType(apkCreatorType)
        .create()

    @Test
    fun mergesRawWithLibraryWithOverride() {
        /*
         * Set app to depend on library.
         */
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            "dependencies { api project(':library') }${System.lineSeparator()}"
        )

        project.executor().run(":app:assembleDebug")

        val rDef = FileUtils.join(
            project.getSubproject("library").intermediatesDir,
            "local_only_symbol_list",
            "debug",
            "R-def.txt"
        )

        assertThat(rDef).exists()
        assertThat(rDef).doesNotContain("raw me")

        /*
         * Create raw/me.raw in library and see that it comes out in the apk.
         *
         * It should also show up in build/intermediates/merged_res/debug/raw/me.raw
         */
        val libraryRaw = FileUtils.join(project.projectDir, "library", "src", "main", "res", "raw")
        FileUtils.mkdirs(libraryRaw)
        Files.write(File(libraryRaw, "me.raw").toPath(), byteArrayOf(0, 1, 2))

        project.executor().run(":app:assembleDebug")

        assertThat(rDef).exists()
        assertThat(rDef).contains("raw me")

        assertThat(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent("res/raw/me.raw", byteArrayOf(0, 1, 2))

        val inIntermediate = File(
                MERGED_RES.getOutputDir(project.getSubproject("app").buildDir),
                "debug/raw_me.raw.flat")
        val inCompiledLocalResources = FileUtils.join(
            project.getSubproject("library").projectDir,
            "build",
            "intermediates",
            "compiled_local_resources",
            "debug",
            "out",
            "raw_me.raw.flat"
        )

        if (project.booleanOptions.getOrDefault(
                BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES,
                BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES.defaultValue
            ) == true
        ) {
            assertThat(inIntermediate).doesNotExist()
            assertThat(inCompiledLocalResources).exists()
        } else {
            assertThat(inIntermediate).exists()
            assertThat(inCompiledLocalResources).doesNotExist()
        }

        /*
         * Create raw/me.raw in application and see that it comes out in the apk, overriding the
         * library's.
         *
         * The change should also show up in build/intermediates/merged_res/debug/raw/me.raw
         */

        val appRaw = FileUtils.join(project.projectDir, "app", "src", "main", "res", "raw")
        FileUtils.mkdirs(appRaw)
        Files.write(File(appRaw, "me.raw").toPath(), byteArrayOf(3))

        project.executor().run(":app:assembleDebug")

        assertThat(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent("res/raw/me.raw", byteArrayOf(3))
        assertThat(inIntermediate).exists()

        /*
         * Now, modify the library's and check that nothing changed.
         */
        val apUnderscore = FileUtils.join(
            project.getSubproject("app").projectDir,
            "build",
            "intermediates",
            "processed_res",
            "debug",
            "out",
            "resources-debug.ap_"
        )

        assertThat(apUnderscore).exists()

        Files.write(File(libraryRaw, "me.raw").toPath(), byteArrayOf(0, 1, 2, 4))

        project.executor().run(":app:assembleDebug")

        assertThat(project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .containsFileWithContent("res/raw/me.raw", byteArrayOf(3))

        assertThat(inIntermediate).wasModifiedAt(inIntermediate.lastModified())
        assertThat(apUnderscore).wasModifiedAt(apUnderscore.lastModified())
        // Sometimes fails with the APK being modified even when it shouldn't. b/37617310
        //assertThat(apk).wasModifiedAt(apkModified);
    }

    @Test
    fun removeResourceFile() {
        /*
         * Add a resource file to the project and build it.
         */
        val raw = FileUtils.join(project.projectDir, "app", "src", "main", "res", "raw")
        FileUtils.mkdirs(raw)
        Files.write(File(raw, "me.raw").toPath(), byteArrayOf(0, 1, 2))
        project.executor().run(":app:assembleDebug")

        /*
         * Check that the file is merged and in the apk.
         */
        val inIntermediate = File(
                MERGED_RES.getOutputDir(project.getSubproject("app").buildDir),
                "debug/raw_me.raw.flat")
        assertThat(inIntermediate).exists()

        val apUnderscore = FileUtils.join(
            project.getSubproject("app").projectDir,
            "build",
            "intermediates",
            "processed_res",
            "debug",
            "out",
            "resources-debug.ap_"
        )

        assertThat(apUnderscore).exists()
        Zip(apUnderscore).use { zip ->
            assertThat(zip).containsFileWithContent(
                "res/raw/me.raw",
                byteArrayOf(0, 1, 2)
            )
        }

        /*
         * Remove the resource from the project and build the project incrementally.
         */
        assertTrue(File(raw, "me.raw").delete())
        project.executor().run(":app:assembleDebug")

        /*
         * Check that the file has been removed from the intermediates and from the apk.
         */
        assertThat(inIntermediate).doesNotExist()
        Zip(apUnderscore).use { zip -> assertThat(zip).doesNotContain("res/raw/me.raw") }
    }

    @Test
    fun updateResourceFile() {
        /*
         * Add a resource file to the project and build it.
         */
        val raw = FileUtils.join(project.projectDir, "app", "src", "main", "res", "raw")
        FileUtils.mkdirs(raw)
        Files.write(File(raw, "me.raw").toPath(), byteArrayOf(0, 1, 2))
        project.executor().run(":app:assembleDebug")

        /*
         * Check that the file is merged and in the apk.
         */
        val inIntermediate = File(
                MERGED_RES.getOutputDir(project.getSubproject("app").buildDir),
                "debug/raw_me.raw.flat")
        assertThat(inIntermediate).exists()

        val apUnderscore = FileUtils.join(
            project.getSubproject("app").projectDir,
            "build",
            "intermediates",
            "processed_res",
            "debug",
            "out",
            "resources-debug.ap_"
        )

        assertThat(apUnderscore).exists()
        Apk(apUnderscore).use { apk ->
            assertThat(apk).containsFileWithContent(
                "res/raw/me.raw",
                byteArrayOf(0, 1, 2)
            )
        }

        /*
         * Change the resource file from the project and build the project incrementally.
         */
        Files.write(File(raw, "me.raw").toPath(), byteArrayOf(1, 2, 3, 4))
        project.executor().run(":app:assembleDebug")

        /*
         * Check that the file has been updated in the intermediates directory and in the project.
         */
        assertThat(inIntermediate).exists()

        Apk(apUnderscore).use { apk ->
            assertThat(apk).containsFileWithContent(
                "res/raw/me.raw",
                byteArrayOf(1, 2, 3, 4)
            )
        }
    }

    @Test
    fun replaceResourceFileWithDifferentExtension() {
        /*
         * Add a resource file to the project and build it.
         */
        val raw = FileUtils.join(project.projectDir, "app", "src", "main", "res", "raw")
        FileUtils.mkdirs(raw)
        Files.write(File(raw, "me.raw").toPath(), byteArrayOf(0, 1, 2))
        project.executor().run(":app:assembleDebug")

        /*
         * Check that the file is merged and in the apk.
         */
        val inIntermediate = File(
                MERGED_RES.getOutputDir(project.getSubproject("app").buildDir),
                "debug/raw_me.raw.flat")
        assertThat(inIntermediate).exists()

        val apUnderscore = FileUtils.join(
            project.getSubproject("app").projectDir,
            "build",
            "intermediates",
            "processed_res",
            "debug",
            "out",
            "resources-debug.ap_"
        )

        assertThat(apUnderscore).exists()
        Apk(apUnderscore).use { apk ->
            assertThat(apk).containsFileWithContent(
                "res/raw/me.raw",
                byteArrayOf(0, 1, 2)
            )
        }

        /*
         * Change the resource file with one with a different extension and build the project
         * incrementally.
         */
        assertTrue(File(raw, "me.raw").delete())
        Files.write(File(raw, "me.war").toPath(), byteArrayOf(1, 2, 3, 4))
        project.executor().run(":app:assembleDebug")

        /*
         * Check that the file has been updated in the intermediates directory and in the project.
         */
        assertThat(inIntermediate).doesNotExist()
        assertThat(File(inIntermediate.parent, "raw_me.war.flat")).exists()
        assertThat(apUnderscore).doesNotContain("res/raw/me.raw")
        Apk(apUnderscore).use { apk ->
            assertThat(apk).containsFileWithContent(
                "res/raw/me.war",
                byteArrayOf(1, 2, 3, 4)
            )
        }
    }

    @Test
    fun injectedMinSdk() {
        val appProject = project.getSubproject(":app")
        val newMainLayout = appProject.file("src/main/res/layout-v23/main.xml")
        Files.createDirectories(newMainLayout.parentFile.toPath())

        // This layout does not define the "foo" ID.
        FileUtils.createFile(
            newMainLayout,
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                    + "android:orientation=\"horizontal\" "
                    + "android:layout_width=\"fill_parent\" "
                    + "android:layout_height=\"fill_parent\"> "
                    + "</LinearLayout>\n"
        )

        TestFileUtils.addMethod(
            appProject.file("src/main/java/com/example/android/multiproject/MainActivity.java"),
            "public int useFoo() { return R.id.foo; }"
        )

        project.executor().with(IntegerOption.IDE_TARGET_DEVICE_API, 23).run(":app:assembleDebug")
    }

    @Test
    fun testIncrementalBuildWithShrinkResources() {
        // Regression test for http://issuetracker.google.com/65829618
        val appProject = project.getSubproject("app")
        val appBuildFile = appProject.buildFile
        TestFileUtils.appendToFile(
            appBuildFile,
            ("""android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                            shrinkResources true
                        }
                    }
                }
                dependencies {
                    implementation 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
                }
            """)
        )

        // Run a full build with shrinkResources enabled
        var result = project.executor().run(":app:clean", ":app:assembleDebug")
        assertThat(result.getTask(":app:mergeDebugResources")).didWork()
        val apkSizeWithShrinkResources =
            appProject.getApk(GradleTestProject.ApkType.DEBUG).contentsSize

        // Run an incremental build with shrinkResources disabled, the MergeResources task should
        // not be UP-TO-DATE and the apk size should be larger
        TestFileUtils.searchAndReplace(
            appBuildFile, "shrinkResources true", "shrinkResources false"
        )
        result = project.executor().run(":app:assembleDebug")
        assertThat(result.getTask(":app:mergeDebugResources")).didWork()
        val apkSizeWithoutShrinkResources =
            appProject.getApk(GradleTestProject.ApkType.DEBUG).contentsSize
        assertThat(apkSizeWithoutShrinkResources).isGreaterThan(apkSizeWithShrinkResources)

        // Run an incremental build again with shrinkResources enabled, the MergeResources task
        // again should not be UP-TO-DATE and the apk size must be exactly the same as the first
        TestFileUtils.searchAndReplace(
            appBuildFile, "shrinkResources false", "shrinkResources true"
        )
        result = project.executor().run(":app:assembleDebug")
        assertThat(result.getTask(":app:mergeDebugResources")).didWork()
        val sameApkSizeShrinkResources =
            appProject.getApk(GradleTestProject.ApkType.DEBUG).contentsSize
        assertThat(sameApkSizeShrinkResources).isEqualTo(apkSizeWithShrinkResources)
    }

    @Test
    fun checkSmallMergeInApp() {
        /*
         * Set app to depend on library.
         */
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            "dependencies { api project(':library') }${System.lineSeparator()}"
        )

        val libraryValues = FileUtils.join(project.projectDir, "library", "src", "main", "res", "values")
        FileUtils.mkdirs(libraryValues)
        FileUtils.createFile(
            File(libraryValues, "lib_values.xml"),
            "<resources><string name=\"my_library_string\">lib string</string></resources>")

        project.executor()
            .with(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS, false)
            .run("clean", ":app:assembleDebug")

        val inMergedDir = FileUtils.join(
            project.getSubproject("app").projectDir,
            "build",
            "intermediates",
            "incremental",
            "mergeDebugResources",
            "merged.dir",
            "values",
            "values.xml"
        )

        val smallMerge = FileUtils.join(
            project.getSubproject("app").projectDir,
            "build",
            "intermediates",
            "packaged_res",
            "debug",
            "values",
            "values.xml"
        )

        assertThat(inMergedDir).contains("my_library_string")
        assertThat(smallMerge).doesNotExist()

        project.executor()
            .with(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS, true)
            .run("clean", ":app:generateDebugRFile")

        assertThat(inMergedDir).doesNotExist()
        assertThat(smallMerge).doesNotContain("my_library_string")
    }

    @Test
    fun testVectorDrawablesWithVersionQualifiersRenderCorrectly() {
        val appProject = project.getSubproject(":app")
        val appBuildFile = appProject.buildFile
        TestFileUtils.appendToFile(appBuildFile,
                "android { defaultConfig { minSdkVersion 19 }    }"
        )

        val drawable = appProject.file("src/main/res/drawable/icon.xml")
        val drawable24 = appProject.file("src/main/res/drawable-v24/icon.xml")
        val drawable28 = appProject.file("src/main/res/drawable-v28/icon.xml")

        FileUtils.createFile(drawable, "<vector>a</vector>")
        FileUtils.createFile(drawable24, "<vector>b</vector>")
        FileUtils.createFile(drawable28, "<vector>c</vector>")

        val generatedPngs = FileUtils.join(appProject.projectDir,
                "build", "generated", "res", "pngs", "debug")

        project.executor()
                .run(":app:assembleDebug")

        assertThat(FileUtils.join(generatedPngs, "drawable-anydpi-v21", "icon.xml")
                .readLines()).containsExactlyElementsIn(listOf("<vector>a</vector>"))

        assertThat(FileUtils.join(generatedPngs, "drawable-anydpi-v24", "icon.xml")
                .readLines()).containsExactlyElementsIn(listOf("<vector>b</vector>"))

        assertThat(FileUtils.join(generatedPngs, "drawable-anydpi-v28", "icon.xml")
                .readLines()).containsExactlyElementsIn(listOf("<vector>c</vector>"))
    }

}

