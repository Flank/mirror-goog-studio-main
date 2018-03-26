/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.scope.CodeShrinker
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests using Proguard/R8 to shrink and obfuscate code in a project with features.
 *
 * Project roughly structured as follows (see implementation below for exact structure) :
 *
 * <pre>
 *   instantApp  --->                --->  library2  ------>
 *                     otherFeature                           library1
 *   app  ---------->                --->  baseFeature  --->
 *
 * More explicitly,
 *   instantApp  depends on  otherFeature, baseFeature
 *          app  depends on  otherFeature, baseFeature
 * otherFeature  depends on  library2, baseFeature
 *  baseFeature  depends on  library1
 *     library2  depends on  library1
 * </pre>
 */
@RunWith(FilterableParameterized::class)
class MinifyFeaturesTest(val codeShrinker: CodeShrinker) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "codeShrinker {0}")
        fun getConfigurations() = listOf(CodeShrinker.PROGUARD, CodeShrinker.R8)
    }

    // TODO: add java classes and proguard rules to modules below, similar to "minify" test project.

    private val lib1 = MinimalSubProject.lib("com.example.lib1")

    private val lib2 = MinimalSubProject.lib("com.example.lib2")

    private val baseFeature = MinimalSubProject.feature("com.example.baseFeature")
            .appendToBuild("""
                    android {
                        baseFeature true
                        buildTypes {
                            debug {
                                minifyEnabled true
                                proguardFiles getDefaultProguardFile('proguard-android.txt'),
                                        "proguard-rules.pro"
                            }
                        }
                    }
                    """)
            .withFile(
                    "src/main/java/com/example/baseFeature/EmptyClassToKeep.java",
                    """package com.example.baseFeature;
                    public class EmptyClassToKeep {
                    }""")
            .withFile(
                    "proguard-rules.pro",
                    """-keep public class com.example.baseFeature.EmptyClassToKeep""")

    private val otherFeature = MinimalSubProject.feature("com.example.otherFeature")

    private val app = MinimalSubProject.app("com.example.app")

    private val instantApp = MinimalSubProject.instantApp()

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .subproject(":baseFeature", baseFeature)
                    .subproject(":otherFeature", otherFeature)
                    .subproject(":app", app)
                    .subproject(":instantApp", instantApp)
                    .dependency(app, otherFeature)
                    .dependency(otherFeature, lib2)
                    .dependency(otherFeature, baseFeature)
                    .dependency(lib2, lib1)
                    .dependency(baseFeature, lib1)
                    .dependency(instantApp, baseFeature)
                    .dependency(instantApp, otherFeature)
                    // Reverse dependencies for the instant app.
                    .dependency("application", baseFeature, app)
                    .dependency("feature", baseFeature, otherFeature)
                    .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testApksAreMinified() {
        project.executor()
                .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
                .run("assembleDebug")

        val baseFeatureApk =
                project.getSubproject(":baseFeature")
                        .getFeatureApk(GradleTestProject.ApkType.DEBUG)
        assertThat(baseFeatureApk).containsClass("Lcom/example/baseFeature/EmptyClassToKeep;")
    }
}

