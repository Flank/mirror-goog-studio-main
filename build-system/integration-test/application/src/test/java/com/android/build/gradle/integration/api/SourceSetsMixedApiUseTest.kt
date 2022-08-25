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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for b/237783279
 */
class SourceSetsMixedApiUseTest {

    @JvmField
    @Rule
    var project = createGradleProject {
        val customPlugin = PluginType.Custom("com.example.generate.jni")
        subProject(":api-use") {
            plugins.add(PluginType.ANDROID_APP)
            plugins.add(customPlugin)
            useNewPluginsDsl = true
            android {
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
                namespace = "com.example.api.use"
                applicationId = "com.example.api.use"
            }
        }

        settings {
            includedBuild("build-logic") {
                rootProject {
                    plugins.add(PluginType.JAVA_GRADLE_PLUGIN)
                    appendToBuildFile {
                        //language=groovy
                        """
                        group = "com.example.generate"
                        version = "0.1-SNAPSHOT"
                        gradlePlugin {
                            plugins {
                                // A plugin that generates .so files to add to jniLibs in the legacy variant API
                                generateJni {
                                    id = "${customPlugin.id}"
                                    implementationClass = "com.example.generate.GenerateJniPlugin"
                                }
                            }
                        }

                        """.trimIndent()
                    }
                    dependencies {
                        implementation("com.android.tools.build:gradle:${com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION}")
                    }

                    addFile(
                            "src/main/java/com/example/generate/GenerateJniTask.java",
                            // language=java
                            """
                                package com.example.generate;

                                import org.gradle.api.DefaultTask;
                                import org.gradle.api.file.DirectoryProperty;
                                import org.gradle.api.provider.Property;
                                import org.gradle.api.tasks.Input;
                                import org.gradle.api.tasks.OutputDirectory;
                                import org.gradle.api.tasks.TaskAction;
                                import java.io.IOException;
                                import java.nio.file.Files;
                                import java.nio.file.Path;

                                /** Task to  generate a placeholder JNI lib */
                                public abstract class GenerateJniTask extends DefaultTask {

                                    @Input
                                    public abstract Property<String> getFileName();

                                    @OutputDirectory
                                    public abstract DirectoryProperty getOutputDirectory();

                                    @TaskAction
                                    public final void generate() throws IOException {
                                        Path myLib = getOutputDirectory().get().getAsFile().toPath().resolve("armeabi-v7a").resolve(getFileName().get());
                                        Files.createDirectories(myLib.getParent());
                                        Files.writeString(myLib, getFileName().get() + " contents");
                                    }
                                }

                            """.trimIndent()
                    )

                    addFile("src/main/java/com/example/generate/GenerateJniPlugin.java",
                        //language=java
                        """
                                package com.example.generate;

                                import com.android.build.gradle.AppExtension;
                                import com.android.build.gradle.api.AndroidSourceSet;
                                import org.gradle.api.Plugin;
                                import org.gradle.api.Project;
                                import org.gradle.api.file.Directory;
                                import org.gradle.api.provider.Provider;
                                import org.gradle.api.tasks.TaskProvider;
                                import java.io.File;
                                import java.io.IOException;
                                import java.nio.file.Files;

                                /* A Plugin that demonstrates adding generated jnilibs from a task using the legacy variant API */
                                class GenerateJniPlugin implements Plugin<Project> {
                                    @Override public void apply(Project project) {
                                        project.getPluginManager()
                                                .withPlugin(
                                                    "com.android.application",
                                                    androidPlugin -> {
                                                        registerGenerationTask(project);
                                                    });
                                    }

                                    private void registerGenerationTask(Project project) {
                                        AppExtension extension = project.getExtensions().getByType(AppExtension.class);

                                        // Eager / global registration (for comparison with the variant API registration)
                                        {
                                            TaskProvider<GenerateJniTask> generatedJniLib = project.getTasks().register(
                                                "mainGenerateCustomJniLib",
                                                GenerateJniTask.class,
                                                task -> {
                                                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generatedJniLib/main"));
                                                    task.getFileName().set("main-sourceset-generated.so");
                                                }
                                            );

                                            AndroidSourceSet variantSourceSet = extension.getSourceSets().getByName("main");
                                            Provider<Directory> outputDir = generatedJniLib.flatMap(GenerateJniTask::getOutputDirectory);
                                            variantSourceSet.getJniLibs().srcDir(outputDir);
                                            project.getTasks().named("preBuild").configure(task -> task.dependsOn(outputDir));
                                        }

                                        // Variant API registration (regression test for b/237783279)
                                        extension.getApplicationVariants().all(variant -> {
                                            TaskProvider<GenerateJniTask> generatedJniLib = project.getTasks().register(
                                                variant.getName() + "GenerateCustomJniLib",
                                                GenerateJniTask.class,
                                                task -> {
                                                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generatedJniLib/" + variant.getDirName()));
                                                    task.getFileName().set(variant.getName() + "-sourceset-generated.so");
                                                }
                                            );

                                            AndroidSourceSet variantSourceSet = extension.getSourceSets().getByName(variant.getName());
                                            Provider<Directory> outputDir = generatedJniLib.flatMap(GenerateJniTask::getOutputDirectory);
                                            variantSourceSet.getJniLibs().srcDir(outputDir);
                                            variant.getPreBuildProvider().configure(task -> task.dependsOn(outputDir));
                                        });

                                        // After evaluation late initialization
                                        project.afterEvaluate(it -> {
                                            AndroidSourceSet variantSourceSet = extension.getSourceSets().getByName("main");
                                            File lateFolder = project.getLayout().getBuildDirectory().dir("lateDefinedJavaSources").get().getAsFile();
                                            variantSourceSet.getJava().srcDir(lateFolder);
                                            try {
                                                File dir = project.getLayout().getBuildDirectory().dir("lateDefinedJavaSources/com/example/generated").get().getAsFile();
                                                dir.mkdirs();
                                                Files.writeString(new File(dir, "Test.java").toPath(), "package com.example.generated;\n\nclass Test { }");
                                            } catch (IOException ignored) { }
                                        });


                                    }
                            }
                            """.trimIndent()
                    )
                }


            }
        }
    }

    @Test
    fun sourceRegistrationShouldBeRepresented() {
        project.executor().run(":api-use:assembleDebug")
        project.getSubproject(":api-use").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk).containsFile("lib/armeabi-v7a/main-sourceset-generated.so")
            assertThat(apk).containsFile("lib/armeabi-v7a/debug-sourceset-generated.so")
            assertThat(apk).containsClass("Lcom/example/generated/Test;")
        }
    }
}
