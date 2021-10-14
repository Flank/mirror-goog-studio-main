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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class MacroTagTest {

    private val app = MinimalSubProject.app("com.example.app")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """
                <resources>
                    <string name="hello">Hello world</string>
                    <macro name="string_ref">@string/hello</macro>
                    <string name="macro_ref_str">@macro/string_ref</string>

                    <macro name="macro_raw">123</macro>
                    <integer name="macro_ref_int">@macro/macro_raw</integer>

                    <macro name="str_const">FOO BAR</macro>
                    <string name="macro_ref_str_raw">@macro/str_const</string>

                    <attr name="colorError"/>
                    <color name="gm_sys_color_dark_error_state_layer">?attr/colorError</color>
                    <macro name="gm_sys_color_dark_error_state_layer">?attr/colorError</macro>
               </resources>""".trimIndent()
            )


    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkMacros() {
        // Macro references are verified during aapt2 link.
        val result = project.executor().run(":app:assembleDebug")
        assertThat(result.didWorkTasks).contains(":app:processDebugResources")
    }
}
