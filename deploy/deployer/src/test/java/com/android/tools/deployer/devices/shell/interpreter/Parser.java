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

import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.BACKTICK;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.CONDITIONAL_AND;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.DO;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.DONE;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.DOUBLE_CONDITIONAL_BINARY;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.DOUBLE_CONDITIONAL_END;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.DOUBLE_CONDITIONAL_START;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.DOUBLE_CONDITIONAL_UNARY;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.EOF;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.FI;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.FILE_PATH;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.FOR;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.IF;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.IN;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.PIPE;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.QUOTED_STRING;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.SEMICOLON;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.THEN;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.VAR;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.WHITESPACE_SEMICOLON_DELIMITED;
import static com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.TokenType.WORD;

import com.android.annotations.NonNull;
import com.android.tools.deployer.devices.shell.interpreter.BashTokenizer.Token;
import com.android.tools.deployer.devices.shell.interpreter.Expression.AssignmentExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.BinaryExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.ChainedStatement;
import com.android.tools.deployer.devices.shell.interpreter.Expression.CommandExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.ConditionalAndExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.ConditionalCheck;
import com.android.tools.deployer.devices.shell.interpreter.Expression.EmptyExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.ForExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.IfExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.ListExpression;
import com.android.tools.deployer.devices.shell.interpreter.Expression.PipeStatement;
import com.android.tools.deployer.devices.shell.interpreter.Expression.VarSubExpression;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    @NonNull
    public static Expression parse(@NonNull String command) {
        return parseScript(new BashTokenizer(command));
    }

    /**
     * Parses a (sub)script:
     *   S ::= E
     *      |  E;
     *      |  E; S
     *      |  E && S
     *      |  E | S
     */
    @NonNull
    private static Expression parseScript(@NonNull BashTokenizer tokenizer) {
        // Since execution is depth-first, we'll create a left-heavy tree for expression execution.
        Expression.BinaryExpression previousExpression =
                new ChainedStatement(new EmptyExpression());
        while (true) {
            Token token =
                    tokenizer.peekToken(
                            FILE_PATH, QUOTED_STRING, CONDITIONAL_AND, PIPE, SEMICOLON, BACKTICK, VAR, EOF);
            if (token.getType() == EOF) {
                previousExpression.bind(new EmptyExpression());
                return previousExpression;
            }
            Expression expression = parseExpression(tokenizer);
            previousExpression.bind(expression);

            Token operator = tokenizer.parseToken(CONDITIONAL_AND, PIPE, SEMICOLON, EOF);
            switch (operator.getType()) {
                case CONDITIONAL_AND:
                    tokenizer.peekToken(
                            FILE_PATH, QUOTED_STRING, BACKTICK); // We don't allow empty after "&&".
                    previousExpression = new ConditionalAndExpression(previousExpression);
                    break;
                case PIPE:
                    tokenizer.peekToken(
                            FILE_PATH, QUOTED_STRING, BACKTICK); // We don't allow empty after "|".
                    previousExpression = new PipeStatement(previousExpression);
                    break;
                case SEMICOLON:
                    tokenizer.peekToken(
                            FILE_PATH,
                            QUOTED_STRING,
                            BACKTICK,
                            EOF); // Empty after semicolon terminates the chain.
                    previousExpression = new ChainedStatement(previousExpression);
                    break;
                case EOF:
                    return previousExpression;
                default:
                    throw new RuntimeException("Unsupported operator: " + operator.getText());
            }
        }
    }

    /**
     * Parses the body of a if or for loop:
     *   B ::= E;
     *      |  E; B
     *      |  E && B
     *      |  E | B
     */
    @NonNull
    private static Expression parseBody(@NonNull BashTokenizer tokenizer) {
        // Since execution is depth-first, we'll create a left-heavy tree for expression execution.
        BinaryExpression previousExpression = new ChainedStatement(new EmptyExpression());
        while (true) {
            Expression expression = parseExpression(tokenizer);
            previousExpression.bind(expression);

            Token operator = tokenizer.parseToken(CONDITIONAL_AND, PIPE, SEMICOLON, EOF);
            switch (operator.getType()) {
                case CONDITIONAL_AND:
                    previousExpression = new ConditionalAndExpression(previousExpression);
                    break;
                case PIPE:
                    previousExpression = new PipeStatement(previousExpression);
                    break;
                case SEMICOLON:
                    Token token =
                            tokenizer.peekToken(
                                    FI,
                                    DONE,
                                    FILE_PATH,
                                    QUOTED_STRING,
                                    BACKTICK); // Empty after semicolon terminates the chain.
                    if (token.getType() == FI || token.getType() == DONE) {
                        return previousExpression;
                    }
                    previousExpression = new ChainedStatement(previousExpression);
                    break;
                case EOF:
                    return previousExpression;
                default:
                    throw new RuntimeException("Unsupported operator: " + operator.getText());
            }
        }
    }

    /**
     * Parses/recognizes an expression:
     *   E ::= if ...
     *      |  for ...
     *      |  command params...
     *      |  "/bin/path/to/command" params...
     *      |  `command params...` params...
     *      |  var=...
     */
    @NonNull
    private static Expression parseExpression(@NonNull BashTokenizer tokenizer) {
        Token token = tokenizer.peekToken(IF, FOR, FILE_PATH, QUOTED_STRING, BACKTICK, VAR);
        switch (token.getType()) {
            case IF:
                return parseIf(tokenizer);
            case FOR:
                return parseFor(tokenizer);
            case BACKTICK:
            case FILE_PATH:
            case QUOTED_STRING:
            case VAR:
                return parseCommand(tokenizer);
            default:
                throw new UnsupportedOperationException(
                        "Can not parse keyword: " + token.getText());
        }
    }

    /**
     * Parses simple bash commands or variable assignments in the form:
     *   C ::= command [`]arg0[`] [`]arg1[`] ...
     *      |  var= `script`
     */
    @NonNull
    private static Expression parseCommand(@NonNull BashTokenizer tokenizer) {
        Token command = tokenizer.parseToken(BACKTICK, VAR, QUOTED_STRING, FILE_PATH);

        // Special case for environment variable assignments.
        // Note that there cannot exist any space between the var name and "=".
        if (command.getType() == VAR) {
            assert command.getText().endsWith("=");
            Token value = tokenizer.parseToken(BACKTICK, QUOTED_STRING, FILE_PATH);
            String varName = command.getText().substring(0, command.getText().lastIndexOf('='));
            Expression assignmentExpression;
            switch (value.getType()) {
                case BACKTICK:
                    assignmentExpression =
                            new AssignmentExpression(varName, parseScript(tokenizer));
                    tokenizer.parseToken(BACKTICK);
                    break;
                case QUOTED_STRING:
                case FILE_PATH:
                    assignmentExpression =
                            new AssignmentExpression(
                                    varName, new VarSubExpression(value.getText()));
                    break;
                default:
                    throw new RuntimeException(
                            "Unrecognized punctuation: " + tokenizer.getResidual());
            }
            return assignmentExpression;
        }

        // Parse the command itself.
        CommandExpression commandExpression;
        if (command.getType() == BACKTICK) {
            commandExpression = new CommandExpression(parseScript(tokenizer));
            tokenizer.parseToken(BACKTICK);
        } else {
            commandExpression = new CommandExpression(new VarSubExpression(command.getText()));
        }

        // Parse all the parameters for the command.
        while (true) {
            Token param =
                    tokenizer.peekToken(
                            PIPE,
                            CONDITIONAL_AND,
                            SEMICOLON,
                            BACKTICK,
                            QUOTED_STRING,
                            WHITESPACE_SEMICOLON_DELIMITED,
                            EOF);
            switch (param.getType()) {
                case PIPE:
                case CONDITIONAL_AND:
                case SEMICOLON:
                case EOF:
                    return commandExpression;
                case WHITESPACE_SEMICOLON_DELIMITED:
                case QUOTED_STRING:
                    tokenizer.parseToken(WHITESPACE_SEMICOLON_DELIMITED, QUOTED_STRING);
                    commandExpression.addParam(new VarSubExpression(param.getText()));
                    break;
                case BACKTICK:
                    tokenizer.parseToken(BACKTICK);
                    commandExpression.addParam(parseScript(tokenizer));
                    tokenizer.parseToken(BACKTICK);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Unexpected token: " + tokenizer.getResidual());
            }
        }
    }

    /**
     * Parses for loops:
     *   E ::= for var in L do B done
     */
    @NonNull
    public static Expression parseFor(@NonNull BashTokenizer tokenizer) {
        tokenizer.parseToken(FOR);
        Token varToken = tokenizer.parseToken(WORD);
        tokenizer.parseToken(IN);
        Expression listExpression = parseList(tokenizer);
        tokenizer.parseToken(DO);
        Expression forBody = parseBody(tokenizer);
        tokenizer.parseToken(DONE);

        return new ForExpression(varToken.getText(), listExpression, forBody);
    }

    /**
     * Parses if statements:
     *   E ::= if [[ E ]]; then B done
     */
    @NonNull
    public static Expression parseIf(@NonNull BashTokenizer tokenizer) {
        tokenizer.parseToken(IF);
        tokenizer.parseToken(DOUBLE_CONDITIONAL_START);

        BinaryExpression conditionalExpression = new ChainedStatement(new EmptyExpression());
        while (true) {
            Token firstParam = tokenizer.parseToken(DOUBLE_CONDITIONAL_UNARY, FILE_PATH);
            String operator;
            Expression firstExpression;
            Expression secondExpression;
            if (firstParam.getType() == DOUBLE_CONDITIONAL_UNARY) {
                firstExpression = new EmptyExpression();
                operator = firstParam.getText();
            } else {
                firstExpression = new VarSubExpression(firstParam.getText());
                operator = tokenizer.parseToken(DOUBLE_CONDITIONAL_BINARY).getText();
            }
            secondExpression =
                    new VarSubExpression(tokenizer.parseToken(FILE_PATH, QUOTED_STRING).getText());
            conditionalExpression.bind(
                    new ConditionalCheck(firstExpression, operator, secondExpression));

            Token expressionJunction =
                    tokenizer.parseToken(CONDITIONAL_AND, DOUBLE_CONDITIONAL_END);
            if (expressionJunction.getType() == CONDITIONAL_AND) {
                conditionalExpression = new ConditionalAndExpression(conditionalExpression);
            } else {
                break;
            }
        }

        tokenizer.parseToken(SEMICOLON); // We're hacking the list.
        tokenizer.parseToken(THEN);

        Expression body = parseBody(tokenizer);
        tokenizer.parseToken(FI);

        return new IfExpression(conditionalExpression, body);
    }

    /**
     * Parses list expressions:
     *   L ::= E;
     *      |  E; L
     *      |  `E`; L
     */
    @NonNull
    private static Expression parseList(@NonNull BashTokenizer tokenizer) {
        final List<Expression> expressions = new ArrayList<>();

        while (true) {
            Token token = tokenizer.peekToken(BACKTICK, WORD, SEMICOLON, FILE_PATH);
            switch (token.getType()) {
                case BACKTICK:
                case WORD:
                    expressions.add(parseExpression(tokenizer));
                    break;
                case SEMICOLON:
                    tokenizer.parseToken(SEMICOLON);
                    return new ListExpression(expressions);
                default:
                    tokenizer.parseToken(FILE_PATH);
                    expressions.add(new VarSubExpression(token.getText()));
                    break;
            }
        }
    }
}
