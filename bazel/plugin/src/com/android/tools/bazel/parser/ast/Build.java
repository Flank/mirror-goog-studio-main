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

public class Build extends Node {
    public static final String INDENT = "    ";
    private final List<Statement> statements;

    public Build(Token first, List<Statement> statements, Token last) {
        super(first, last);
        this.statements = statements;
    }

    public void write(PrintWriter writer) {
        boolean first = true;
        for (Statement statement : statements) {
            if (!first) {
                writer.append("\n");
            }
            if (!statement.isHidden()) {
                statement.write(writer);
            }
            first = false;
        }
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        for (Statement statement : statements) {
            statement.preOrder(nodes);
        }
    }

    @Override
    public void postOrder(List<Node> nodes) {
        for (Statement statement : statements) {
            statement.postOrder(nodes);
        }
        nodes.add(this);
    }

    public CallStatement getCall(String name) {
        for (Statement statement : statements) {
            if (statement instanceof CallStatement) {
                CallExpression call = ((CallStatement)statement).getCall();
                String literal = call.getLiteralArgument("name");
                if (literal != null && literal.equals("\"" + name + "\"")) {
                    return (CallStatement)statement;
                }
            }
        }
        return null;
    }

    public void addStatement(Statement statement) {
        statements.add(statement);
    }

    /**
     * Hides all the statements that are managed by this plugin. This plugin identifies
     * managed rules by the tag "managed" present in the rule.
     */
    public void hideManagedStatements() {
        for (Statement statement : statements) {
            if (statement instanceof CallStatement) {
                CallStatement call = ((CallStatement)statement);
                Expression tags = call.getCall().getArgument("tags");
                if (tags instanceof ListExpression) {
                    ListExpression list = (ListExpression)tags;
                    if (list.contains("managed")) {
                        statement.setHidden(true);
                    }
                }
            }
        }
    }
}
