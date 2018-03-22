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
import org.junit.Rule
import org.junit.Test

class NoManifestTest {

    val app = MinimalSubProject.app("com.example.app")
    init {
        app.removeFile(app.getFile("AndroidManifest.xml"))
    }
    private val testApp = MultiModuleTestProject.builder().subproject(":app", app).build()
    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun noManifestConfigurationPassesTest() {
        project.execute("tasks")

        // we should be able to create task list, without a valid manifest.
    }

}
