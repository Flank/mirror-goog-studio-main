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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.AbiMatcher
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.StringOption
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexClassSubject
import com.android.utils.FileUtils
import com.google.common.io.Resources
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import kotlin.test.fail

@RunWith(FilterableParameterized::class)
class CoreLibraryDesugarConversionTest(val minSdkVersion: Int) {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPluginWithMinSdkVersion("com.android.application", minSdkVersion))
        .create()

    @get:Rule
    var adb = Adb()

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
                }
                android.defaultConfig.multiDexEnabled = true
                dependencies {
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent())

        // add a function with desugar library parameter, which is called from application
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static int[] getNumbers() {
                    int[] numbers = new int[3];
                    java.util.Arrays.setAll(numbers, index -> index * 5);
                    return numbers;
                }
            """.trimIndent())

        // add a function with desugar library parameter, which is called from application
        // regression test for b/150774053, make sure it works when minSdkVersion is 24
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static String getTime() {
                    return java.util.TimeZone.getTimeZone(java.time.ZoneId.of("GMT")).getID();
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
            FileUtils.join(project.testDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetNumbers() {
                    Assert.assertEquals(5, HelloWorld.getNumbers()[1]);
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.testDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetTime() {
                    Assert.assertEquals("GMT", HelloWorld.getTime());
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.testDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetDirectActions() {
                    rule.getActivity().onGetDirectActions(new android.os.CancellationSignal(),
                        x -> x.forEach(it -> System.out.println(it.getId())));
                }
            """.trimIndent())
    }

    @Test
    @Category(DeviceTests::class)
    fun testFunctionWithDesugaredLibraryParam() {
        val device = adb.getDevice(AndroidVersionMatcher.exactly(29), AbiMatcher.anyAbi())
        // check non-minified debug build(d8 without keep rules)
        project.executor()
            .with(StringOption.DEVICE_POOL_SERIAL, device.serialNumber)
            .run("clean", "connectedDebugAndroidTest")
        // check minified debug build(r8 with keep rules)
        project.buildFile.appendText("""

            android.buildTypes.debug.minifyEnabled = true
        """.trimIndent())
        TestFileUtils.searchAndReplace(
            FileUtils.join(project.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
            "// onCreate",
            "getNumbers(); getTime();"
        )
        project.executor()
            .with(StringOption.DEVICE_POOL_SERIAL, device.serialNumber)
            .run("clean", "connectedDebugAndroidTest")
        // check non-minified release build(d8 with keep rules)
        setupKeyStore()
        project.buildFile.appendText("""

            android.testBuildType "release"
        """.trimIndent())
        project.executor()
            .with(StringOption.DEVICE_POOL_SERIAL, device.serialNumber)
            .run("clean", "connectedReleaseAndroidTest")
    }

    //TODO(bingran) remove once we enable connected tests in presubmit/postsubmit
    @Test
    fun testBytecodeOfFunctionWithDesugaredLibraryParam() {
        project.executor().run("clean", "assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        val programClass = "Lcom/example/helloworld/HelloWorld;"
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getTime", "Lj$/time/TimeConversions;->convert(Lj$/time/ZoneId;)Ljava/time/ZoneId;")
        // Consumer and IntUnaryOperator are desugared up to 23 so conversion doesn't exist for 24 and above
        Assume.assumeTrue(minSdkVersion < 24)
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("getNumbers", "Lj\$/\$r8\$wrapper\$java\$util\$function\$IntUnaryOperator$-WRP;->convert(Lj$/util/function/IntUnaryOperator;)Ljava/util/function/IntUnaryOperator;")
        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes("onGetDirectActions", "Lj\$/\$r8\$wrapper\$java\$util\$function\$Consumer$-V-WRP;->convert(Ljava/util/function/Consumer;)Lj$/util/function/Consumer;")
    }

    private fun setupKeyStore() {
        val keystoreFile = project.file(STORE_FILE_NAME)
        val keystoreContents =
            Resources.toByteArray(
                Resources.getResource(SigningTest::class.java, "SigningTest/rsa_keystore.jks")
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

    private fun getDexWithSpecificClass(className: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            AndroidArchive.checkValidClassName(className)
            it.classes.keys.contains(className)
        }

    companion object {
        @Parameterized.Parameters(name = "minSdkVersion_{0}")
        @JvmStatic
        fun params() = listOf(21, 24)

        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
        private const val STORE_FILE_NAME = "keystore.jks"
        private const val STORE_PASSWORD = "store_password"
        private const val ALIAS_NAME = "alias_name"
        private const val KEY_PASSWORD = "key_password"
    }
}