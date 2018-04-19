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

import com.google.common.base.Preconditions;
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
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (i > 0 && !shouldCompact(statements.get(i-1), statement)) {
                writer.append("\n");
            }
            if (!statement.isHidden()) {
                statement.write(writer);
            }
        }
    }

    /**
     * Returns {@code true} if the pair of statements {@code s1}, {@code s2}
     * should be printed with no intervening blank line.
     */
    private boolean shouldCompact(Statement s1, Statement s2) {
        return !s2.hasPreComment()
            && s1 instanceof CallStatement && ((CallStatement)s1).getCall().getFunctionName().equals("load")
            && s2 instanceof CallStatement && ((CallStatement)s2).getCall().getFunctionName().equals("load");
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

    /** Returns the load statement for the function called by {@code functionCall}, or null. */
    public CallStatement getLoad(CallStatement functionCall) {
        String functionNameLiteral = Token.string(functionCall.getCall().getFunctionName()).value();
        CallStatement loadStatement = null;
        for (Statement statement : statements) {
            if (statement == functionCall) {
                return loadStatement;  // last found before functionCall
            }
            if (statement instanceof CallStatement) {
                CallStatement callStatement = (CallStatement) statement;
                CallExpression callExpression = callStatement.getCall();
                if (callExpression.getFunctionName().equals("load") &&
                    callExpression.getArguments().stream()
                        .map(Argument::getExpression)
                        .map(Expression::getLiteral)
                        .anyMatch(functionNameLiteral::equals)) {
                    loadStatement = callStatement;  // update last found
                }
            }
        }
        throw new IllegalArgumentException(functionCall + " is not in " + statements);
    }

    public void addStatement(Statement statement) {
        statements.add(statement);
    }

    public void addStatementBefore(Statement toAdd, Statement that) {
        Preconditions.checkArgument(statements.contains(that), that + " is not in " + statements);
        statements.add(statements.indexOf(that), toAdd);
    }

    /**
     * Hides all the statements that are managed by this plugin. This plugin identifies
     * managed rules by the tag "managed" present in the rule.
     */
    public void hideManagedStatements() {
        for (Statement statement : statements) {
            if (statement instanceof CallStatement) {
                CallStatement call = ((CallStatement)statement);
                if (call.isManaged()) {
                    statement.setHidden(true);
                }
            }
        }
    }
}
