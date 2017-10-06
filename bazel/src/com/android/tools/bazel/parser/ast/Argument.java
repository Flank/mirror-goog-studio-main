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

package com.android.tools.bazel.parser.ast;

import com.google.common.collect.ImmutableMap;

import java.io.PrintWriter;
import java.util.List;

/**
 * An argument of a function call.
 */
public class Argument extends Node implements Comparable<Argument> {
    private final Token name;
    private Expression expression;

    public Argument(Token name, Expression expression) {
        super(name != null ? name : expression.getStart(), expression.getEnd());
        this.name = name;
        this.expression = expression;
    }

    public Argument(Expression expression) {
        this(null, expression);
    }

    public static Argument string(String value) {
        return new Argument(LiteralExpression.string(value));
    }

    public void write(String indent, PrintWriter writer) {
        for (String comment : preComments) {
            writer.append(comment);
            writer.append(indent);
        }
        if (name == null) {
            expression.write(indent, writer);
        } else {
            writer.append(name.value()).append(" = ");
            expression.write(indent, writer);
        }
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        expression.preOrder(nodes);
    }

    @Override
    public void postOrder(List<Node> nodes) {
        expression.postOrder(nodes);
        nodes.add(this);
    }

    public boolean hasName() {
        return name != null;
    }

    public String getName() {
        return name.value();
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    /** Adds a comment for buildifier not to sort this argument for {@code reason}. */
    public void setDoNotSort(String reason) {
        if (preComments.stream().noneMatch(s -> s.toLowerCase().contains("do not sort"))
            && expression instanceof ListExpression
            && ((ListExpression) expression).size() > 1) {
            preComments.add(String.format("# do not sort: %s\n", reason));
        }
    }

    @Override
    public int compareTo(Argument that) {
        int priority = this.getSortPriority() - that.getSortPriority();
        return (priority != 0) ? priority : this.name.value().compareTo(that.name.value());
    }

    private int getSortPriority() {
        return SORT_PRIORITY_BY_NAME.getOrDefault(name.value(), 0);
    }

    // copied from http://google3/third_party/bazel_buildifier/core/rewrite.go
    private static final ImmutableMap<String, Integer> SORT_PRIORITY_BY_NAME =
        ImmutableMap.<String, Integer>builder()
            .put("name", -99)
            .put("size", -95)
            .put("timeout", -94)
            .put("testonly", -93)
            .put("src", -92)
            .put("srcdir", -91)
            .put("srcs", -90)
            .put("out", -89)
            .put("outs", -88)
            .put("hdrs", -87)
            .put("has_services", -86)
            .put("include", -85)
            .put("of", -84)
            .put("baseline", -83)
            // All others sort here, at 0.
            .put("destdir", 1)
            .put("exports", 2)
            .put("runtime_deps", 3)
            .put("deps", 4)
            .put("implementation", 5)
            .put("implements", 6)
            .put("alwayslink", 7)
            .build();
}
