/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer.devices.shell.interpreter;

import com.android.annotations.NonNull;
import com.android.tools.deployer.devices.shell.ShellCommand;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Expression {
    ExecutionResult execute(@NonNull ShellEnv env);

    class ExecutionResult {
        public String text;
        boolean success;

        private ExecutionResult(boolean success) {
            this.text = null;
            this.success = success;
        }

        private ExecutionResult(String result) {
            this.text = result;
            this.success = true;
        }
    }

    class EmptyExpression implements Expression {
        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            // do nothing
            return new ExecutionResult(true);
        }
    }

    abstract class BinaryExpression implements Expression {
        @NonNull protected Expression firstExpression;
        @NonNull protected Expression secondExpression;

        public void bind(@NonNull Expression exp) {
            if (firstExpression == null) {
                firstExpression = exp;
            } else if (secondExpression == null) {
                secondExpression = exp;
            } else {
                throw new RuntimeException(
                        "Attempting to bind more than two expressions to a BinaryExpression");
            }
        }
    }

    class ChainedStatement extends BinaryExpression {
        public ChainedStatement(@NonNull Expression firstExpression) {
            bind(firstExpression);
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            firstExpression.execute(env);
            return secondExpression.execute(env);
        }
    }

    class ConditionalAndExpression extends BinaryExpression {
        public ConditionalAndExpression(@NonNull Expression firstExpression) {
            bind(firstExpression);
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            if (firstExpression.execute(env).success) {
                try {
                    // Print out the pipe, since we're not piping.
                    env.getOutputStream().print(env.readStringFromPipe());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return secondExpression.execute(env);
            }
            return new ExecutionResult(false);
        }
    }

    class PipeStatement extends BinaryExpression {
        public PipeStatement(@NonNull Expression firstExpression) {
            bind(firstExpression);
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            // Note we're not implementing a full process forking mechanism, and are just buffering everything in RAM.
            boolean success = firstExpression.execute(env).success;
            if (success) {
                env.preparePipe();
                success = secondExpression.execute(env).success;
            }
            return new ExecutionResult(success);
        }
    }

    class ConditionalCheck implements Expression {
        @NonNull private final Expression firstExpression;
        @NonNull private final String operator;
        @NonNull private final Expression secondExpression;

        public ConditionalCheck(
                @NonNull Expression firstExpression,
                @NonNull String operator,
                @NonNull Expression secondExpression) {
            this.firstExpression = firstExpression;
            this.operator = operator;
            this.secondExpression = secondExpression;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            ExecutionResult result = firstExpression.execute(env);
            String firstResult = result.text;

            if (!result.success) {
                return new ExecutionResult(false);
            }

            switch (operator) {
                case "&&":
                    result = secondExpression.execute(env);
                    break;
                case "||":
                    if (result.success) {
                        return new ExecutionResult(true);
                    }
                    result = secondExpression.execute(env);
                    break;
                case "==":
                    if (!result.success) {
                        break;
                    }
                    result = secondExpression.execute(env);
                    String secondResult = result.text;
                    // We only handle path comparisons right now.
                    return new ExecutionResult(secondResult.startsWith(firstResult));
            }
            return new ExecutionResult(result.success); // Clear out the text.
        }
    }

    class AssignmentExpression implements Expression {
        @NonNull private final String variableName;
        @NonNull private final Expression expression;

        public AssignmentExpression(@NonNull String variableName, @NonNull Expression expression) {
            this.variableName = variableName;
            this.expression = expression;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            ExecutionResult result = expression.execute(env);
            if (result.success) {
                env.setScope(variableName, result.text);
            }
            return new ExecutionResult(result.success);
        }
    }

    class CommandExpression implements Expression {
        @NonNull private final Expression commandExpression;
        @NonNull private final List<Expression> params;

        public CommandExpression(@NonNull Expression commandExpression) {
            this.commandExpression = commandExpression;
            params = new ArrayList<>();
        }

        public void addParam(@NonNull Expression expression) {
            params.add(expression);
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            try {
                ExecutionResult result = commandExpression.execute(env);
                String commandName = result.text;
                ShellCommand command = env.getCommand(commandName);
                if (command == null) {
                    env.getPrintStdout()
                            .format(String.format("/system/bin/sh: %s: not found\n", commandName));
                    return new ExecutionResult(false);
                }

                List<String> paramResults = new ArrayList<>();
                for (Expression expression : params) {
                    result = expression.execute(env);
                    paramResults.add(result.text);
                }
                return new ExecutionResult(
                        command.execute(
                                env.getDevice(),
                                paramResults.toArray(new String[] {}),
                                env.takeStdin(),
                                env.getPrintStdout()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Expression that handles paths and variable substitution. */
    class VarSubExpression implements Expression {
        private static final Pattern VAR_PATTERN = Pattern.compile("\\$(\\p{Alpha}\\w*)");

        private final String expressionString;

        public VarSubExpression(@NonNull String expressionString) {
            this.expressionString = expressionString;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            Matcher matcher = VAR_PATTERN.matcher(expressionString);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, "");
                buffer.append(env.getScope(matcher.group(1)));
            }
            matcher.appendTail(buffer);
            return new ExecutionResult(buffer.toString());
        }
    }

    class ForExpression implements Expression {
        @NonNull private final String varName;
        @NonNull private final Expression listExpression;
        @NonNull private final Expression bodyExpression;

        public ForExpression(
                @NonNull String varName,
                @NonNull Expression listExpression,
                @NonNull Expression bodyExpression) {
            this.varName = varName;
            this.listExpression = listExpression;
            this.bodyExpression = bodyExpression;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            ExecutionResult result = listExpression.execute(env);
            if (!result.success) {
                throw new RuntimeException("List in for loop failed to materialize.");
            }
            try {
                String listString = result.text;
                for (String listItem : listString.split("\\s+")) {
                    env.setScope(varName, listItem);
                    result = bodyExpression.execute(env);
                    if (!result.success) {
                        return new ExecutionResult(false);
                    }
                }
                return new ExecutionResult(true);
            } finally {
                // We're not handling nested scopes right now, including global.
                env.setScope(varName, null);
            }
        }
    }

    class IfExpression implements Expression {
        @NonNull private final Expression conditionalExpression;
        @NonNull private final Expression body;

        public IfExpression(@NonNull Expression conditionalExpression, @NonNull Expression body) {
            this.conditionalExpression = conditionalExpression;
            this.body = body;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            if (conditionalExpression.execute(env).success) {
                return new ExecutionResult(body.execute(env).success);
            }
            return new ExecutionResult(true);
        }
    }

    class ListExpression implements Expression {
        private final List<Expression> expressionsList;

        public ListExpression(@NonNull List<Expression> expressionList) {
            this.expressionsList = expressionList;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellEnv env) {
            try {
                StringBuilder builder = new StringBuilder();
                for (Expression expression : expressionsList) {
                    ExecutionResult result = expression.execute(env);
                    if (!result.success) {
                        return new ExecutionResult(false);
                    }
                    String resolution = result.text;
                    if (resolution != null) {
                        builder.append(resolution);
                        builder.append(" ");
                    }
                    builder.append(env.readStringFromPipe());
                }
                return new ExecutionResult(builder.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
