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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Rule
import org.junit.Test

class D8DesugarMethodsTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testModelFetching() {
        val model = project.modelV2().fetchModels().container.getProject(":").androidProject
        val expectedMethods = "java/lang/Boolean#compare(ZZ)I"
        val d8BackportedMethods = model!!.variants.first().desugaredMethods
            .find { it.name.contains("D8BackportedDesugaredMethods.txt") }
        d8BackportedMethods!!.readLines().contains(expectedMethods)
    }
}
