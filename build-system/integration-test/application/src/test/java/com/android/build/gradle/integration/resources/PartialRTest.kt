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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertFailsWith

class PartialRTest {

    val app = MinimalSubProject.app("com.example.app")
        .appendToBuild("android.aaptOptions.namespaced = true")
        .withFile(
                "src/main/res/values/strings.xml",
                """<resources>
                       <string name="default_string">My String</string>
                       <string name="public_string">public</string>
                       <string name="private_string">private</string>
                   </resources>"""
        )
        .withFile(
                "src/main/res/values/public.xml",
                """<resources>
                       <public type="string" name="public_string"/>
                   </resources>"""
        )
        .withFile(
                "src/main/res/values/symbols.xml",
                """<resources>
                       <java-symbol type="string" name="private_string"/>
                   </resources>"""
        )

    val testApp =
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun checkBuilds() {
        project.executor().run(":app:assembleDebug")

        val stringsR = FileUtils.join(
                project.getSubproject("app").intermediatesDir,
            "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "out",
                "values_strings.arsc.flat-R.txt"
        )
        val publicR = File(stringsR.parentFile, "values_public.arsc.flat-R.txt")
        val symbolsR = File(stringsR.parentFile, "values_symbols.arsc.flat-R.txt")

        assertThat(stringsR).exists()
        assertThat(publicR).exists()
        assertThat(symbolsR).exists()

        assertThat(stringsR).contains(
                "" +
                        "default int string default_string\n" +
                        "default int string private_string\n" +
                        "default int string public_string\n"
        )
        assertThat(publicR).contains("public int string public_string")
        assertThat(symbolsR).contains("private int string private_string")

        val resIds = FileUtils.join(
                project.getSubproject("app").intermediatesDir, "res-ids", "debug", "res-ids.txt")
        assertThat(resIds).exists()
        assertThat(Files.readAllLines(resIds.toPath()))
            .containsExactly(
                        "com.example.app",
                        "default int string default_string",
                        "private int string private_string",
                        "public int string public_string")
            .inOrder()

        val rJar = FileUtils.join(
                project.getSubproject("app").intermediatesDir, "res-rJar", "debug", "R.jar")
        assertThat(rJar).exists()

        URLClassLoader(arrayOf(rJar.toURI().toURL()), null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.app.R\$string")
            checkResource(testC, "public_string")
            checkResource(testC, "private_string")
            checkResource(testC, "default_string")

            assertFailsWith<NoSuchFieldException> {
                checkResource(testC, "invalid")
            }
        }
    }

    private fun checkResource(testC: Class<*>, name: String) {
        val field = testC.getField(name)
        Truth.assertThat(field.getInt(testC)).isEqualTo(0)
    }
}