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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.collect.ImmutableList
import org.junit.Rule
import org.junit.Test

class LocalAarModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }

            dependencies {
                implementation(project(":lib"))
            }
        }

        subProject(":lib") {
            wrap(
                generateAarWithContent(
                    packageName = "com.example.aar",
                    mainJar = TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/aar/AarClass")),
                    resources = mapOf("values/strings.xml" to """<resources><string name="aar_string">Aar String</string></resources>""".toByteArray())
                ),
                "lib.aar"
            )
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            modelAction = { container.singleVariantDependencies },
            goldenFile = "VariantDependencies"
        )
    }
}
