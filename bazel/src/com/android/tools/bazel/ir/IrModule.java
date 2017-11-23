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

package com.android.tools.bazel.ir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection of modules with cyclic dependencies that form a strongly connected component.
 */
public class IrModule extends IrNode {

    private List<File> imls = new ArrayList<>();
    private List<File> testSources = new ArrayList<>();
    private List<File> sources = new ArrayList<>();
    private List<File> testResources = new ArrayList<>();
    private List<File> resources = new ArrayList<>();
    private Map<File, String> prefixes = new LinkedHashMap<>();
    private List<Dependency<? extends IrNode>> dependencies = new ArrayList<>();
    private List<File> excludes = new ArrayList<>();
    private String name;
    private Path baseDir;

    public IrModule(String name) {
        this.name = name;
    }

    public void addDependency(IrModule dep, boolean exported, Scope scope) {
        addDependency(new Dependency<>(dep, exported, scope));
    }

    public void addDependency(IrLibrary dep, boolean exported, Scope scope) {
        addDependency(new Dependency<>(dep, exported, scope));
    }

    public void addDependency(Dependency<? extends IrNode> dependency) {
        if (dependency.dependency == this) {
            return;
        }
        for (Dependency<? extends IrNode> dep : dependencies) {
            if (dep.dependency == dependency.dependency) {
                dep.exported = dep.exported || dependency.exported;
                dep.scope = dependency.scope.compareTo(dep.scope) < 0 ? dependency.scope
                        : dep.scope;
                return;
            }
        }
        dependencies.add(dependency);
    }

    public String getName() {
        return name;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public List<File> getSources() {
        return sources;
    }

    public List<File> getTestSources() {
        return testSources;
    }

    public List<File> getResources() {
        return resources;
    }

    public List<File> getTestResources() {
        return testResources;
    }

    public Map<File, String> getPrefixes() {
        return prefixes;
    }

    public List<Dependency<? extends IrNode>> getDependencies() {
        return dependencies;
    }

    public List<File> getImls() {
        return imls;
    }

    public void addExcludeFile(File excludeFile) {
        excludes.add(excludeFile);
    }

    public List<File> getExcludes() {
        return excludes;
    }

    public void addIml(File file) {
        imls.add(file);
    }

    public void addTestSource(File file) {
        testSources.add(file);
    }

    public void addSource(File file) {
        sources.add(file);
    }

    public void addPrefix(File file, String prefix) {
        prefixes.put(file, prefix);
    }

    public void addTestResource(File file) {
        testResources.add(file);
    }

    public void addResource(File file) {
        resources.add(file);
    }

    public void setBaseDir(Path baseDir) {
        this.baseDir = baseDir;
    }

    public enum Scope {
        COMPILE,
        TEST,
        RUNTIME,
        TEST_RUNTIME,
    }

    public static class Dependency<T> {
        public T dependency;
        public boolean exported;
        public Scope scope;

        public Dependency(T dependency, boolean exported, Scope scope) {
            this.dependency = dependency;
            this.exported = exported;
            this.scope = scope;
        }
    }
}
