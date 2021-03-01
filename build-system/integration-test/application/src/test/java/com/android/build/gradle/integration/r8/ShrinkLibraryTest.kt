/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.r8

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShrinkLibraryTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                        }
                    }
                }
            """.trimIndent()
        )

        TestFileUtils.searchAndReplace(
            FileUtils.join(project.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
            "// onCreate",
            """
                new java.util.ArrayList<Integer>().forEach( (n) -> System.out.println(n));
            """.trimIndent()
        )
    }

    @Test
    fun testLambdaStubOnBootclasspath() {
        compileWithJava8Target()
        project.executor()
            .with(BooleanOption.R8_FAIL_ON_MISSING_CLASSES, true)
            .run(":assembleDebug")
    }

    private fun compileWithJava8Target() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                    }
                }
            """.trimIndent()
        )
    }
}
