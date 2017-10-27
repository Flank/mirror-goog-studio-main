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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.tasks.LintStandaloneTask;
import java.io.File;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskContainer;

/**
 * Plugin for running lint <b>without</b> the Android Gradle plugin, such as in a pure Kotlin
 * project.
 */
public class LintPlugin implements Plugin<Project> {
    private Project project;
    private LintOptions lintOptions;

    @Override
    public void apply(Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;
        createExtension(project);
        BasePlugin.createLintClasspathConfiguration(project);
        withJavaPlugin(plugin -> {
            JavaPluginConvention javaConvention = getJavaPluginConvention();
            if (javaConvention != null) {
                LintStandaloneTask task = createTask(project, javaConvention);
                task.setLintChecks(TaskManager.createCustomLintChecksConfig(project));
            }
        });
    }

    private void createExtension(Project project) {
        lintOptions = project.getExtensions().create("lintOptions", LintOptions.class);
    }

    private void withJavaPlugin(Action<Plugin> action) {
        project.getPlugins().withType(JavaBasePlugin.class, action);
    }

    @NonNull
    private LintStandaloneTask createTask(
            @NonNull Project project,
            @NonNull JavaPluginConvention javaConvention) {
        File testResultsDir = javaConvention.getTestResultsDir();
        TaskContainer tasks = project.getTasks();
        LintStandaloneTask task = tasks.create("lint", LintStandaloneTask.class);
        String desc = "Run Android Lint analysis on project '" + project.getName() + "'";
        task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        task.setDescription(desc);
        task.setReportDir(testResultsDir);
        task.setLintOptions(lintOptions);

        // Make check task depend on lint
        tasks.findByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(task);

        return task;
    }

    @Nullable
    private JavaPluginConvention getJavaPluginConvention() {
        Convention convention = project.getConvention();
        JavaPluginConvention javaConvention = convention.getPlugin(JavaPluginConvention.class);
        if (javaConvention == null) {
            project.getLogger().warn("Cannot apply lint if the java or kotlin Gradle plugins " +
                    "have also been applied");
            return null;
        }
        return javaConvention;
    }
}
