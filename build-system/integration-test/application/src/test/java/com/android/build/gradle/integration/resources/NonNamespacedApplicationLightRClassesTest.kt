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
import com.android.build.gradle.integration.common.fixture.TEST_SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

class NonNamespacedApplicationLightRClassesTest {

    private val lib = MinimalSubProject.lib("com.example.lib")
        .appendToBuild(
            """
                dependencies {
                    implementation 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
                }
                """
        )
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="lib_string">lib string</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/lib/Example.java",
            """package com.example.lib;
                    public class Example {
                        public static int LOCAL_RES = R.string.lib_string;
                        public static int DEP_RES = android.support.v7.appcompat.R.attr.actionBarDivider;
                        public static int DEP_RES_AS_LOCAL = R.attr.actionBarDivider;
                    }
                    """)

    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <attr name="my_attr" format="string"/>
                        <declare-styleable name="my_styleable">
                            <attr name="my_attr"/>
                            <attr name="android:keyHeight"/>
                        </declare-styleable>
                        <string name="app_string">app string</string>
                    </resources>""")
        .withFile(
            "src/androidTest/res/values/strings.xml",
            """<resources>
                        <string name="test_app_string">test app string</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/app/Example.java",
            """package com.example.app;
                    public class Example {
                        public static int LOCAL_RES = R.string.app_string;
                        public static int LIB_RES = com.example.lib.R.string.lib_string;
                        public static int LIB_RES_AS_LOCAL = R.string.lib_string;
                        public static int DEP_RES = android.support.v7.appcompat.R.attr.actionBarDivider;
                        public static int DEP_RES_AS_LIB = com.example.lib.R.attr.actionBarDivider;
                        public static int DEP_RES_AS_LOCAL = R.attr.actionBarDivider;

                        public int test(int resId) {
                            switch(resId) {
                                case R.styleable.my_styleable_my_attr: return 0;
                                case R.styleable.my_styleable_android_keyHeight: return 1;
                                case R.string.app_string: return 2;
                            }
                            return -1;
                        }
                    }
                    """)
        .withFile(
            "src/androidTest/java/com/example/app/ExampleTest.java",
            """package com.example.app;

                    import android.support.test.runner.AndroidJUnit4;

                    import org.junit.Test;
                    import org.junit.runner.RunWith;

                    @RunWith(AndroidJUnit4.class)
                    public class ExampleTest {

                        @Test
                        public void testAccess() {
                            int LOCAL_RES = com.example.app.test.R.string.test_app_string;
                            int APP_RES = R.string.app_string;
                            int LIB_RES = com.example.lib.R.string.lib_string;
                            int LIB_RES_AS_LOCAL = R.string.lib_string;
                            int DEP_RES = android.support.v7.appcompat.R.attr.actionBarDivider;
                            int DEP_RES_AS_LIB = com.example.lib.R.attr.actionBarDivider;
                            int DEP_RES_AS_LOCAL = R.attr.actionBarDivider;
                        }
                    }
                    """)
        .appendToBuild(
            """
                dependencies {
                    testImplementation 'junit:junit:4.12'
                    androidTestImplementation 'com.android.support.test:runner:$TEST_SUPPORT_LIB_VERSION'
                }
                """)

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":app", app)
            .dependency(app, lib)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testResourcesCompiled() {
        project.executor().run(":app:assembleDebug")

        // Check library resources
        val libFiles = project.getSubproject("lib")
        assertThat(
            libFiles.getIntermediateFile(
                    "compile_r_class_jar",
                    "debug",
                    "R.jar")).exists()

        assertThat(
            libFiles.getIntermediateFile(
                    "local_only_symbol_list",
                    "debug",
                    "R-def.txt")).exists()

        assertThat(
            libFiles.getIntermediateFile(
                    "local_only_symbol_list",
                    "debug",
                    "R-def.txt")).contains("lib_string")

        assertThat(
            libFiles.getIntermediateFile(
                    "symbol_list_with_package_name",
                    "debug",
                    "package-aware-r.txt")).exists()

        assertThat(
            libFiles.getIntermediateFile(
                    "symbol_list_with_package_name",
                    "debug",
                    "package-aware-r.txt")).contains("lib_string")

        // Application resources
        val appFiles = project.getSubproject("app")

        assertThat(
            appFiles.getIntermediateFile(
                "runtime_symbol_list",
                "debug",
                "R.txt")).exists()

        assertThat(
            appFiles.getIntermediateFile(
                    "runtime_symbol_list",
                    "debug",
                    "R.txt")).containsAllOf("lib_string", "app_string")

        val rJarFile = appFiles.getIntermediateFile(
            "compile_and_runtime_not_namespaced_r_class_jar",
            "debug",
            "R.jar"
        )
        assertThat(rJarFile).exists()
    }

    @Test
    fun testAndroidTestResourcesCompiled() {
        project.executor()
            .run(":app:assembleDebugAndroidTest")

        // Application resources
        val appFiles = project.getSubproject("app")

        // app androidTest resources java
        val rJarFile = appFiles.getIntermediateFile(
            "compile_and_runtime_not_namespaced_r_class_jar",
            "debugAndroidTest",
            "R.jar"
        )
        assertThat(rJarFile).exists()
    }
}
