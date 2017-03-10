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

package com.android.tools.lint.checks.infrastructure;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * A description of a lint test project
 */
public class ProjectDescription {
    TestFile[] files;
    final List<ProjectDescription> dependsOn = Lists.newArrayList();
    String name;
    String dependencyGraph;
    Type type = Type.APP;
    boolean report = true;

    /**
     * Creates a new project description
     */
    public ProjectDescription() {
    }

    /**
     * Creates a new project with the given set of files
     */
    public ProjectDescription(@NonNull TestFile... files) {
        files(files);
    }

    /**
     * Names the project; most useful in multi-project tests where the project name
     * will be part of the error output
     *
     * @param name the name for the project
     * @return this for constructor chaining
     */
    public ProjectDescription name(@NonNull String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the given set of test files as the project contents
     *
     * @param files the test files
     * @return this for constructor chaining
     */
    public ProjectDescription files(@NonNull TestFile... files) {
        this.files = files;
        return this;
    }

    /**
     * Adds the given project description as a direct dependency for this project
     *
     * @param library the project to depend on
     * @return this for constructor chaining
     */
    public ProjectDescription dependsOn(@NonNull ProjectDescription library) {
        dependsOn.add(library);
        assert library.type != Type.APP : "Depended upon project should not have APP";
        return this;
    }

    /**
     * Adds the given dependency graph (the output of the Gradle dependency task)
     * to be constructed when mocking a Gradle model for this project
     *
     * @param dependencyGraph the graph description
     * @return this for constructor chaining
     */
    public ProjectDescription withDependencyGraph(@NonNull String dependencyGraph) {
        this.dependencyGraph = dependencyGraph;
        return this;
    }

    /**
     * Marks the project as an app, library or Java module
     *
     * @param type the type of project to create
     * @return this for constructor chaining
     */
    public ProjectDescription type(@NonNull Type type) {
        this.type = type;
        return this;
    }

    /**
     * Marks this project as reportable (the default) or non-reportable.
     * Lint projects are usually reportable, but if they depend on libraries
     * (such as appcompat) those dependencies are marked as non-reportable.
     * Lint will still analyze those projects (for example, an unused resource
     * analysis should list resources pulled in from these libraries) but issues
     * found within those libraries will not be reported.
     *
     * @param report whether we should report issues for this project
     * @return this for constructor chaining
     */
    public ProjectDescription report(boolean report) {
        this.report = report;
        return this;
    }

    /** Describes different types of lint test projects */
    public enum Type {
        APP,
        LIBRARY,
        JAVA
    }
}