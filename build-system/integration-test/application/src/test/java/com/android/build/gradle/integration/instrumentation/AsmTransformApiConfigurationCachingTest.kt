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

package com.android.build.gradle.integration.instrumentation

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AsmTransformApiConfigurationCachingTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUp() {
        FileUtils.writeToFile(project.file("buildSrc/build.gradle"),
            // language=groovy
            """
                apply from: "../../commonHeader.gradle"
                buildscript {
                    apply from: "../../commonBuildScript.gradle"
                }

                apply from: "../../commonLocalRepo.gradle"
                apply plugin: 'java-gradle-plugin'

                dependencies {
                    api gradleApi()
                    api "com.android.tools.build:gradle:${"$"}rootProject.buildVersion"
                    implementation "org.ow2.asm:asm-util:7.0"
                }

                gradlePlugin {
                    plugins {
                        instrumentationPlugin {
                            id = 'instrumentation-plugin'
                            implementationClass = 'com.example.buildsrc.plugin.InstrumentationPlugin'
                        }
                    }
                }
            """.trimIndent()
        )

        FileUtils.writeToFile(
            project.file("buildSrc/src/main/java/com/example/buildsrc/plugin/InstrumentationPlugin.java"),
            // language=java
            """
                package com.example.buildsrc.plugin;

                import com.android.build.api.variant.AndroidComponentsExtension;
                import com.android.build.api.instrumentation.AsmClassVisitorFactory;
                import com.android.build.api.instrumentation.ClassData;
                import com.android.build.api.instrumentation.ClassContext;
                import com.android.build.api.instrumentation.FramesComputationMode;
                import com.android.build.api.instrumentation.InstrumentationParameters;
                import com.android.build.api.instrumentation.InstrumentationScope;
                import com.android.build.api.variant.ApplicationVariant;

                import org.gradle.api.Plugin;
                import org.gradle.api.Project;

                import kotlin.Unit;
                import org.objectweb.asm.ClassVisitor;
                import org.objectweb.asm.util.TraceClassVisitor;

                import java.io.PrintWriter;

                public abstract class InstrumentationPlugin implements Plugin<Project> {

                    public static abstract class ClassVisitorFactory
                            implements AsmClassVisitorFactory<InstrumentationParameters.None> {

                        @Override
                        public ClassVisitor createClassVisitor(
                                ClassContext classContext, ClassVisitor nextClassVisitor) {
                            return new TraceClassVisitor(nextClassVisitor, new PrintWriter(System.out));
                        }

                        @Override
                        public boolean isInstrumentable(ClassData classData) {
                            return true;
                        }
                    }

                    @Override
                    public void apply(final Project project) {

                         AndroidComponentsExtension androidComponents =
                                project.getExtensions().getByType(AndroidComponentsExtension.class);

                        androidComponents.onVariants(androidComponents.selector().all(),
                                variantProperties -> {
                                    ApplicationVariant appVariant = (ApplicationVariant) variantProperties;
                                    appVariant.transformClassesWith(
                                            ClassVisitorFactory.class,
                                            InstrumentationScope.ALL,
                                            params -> Unit.INSTANCE);
                                    appVariant.setAsmFramesComputationMode(
                                            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES);
                                    });
                    }
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(project.buildFile,
            """
                apply plugin: 'instrumentation-plugin'
            """.trimIndent())
    }

    @Test
    fun testConfigCachingRun() {
        project.executor().run("clean")
        var result = project.executor().run("assembleDebug")
        assertThat(result.didWorkTasks).contains(":transformDebugClassesWithAsm")

        // task graph is cached

        project.executor().run("clean")
        result = project.executor().run("assembleDebug")
        assertThat(result.didWorkTasks).contains(":transformDebugClassesWithAsm")
    }
}
