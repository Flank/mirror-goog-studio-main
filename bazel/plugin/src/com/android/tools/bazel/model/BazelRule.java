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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Updates the associated BUILD file with the contents of this rule.
     */
    public abstract void update() throws IOException;

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

    /**
     * Sets the argument of the given call expression named {@code name} to be {@code values}.
     */
    protected final void setArgument(CallStatement rule, String name, Collection<?> values) {
        if (!values.isEmpty()) {
            ListExpression list = ListExpression.build(values.stream().map(Object::toString).collect(Collectors.toList()));
            list.setSingleLine(values.size() <= 1);
            rule.getCall().setArgument(name, list);
        }
    }


    /**
     * Ensures an element is in the a list in the given call expression.
     */
    protected void addElementToList(CallStatement call, String attribute, String element) {
        Expression expression = call.getCall().getArgument(attribute);
        ListExpression list;
        if (expression == null) {
            list = ListExpression.build(ImmutableList.of());
            call.getCall().setArgument(attribute, list);
        } else if (expression instanceof BinaryExpression
                && (((BinaryExpression)expression).getLeft() instanceof ListExpression)) {
            list = (ListExpression) ((BinaryExpression) expression).getLeft();
        } else if (expression instanceof ListExpression) {
            list = (ListExpression) expression;
        } else {
            list = ListExpression.build(ImmutableList.of());
            BinaryExpression plus = new BinaryExpression(list, new Token("+", Token.Kind.PLUS), expression);
            call.getCall().setArgument(attribute, plus);
        }
        list.addIfNew(LiteralExpression.build(element));
        list.setSingleLine(list.size() <= 1);
    }
}
