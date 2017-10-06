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
 * A binary expression, with a given operator.
 */
public class BinaryExpression extends Expression {
    private final Expression left;
    private final Token op;
    private final Expression right;

    public BinaryExpression(Expression left, Token op, Expression right) {
        super(left.getStart(), right.getEnd());
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public void write(String indent, PrintWriter writer) {
        left.write(indent, writer);
        writer.append(" ").append(op.value()).append(" ");
        right.write(indent, writer);
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        left.preOrder(nodes);
        right.preOrder(nodes);
    }

    @Override
    public void postOrder(List<Node> nodes) {
        left.postOrder(nodes);
        right.postOrder(nodes);
        nodes.add(this);
    }

    public Expression getLeft() {
        return left;
    }
}
