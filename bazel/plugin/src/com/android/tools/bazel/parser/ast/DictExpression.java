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
 * A dictionary of key:value pairs.
 */
public class DictExpression extends Expression {
    final List<Expression> keys;
    final List<Expression> expressions;
    private boolean singleLine;

    public DictExpression(Token start, Token end, List<Expression> keys, List<Expression> expressions) {
        super(start, end);
        this.keys = keys;
        this.expressions = expressions;
    }

    @Override
    public void write(String indent, PrintWriter writer) {
        boolean canInline = this.singleLine;
        if (!keys.isEmpty() && keys.get(0).hasPreComment()) {
            canInline = false;
        }

        writer.append("{");
        if (!canInline) {
            writer.append("\n");
        }
        for (int i = 0; i < keys.size(); i++) {
            if (!canInline) {
                writer.append(indent + Build.INDENT);
            }
            keys.get(i).write(indent + Build.INDENT, writer);
            writer.append(": ");
            expressions.get(i).write(indent + Build.INDENT, writer);
            if (canInline) {
                if (i < expressions.size() - 1) {
                    writer.append(", ");
                }
            } else {
                writer.append(",\n");
            }
        }
        if (!canInline) {
            writer.append(indent);
        }
        writer.append("}");
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        for (int i = 0; i < keys.size(); i++) {
            keys.get(i).preOrder(nodes);
            expressions.get(i).preOrder(nodes);
        }
    }

    @Override
    public void postOrder(List<Node> nodes) {
        for (int i = 0; i < keys.size(); i++) {
            keys.get(i).postOrder(nodes);
            expressions.get(i).postOrder(nodes);
        }
        nodes.add(this);
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
    }
}
