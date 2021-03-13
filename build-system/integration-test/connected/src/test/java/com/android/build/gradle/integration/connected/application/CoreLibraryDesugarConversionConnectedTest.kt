/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.utils.FileUtils
import com.google.common.io.Resources
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files

/** Connected test for core library desugaring API conversion. */
@RunWith(FilterableParameterized::class)
class CoreLibraryDesugarConversionConnectedTest(minSdkVersion: Int) {

    companion object {
        @ClassRule
        @JvmField
        val emulator = getEmulator()

        @Parameterized.Parameters(name = "minSdkVersion_{0}")
        @JvmStatic
        fun params() = listOf(21, 24)

        private const val DESUGAR_DEPENDENCY =
            "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
        private const val STORE_FILE_NAME = "keystore.jks"
        private const val STORE_PASSWORD = "store_password"
        private const val ALIAS_NAME = "alias_name"
        private const val KEY_PASSWORD = "key_password"
    }

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                HelloWorldApp.forPluginWithMinSdkVersion("com.android.application", minSdkVersion)
            )
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
            // b/149978740
            .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=1")
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }

                    defaultConfig {
                        multiDexEnabled true
                    }
                }
                dependencies {
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent())

        // fail fast if no response
        project.addAdbTimeout()

        // add a function with desugar library parameter, which is called from application
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static int[] getNumbers() {
                    // TODO(b/182255766): Fix this.
                    // int[] numbers = new int[3];
                    // java.util.Arrays.setAll(numbers, index -> index * 5);
                    // return numbers;
                    return null;
                }
            """.trimIndent())

        // add a function with desugar library parameter, which is called from application
        // regression test for b/150774053, make sure it works when minSdkVersion is 24
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static String getTime() {
                    // TODO(b/182255766): Fix this.
                    // return java.util.TimeZone.getTimeZone(java.time.ZoneId.of("GMT")).getID();
                    return null;
                }
            """.trimIndent())

        // add a function with desugar library parameter, which is called from android platform
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                @Override
                public void onGetDirectActions(android.os.CancellationSignal cancellationSignal,
                    java.util.function.Consumer<java.util.List<android.app.DirectAction>> callback) {
                    callback.accept(java.util.Collections.singletonList(new android.app.DirectAction.Builder("1").build()));
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetNumbers() {
                    // TODO(b/182255766): Fix this.
                    // Assert.assertEquals(5, HelloWorld.getNumbers()[1]);
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetTime() {
                    // TODO(b/182255766): Fix this.
                    // Assert.assertEquals("GMT", HelloWorld.getTime());
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetDirectActions() {
                    rule.getActivity().onGetDirectActions(new android.os.CancellationSignal(),
                        x -> x.forEach(it -> System.out.println(it.getId())));
                }
            """.trimIndent())

        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    fun testFunctionWithDesugaredLibraryParam() {
        // check non-minified debug build (d8 without keep rules)
        project.executor().run("connectedDebugAndroidTest")

        // check minified debug build (r8 with keep rules)
        project.buildFile.appendText("\n\nandroid.buildTypes.debug.minifyEnabled = true\n\n")
        TestFileUtils.searchAndReplace(
            FileUtils.join(project.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
            "// onCreate",
            "getNumbers(); getTime();"
        )
        project.executor().run("connectedDebugAndroidTest")
    }

    // TODO(bingran) This test is temporarily disabled because of b/126429384. For more details,
    // see b/177973669.
    @Ignore
    @Test
    fun testFunctionWithDesugaredLibraryParamInNonMinifiedReleaseBuild() {
        // check non-minified release build (d8 with keep rules)
        setupKeyStore()
        project.buildFile.appendText("\n\nandroid.testBuildType \"release\"\n\n")
        project.executor().run("connectedReleaseAndroidTest")
    }

    private fun setupKeyStore() {
        val keystoreFile = project.file(STORE_FILE_NAME)
        val keystoreContents =
            Resources.toByteArray(
                Resources.getResource(
                    CoreLibraryDesugarConversionConnectedTest::class.java,
                    "rsa_keystore.jks"
                )
            )
        Files.write(keystoreFile.toPath(), keystoreContents)
        project.buildFile.appendText("""

            android {
                signingConfigs {
                    release {
                        storeFile file("$STORE_FILE_NAME")
                        storePassword "$STORE_PASSWORD"
                        keyAlias "$ALIAS_NAME"
                        keyPassword "$KEY_PASSWORD"
                    }
                }
                buildTypes {
                    release.signingConfig signingConfigs.release
                }
            }
        """.trimIndent())
    }
}
