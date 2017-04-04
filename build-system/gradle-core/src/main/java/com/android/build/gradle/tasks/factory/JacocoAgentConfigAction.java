/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks.factory;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.coverage.JacocoPlugin;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Copy;

/**
 * Configuration Action for the Jacoco agent unzip task.
 */
public class JacocoAgentConfigAction implements TaskConfigAction<Copy> {

    @NonNull
    private final GlobalScope scope;

    public JacocoAgentConfigAction(@NonNull GlobalScope scope) {
        this.scope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return "unzipJacocoAgent";
    }

    @NonNull
    @Override
    public Class<Copy> getType() {
        return Copy.class;
    }

    @Override
    public void execute(@NonNull Copy task) {
        Project project = scope.getProject();
        Configuration config =
                project.getConfigurations().getByName(JacocoPlugin.AGENT_CONFIGURATION_NAME);

        // Create bypass supplier to return jacocagent.jar.
        // We need to unzip the jacoco dependency to get jacocoagent.jar inside.  So we create
        // {@link FileTree} using {@link project#zipTree}.  To avoid resolving the
        // AGENT_CONFIGURATION_NAME configuration during configuration phase, we use a bypass
        // supplier that returns empty list during configuration.
        Supplier<Collection<File>> jacocoAgent =
                TaskInputHelper.bypassFileSupplier(config::getFiles);
        Callable<Collection<FileTree>> callable  =
                () -> jacocoAgent.get().stream().map(project::zipTree).collect(Collectors.toList());

        task.from(callable);
        task.include(scope.getJacocoAgent().getName());
        task.into(scope.getJacocoAgentOutputDirectory());
        task.dependsOn(config);
    }

}
