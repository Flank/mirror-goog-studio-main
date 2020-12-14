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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.jf.dexlib2.iface.value.IntEncodedValue
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.Opcodes

class NonNamespacedCompileRClassTest {

    private val lib = MinimalSubProject.lib("com.example.lib")
        .appendToBuild(
            """
                dependencies {
                    api 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
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
                        public static int LIB_RES = R.string.lib_string;
                        public static int DEP_RES = android.support.v7.appcompat.R.attr.actionBarDivider;
                        public static int DEP_RES_AS_LIB = R.attr.actionBarDivider;
                    }
                    """)

    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="app_string">app string</string>
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
    fun checkBuilds() {
        val build = project.executor()
            .with(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS, true)
            .run(":app:assembleDebug")

        Truth.assertThat(build.didWorkTasks).containsAtLeastElementsIn(
            listOf(
                // little merge and creating compile time artifacts
                ":app:packageDebugResources",
                ":app:parseDebugLocalResources",
                ":app:generateDebugRFile",
                // big merge and creating runtime artifacts
                ":app:mergeDebugResources",
                ":app:processDebugResources"))

        val app = project.getSubproject("app")

        // Check that we only parsed the local app files
        val rDef = app.getIntermediateFile("local_only_symbol_list", "debug", "R-def.txt")
        assertThat(rDef).contains("string app_string")
        assertThat(rDef).doesNotContain("lib_string")
        assertThat(rDef).doesNotContain("abc_action_bar_home_description")

        // Check that the compile R class was generated
        val compileR =
            app.getIntermediateFile("compile_r_class_jar", "debug", "R.jar")
        assertThat(compileR).exists()

        // Check that the compile R.txt contains symbols from the app, lib and transitive
        // dependencies, and also they are initialized with the mock value of 0.
        val compileRTxt = app.getIntermediateFile("compile_symbol_list", "debug", "R.txt")
        assertThat(compileRTxt).exists()
        assertThat(compileRTxt).containsAllOf(
            "int string app_string 0x0",
            "int string lib_string 0x0",
            "int string abc_action_bar_home_description 0x0")

        // Check the APK has the correct runtime R class
        val apk = app.getApk(DEBUG)

        ApkSubject.assertThat(apk).containsClass("Lcom/example/app/R\$string;")
        val runtimeRStrings = apk.getClass("Lcom/example/app/R\$string;")!!
        Truth.assertThat(runtimeRStrings.printFields())
            .containsAtLeastElementsIn(
                listOf(
                    // The runtime R has final fields (while compile time R has non-final fields)
                    "public static final I app_string", // string from app
                    "public static final I lib_string", // string from lib
                    "public static final I abc_action_bar_home_description")) // string from transitive dep

        // Make sure all the fields have correct values (the compile R class has fake values of 0).
        val runtimeStringIDs = runtimeRStrings.getValues()
        Truth.assertThat(runtimeStringIDs).isNotEmpty()
        Truth.assertThat(runtimeStringIDs).doesNotContain(0)
    }

    private val modifierToString = mapOf(
        Opcodes.ACC_PUBLIC to "public",
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

    private fun DexBackedClassDef.getValues(): List<Int> =
        this.fields.map { (it.initialValue!! as IntEncodedValue).value }
}
