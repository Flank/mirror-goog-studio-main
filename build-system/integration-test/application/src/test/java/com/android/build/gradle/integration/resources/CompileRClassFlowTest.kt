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
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat

/**
 * Sanity tests for the new compile R class flow pipeline.
 */
class CompileRClassFlowTest {

    private val lib1 = MinimalSubProject.lib("com.example.lib1")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources><string name="lib1String">Lib1 string</string></resources>"""
        )
        .withFile(
            "src/main/java/com/example/lib1/Example.java",
            """package com.example.lib1;
                    public class Example {
                        public static final int LIB1_STRING = R.string.lib1String;
                        public static final int SUPPORT_LIB_STRING = android.support.design.R.string.appbar_scrolling_view_behavior;
                    }"""
        )

    private val lib2 = MinimalSubProject.lib("com.example.lib2")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="lib2String">Lib2 string</string>
                    </resources>"""
        )
        .withFile(
            "src/main/java/com/example/lib2/Example.java",
            """package com.example.lib2;
                    public class Example {
                        public static final int LIB2_STRING = R.string.lib2String;
                        public static final int LIB1_STRING = com.example.lib1.R.string.lib1String;
                        public static final int SUPPORT_LIB_STRING = android.support.design.R.string.appbar_scrolling_view_behavior;
                    }"""
        )

    /** Included to make sure that the ids on an app compilation R class are constant expressions */
    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/java/com/example/app/Example.java",
            """package com.example.app;
                    public class Example {
                        public void checkLibRFilesConstant() {
                            int x = R.string.lib2String;
                            switch(x) {
                                // These must be constant expressions.
                                case com.example.lib1.R.string.lib1String:
                                case com.example.lib2.R.string.lib2String:
                            }
                        }
                    }
                    """
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib1", lib1)
            .subproject(":lib2", lib2)
            .subproject(":app", app)
            .dependency(lib1, "com.android.support:design:$SUPPORT_LIB_VERSION")
            .dependency(lib2, lib1)
            .dependency(app, lib2)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    /** Verifies the behavior when enabling and disabling the COMPILE_CLASSPATH_LIBRARY_R_CLASSES flag. */
    @Test
    fun runtimeRClassFlowTest() {

        val tasks = listOf(
            ":app:assembleDebug",
            ":app:assembleDebugAndroidTest",
            ":lib1:assembleDebugAndroidTest",
            ":lib2:assembleDebug",
            ":lib2:assembleDebugAndroidTest"
        )

        // Given a project (lib2) that references things in the runtime classpath not on the compile
        // classpath
        // When compiled with the COMPILE_CLASSPATH_LIBRARY_R_CLASSES flag disabled
        // then everything should work:
        project.executor()
            .with(BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES, false)
            .run(tasks)

        // When compiled with the flag is enabled,
        // then the build should fail:
        val result = project.executor()
            .expectFailure()
            .with(BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES, true)
            .run(tasks)
        assertThat(result.stderr)
            .contains("public static final int SUPPORT_LIB_STRING = android.support.design.R.string.appbar_scrolling_view_behavior;\n")

        // Given an updated project where the library on to the compile classpath:
        // (Done by having an 'api' rather than 'implementation' dependency in lib1, so lib2
        // has the library on the compile classpath.)
        TestFileUtils.searchAndReplace(
            project.file("lib1/build.gradle"),
            "implementation '",
            "api '"
        )
        // When compiled with the flag is enabled,
        // then the build should succeed:
        project.executor()
            .with(BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES, true)
            .run(tasks)
    }
}

