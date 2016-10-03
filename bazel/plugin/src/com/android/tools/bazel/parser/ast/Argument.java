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

import java.io.PrintWriter;
import java.util.List;

/**
 * An argument of a function call.
 */
public class Argument extends Node {
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
}
