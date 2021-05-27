/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.getOutputDir
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

class JavaCompileTest {

    @get:Rule
    var project = EmptyActivityProjectBuilder().build()

    /** Regression test for bug 189326895. */
    @Test
    fun `check -parameters not added to JavaCompile options`() {
        buildAndCheckPresenceOfMethodParamsInByteCode(presenceExpected = false)
    }

    /** Regression test for bug 189326895. */
    @Test
    fun `check -parameters added to JavaCompile options when using Room`() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
            dependencies {
                annotationProcessor("androidx.room:room-compiler:+") {
                    // Not needed in this test, except for the ones below
                    transitive = false
                }
                // Certain classes are required during initialization of Room, so declare those
                // transitive dependencies explicitly as we set `transitive=false` above.
                annotationProcessor("androidx.room:room-common:+")
                annotationProcessor("com.google.auto:auto-common:+")
                annotationProcessor("org.jetbrains.kotlin:kotlin-stdlib:+")
            }
            """.trimIndent()
        )
        buildAndCheckPresenceOfMethodParamsInByteCode(presenceExpected = true)
    }

    private fun buildAndCheckPresenceOfMethodParamsInByteCode(presenceExpected: Boolean) {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").mainSrcDir.resolve("com/example/Foo.java"),
            """
            package com.example;
            public class Foo {
                public void fooMethod(String fooParam) { }
            }
            """.trimIndent()
        )
        project.executor().run(":app:compileDebugJavaWithJavac")

        val fooClassFile =
            JAVAC.getOutputDir(project.getSubproject("app").buildDir)
                .resolve("debug/classes/com/example/Foo.class")
        val fooClass = ClassNode(Opcodes.ASM7).also {
            ClassReader(fooClassFile.readBytes()).accept(it, 0)
        }
        val fooMethod = fooClass.methods[1]
        assertThat(fooMethod.name).isEqualTo("fooMethod")
        if (presenceExpected) {
            assertThat(fooMethod.parameters[0].name).isEqualTo("fooParam")
        } else {
            assertThat(fooMethod.parameters).isNull()
        }
    }
}
