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

import com.google.common.collect.ImmutableList;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A function call expression.
 */
public class CallExpression extends Expression {
    private final Token token;
    private final List<Argument> arguments;
    private boolean singleLine;

    public CallExpression(Token ident, List<Argument> arguments, Token end) {
        super(ident, end);
        this.token = ident;
        this.arguments = arguments;
    }

    /** Returns an expression to load {@code symbol} from {@code label}. */
    public static CallExpression load(String label, String symbol) {
        CallExpression callExpression = new CallExpression(Token.ident("load"),
            ImmutableList.of(Argument.string(label), Argument.string(symbol)), Token.NONE);
        callExpression.setSingleLine(true);
        return callExpression;
    }

    @Override
    public void write(String indent, PrintWriter writer) {
        for (String comment : preComments) {
            writer.append(indent).append(comment);
        }
        if (!preComments.isEmpty()) {
            writer.append(indent);
        }
        writer.append(token.value()).append("(");
        if (!singleLine) {
            writer.append("\n");
        }

        int i = 0;

        List<Argument> argumentsInWriteOrder = arguments.stream().allMatch(Argument::hasName) ?
            arguments.stream().sorted().collect(Collectors.toList()) : arguments;
        for (Argument argument : argumentsInWriteOrder) {
            if (!singleLine) {
                writer.append(indent + Build.INDENT);
            }
            argument.write(singleLine ? indent : indent + Build.INDENT, writer);
            if (singleLine) {
                if (i < arguments.size() - 1) {
                    writer.append(", ");
                }
            } else {
                writer.append(",");
                if (argument.hasPostComment()) {
                    writer.append("  ").append(argument.getPostComment());
                }
                writer.append("\n");
            }
            i++;
        }
        if (!singleLine) {
            writer.append(indent);
        }
        writer.append(")");
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
    }

    @Override
    public void preOrder(List<Node> nodes) {
        nodes.add(this);
        for (Argument argument : arguments) {
            argument.preOrder(nodes);
        }
    }

    @Override
    public void postOrder(List<Node> nodes) {
        for (Argument argument : arguments) {
            argument.postOrder(nodes);
        }
        nodes.add(this);
    }

    public String getFunctionName() {
        return token.value();
    }

    public ImmutableList<Argument> getArguments() {
        return ImmutableList.copyOf(arguments);
    }

    public String getLiteralArgument(String name) {
        Argument argument = getNamedArgument(name);
        return argument == null ? null : argument.getExpression().getLiteral();
    }

    public Argument getNamedArgument(String name) {
        for (Argument argument : arguments) {
            if (argument.hasName() && argument.getName().equals(name)) {
                return argument;
            }
        }
        return null;
    }

    public static CallExpression build(String ident, Map<String, Object> arguments) {
        List<Argument> list = new LinkedList<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Expression expression = null;
            if (entry.getValue() instanceof String) {
                expression = new LiteralExpression(Token.string((String) entry.getValue()));
            }
            list.add(new Argument(Token.ident(entry.getKey()), expression));
        }
        return new CallExpression(Token.ident(ident), list, Token.NONE);
    }

    public final void setArgument(String name, Collection<?> values) {
        if (!values.isEmpty()) {
            ListExpression list = ListExpression.build(
                values.stream().map(Object::toString).collect(Collectors.toList()));
            list.setSingleLine(values.size() <= 1);
            setArgument(name, list);
        } else {
            removeArgument(name);
        }
    }

    public final void setArgument(String name, Map<?, ?> values) {
        if (!values.isEmpty()) {
            Map<String, String> mapped =
                    values.entrySet()
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            e -> e.getKey().toString(),
                                            e -> e.getValue().toString()));
            DictExpression dict = DictExpression.build(mapped);
            dict.setSingleLine(values.size() <= 1);
            setArgument(name, dict);
        } else {
            removeArgument(name);
        }
    }

    private void removeArgument(String name) {
        Argument arg = getNamedArgument(name);
        if (arg != null) {
            arguments.remove(arg);
        }
    }

    public void setArgument(String name, Expression expression) {
        Argument arg = getNamedArgument(name);
        if (arg == null) {
            arguments.add(new Argument(Token.ident(name), expression));
        } else {
            arg.setExpression(expression);
        }
    }

    public Expression getArgument(String name) {
        Argument arg = getNamedArgument(name);
        if (arg == null) {
            return null;
        } else {
            return arg.getExpression();
        }
    }

    /** Adds a comment for buildifier not to sort {@code argumentName} for {@code reason}. */
    public void setDoNotSort(String argumentName, String reason) {
        Argument argument = getNamedArgument(argumentName);
        if (argument != null) {
            argument.setDoNotSort(reason);
        }
    }

    /** Ensures {@code element} is in the list {@code attribute}. */
    public void addElementToList(String attribute, String element) {
        Expression expression = getArgument(attribute);
        ListExpression list;
        if (expression == null) {
            list = ListExpression.build(ImmutableList.of());
            setArgument(attribute, list);
        } else if (expression instanceof BinaryExpression
                && (((BinaryExpression)expression).getLeft() instanceof ListExpression)) {
            list = (ListExpression) ((BinaryExpression) expression).getLeft();
        } else if (expression instanceof ListExpression) {
            list = (ListExpression) expression;
        } else {
            list = ListExpression.build(ImmutableList.of());
            BinaryExpression plus = new BinaryExpression(list, new Token("+", Token.Kind.PLUS), expression);
            setArgument(attribute, plus);
        }
        list.addIfNew(LiteralExpression.string(element));
        list.setSingleLine(list.size() <= 1);
    }
}
