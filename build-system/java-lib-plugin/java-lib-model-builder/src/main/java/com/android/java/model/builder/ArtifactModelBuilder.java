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

package com.android.java.model.builder;

import com.android.java.model.ArtifactModel;
import com.android.java.model.impl.ArtifactModelImpl;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * Builder for ArtifactModel.
 */
public class ArtifactModelBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(ArtifactModel.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        PluginContainer plugins = project.getPlugins();

        // If there is no java or android plugin applied, it maybe a Jar/Aar module.
        // This is based on best guess, since Jar/Aar module doesn't require any plugin.
        String[] knownPlugins = {
                "java", "com.android.application", "com.android.library",
                "android", "android-library", "com.android.atom", "com.android.instantapp",
                "com.android.test", "com.android.model.atom", "com.android.model.application",
                "com.android.model.library", "com.android.model.native"};
        for (String plugin : knownPlugins) {
            if (plugins.hasPlugin(plugin)) {
                return null;
            }
        }
        return new ArtifactModelImpl(project.getName().intern(),
                getArtifactsByConfiguration(project));
    }

    private static Map<String, Set<File>> getArtifactsByConfiguration(Project project) {
        return project.getConfigurations().stream().collect(Collectors
                .toMap(p -> p.getName().intern(), p -> p.getAllArtifacts().getFiles().getFiles()));
    }
}