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

package com.android.tools.bazel.model;

import com.google.common.collect.ImmutableSet;

import java.io.PrintWriter;
import java.util.*;

public class ImlModule extends BazelRule {

    private List<String> sources = new LinkedList<>();
    private List<String> testSources = new LinkedList<>();
    private List<String> testResources = new LinkedList<>();
    private List<String> resources = new LinkedList<>();
    private List<String> exclude = new LinkedList<>();
    private Map<BazelRule, List<String>> dependencyTags = new HashMap<>();

    public ImlModule(Package pkg, String name) {
        super(pkg, name);
    }

    @Override
    public void generate(PrintWriter writer) {
        writer.append("iml_module").append("(\n");
        writer.append("    name = \"").append(name).append("\",\n");

        append(writer, "srcs", sources);
        append(writer, "test_srcs", testSources);
        append(writer, "exclude", exclude);
        append(writer, "resources", resources);
        append(writer, "test_resources", testResources);
        append(writer, "deps", tagDependencies(dependencies));
        append(writer, "exports", exported);

        writer.append("    javacopts = [\"-extra_checks:off\"],\n");
        writer.append("    visibility = [\"//visibility:public\"],\n");
        writer.append(")\n");
    }

    private List<String> tagDependencies(Set<BazelRule> dependencies) {
        List<String> deps = new LinkedList<>();
        for (BazelRule dependency : dependencies) {
            List<String> tags = dependencyTags.get(dependency);
            String suffix = "";
            if (tags != null && tags.size() > 0) {
                suffix = "[" + String.join(", ", tags) + "]";
            }
            deps.add(dependency.getLabel() + suffix);
        }
        return deps;
    }

    public void addDependency(BazelRule rule, boolean isExported, List<String> tags) {
        super.addDependency(rule, isExported);
        dependencyTags.put(rule, tags);
    }

    private void append(PrintWriter writer, String name, Collection<? extends Object> collection) {
        if (!collection.isEmpty()) {
            boolean single = collection.size() == 1;
            writer.append("    ").append(name).append(" = [").append(single ? "" : "\n");
            for (Object element : collection) {
                writer.append(single ? "" : "        ");
                writer.append("\"").append(element.toString()).append("\"");
                writer.append(single ? "" : ",\n");
            }
            writer.append(single ?  "" : "    ").append("],\n");
        }
    }

    public void addSource(String source) {
        sources.add(source);
    }

    public void addTestSource(String source) {
        testSources.add(source);
    }

    public void addResource(String resource) {
        resources.add(resource);
    }

    public void addTestResource(String resource) {
        testResources.add(resource);
    }

    public void addExclude(String exclude) {
        this.exclude.add(exclude);
    }

    @Override
    public Set<String> getImports() {
        return ImmutableSet.of("iml_module");
    }
}
