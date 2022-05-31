/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.net.URLClassLoader

internal class FusedLibraryClassesRewriteTaskTest {

    @JvmField
    @Rule
    val project = createGradleProject {
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
            }
            addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="greeting">Hello</string>
                <string name="farewell">Goodbye</string>
              </resources>"""
            )
            addFile("src/main/layout/main_activity.xml", "<root></root>")
        }
        subProject(":androidLib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
            }
            addFile(
                "src/main/java/com/example/androidLib2/MyClass.java",
                // language=JAVA
                """package com.example.androidLib2;
                public class MyClass {
                    public static void methodUsingNonNamespacedResource() {
                        int string = R.string.greeting;
                        int string2 = R.string.farewell;
                    }
                }
            """.trimIndent()
            )
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":fusedLib1") {
            plugins.add(PluginType.FUSED_LIBRARY)
            appendToBuildFile { """android.namespace="com.example.fusedLib1" """ }
            dependencies {
                include(project(":androidLib2"))
            }
        }
    }

    @Test
    fun rewritesUnderFusedRClass() {
        project.execute(":fusedLib1:rewriteClasses")
        val rewrittenClasses =
            project.getSubproject("fusedLib1")
                .getIntermediateFile(
                    FusedLibraryInternalArtifactType.CLASSES_WITH_REWRITTEN_R_CLASS_REFS.getFolderName(),
                    "single"
                )
        val fusedLibraryRjar =
            project.getSubproject("fusedLib1")
                .getIntermediateFile(
                    FusedLibraryInternalArtifactType.FUSED_R_CLASS.getFolderName(),
                    "single",
                    "R.jar"
                )

        URLClassLoader(
            arrayOf(rewrittenClasses.toURI().toURL(), fusedLibraryRjar.toURI().toURL()),
            null
        ).use { classLoader ->
            // Check fused library R class generated and contains fields.
            val fusedLibraryRStringsClass =
                classLoader.loadClass("com.example.fusedLib1.R\$string")
            val fusedLibraryRClassStringFieldNames =
                (fusedLibraryRStringsClass.declaredFields).map { it.name }
            assertThat(fusedLibraryRClassStringFieldNames)
                .containsAtLeast("greeting", "farewell")

            // Check that R class references use the fused library R Class
            try {
                val myClass = classLoader.loadClass("com.example.androidLib2.MyClass")
                val method = myClass.getMethod("methodUsingNonNamespacedResource")
                method.invoke(null)
            } catch (e: Exception) {
                throw AssertionError(
                    "Failed to resolve fused library R class reference", e
                )
            }
        }
    }
}
