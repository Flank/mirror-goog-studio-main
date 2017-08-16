/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage;

import com.android.annotations.Nullable;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;


/**
 * Jacoco plugin. This is very similar to the built-in support for Jacoco but we dup it in order
 * to control it as we need our own offline instrumentation.
 *
 * This may disappear if we can ever reuse the built-in support.
 *
 */
public class JacocoPlugin implements Plugin<Project> {
    public static final String ANT_CONFIGURATION_NAME = "androidJacocoAnt";
    public static final String AGENT_CONFIGURATION_NAME = "androidJacocoAgent";

    /** This version must be kept in sync with the version that the gradle plugin depends on. */
    private static final String DEFAULT_JACOCO_VERSION = "0.7.4.201502262128";

    private Project project;

    @Nullable
    private String jacocoVersion;

    @Override
    public void apply(Project project) {
        this.project = project;
        addJacocoConfigurations();
    }

    /**
     * Creates the configurations used by plugin.
     */
    private void addJacocoConfigurations() {
        Configuration config = this.project.getConfigurations().create(AGENT_CONFIGURATION_NAME);

        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The Jacoco agent to use to get coverage data.");

        project.getDependencies()
                .add(
                        AGENT_CONFIGURATION_NAME,
                        "org.jacoco:org.jacoco.agent:" + getJacocoVersion() + ":runtime");

        config = this.project.getConfigurations().create(ANT_CONFIGURATION_NAME);

        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The Jacoco ant tasks to use to get execute Gradle tasks.");

        project.getDependencies()
                .add(ANT_CONFIGURATION_NAME, "org.jacoco:org.jacoco.ant:" + getJacocoVersion());
    }

    @Nullable
    private String getJacocoVersion() {
        if (jacocoVersion != null) {
            return jacocoVersion;
        }

        Project candidateProject = project;
        boolean shouldFailWithException = false;

        while (candidateProject != null) {
            Set<ResolvedArtifact> resolvedArtifacts =
                    candidateProject.getBuildscript().getConfigurations().getByName("classpath")
                            .getResolvedConfiguration().getResolvedArtifacts();
            for (ResolvedArtifact artifact : resolvedArtifacts) {
                ModuleVersionIdentifier moduleVersion = artifact.getModuleVersion().getId();
                if ("org.jacoco.core".equals(moduleVersion.getName())) {
                    jacocoVersion = moduleVersion.getVersion();
                    return jacocoVersion;
                }
            }
            if (!resolvedArtifacts.isEmpty()) {
                // not in the DSL test case, where nothing will have been resolved.
                shouldFailWithException = true;
            }

            candidateProject = candidateProject.getParent();
        }

        if (shouldFailWithException) {
            throw new IllegalStateException(
                    "Could not find project build script dependency on org.jacoco.core");
        }

        project.getLogger().error(
                "No resolved dependencies found when searching for the jacoco version.");
        jacocoVersion = DEFAULT_JACOCO_VERSION;
        return jacocoVersion;
    }
}
