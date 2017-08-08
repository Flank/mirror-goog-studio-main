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

import com.android.tools.bazel.parser.ast.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;

public abstract class BazelRule {

    private boolean export = true;
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

    /**
     * Updates the associated BUILD file with the contents of this rule.
     */
    public abstract void update() throws IOException;

    public String getLabel() {
        return "//" + pkg.getName() + (pkg.getName().endsWith("/" + name) ? "" : ":" + name);
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

    public void suppress() {
        export = false;
    }

    public boolean isExport() {
        return export;
    }

    boolean shouldUpdate() throws IOException {
        CallStatement call = pkg.getBuildFile().getCall(name);
        return (call == null) || !call.getPreComments().stream().anyMatch(
            (s) -> s.toLowerCase().contains("do not generate"));
    }

    @Override
    public String toString() {
        return getLabel();
    }

    /**
     * Gets the statement in the BUILD file that represents this rule. If the rule doesn't
     * exist in the BUILD file, a new one is created.
     */
    protected final CallStatement getCallStatement(String type, String name) throws IOException {
        Build build = pkg.getBuildFile();
        CallStatement call = build.getCall(name);
        if (call == null) {
            call = new CallStatement(CallExpression.build(type, ImmutableMap.of("name", name)));
            build.addStatement(call);
        }
        call.setHidden(false);
        return call;
    }

    /** Returns the load statement for the function called by {@code functionCall}, or null. */
    final CallStatement getLoad(CallStatement functionCall) throws IOException {
        return pkg.getBuildFile().getLoad(functionCall);
    }

    /** Adds a statement to load from {@code label} the function called by {@code functionCall}. */
    final CallStatement addLoad(String label, CallStatement functionCall) throws IOException {
        String functionName = functionCall.getCall().getFunctionName();
        CallStatement loadStatement = new CallStatement(CallExpression.load(label, functionName));
        loadStatement.setHidden(false);
        pkg.getBuildFile().addStatementBefore(loadStatement, functionCall);
        return loadStatement;
    }

    public Set<BazelRule> getDependencies() {
        return dependencies;
    }
}
