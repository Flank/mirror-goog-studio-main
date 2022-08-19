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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Test property values in Variant API.  */
class VariantApiPropertiesTest {
    @get:Rule
    val project = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun checkMergedJavaCompileOptions() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android {
                buildTypes {
                    debug {
                        javaCompileOptions.annotationProcessorOptions {
                            className 'Foo'
                            argument 'value', 'debugArg'
                        }
                    }
                }
                flavorDimensions 'dimension'
                productFlavors {
                    flavor1 {
                        javaCompileOptions.annotationProcessorOptions {
                            className 'Bar'
                            argument 'value', 'flavor1Arg'
                        }
                    }
                }
                applicationVariants.all { variant ->
                    def options = variant.javaCompileOptions.annotationProcessorOptions
                    if (variant.name == 'flavor1Debug') {
                        assert options.classNames == ['Bar', 'Foo']
                        assert options.arguments.get('value') == 'debugArg'
                    }
                }
            }
            """.trimIndent()
        )
        project.executor().run("help")
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun checkOutputFileName() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android {
                flavorDimensions 'dimension'
                productFlavors {
                    flavor1 {
                    }
                }
                applicationVariants.all { variant ->
                    if (variant.name == 'flavor1Debug') {
                        assert variant.outputs.first().outputFileName == 'project-flavor1-debug.apk'
                        def outputFileName = variant.outputs.first().outputFileName
                        def variantOutput = variant.outputs.first()
                        variantOutput.outputFileName = outputFileName.replace('flavor1', "flavor1-${"$"}{variant.versionName}")
                        assert variantOutput.outputFile == project.file("build/outputs/apk/flavor1/debug/${"$"}{variantOutput.outputFileName}")
                    }
                    if (variant.name == 'flavor1Release') {
                        assert variant.outputs.first().outputFileName == 'project-flavor1-release-unsigned.apk'
                    }
                }
            }
            """.trimIndent()
        )
        project.executor().run("assembleFlavor1Debug")
        TruthHelper.assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "flavor1")).doesNotExist()
        TruthHelper.assertThat(project.getApk("1.0", GradleTestProject.ApkType.DEBUG, GradleTestProject.ApkLocation.Output, "flavor1")).exists()
    }
}
