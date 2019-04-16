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
import com.android.tools.deployer.devices.shell.ExternalCommand;
import com.android.tools.deployer.devices.shell.ShellCommand;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Expression {
    ExecutionResult execute(@NonNull ShellContext env);

    class ExecutionResult {
        public String text;
        public int code;

        private ExecutionResult(int code) {
            this.text = null;
            this.code = code;
        }

        private ExecutionResult(String result) {
            this.text = result;
            this.code = 0;
        }
    }

    class EmptyExpression implements Expression {
        @Override
        public ExecutionResult execute(@NonNull ShellContext env) {
            // do nothing
            return new ExecutionResult(0);
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
        public ExecutionResult execute(@NonNull ShellContext env) {
            firstExpression.execute(env);
            return secondExpression.execute(env);
        }
    }

    class ConditionalAndExpression extends BinaryExpression {
        public ConditionalAndExpression(@NonNull Expression firstExpression) {
            bind(firstExpression);
        }

        @Override
        public ExecutionResult execute(@NonNull ShellContext env) {
            ExecutionResult result = firstExpression.execute(env);
            if (result.code == 0) {
                try {
                    // Print out the pipe, since we're not piping.
                    env.getOutputStream().print(env.readStringFromPipe());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return secondExpression.execute(env);
            }
            return new ExecutionResult(result.code);
        }
    }

    class PipeStatement extends BinaryExpression {
        public PipeStatement(@NonNull Expression firstExpression) {
            bind(firstExpression);
        }

        @Override
        public ExecutionResult execute(@NonNull ShellContext env) {
            // Note we're not implementing a full process forking mechanism, and are just buffering everything in RAM.
            int code = firstExpression.execute(env).code;
            if (code == 0) {
                env.preparePipe();
                code = secondExpression.execute(env).code;
            }
            return new ExecutionResult(code);
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
        public ExecutionResult execute(@NonNull ShellContext env) {
            ExecutionResult result = firstExpression.execute(env);
            String firstResult = result.text;


            switch (operator) {
                case "&&":
                    if (result.code != 0) {
                        return result;
                    }
                    result = secondExpression.execute(env);
                    break;
                case "||":
                    if (result.code == 0) {
                        return result;
                    }
                    result = secondExpression.execute(env);
                    break;
                case "==":
                    if (result.code != 0) {
                        break;
                    }
                    result = secondExpression.execute(env);
                    String secondResult = result.text;
                    // We only handle path comparisons right now.
                    return new ExecutionResult(secondResult.startsWith(firstResult) ? 0 : 1);
            }
            return new ExecutionResult(result.code); // Clear out the text.
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
        public ExecutionResult execute(@NonNull ShellContext env) {
            ExecutionResult result = expression.execute(env);
            if (result.code == 0) {
                env.setScope(variableName, result.text);
            }
            return new ExecutionResult(result.code);
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
        public ExecutionResult execute(@NonNull ShellContext env) {
            try {
                ExecutionResult result = commandExpression.execute(env);
                String commandName = result.text;
                List<String> paramResults = new ArrayList<>();
                for (Expression expression : params) {
                    result = expression.execute(env);
                    paramResults.add(result.text);
                }
                String[] cmdArgs = paramResults.toArray(new String[] {});
                InputStream stdin = env.takeStdin();
                PrintStream stdout = env.getPrintStdout();

                ShellCommand command = env.getDevice().getShell().getCommand(commandName);
                int code = 0;
                if (command == null) {
                    if (env.getDevice().hasFile(commandName)) {
                        if (env.getDevice().isExecutable(commandName)) {
                            command = new ExternalCommand(commandName);
                        } else {
                            stdout.format(
                                    "/system/bin/sh: cmd: can't execute: Permission denied\n");
                            code = 126;
                        }
                    } else {
                        stdout.format(
                                String.format("/system/bin/sh: %s: not found\n", commandName));
                        code = 127;
                    }
                }
                if (command != null) {
                    code = command.execute(env, cmdArgs, stdin, stdout);
                }
                return new ExecutionResult(code);
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
        public ExecutionResult execute(@NonNull ShellContext env) {
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
        public ExecutionResult execute(@NonNull ShellContext env) {
            ExecutionResult result = listExpression.execute(env);
            if (result.code != 0) {
                throw new RuntimeException("List in for loop failed to materialize.");
            }
            try {
                String listString = result.text;
                for (String listItem : listString.split("\\s+")) {
                    env.setScope(varName, listItem);
                    result = bodyExpression.execute(env);
                    if (result.code != 0) {
                        return new ExecutionResult(result.code);
                    }
                }
                return new ExecutionResult(0);
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
        public ExecutionResult execute(@NonNull ShellContext env) {
            if (conditionalExpression.execute(env).code == 0) {
                return new ExecutionResult(body.execute(env).code);
            }
            return new ExecutionResult(0);
        }
    }

    class ListExpression implements Expression {
        private final List<Expression> expressionsList;

        public ListExpression(@NonNull List<Expression> expressionList) {
            this.expressionsList = expressionList;
        }

        @Override
        public ExecutionResult execute(@NonNull ShellContext env) {
            try {
                StringBuilder builder = new StringBuilder();
                for (Expression expression : expressionsList) {
                    ExecutionResult result = expression.execute(env);
                    if (result.code != 0) {
                        return new ExecutionResult(result.code);
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
