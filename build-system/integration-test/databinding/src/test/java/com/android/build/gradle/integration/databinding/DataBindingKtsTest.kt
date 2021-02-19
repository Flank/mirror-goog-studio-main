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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Regression test for http://b/167583121. */
class DataBindingKtsTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestProject("databinding")
            .withPluginManagementBlock(true)
            .create()

    @Before
    fun setUp() {
        project.file("build.gradle").delete()
        project.file("build.kotlindsl.gradle").copyTo(project.file("build.gradle.kts"))

        project.file("src/main/res/layout/activity_main.xml").readText().replace(
                "<TextView ",
                "<TextView android:id=\"@+id/a-b-c\""
        )
    }

    @Test
    fun testBuild() {
        project.executor().run("assembleDebug")
    }
}
