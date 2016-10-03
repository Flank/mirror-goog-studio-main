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
 * A variable assignment.
 */
public class Assignment extends Statement {
    private final Token ident;
    private final Expression expression;

    public Assignment(Token ident, Expression expression) {
        super(ident, expression.getEnd());
        this.ident = ident;
        this.expression = expression;
    }

    @Override
    public void doWrite(PrintWriter writer) {
        writer.append(ident.value()).append(" = ");
        expression.write("", writer);
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
}
