/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.GradleVisitor;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.GradleScanner;
import com.android.tools.lint.detector.api.Location;
import com.android.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;

// Copy of com.android.tools.lint.gradle.GroovyGradleVisitor
//
// THIS CODE DUPLICATION IS NOT AN IDEAL SITUATION! But it's here because we don't want
// a groovy dependency in lint-cli, so instead there's a copy in tests and a copy
// in the Gradle module.
//

/**
 * Implementation of the {@link GradleDetector} using a real Groovy AST, which the Gradle plugin has
 * access to.
 */
public class GroovyGradleVisitor extends GradleVisitor {
    @Override
    public void visitBuildScript(
            @NonNull GradleContext context, @NonNull List<? extends GradleScanner> detectors) {
        try {
            visitQuietly(context, detectors);
        } catch (Throwable t) {
            // Unlike the Gradle version, from tests we want to see this!
            t.printStackTrace();

            // else: ignore
            // Parsing the build script can involve class loading that we sometimes can't
            // handle. This happens for example when running lint in build-system/tests/api/.
            // This is a lint limitation rather than a user error, so don't complain
            // about these. Consider reporting a Issue#LINT_ERROR.
        }
    }

    private static void visitQuietly(
            @NonNull final GradleContext context,
            @NonNull final List<? extends GradleScanner> detectors) {
        CharSequence sequence = context.getContents();
        if (sequence == null) {
            return;
        }

        final String source = sequence.toString();
        List<ASTNode> astNodes = new AstBuilder().buildFromString(source);
        GroovyCodeVisitor visitor =
                new CodeVisitorSupport() {
                    private final List<MethodCallExpression> mMethodCallStack = new ArrayList<>();

                    @Override
                    public void visitMethodCallExpression(MethodCallExpression expression) {
                        mMethodCallStack.add(expression);
                        super.visitMethodCallExpression(expression);
                        assert !mMethodCallStack.isEmpty();
                        assert mMethodCallStack.get(mMethodCallStack.size() - 1) == expression;
                        mMethodCallStack.remove(mMethodCallStack.size() - 1);
                    }

                    @Override
                    public void visitTupleExpression(TupleExpression tupleExpression) {
                        if (!mMethodCallStack.isEmpty()) {
                            MethodCallExpression call =
                                    mMethodCallStack.get(mMethodCallStack.size() - 1);
                            if (call.getArguments() == tupleExpression) {
                                String parent = call.getMethodAsString();
                                String parentParent = getParentParent();
                                String parent3 = getParentN(2);
                                if (tupleExpression instanceof ArgumentListExpression) {
                                    ArgumentListExpression ale =
                                            (ArgumentListExpression) tupleExpression;
                                    List<Expression> expressions = ale.getExpressions();
                                    if (expressions.size() == 1
                                            && expressions.get(0) instanceof ClosureExpression) {
                                        ClosureExpression closureExpression =
                                                (ClosureExpression) expressions.get(0);
                                        Statement block = closureExpression.getCode();
                                        if (block instanceof BlockStatement) {
                                            BlockStatement bs = (BlockStatement) block;
                                            for (Statement statement : bs.getStatements()) {
                                                if (statement instanceof ExpressionStatement) {
                                                    ExpressionStatement e =
                                                            (ExpressionStatement) statement;
                                                    if (e.getExpression()
                                                            instanceof MethodCallExpression) {
                                                        checkDslProperty(
                                                                parent,
                                                                (MethodCallExpression)
                                                                        e.getExpression(),
                                                                parentParent,
                                                                detectors);
                                                    }
                                                } else if (statement instanceof ReturnStatement) {
                                                    // Single item in block
                                                    ReturnStatement e = (ReturnStatement) statement;
                                                    if (e.getExpression()
                                                            instanceof MethodCallExpression) {
                                                        checkDslProperty(
                                                                parent,
                                                                (MethodCallExpression)
                                                                        e.getExpression(),
                                                                parentParent,
                                                                detectors);
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Map<String, String> namedArguments = new HashMap<>();
                                        List<String> unnamedArguments = new ArrayList<>();
                                        extractMethodCallArguments(
                                                tupleExpression, unnamedArguments, namedArguments);
                                        for (GradleScanner scanner : detectors) {
                                            scanner.checkMethodCall(
                                                    context,
                                                    parent,
                                                    parentParent,
                                                    parent3,
                                                    namedArguments,
                                                    unnamedArguments,
                                                    call);
                                        }
                                    }
                                } else {
                                    Map<String, String> namedArguments = new HashMap<>();
                                    List<String> unnamedArguments = new ArrayList<>();
                                    extractMethodCallArguments(
                                            tupleExpression, unnamedArguments, namedArguments);
                                    for (GradleScanner scanner : detectors) {
                                        scanner.checkMethodCall(
                                                context,
                                                parent,
                                                parentParent,
                                                parent3,
                                                namedArguments,
                                                unnamedArguments,
                                                call);
                                    }
                                }
                            }
                        }

                        super.visitTupleExpression(tupleExpression);
                    }

                    private void extractMethodCallArguments(
                            TupleExpression tupleExpression,
                            List<String> unnamedArguments,
                            Map<String, String> namedArguments) {
                        for (Expression subExpr : tupleExpression.getExpressions()) {
                            if (subExpr instanceof NamedArgumentListExpression) {
                                NamedArgumentListExpression nale =
                                        (NamedArgumentListExpression) subExpr;
                                for (MapEntryExpression mae : nale.getMapEntryExpressions()) {
                                    namedArguments.put(
                                            mae.getKeyExpression().getText(),
                                            mae.getValueExpression().getText());
                                }
                            }
                        }
                    }

                    private String getParentN(int n) {
                        int nParent = 0;
                        for (int i = mMethodCallStack.size() - 2; i >= 0; i--) {
                            MethodCallExpression expression = mMethodCallStack.get(i);
                            Expression arguments = expression.getArguments();
                            if (arguments instanceof ArgumentListExpression) {
                                ArgumentListExpression ale = (ArgumentListExpression) arguments;
                                List<Expression> expressions = ale.getExpressions();
                                if (expressions.size() == 1
                                        && expressions.get(0) instanceof ClosureExpression) {
                                    nParent += 1;
                                }
                            }
                            if (nParent == n) {
                                return expression.getMethodAsString();
                            }
                        }
                        return null;
                    }

                    private String getParentParent() {
                        return getParentN(1);
                    }

                    private void checkDslProperty(
                            String parent,
                            MethodCallExpression c,
                            String parentParent,
                            @NonNull List<? extends GradleScanner> detectors) {
                        String property = c.getMethodAsString();
                        String value = getText(c.getArguments());
                        for (GradleScanner scanner : detectors) {
                            scanner.checkDslPropertyAssignment(
                                    context,
                                    property,
                                    value,
                                    parent,
                                    parentParent,
                                    c.getMethod(),
                                    c.getArguments(),
                                    c);
                        }
                    }

                    private String getText(ASTNode node) {
                        Pair<Integer, Integer> offsets = getOffsets(node, context);
                        return source.substring(offsets.getFirst(), offsets.getSecond());
                    }
                };

        for (ASTNode node : astNodes) {
            node.visit(visitor);
        }
    }

    @NonNull
    private static Pair<ASTNode, ASTNode> getBoundingNodes(ASTNode node) {
        if (node.getLastLineNumber() == -1 && node instanceof TupleExpression) {
            // This TupleExpression is not real, probably corresponding to a named argument
            // list.  Use its children instead.
            TupleExpression expression = (TupleExpression) node;
            List<Expression> expressions = expression.getExpressions();
            if (!expressions.isEmpty()) {
                return Pair.of(
                        getBoundingNodes(expressions.get(0)).getFirst(),
                        getBoundingNodes(expressions.get(expressions.size() - 1)).getSecond());
            }
        }
        if (node instanceof ArgumentListExpression) {
            // An ArgumentListExpression's bounds can extend beyond the last expression in the
            // argument list, for example into trailing whitespace or comments.  The bounds given
            // by the first and last subexpressions are more appropriate for highlighting issues
            // related to user code.
            ArgumentListExpression expression = (ArgumentListExpression) node;
            List<Expression> expressions = expression.getExpressions();
            if (!expressions.isEmpty()) {
                return Pair.of(
                        getBoundingNodes(expressions.get(0)).getFirst(),
                        getBoundingNodes(expressions.get(expressions.size() - 1)).getSecond());
            }
        }

        return Pair.of(node, node);
    }

    @NonNull
    private static Pair<Integer, Integer> getOffsets(ASTNode node, Context context) {
        Pair<ASTNode, ASTNode> boundingNodes = getBoundingNodes(node);
        ASTNode startNode = boundingNodes.getFirst();
        ASTNode endNode = boundingNodes.getSecond();

        CharSequence source = context.getContents();
        assert source != null; // because we successfully parsed
        int start = 0;
        int end = source.length();
        int line = 1;
        int startLine = startNode.getLineNumber();
        int startColumn = startNode.getColumnNumber();
        int endLine = endNode.getLastLineNumber();
        int endColumn = endNode.getLastColumnNumber();
        int column = 1;
        for (int index = 0, len = end; index < len; index++) {
            if (line == startLine && column == startColumn) {
                start = index;
            }
            if (line == endLine && column == endColumn) {
                end = index;
                break;
            }

            char c = source.charAt(index);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        return Pair.of(start, end);
    }

    @Override
    public int getStartOffset(@NonNull GradleContext context, @NonNull Object cookie) {
        ASTNode node = (ASTNode) cookie;
        Pair<Integer, Integer> offsets = getOffsets(node, context);
        return offsets.getFirst();
    }

    @NonNull
    @Override
    public Location createLocation(@NonNull GradleContext context, @NonNull Object cookie) {
        ASTNode node = (ASTNode) cookie;
        Pair<ASTNode, ASTNode> boundingNodes = getBoundingNodes(node);
        ASTNode startNode = boundingNodes.getFirst();
        ASTNode endNode = boundingNodes.getSecond();

        Pair<Integer, Integer> offsets = getOffsets(node, context);
        int fromLine = startNode.getLineNumber() - 1;
        int fromColumn = startNode.getColumnNumber() - 1;
        int toLine = endNode.getLastLineNumber() - 1;
        int toColumn = endNode.getLastColumnNumber() - 1;
        return Location.create(
                context.file,
                new DefaultPosition(fromLine, fromColumn, offsets.getFirst()),
                new DefaultPosition(toLine, toColumn, offsets.getSecond()));
    }
}
