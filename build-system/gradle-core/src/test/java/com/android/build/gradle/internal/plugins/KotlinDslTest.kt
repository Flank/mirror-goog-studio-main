/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.builder.errors.EvalIssueException
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith

/** Functional tests for the new Kotlin DSL. */
class KotlinDslTest {

    @get:Rule
    val projectDirectory = TemporaryFolder()

    private lateinit var plugin: AppPlugin
    private lateinit var android: ApplicationExtension<*, *, *, *, *, *>
    private lateinit var project: Project

    @Before
    fun setUp() {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
            .withPlugin(TestProjects.Plugin.APP)
            .build()

        initFieldsFromProject()
    }

    private fun initFieldsFromProject() {
        android =
            project.extensions.getByType(ApplicationExtension::class.java)
        android.compileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        plugin = project.plugins.getPlugin(AppPlugin::class.java)
    }

    @Test
    fun testDslLocking() {
        plugin.createAndroidTasks()
        val exception = assertFailsWith(EvalIssueException::class) {
            android.compileSdkVersion(28)
        }
        assertThat(exception).hasMessageThat().isEqualTo(
            """
                It is too late to set property 'compileSdkVersion' to 'android-28'. (It has value 'android-${TestConstants.COMPILE_SDK_VERSION}')
                The DSL is now locked as the variants have been created.
                Either move this call earlier, or use the variant API to customize individual variants.
                """.trimIndent()
        )
    }
}