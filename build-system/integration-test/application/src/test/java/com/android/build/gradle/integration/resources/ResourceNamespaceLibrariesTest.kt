/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.BooleanOption
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.Opcodes

/**
 * Smoke test for the new namespaced resource pipeline with libraries.
 *
 * Project structured as follows:
 *
 * ```
 *   notNamespacedLib -->
 *                         lib2
 *        -------------->        ------------>
 *   app                                        lib
 *        ----------------------------------->
 * ```
 */
class ResourceNamespaceLibrariesTest {

    private val buildScriptContent = """
android.defaultConfig.minSdkVersion 21
"""

    private val lib = MinimalSubProject.lib("com.example.lib")
        .appendToBuild(buildScriptContent)
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources><string name="libString">Lib1 string</string></resources>""")
        .withFile(
            "src/main/java/com/example/lib/Example.java",
            """package com.example.lib;
                    public class Example {
                        public static int getLib1String() { return R.string.libString; }
                        public static int getSupportDesignString() {
                            return android.support.design.R.string.appbar_scrolling_view_behavior;
                        }
                    }""")
        .withFile(
            "src/main/res/values/stringuse.xml",
            """<resources>
                        <string name="remoteDependencyString"
                            >@android.support.design:string/appbar_scrolling_view_behavior</string>
                        </resources>""")
        .withFile(
            "src/main/res/drawable/dot.xml",
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="24dp"
                        android:height="24dp"
                        android:viewportWidth="24.0"
                        android:viewportHeight="24.0">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="M12,12m-10,0a10,10 0,1 1,20 0a10,10 0,1 1,-20 0"/>
                    </vector>""")


    private val lib2 = MinimalSubProject.lib("com.example.lib2")
        .appendToBuild(buildScriptContent)
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="libString_from_lib">@*com.example.lib:string/libString</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/lib2/Example.java",
            """package com.example.lib2;
                    public class Example {
                        public static int lib2() { return R.string.libString_from_lib; }
                        public static int lib() { return com.example.lib.R.string.libString; }
                    }
                    """)


    val app = MinimalSubProject.app("com.example.app")
        .appendToBuild(buildScriptContent)
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="appString">App string</string>
                        <string name="libString_from_lib">@*com.example.lib:string/libString</string>
                        <string name="libString_via_lib2">@*com.example.lib2:string/libString_from_lib</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/app/Example.java",
            """package com.example.app;
                    public class Example {
                        public static final int APP_STRING = R.string.appString;
                        public static final int LIB1_STRING = com.example.lib.R.string.libString;
                    }""")
        .withFile("src/androidTest/res/raw/text.txt", "test file")
        .withFile(
            "src/androidTest/res/values/strings.xml",
            """<resources>
                        <string name="appTestString">App test string</string>
                    </resources>""")
        .withFile(
            "src/androidTest/java/com/example/app/ExampleTest.java",
            """package com.example.app.test;
                    public class ExampleTest {
                        public static final int TEST_STRING = R.string.appTestString;
                        public static final int APP_STRING = com.example.app.R.string.appString;
                        public static final int LIB1_STRING = com.example.lib.R.string.libString;
                    }
                    """)

    private val notNamespacedLib = MinimalSubProject.lib("com.example.notNamespaced")
        .appendToBuild("android.defaultConfig.minSdkVersion 21\nandroid.aaptOptions.namespaced false")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="myString_from_lib">@string/libString_from_lib</string>
                    </resources>
                    """)

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":lib2", lib2)
            .subproject(":app", app)
            .subproject(":notNamespacedLib", notNamespacedLib)
            .dependency(lib2, lib)
            .dependency(app, lib2)
            .dependency(app, lib)
            .dependency(notNamespacedLib, lib2)
            // Remote dependency
            .dependency(lib, "com.android.support:design:$SUPPORT_LIB_VERSION")
            .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun smokeTest() {
        project.executor()
            .with(BooleanOption.ENABLE_RESOURCE_NAMESPACING_DEFAULT, true)
            .with(BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP, false) // b/178461741
            .with(BooleanOption.RELATIVE_COMPILE_LIB_RESOURCES, false) // b/178461741
            .run(
                ":lib:assembleDebug",
                ":lib:assembleDebugAndroidTest",
                ":notNamespacedLib:assembleDebug",
                ":notNamespacedLib:assembleDebugAndroidTest",
                ":notNamespacedLib:verifyReleaseResources",
                ":app:assembleDebug",
                ":app:assembleDebugAndroidTest")

        val dotDrawablePath = "res/drawable/com.example.lib\$dot.xml"

        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        assertThat(apk).contains(dotDrawablePath)
        assertThat(apk).containsClass("Lcom/example/app/R;")
        assertThat(apk).containsClass("Lcom/example/app/R\$string;")

        assertThat(apk.getClass("Lcom/example/app/R\$string;")!!.printFields())
            .containsExactly(
                "public static final I appString",
                "public static final I libString_from_lib",
                "public static final I libString_via_lib2")
        assertThat(apk).containsClass("Lcom/example/lib/R\$string;")
        val testApk = project.getSubproject(":app").testApk
        assertThat(testApk).exists()
        assertThat(testApk).doesNotContain(dotDrawablePath)
        assertThat(testApk).contains("res/raw/text.txt")

    }

    private val modifierToString = mapOf(Opcodes.ACC_PUBLIC to "public",
        Opcodes.ACC_STATIC to "static",
        Opcodes.ACC_FINAL to "final")

    private fun modifiers(accessFlags: Int): String {
        val modifiers = ArrayList<String>()
        var runningFlags = accessFlags
        modifierToString.forEach {
                value, string ->
            if (runningFlags and value != 0) {
                modifiers += string
                runningFlags = runningFlags and value.inv()
            }
        }
        if (runningFlags != 0) {
            throw IllegalArgumentException("Unexpected flags, %2x".format(runningFlags))
        }
        return modifiers.joinToString(" ")
    }

    private fun DexBackedClassDef.printFields(): List<String> =
        this.fields.map { modifiers(it.accessFlags) + " " + it.type + " " + it.name }
}

