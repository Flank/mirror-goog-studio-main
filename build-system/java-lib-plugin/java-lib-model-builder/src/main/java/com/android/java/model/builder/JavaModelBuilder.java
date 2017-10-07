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

import com.android.annotations.VisibleForTesting;
import com.android.java.model.JavaLibrary;
import com.android.java.model.JavaProject;
import com.android.java.model.SourceSet;
import com.android.java.model.impl.JavaLibraryImpl;
import com.android.java.model.impl.JavaProjectImpl;
import com.android.java.model.impl.SourceSetImpl;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Specs;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.VersionNumber;

/**
 * Builder for the custom Java library model.
 */
public class JavaModelBuilder implements ToolingModelBuilder {

    private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";
    private static final String LOCAL_JAR_DISPLAY_NAME = "local jar - ";

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(JavaProject.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        if (!project.getPlugins().hasPlugin(JavaPlugin.class)) {
            return null;
        }
        JavaPluginConvention javaPlugin =
                project.getConvention().findPlugin(JavaPluginConvention.class);

        Collection<SourceSet> sourceSets = new ArrayList<>();
        for (org.gradle.api.tasks.SourceSet sourceSet : javaPlugin.getSourceSets()) {
            sourceSets.add(createSourceSets(project, sourceSet));
        }

        return new JavaProjectImpl(
                project.getName(), sourceSets, javaPlugin.getSourceCompatibility().toString());
    }

    private static SourceSet createSourceSets(
            Project project, org.gradle.api.tasks.SourceSet sourceSet) {
        String compileConfigurationName;
        if (isGradleAtLeast(project.getGradle().getGradleVersion(), "2.12")) {
            compileConfigurationName = sourceSet.getCompileClasspathConfigurationName();
        } else {
            compileConfigurationName = sourceSet.getCompileConfigurationName();
        }
        return new SourceSetImpl(
                sourceSet.getName(),
                sourceSet.getAllJava().getSrcDirs(),
                sourceSet.getResources().getSrcDirs(),
                sourceSet.getOutput().getClassesDir(),
                sourceSet.getOutput().getResourcesDir(),
                getLibrariesForConfiguration(project, compileConfigurationName));
    }

    @VisibleForTesting
    static boolean isGradleAtLeast(String gradleVersion, String expectedVersion) {
        VersionNumber currentVersion = VersionNumber.parse(gradleVersion);
        VersionNumber givenVersion = VersionNumber.parse(expectedVersion);
        return currentVersion.compareTo(givenVersion) >= 0;
    }

    private static Collection<JavaLibrary> getLibrariesForConfiguration(
            Project project, String configurationName) {
        Configuration configuration = project.getConfigurations().getAt(configurationName);
        Collection<JavaLibrary> javaLibraries = new ArrayList<>();

        // Since this plugin is always called from IDE, it should not break on unresolved dependencies.
        Set<ResolvedArtifact> allArtifacts =
                configuration
                        .getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getArtifacts(Specs.satisfyAll());
        for (ResolvedArtifact artifact : allArtifacts) {
            String projectPath = getProjectPath(project, artifact);
            File jarFile = artifact.getFile();
            javaLibraries.add(
                    new JavaLibraryImpl(projectPath, artifact.getName().intern(), jarFile));
        }

        // Add unresolved dependencies, mark by adding prefix UNRESOLVED_DEPENDENCY_PREFIX to name.
        // This follows idea plugin.
        Set<UnresolvedDependency> unresolvedDependencies =
                configuration
                        .getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getUnresolvedModuleDependencies();
        for (UnresolvedDependency dependency : unresolvedDependencies) {
            String unresolvedName =
                    UNRESOLVED_DEPENDENCY_PREFIX
                            + dependency.getSelector().toString().replaceAll(":", " ");
            javaLibraries.add(
                    new JavaLibraryImpl(null, unresolvedName.intern(), new File(unresolvedName)));
        }

        // Collect jars from local directory
        for (Dependency dependency : configuration.getAllDependencies()) {
            if (dependency instanceof SelfResolvingDependency
                    && !(dependency instanceof ProjectDependency)) {
                for (File file : ((SelfResolvingDependency) dependency).resolve()) {
                    String localJarName = LOCAL_JAR_DISPLAY_NAME + file.getName();
                    javaLibraries.add(new JavaLibraryImpl(null, localJarName.intern(), file));
                }
            }
        }
        return javaLibraries;
    }

    /** Returns project path if artifact is a module dependency, returns null otherwise. */
    private static String getProjectPath(Project project, ResolvedArtifact artifact) {
        if (isGradleAtLeast(project.getGradle().getGradleVersion(), "2.6")) {
            ComponentIdentifier id = artifact.getId().getComponentIdentifier();
            if (id instanceof ProjectComponentIdentifier) {
                return ((ProjectComponentIdentifier) id).getProjectPath().intern();
            }
        } else {
            Set<Project> allProjects = project.getRootProject().getAllprojects();
            for (Project subProject : allProjects) {
                if (contains(subProject.getBuildDir(), artifact.getFile())) {
                    return subProject.getPath().intern();
                }
            }
        }
        return null;
    }

    /** Returns true if file is inside of directory or any of its sub-directories. */
    private static boolean contains(File directory, File file) {
        try {
            File canonicalFile = file.getCanonicalFile().getParentFile();
            File canonicalDirectory = directory.getCanonicalFile();
            while (canonicalFile != null) {
                if (canonicalFile.equals(canonicalDirectory)) {
                    return true;
                }
                canonicalFile = canonicalFile.getParentFile();
            }
        } catch (IOException ex) {
            return false;
        }
        return false;
    }
}
