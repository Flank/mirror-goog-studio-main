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

import org.jaxen.expr.Expr;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

public class ListExpression extends Expression {
    private final List<Expression> expressions;
    private boolean singleLine;

    public ListExpression(Token start, Token end, List<Expression> expressions) {
        super(start, end);
        this.expressions = expressions;
    }

    public ListExpression() {
        this(Token.NONE, Token.NONE, new LinkedList<>());
    }

    @Override
    public void write(String indent, PrintWriter writer) {
        boolean canInline = singleLine;
        if (expressions.size() > 0 && expressions.get(0).hasPreComment()) {
            canInline = false;
        }

        writer.append("[");
        if (!canInline) {
            writer.append("\n");
        }
        int i = 0;
        for (Expression expression : expressions) {
            if (!canInline) {
                writer.append(indent + Build.INDENT);
            }
            expression.write(indent + Build.INDENT, writer);
            if (canInline) {
                if (i < expressions.size() - 1) {
                    writer.append(", ");
                }
            } else {
                writer.append(",");
                if (expression.hasPostComment()) {
                    writer.append("  ").append(expression.getPostComment());
                }
                writer.append("\n");
            }
            i++;
        }
        if (!canInline) {
            writer.append(indent);
        }
        writer.append("]");
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
    }

    public void add(Expression expression) {
        expressions.add(expression);
    }

    public void addIfNew(Expression e) {
        if (!contains(e)) {
            add(e);
        }
    }
    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        for (Expression expression : expressions) {
            expression.preOrder(nodes);
        }
    }

    @Override
    public void postOrder(List<Node> nodes) {
        for (Expression expression : expressions) {
            expression.postOrder(nodes);
        }
        nodes.add(this);
    }

    public static ListExpression build(List<String> sources) {
        ListExpression list = new ListExpression();
        for (String source : sources) {
            list.add(LiteralExpression.string(source));
        }
        return list;
    }

    public int size() {
        return expressions.size();
    }

    public boolean contains(Expression e) {
        for (Expression expression : expressions) {
            if (expression.equals(e)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(String managed) {
        return contains(LiteralExpression.string(managed));
    }
}
