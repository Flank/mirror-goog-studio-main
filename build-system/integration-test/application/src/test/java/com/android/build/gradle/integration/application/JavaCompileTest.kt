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
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File

class JavaCompileTest {

    @get:Rule
    val project = EmptyActivityProjectBuilder().build()

    @get:Rule
    val testFolder = TemporaryFolder()

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

    /** Regression test for bug 193133041. */
    @Test
    fun `check DSL arguments are added to JavaCompile options when using Room`() {
        val outFile = testFolder.newFile()
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """

            abstract class DisplayArguments extends DefaultTask {
                @Input
                abstract MapProperty<String, String> getArguments()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    getOutputFile().get().asFile.write(arguments.get().toString());
                }
            }
            android {
                defaultConfig {
                    javaCompileOptions {
                        annotationProcessorOptions {
                            arguments = [
                                    "room.incremental"     : "true",
                                    "room.expandProjection": "true",
                                    "room.schemaLocation"  : "${'$'}projectDir/src/schemas".toString()
                            ]
                        }
                    }
                }
                sourceSets {
                    androidTest.assets.srcDirs += files("${'$'}projectDir/src/schemas".toString())
                }
            }
            androidComponents {
                onVariants(selector().all()) { variant ->
                    tasks.register(variant.name + "DisplayArguments", DisplayArguments.class) { task ->
                        task.outputFile.set(new File("${outFile.absolutePath.replace("\\", "\\\\")}"))
                        task.arguments.set(variant.javaCompilation.annotationProcessor.arguments)
                    }
                }
            }
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

                implementation "androidx.room:room-runtime:+"
            }
            """.trimIndent()
        )
        project.getSubproject((":app")).let {
            TestFileUtils.appendToFile(it.mainSrcDir.resolve("com/example/User.java"),
            """
            import androidx.room.Entity;
            import androidx.room.PrimaryKey;

            @Entity
            public class User {
                @PrimaryKey
                public int id;

                public String firstName;
                public String lastName;
            }
            """.trimIndent())

        TestFileUtils.appendToFile(it.mainSrcDir.resolve("com/example/AppDatabase.java"),
            """
            import androidx.room.Database;
            import androidx.room.RoomDatabase;

            @Database(entities = {User.class},
                      exportSchema = true,
                      version = 1)
            public abstract class AppDatabase extends RoomDatabase {
            }
            """.trimIndent())
        }
        project.executor().run(":app:debugDisplayArguments")

        Truth.assertThat(outFile.exists()).isTrue()
        val content = outFile.readText()
        Truth.assertThat(content).containsMatch("room.schemaLocation=.*schemas")
        Truth.assertThat(content).contains("room.incremental=true")
        Truth.assertThat(content).contains("room.expandProjection=true")
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
