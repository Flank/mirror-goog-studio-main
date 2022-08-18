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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

class UserAddedGeneratedResourcesTest {

    private val buildSrc = MinimalSubProject.buildSrc().also {
        it.addFile("src/main/java/TestPlugin.java",
        // language=java
        """
        import org.gradle.api.Action;
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;
        import org.gradle.api.file.Directory;
        import org.gradle.api.file.ProjectLayout;
        import org.gradle.api.model.ObjectFactory;
        import org.gradle.api.provider.Provider;
        import org.gradle.api.tasks.TaskContainer;
        import org.gradle.api.tasks.TaskProvider;

        import com.android.build.api.dsl.AndroidSourceSet;
        import com.android.build.api.variant.AndroidComponentsExtension;
        import com.android.build.api.variant.Variant;

        import java.util.Arrays;

        @SuppressWarnings({"rawtypes", "UnstableApiUsage"})
        public class TestPlugin implements Plugin<Project>
        {
            public void apply(Project project) {

                final ProjectLayout layout = project.getLayout();
                final TaskContainer tasks = project.getTasks();
                final ObjectFactory objects = project.getObjects();

                Action<? super Plugin> action = plugin -> {
                    AndroidComponentsExtension components = project.getExtensions().getByType(AndroidComponentsExtension.class);
                    components.onVariants(components.selector().all(), (Action<? super Variant>) variant -> {

                        TaskProvider<MyCopy> copyTask = tasks.register("copyResources" + variant.getName(), MyCopy.class, task -> {
                            task.setDescription("Copy resources into target directory");
                            task.setGroup("Custom");
                            task.getInputFiles().from(
                                    project.fileTree(
                                            layout.getProjectDirectory().dir("default"),
                                            c -> c.include("**/*.xml")));
                        });

                        variant.getSources().getRes().addGeneratedSourceDirectory(copyTask, MyCopy::getOutputDirectory);
                    });
                };

                project.getPlugins().withId("com.android.application", action);
            }
        }
        """.trimIndent())

        it.addFile("src/main/java/MyCopy.java",
        // language=java
        """
        import org.gradle.api.DefaultTask;
        import org.gradle.api.file.ConfigurableFileCollection;
        import org.gradle.api.file.DirectoryProperty;
        import org.gradle.api.file.FileSystemOperations;
        import org.gradle.api.tasks.InputFiles;
        import org.gradle.api.tasks.OutputDirectory;
        import org.gradle.api.tasks.TaskAction;

        import javax.inject.Inject;

        public abstract class MyCopy extends DefaultTask {

            @OutputDirectory
            public abstract DirectoryProperty getOutputDirectory();

            @InputFiles
            public abstract ConfigurableFileCollection getInputFiles();

            @Inject
            public abstract FileSystemOperations getFileSystemOperations();

            @TaskAction
            public void copy() {
                getFileSystemOperations().copy( c -> {
                    c.from(getInputFiles());
                    c.into(getOutputDirectory());
                });
            }
        }

        """.trimIndent())
    }

    private val app = MinimalSubProject.app("com.example.app")
        .withFile("default/values/ConnectStatus.xml",
        // language=xml
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <resources>
               <string name="connect_status_none">Unknown status.</string>
            </resources>
        """.trimIndent())

    private val testApp =
        MultiModuleTestProject.builder()
            .buildSrcProject(buildSrc)
            .subproject(":app", app)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun testGeneratedResourcesArePresentInMergedResources() {
        val appProject = project.getSubproject(":app")
        appProject.buildFile.appendText(
            "apply plugin: TestPlugin"
        )
        project.execute(":app:mergeDebugResources")

        val mergedAppValues = appProject.getIntermediateFile(
            "incremental",
            "debug",
            "mergeDebugResources",
            "merged.dir",
            "values",
            "values.xml"
        )

        // Make sure the merged values in app (big merge) contain both overlayable from app and lib,
        // as well as their content.
        PathSubject.assertThat(mergedAppValues).containsAllOf(
            "<string name=\"connect_status_none\">Unknown status.</string>"
        )
    }
}
