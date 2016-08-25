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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class Package {

    private final String name;
    private final Workspace workspace;
    private Map<String, BazelRule> rules = Maps.newLinkedHashMap();
    private Set<String> imports = Sets.newLinkedHashSet();

    public Package(Workspace workspace, String name) {
        this.workspace = workspace;
        this.name = name;
    }

    public void generate() throws IOException {
        if (workspace == null) return;

        boolean hasRules = false;
        for (BazelRule rule : rules.values()) {
            if (rule.isExport()) {
                hasRules = true;
                break;
            }
        }
        if (!hasRules) return;

        File dir = getPackageDir();
        File build = new File(dir, "BUILD");
        System.err.println(">> " + build.getAbsolutePath());
        FileUtil.createIfDoesntExist(build);
        try (FileOutputStream fileOutputStream = new FileOutputStream(build);
            PrintWriter writer = new PrintWriter(fileOutputStream)) {
            writer.append("# This file has been automatically generated, please do not modify directly.\n");
            if (!imports.isEmpty()) {
                writer.append("load(\"//tools/base/bazel:bazel.bzl\"");
                for (String name : imports) {
                    writer.append(", \"").append(name).append("\"");
                }
                writer.append(")\n");
            }
            for (BazelRule rule : rules.values()) {
                if (rule.isEmpty()) continue;
                if (!rule.isExport()) continue;

                writer.append("\n");
                rule.generate(writer);
            }
        }
    }

    public BazelRule getRule(String name) {
        return rules.get(name);
    }

    @NotNull
    public File getPackageDir() {
        return new File(workspace.getDirectory(), name);
    }

    public String getName() {
        return name;
    }

    public void addRule(BazelRule rule) {
        if (rules.get(rule.getName()) != null) {
            throw new IllegalStateException("Duplicated rule " + rule.getName());
        }
        imports.addAll(rule.getImports());
        rules.put(rule.getName(), rule);
    }
}
