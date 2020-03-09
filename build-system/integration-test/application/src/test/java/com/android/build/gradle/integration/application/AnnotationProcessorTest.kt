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

import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_BUILD_TOOL_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.app.AnnotationProcessorLib
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getAndroidTestArtifact
import com.android.build.gradle.integration.common.utils.getDebugVariant
import com.android.build.gradle.integration.common.utils.getUnitTestArtifact
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests for annotation processor.
 */
class AnnotationProcessorTest {

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject(
                mapOf<String, GradleProject>(
                    ":app" to app,
                    ":lib" to AnnotationProcessorLib.createLibrary(),
                    ":lib-compiler" to AnnotationProcessorLib.createCompiler()
                )
            )
        ).create()

    @Before
    fun setUp() {
        val testSupportLibVersion = "\${project.testSupportLibVersion}"
        val buildScript = ("""
                apply from: "../../commonHeader.gradle"
                buildscript { apply from: "../../commonBuildScript.gradle" }
                apply from: "../../commonLocalRepo.gradle"

                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion $DEFAULT_COMPILE_SDK_VERSION

                    buildToolsVersion '$DEFAULT_BUILD_TOOL_VERSION'
                    defaultConfig {
                        javaCompileOptions {
                            annotationProcessorOptions {
                                argument "value", "Hello"
                            }
                        }
                        minSdkVersion rootProject.supportLibMinSdk
                        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
                    }
                }
                dependencies {
                    androidTestImplementation (
                        "com.android.support.test:runner:$testSupportLibVersion"
                    )
                    androidTestImplementation (
                        "com.android.support.test:rules:$testSupportLibVersion"
                    )
                }
                """).trimIndent()
        Files.asCharSink(project.getSubproject(":app")
            .file("build.gradle"), Charsets.UTF_8)
            .write(buildScript)
    }

    @Test
    fun normalBuild() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
            dependencies {
                api project(':lib')
                annotationProcessor project(':lib-compiler')
            }
            """.trimIndent()
        )

        project.executor().run("assembleDebug")
        val aptOutputFolder = project.getSubproject(":app")
            .file(ANNOTATION_PROCESSOR_SOURCES_OUT_FOLDER + "debug/out")
        assertThat(File(aptOutputFolder, "com/example/helloworld/HelloWorldStringValue.java"))
            .exists()

        val model = project.model().fetchAndroidProjects().onlyModelMap[":app"]
        val debugVariant = (model)!!.getDebugVariant()

        assertThat(debugVariant.mainArtifact.generatedSourceFolders)
            .contains(aptOutputFolder)

        // Ensure that test sources also have their generated sources files sent to the IDE. This
        // specifically tests for the issue described in
        // https://issuetracker.google.com/37121918.
        val testAptOutputFolder = project.getSubproject(":app")
            .file(ANNOTATION_PROCESSOR_SOURCES_OUT_FOLDER + "debugUnitTest/out")
        val testArtifact = debugVariant.getUnitTestArtifact()
        assertThat(testArtifact.generatedSourceFolders).contains(testAptOutputFolder)

        // Ensure that test projects also have their generated sources files sent to the IDE. This
        // specifically tests for the issue described in
        // https://issuetracker.google.com/37121918.
        val androidTestAptOutputFolder = project.getSubproject(":app")
            .file(ANNOTATION_PROCESSOR_SOURCES_OUT_FOLDER + "debugAndroidTest/out")
        val androidTest = debugVariant.getAndroidTestArtifact()
        assertThat(androidTest.generatedSourceFolders).contains(androidTestAptOutputFolder)

        // check incrementality.
        val result = project.executor().run("assembleDebug")
        assertThat(result.upToDateTasks).contains(":app:javaPreCompileDebug")
    }

    @Test
    fun testBuild() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
            dependencies {
                annotationProcessor project(':lib-compiler')
                testAnnotationProcessor project(':lib-compiler')
                androidTestAnnotationProcessor project(':lib-compiler')
                api project(':lib')
            }
            """.trimIndent()
        )

        project.executor().run("assembleDebugAndroidTest", "testDebug")
        val aptOutputFolder =
            project.getSubproject(":app").file(ANNOTATION_PROCESSOR_SOURCES_OUT_FOLDER)
        assertThat(
            File(
                aptOutputFolder,
                "debugAndroidTest/out/com/example/helloworld/HelloWorldAndroidTestStringValue.java"
            )
        )
            .exists()
        assertThat(
            File(
                aptOutputFolder,
                "debugUnitTest/out/com/example/helloworld/HelloWorldTestStringValue.java"
            )
        )
            .exists()
    }

    @Test
    fun androidAptPluginFail() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            "apply plugin: 'com.neenbedankt.android-apt'\n")

        project.executor().expectFailure().run("assembleDebug")
    }

    companion object {

        private val ANNOTATION_PROCESSOR_SOURCES_OUT_FOLDER =
            "build/generated/ap_generated_sources/"
        private val app = HelloWorldApp.noBuildFile()

        init {
            app.replaceFile(
                TestSourceFile(
                    "src/main/java/com/example/helloworld/HelloWorld.java",
                    """
                    package com.example.helloworld;

                    import android.app.Activity;
                    import android.widget.TextView;
                    import android.os.Bundle;
                    import com.example.annotation.ProvideString;

                    @ProvideString
                    public class HelloWorld extends Activity {
                        /** Called when the activity is first created. */
                        @Override
                        public void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                            TextView tv = new TextView(this);
                            tv.setText(getString());
                            setContentView(tv);
                        }

                            public static String getString() {
                                return new com.example.helloworld.HelloWorldStringValue().value;
                            }

                            public static String getProcessor() {
                                return new com.example.helloworld.HelloWorldStringValue().processor;
                            }
                        }
                        """.trimIndent()
                )
            )

            app.removeFileByName("HelloWorldTest.java")

            app.addFile(
                TestSourceFile(
                    "src/test/java/com/example/helloworld/HelloWorldTest.java",
                    """
                    package com.example.helloworld;
                    import com.example.annotation.ProvideString;

                    @ProvideString
                    public class HelloWorldTest {
                    }
                    """.trimIndent()
                )
            )

            app.addFile(
                TestSourceFile(
                    "src/androidTest/java/com/example/hellojni/HelloWorldAndroidTest.java",
                    """
                    package com.example.helloworld;

                    import android.support.test.runner.AndroidJUnit4;
                    import org.junit.Assert;
                    import org.junit.Test;
                    import org.junit.runner.RunWith;
                    import com.example.annotation.ProvideString;

                    @ProvideString
                    @RunWith(AndroidJUnit4.class)
                    public class HelloWorldAndroidTest {

                        @Test
                        public void testStringValue() {
                            Assert.assertTrue("Hello".equals(HelloWorld.getString()));
                        }
                        @Test
                        public void testProcessor() {
                            Assert.assertTrue("Processor".equals(HelloWorld.getProcessor()));
                        }
                    }
                    """.trimIndent()
                )
            )
        }
    }
}
