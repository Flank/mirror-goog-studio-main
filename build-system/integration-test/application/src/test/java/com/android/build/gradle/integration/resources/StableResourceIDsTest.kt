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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test

class StableResourceIDsTest {

    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/values/colours.xml",
            """
                <resources>
                    <color name="my_color_a"/>
                    <color name="my_color_b"/>
                    <color name="my_color_c"/>
               </resources>""".trimIndent()
        )
        .withFile(
            "src/main/res/values/styleables.xml",
            """
                <resources>
                    <attr name="attr_a" type="string"/>
                    <attr name="attr_b" type="string"/>
                    <attr name="attr_c" type="string"/>

                    <declare-styleable name="ds">
                        <attr name="attr_a"/>
                        <attr name="attr_b"/>
                        <attr name="attr_c"/>
                    </declare-styleable>
                </resources>
            """.trimIndent()
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun resourceIDsAreKept() {
        project.executor()
            .with(BooleanOption.ENABLE_STABLE_IDS, true)
            .run(":app:assembleDebug")

        val appProject = project.getSubproject(":app")

        val stableIdsTxt =
           appProject.getIntermediateFile("stable_resource_ids_file", "debug", "stableIds.txt")
        assertThat(stableIdsTxt).exists()

        assertThat(stableIdsTxt).containsAllOf(
            "com.example.app:color/my_color_a = 0x7f020000",
            "com.example.app:color/my_color_b = 0x7f020001",
            "com.example.app:color/my_color_c = 0x7f020002",
            "com.example.app:styleable/ds = 0x7f030000",
            "com.example.app:attr/attr_a = 0x7f010000",
            "com.example.app:attr/attr_b = 0x7f010001",
            "com.example.app:attr/attr_c = 0x7f010002"
        )

        val rDotTxt =
            appProject.getIntermediateFile("runtime_symbol_list", "debug", "R.txt")
        assertThat(rDotTxt).exists()
        assertThat(rDotTxt).containsAllOf(
            "my_color_a 0x7f020000",
            "my_color_b 0x7f020001",
            "my_color_c 0x7f020002",
            "styleable ds { 0x7f010000, 0x7f010001, 0x7f010002 }",
            "styleable ds_attr_a 0",
            "styleable ds_attr_b 1",
            "styleable ds_attr_c 2",
            "attr attr_a 0x7f010000",
            "attr attr_b 0x7f010001",
            "attr attr_c 0x7f010002"
        )

        val valuesXml =
            FileUtils.join(appProject.mainSrcDir.parentFile, "res", "values", "colours.xml")
        TestFileUtils.searchAndReplace(valuesXml, "my_color_b", "my_color_bb")
        val styleablesXml =
            FileUtils.join(appProject.mainSrcDir.parentFile, "res", "values", "styleables.xml")
        TestFileUtils.searchAndReplace(styleablesXml, "<attr name=\"attr_b\"/>", "")

        project.executor()
            .with(BooleanOption.ENABLE_STABLE_IDS, true)
            .run(":app:assembleDebug")

        assertThat(stableIdsTxt).containsAllOf(
            "com.example.app:color/my_color_a = 0x7f020000", // should use old ID
            "com.example.app:color/my_color_b = 0x7f020001", // kept from previous run
            "com.example.app:color/my_color_c = 0x7f020002", // should use old ID
            "com.example.app:color/my_color_bb = 0x7f020003", // should use first unused ID
            "com.example.app:styleable/ds = 0x7f030000", // styleable[] ID doesn't matter but is kept
            "com.example.app:attr/attr_a = 0x7f010000",
            "com.example.app:attr/attr_b = 0x7f010001",
            "com.example.app:attr/attr_c = 0x7f010002")

        // Removed resource should not be present, and the res IDs should match the stable IDs file.
        // Since the resources are still ordered alphabetically in the file, and the newly added
        // resources cannot re-use old IDs this means that the IDs will NOT BE IN ORDER ANYMORE.
        assertThat(rDotTxt).containsAllOf(
            "my_color_a 0x7f020000",
            "my_color_bb 0x7f020003", // should not contain b, only the newly added bb
            "my_color_c 0x7f020002",
            "styleable ds { 0x7f010000, 0x7f010002 }",
            "styleable ds_attr_a 0",
            "styleable ds_attr_c 1", // Was 2 but now is 1, expected
            "attr attr_a 0x7f010000",
            "attr attr_b 0x7f010001",
            "attr attr_c 0x7f010002"
        )
    }

    @Test
    fun disabledStableIds() {
        project.executor()
            .with(BooleanOption.ENABLE_STABLE_IDS, false)
            .run(":app:assembleDebug")

        val appProject = project.getSubproject(":app")

        val stableIdsTxt =
            appProject.getIntermediateFile("stable_resource_ids_file", "debug", "stableIds.txt")
        assertThat(stableIdsTxt).doesNotExist()

        val rDotTxt =
            appProject.getIntermediateFile("runtime_symbol_list", "debug", "R.txt")
        assertThat(rDotTxt).exists()
        assertThat(rDotTxt).containsAllOf(
            "my_color_a 0x7f020000",
            "my_color_b 0x7f020001",
            "my_color_c 0x7f020002",
            "styleable ds { 0x7f010000, 0x7f010001, 0x7f010002 }",
            "styleable ds_attr_a 0",
            "styleable ds_attr_b 1",
            "styleable ds_attr_c 2",
            "attr attr_a 0x7f010000",
            "attr attr_b 0x7f010001",
            "attr attr_c 0x7f010002"
        )

        val valuesXml =
            FileUtils.join(appProject.mainSrcDir.parentFile, "res", "values", "colours.xml")
        TestFileUtils.searchAndReplace(valuesXml, "my_color_b", "my_color_bb")
        val styleablesXml =
            FileUtils.join(appProject.mainSrcDir.parentFile, "res", "values", "styleables.xml")
        TestFileUtils.searchAndReplace(styleablesXml, "<attr name=\"attr_b\"/>", "")

        project.executor()
            .with(BooleanOption.ENABLE_STABLE_IDS, false)
            .run(":app:assembleDebug")

        assertThat(stableIdsTxt).doesNotExist()

        // IDs should be assigned from scratch
        assertThat(rDotTxt).containsAllOf(
            "my_color_a 0x7f020000",
            "my_color_bb 0x7f020001",
            "my_color_c 0x7f020002",
            "styleable ds { 0x7f010000, 0x7f010002 }",
            "styleable ds_attr_a 0",
            "styleable ds_attr_c 1",
            "attr attr_a 0x7f010000",
            "attr attr_b 0x7f010001",
            "attr attr_c 0x7f010002"
        )
    }
}
