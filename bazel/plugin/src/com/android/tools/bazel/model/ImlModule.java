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

import com.android.tools.bazel.parser.ast.CallStatement;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
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
    public void update() throws IOException {
        CallStatement call = getCallStatement("iml_module", name);
        if (getLoad(call) == null) {
            addLoad("//tools/base/bazel:bazel.bzl", call);
        }

        setArgument(call, "srcs", sources);
        setArgument(call, "test_srcs", testSources);
        setArgument(call, "exclude", exclude);
        setArgument(call, "resources", resources);
        setArgument(call, "test_resources", testResources);
        setArgument(call, "deps", tagDependencies(dependencies));
        setArgument(call, "exports", exported);
        if (!call.isFromFile()) {
            setArgument(call, "javacopts", ImmutableList.of("-extra_checks:off"));
            setArgument(call, "visibility", ImmutableList.of("//visibility:public"));
        }
        addElementToList(call, "tags", "managed");
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
