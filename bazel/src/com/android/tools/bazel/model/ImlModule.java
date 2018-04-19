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

import com.android.tools.bazel.parser.ast.CallExpression;
import com.android.tools.bazel.parser.ast.CallStatement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ImlModule extends BazelRule {

    public enum Tag {
        MODULE,
        TEST,
        RUNTIME,
    }

    private List<String> sources = new LinkedList<>();
    private List<String> testSources = new LinkedList<>();
    private List<String> testResources = new LinkedList<>();
    private List<String> resources = new LinkedList<>();
    private List<String> exclude = new LinkedList<>();
    private List<String> imlFiles = new LinkedList<>();
    private Map<String, String> prefixes = new LinkedHashMap<>();
    private Map<BazelRule, List<Tag>> dependencyTags = new HashMap<>();
    private Set<BazelRule> runtimeDeps = Sets.newLinkedHashSet();
    private Set<BazelRule> testRuntimeDeps = Sets.newLinkedHashSet();

    public ImlModule(Package pkg, String name) {
        super(pkg, name);
    }

    @Override
    public void update() throws IOException {
        CallStatement statement = getCallStatement("iml_module", name);
        if (getLoad(statement) == null) {
            addLoad("//tools/base/bazel:bazel.bzl", statement);
        }

        CallExpression call = statement.getCall();
        call.setArgument("srcs", sources);
        call.setArgument("test_srcs", testSources);
        call.setArgument("exclude", exclude);
        call.setArgument("resources", resources);
        call.setArgument("test_resources", testResources);
        call.setArgument("deps", tagDependencies(dependencies));
        call.setArgument("exports", exported);
        call.setArgument("iml_files", imlFiles);
        call.setArgument("package_prefixes", prefixes);
        call.setArgument("runtime_deps", runtimeDeps);
        call.setArgument("test_runtime_deps", testRuntimeDeps);

        if (!statement.isFromFile()) {
            call.setArgument("visibility", ImmutableList.of("//visibility:public"));
        }
        String reason = "must match IML order";
        call.setDoNotSort("srcs", reason);
        call.setDoNotSort("resources", reason);
        call.setDoNotSort("exports", reason);
        call.setDoNotSort("deps", reason);
        call.setDoNotSort("runtime_deps", reason);
        call.setDoNotSort("test_runtime_deps", reason);

        statement.setIsManaged();
    }

    private List<String> tagDependencies(Set<BazelRule> dependencies) {
        List<String> deps = new LinkedList<>();
        for (BazelRule dependency : dependencies) {
            List<Tag> tags = dependencyTags.get(dependency);
            String suffix = "";
            if (tags != null && tags.size() > 0) {
                suffix =
                        tags.stream()
                                .map(tag -> tag.name().toLowerCase())
                                .collect(Collectors.joining(", ", "[", "]"));
            }
            deps.add(dependency.getLabel() + suffix);
        }
        return deps;
    }

    public void addDependency(BazelRule rule, boolean isExported, List<Tag> tags) {
        if (tags.contains(Tag.RUNTIME)) {
            // Export is ignored it doesn't make sense for runtime deps
            if (tags.contains(Tag.TEST)) {
                testRuntimeDeps.add(rule);
            } else {
                runtimeDeps.add(rule);
            }
            return;
        }
        super.addDependency(rule, isExported);
        List<Tag> oldTags = dependencyTags.get(rule);
        // Don't override with test if the dependency was not test already.
        if (oldTags != null && !oldTags.contains(Tag.TEST) && tags.contains(Tag.TEST)) {
            tags = new ArrayList<>(tags);
            tags.remove(Tag.TEST);
        }
        dependencyTags.put(rule, tags);
    }

    public void addPackagePrefix(String src, String prefix) {
        prefixes.put(src, prefix);
    }

    public void addModuleFile(String name) {
        imlFiles.add(name);
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
}
