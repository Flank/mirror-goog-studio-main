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
import com.google.common.collect.Sets;

import java.io.PrintWriter;
import java.util.Set;

public abstract class BazelRule {

    private boolean export;
    private final Package pkg;
    String name;
    Set<BazelRule> dependencies = Sets.newLinkedHashSet();
    Set<BazelRule> exported = Sets.newLinkedHashSet();

    public BazelRule(Package pkg, String name) {
        this.name = name.replaceAll(" ", "_");
        this.pkg = pkg;
        this.pkg.addRule(this);
    }

    public String getName() {
        return name;
    }

    public boolean isEmpty() {
        return false;
    }

    abstract public void generate(PrintWriter writer);

    public String getLabel() {
        return "//" + pkg.getName() + ":" + name;
    }

    public void addDependency(BazelRule rule, boolean isExported) {
        dependencies.add(rule);
        if (isExported) {
            exported.add(rule);
        }
    }

    public void setExport() {
        if (export) return;

        export = true;
        for (BazelRule dependency : dependencies) {
            dependency.setExport();
        }
    }

    public boolean isExport() {
        return export;
    }

    @Override
    public String toString() {
        return getLabel();
    }

    public Set<String> getImports() {
        return ImmutableSet.of();
    }
}
