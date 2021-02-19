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
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

class NonTransitiveAppRClassesTest {
    private val lib = MinimalSubProject.lib("com.example.lib")
        .withFile(
            "src/main/res/values/values.xml",
            """<resources>
                   <string name="libString">Lib string</string>
                   <attr name="libattr" format="reference"/>
               </resources>""".trimMargin()
        )
        .withFile(
            "src/main/java/com/example/lib/Example.java",
            """package com.example.lib;
                    public class Example {
                        public static final int LIB_STRING =  com.example.lib.R.string.libString;
                        public static final int SUPPORT_LIB_STRING = com.example.lib.R.string.appbar_scrolling_view_behavior;
                    }"""
        )

    /** Included to make sure that the ids on an app compilation R class are constant expressions */
    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/values/strings.xml",
            """
                <resources>
                    <string name="appString">App string</string>
                </resources>""".trimIndent()
        )
        .withFile(
            "src/main/java/com/example/app/Example.java",
            """package com.example.app;
                    public class Example {
                        public void test() {
                            int lib = com.example.app.R.string.libString;
                            int supportLib = com.example.app.R.string.appbar_scrolling_view_behavior;
                            int local = com.example.app.R.string.appString;
                        }
                    }
                    """
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":app", app)
            .dependency(lib, "com.android.support:design:$SUPPORT_LIB_VERSION")
            .dependency(app, lib)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun runtimeRClassFlowTestWithNonTransitive() {

        // Need to change the dependency on the support lib from implementation to api.
        TestFileUtils.searchAndReplace(
            project.file("lib/build.gradle"),
            "implementation '",
            "api '"
        )

        // If the flag is off, app R should be transitive.
        project.executor()
            .with(BooleanOption.NON_TRANSITIVE_R_CLASS, false)
            .run("assembleDebug")

        // When the flag is enabled, references to non-local resources need to be updated to the
        // correct R class.
        project.executor()
            .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
            .expectFailure()
            .run("assembleDebug")

        // Update R references in the lib code.
        TestFileUtils.searchAndReplace(
            project.file("lib/src/main/java/com/example/lib/Example.java"),
            "com.example.lib.R.string.appbar_scrolling_view_behavior",
            "android.support.design.R.string.appbar_scrolling_view_behavior"
        )

        // Changing just the code lib shouldn't be enough anymore - build should still fail because
        // the references in the app module haven't been updated.
        project.executor()
            .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
            .expectFailure()
            .run("assembleDebug")

        // Fix references in the app module.
        TestFileUtils.searchAndReplace(
            project.file("app/src/main/java/com/example/app/Example.java"),
            "com.example.app.R.string.libString",
            "com.example.lib.R.string.libString"
        )

        TestFileUtils.searchAndReplace(
            project.file("app/src/main/java/com/example/app/Example.java"),
            "com.example.app.R.string.appbar_scrolling_view_behavior",
            "android.support.design.R.string.appbar_scrolling_view_behavior"
        )

        // Finally, everything should build with the fixed non-transitive R references.
        project.executor()
            .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
            .run("assembleDebug")
    }
}
