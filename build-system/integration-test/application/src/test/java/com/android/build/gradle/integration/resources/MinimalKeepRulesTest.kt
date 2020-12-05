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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

class MinimalKeepRulesTest {
    @get:Rule
    var project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun checkMinimalRuleGeneration() {
        project.buildFile.appendText("""
            android {
              buildTypes {
                debug {
                  minifyEnabled true
                }
              }
            }
        """)

        val layouts = FileUtils.join(project.mainSrcDir.parentFile, "res", "layout")
        FileUtils.writeToFile(
            File(layouts, "layoutone.xml"),
            """<?xml version="1.0" encoding="utf-8"?><com.custom.MyView/>""")

        val rules = project.getIntermediateFile("aapt_proguard_file", "debug", "aapt_rules.txt")

        project.executor().run("assembleDebug") // Verify the default behavior.
        assertThat(rules).contains("-keep class com.custom.MyView { <init>(android.content.Context, android.util.AttributeSet); }")

        project.executor().with(BooleanOption.MINIMAL_KEEP_RULES, false).run("assembleDebug")
        assertThat(rules).contains("-keep class com.custom.MyView { <init>(...); }")

        project.executor().with(BooleanOption.MINIMAL_KEEP_RULES, true).run("assembleDebug")
        assertThat(rules).contains("-keep class com.custom.MyView { <init>(android.content.Context, android.util.AttributeSet); }")
    }
}
