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
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.apk.AndroidArchive
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class GlobalSyntheticsFeatureTest {

    private val app = MinimalSubProject.app("com.example.app").also {
        it.appendToBuild("""

            android.defaultConfig.minSdkVersion = 21
            android.dynamicFeatures = [":feature"]
        """.trimIndent())
    }

    private val feature = MinimalSubProject.dynamicFeature("com.example.feature").also {
        it.appendToBuild("""

            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation project('::app')
            }
        """.trimIndent())
        it.addFile("src/main/java/com/example/feature/IllformedLocaleExceptionUsage.java",
            """
                package com.example.feature;

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

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject("app", app)
                .subproject("feature", feature)
                .build()
        ).create()

    @Test
    fun basicTest() {
        project.executor()
            .with(BooleanOption.ENABLE_GLOBAL_SYNTHETICS, true)
            .run("assembleDebug")

        checkPackagedGlobal("Landroid/icu/util/IllformedLocaleException;")
    }

    private fun checkPackagedGlobal(global: String) {
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)

        // there should only be a single global synthetics of specific type in the apk
        val dexes = apk.allDexes.filter {
            AndroidArchive.checkValidClassName(global)
            it.classes.keys.contains(global)
        }
        Truth.assertThat(dexes.size).isEqualTo(1)
    }
}
