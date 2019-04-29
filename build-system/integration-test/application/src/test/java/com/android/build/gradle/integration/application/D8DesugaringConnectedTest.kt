/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TEST_SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.AbstractAndroidTestModule
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableMap
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

class D8DesugaringConnectedTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject(
                ImmutableMap.of<String, AbstractAndroidTestModule>(
                    ":app",
                    HelloWorldApp.noBuildFile(),
                    ":lib",
                    EmptyAndroidTestApp()
                )
            )
        )
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                    apply plugin: "com.android.application"

                    apply from: "../../commonLocalRepo.gradle"

                    android {
                        compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}

                        defaultConfig {
                            applicationId "com.example.d8desugartest"
                            minSdkVersion 20
                            testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
                        }

                      flavorDimensions "whatever"

                        productFlavors {
                          multidex {
                            dimension "whatever"
                            multiDexEnabled true
                            multiDexKeepFile file('debug_main_dex_list.txt')
                          }
                          base {
                            dimension "whatever"
                          }
                        }
                        compileOptions {
                            sourceCompatibility JavaVersion.VERSION_1_8
                            targetCompatibility JavaVersion.VERSION_1_8
                        }

                        dependencies {
                            implementation project(':lib')
                            androidTestImplementation 'com.android.support:support-v4:$SUPPORT_LIB_VERSION'
                            testImplementation 'junit:junit:4.12'
                            androidTestImplementation 'com.android.support.test:runner:$TEST_SUPPORT_LIB_VERSION'
                            androidTestImplementation 'com.android.support.test:rules:$TEST_SUPPORT_LIB_VERSION'
                        }
                    }
                    """
        )

        TestFileUtils.appendToFile(
            project.getSubproject(":lib").buildFile, "apply plugin: 'java'\n"
        )
        val interfaceWithDefault = project.getSubproject(":lib")
            .file("src/main/java/com/example/helloworld/InterfaceWithDefault.java")
        FileUtils.mkdirs(interfaceWithDefault.parentFile)
        TestFileUtils.appendToFile(
            interfaceWithDefault,
            ("""package com.example.helloworld;

                    public interface InterfaceWithDefault {

                      static String defaultConvert(String input) {
                        return input + "-default";
                      }

                      default String convert(String input) {
                        return defaultConvert(input);
                      }
                    }
                    """)
        )
        val stringTool = project.getSubproject(":lib")
            .file("src/main/java/com/example/helloworld/StringTool.java")
        FileUtils.mkdirs(stringTool.parentFile)
        TestFileUtils.appendToFile(
            stringTool,
            ("""package com.example.helloworld;

                    public class StringTool {
                      private InterfaceWithDefault converter;
                      public StringTool(InterfaceWithDefault converter) {
                        this.converter = converter;
                      }
                      public String convert(String input) {
                        return converter.convert(input);
                      }
                    }
                    """)
        )
        val exampleInstrumentedTest = project.getSubproject(":app")
            .file(
                "src/androidTest/java/com/example/helloworld/ExampleInstrumentedTest.java"
            )
        FileUtils.mkdirs(exampleInstrumentedTest.parentFile)
        TestFileUtils.appendToFile(
            exampleInstrumentedTest,
            ("""package com.example.helloworld;

                    import android.content.Context;
                    import android.support.test.InstrumentationRegistry;
                    import android.support.test.runner.AndroidJUnit4;

                    import org.junit.Test;
                    import org.junit.runner.RunWith;

                    import static org.junit.Assert.*;

                    @RunWith(AndroidJUnit4.class)
                    public class ExampleInstrumentedTest {
                        @Test
                        public void useAppContext() throws Exception {
                            // Context of the app under test.
                            Context appContext = InstrumentationRegistry.getTargetContext();
                            assertEquals("toto-default", new StringTool(new InterfaceWithDefault() { }).convert("toto"));
                        }
                    }
                    """)
        )
        val debugMainDexList = project.getSubproject(":app").file("debug_main_dex_list.txt")
        TestFileUtils.appendToFile(
            debugMainDexList, "com/example/helloworld/InterfaceWithDefault.class"
        )
    }

    @Category(DeviceTests::class)
    @Test
    fun runAndroidTest() {
        val result = project.executor()
            .with(BooleanOption.ENABLE_D8, true)
            .with(BooleanOption.ENABLE_D8_DESUGARING, true)
            .run("app:connectedBaseDebugAndroidTest")
        result.stdout.use { stdout -> assertThat(stdout).contains("Starting 2 tests on") }
    }
}
