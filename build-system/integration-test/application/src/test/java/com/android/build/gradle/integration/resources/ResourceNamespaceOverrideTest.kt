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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Sanity tests for source set overrides with resource namespacing.
 */
class ResourceNamespaceOverrideTest {

    val app = MinimalSubProject.app("com.example.app")
            .appendToBuild("""
                android {
                    aaptOptions.namespaced = true
                    flavorDimensions   "business", "market"
                    productFlavors {
                        free { dimension   "business" }
                        paid { dimension   "business" }
                        play { dimension   "market" }
                        other { dimension   "market" }
                    }
                }
            """)
            // Default (lowest priority)
            .withFile("src/main/res/raw/a.txt", "Default")
            .withFile("src/main/res/raw/b.txt", "Default")
            .withFile("src/main/res/raw/c.txt", "Default")
            .withFile("src/main/res/raw/d.txt", "Default")
            .withFile("src/main/res/raw/e.txt", "Default")
            .withFile("src/main/res/raw/f.txt", "Default")
            // flavor, second dimension
            .withFile("src/play/res/raw/a.txt", "Play")
            .withFile("src/play/res/raw/b.txt", "Play")
            .withFile("src/play/res/raw/c.txt", "Play")
            .withFile("src/play/res/raw/d.txt", "Play")
            .withFile("src/play/res/raw/e.txt", "Play")
            // flavor, first dimension (overrides second)
            .withFile("src/free/res/raw/a.txt", "Free")
            .withFile("src/free/res/raw/b.txt", "Free")
            .withFile("src/free/res/raw/c.txt", "Free")
            .withFile("src/free/res/raw/d.txt", "Free")
            // multi-flavor
            .withFile("src/freePlay/res/raw/a.txt", "FreePlay")
            .withFile("src/freePlay/res/raw/b.txt", "FreePlay")
            .withFile("src/freePlay/res/raw/c.txt", "FreePlay")
            // build type
            .withFile("src/debug/res/raw/a.txt", "Debug")
            .withFile("src/debug/res/raw/b.txt", "Debug")
            // variant (highest priority)
            .withFile("src/freePlayDebug/res/raw/a.txt", "FreePlayDebug")


    @get:Rule val project = GradleTestProject.builder().fromTestApp(app).create()

    @Test
    fun smokeTest() {
        project.executor()
                .run(":assembleFreePlayDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG, "free", "play")
        assertThat(apk).exists()
        // a.txt overridden everywhere, variant specific value is used.
        assertThat(apk).containsFileWithContent("res/raw/a.txt", "FreePlayDebug")
        // b.txt overridden everywhere except variant, build-type value is used.
        assertThat(apk).containsFileWithContent("res/raw/b.txt", "Debug")
        // c.txt overridden everywhere except variant and build type, multi-flavor value is used.
        assertThat(apk).containsFileWithContent("res/raw/c.txt", "FreePlay")
        // d.txt overridden in first and second flavor dimension, first value is used.
        assertThat(apk).containsFileWithContent("res/raw/d.txt", "Free")
        // e.txt overridden in second flavor dimension, that value is used.
        assertThat(apk).containsFileWithContent("res/raw/e.txt", "Play")
        // f.txt is never overridden, the default sourceset value is used.
        assertThat(apk).containsFileWithContent("res/raw/f.txt", "Default")

    }

}

