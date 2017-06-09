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

package com.android.tools.bazel.parser;

import com.android.tools.bazel.parser.ast.*;
import com.android.tools.bazel.parser.ast.Token.Kind;
import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple BUILD file parser. It's written as a descent parser with 2 look ahead.
 * The grammar is in the comment of each method.
 */
public class BuildParser {
    private static final Logger LOG = Logger.getInstance(BuildParser.class);

    private final Tokenizer tokenizer;

    private TokenizerToken token;

    List<TokenizerToken> preComments = new ArrayList<>();
    List<TokenizerToken> postComments = new ArrayList<>();

    /**
     * Creates a build parser for the given tokenized imput.
     */
    public BuildParser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public Build parse() {
        parseComments();
        TokenizerToken last = tokenizer.lastToken();
        token = tokenizer.firstToken();
        Build build = new Build(token, parseStatements(), last);
        assignComments(build);
        return build;
    }

    /**
     * After parsing is done, comments are assigned to the nearest AST node.
     * There are two types of comments:
     *   # comments on their own
     *   function(a,b.c)
     *
     *   function(a,b,c) # comments after an expression.
     *
     * These two types are assigned differently, pre-comments, are assigned to the
     * next AST node in pre-order. post-comments are assigned to the previous AST node
     * in post-order.
     */
    private void assignComments(Build build) {
        List<Node> nodes = new ArrayList<>();
        build.preOrder(nodes);
        int i = 0;
        int j = 0;
        while (i < preComments.size() && j < nodes.size()) {
            Node node = nodes.get(j);
            TokenizerToken start = (TokenizerToken) node.getStart();
            TokenizerToken comment = preComments.get(i);
            if (comment.getStart() >= start.getStart()) {
                j++;
            } else if (start.getStart() > comment.getStart()) {
                node.addPreComment(comment);
                i++;
            }
        }

        nodes.clear();
        build.postOrder(nodes);
        i = postComments.size() - 1;
        j = nodes.size() - 1;
        while (i >= 0 && j >= 0) {
            TokenizerToken comment = postComments.get(i);
            Node node = nodes.get(j);
            TokenizerToken end = (TokenizerToken)node.getEnd();
            if (comment.getStart() <= end.getEnd()) {
                j--;
            } else if (end.getEnd() < comment.getStart()) {
                node.addPostComment(comment);
                i--;
            }
        }
    }

    /**
     * Removes comments and qualifies them in pre or post.
     */
    private void parseComments() {
        TokenizerToken t = tokenizer.firstToken();
        boolean first = true;
        while (t != null && t.kind != Kind.EOF) {
            if (t.kind == Kind.NEWLINE) {
                String value = t.value();
                if (value.startsWith("#")) {
                    if (first) {
                        preComments.add(t);
                    } else {
                        postComments.add(t);
                    }
                }
            }
            first = t.kind == Kind.NEWLINE;
            t = tokenizer.nextToken();
        }
    }

    /**
     * BUILD ::=
     *     STATEMENT BUILD
     *     EOF
     */
    private List<Statement> parseStatements() {
        List<Statement> list = new ArrayList<>();
        while (token.kind == Kind.NEWLINE) consume();
        while (token.kind != Kind.EOF) {
            Statement statement = parseStatement();
            list.add(statement);
            while (token.kind == Kind.NEWLINE) consume();
        }
        // TODO end of file preComments
        return list;
    }

    /**
     * STATEMENT ::=
     *     IDENT FUNCTION_ARGS
     *     IDENT = EXPRESSION
     */
    private Statement parseStatement() {
        TokenizerToken ident = consume(Kind.IDENT);
        while (token.kind == Kind.NEWLINE) consume();
        Statement statement;
        switch (token.kind) {
            case LPAREN:
                int startLine = token.getLine();
                boolean firstInLine = !peek(Kind.NEWLINE);
                List<Argument> args = parseFunctionArgs();
                CallExpression call = new CallExpression(ident, args, token);
                int endLine = consume(Kind.RPAREN).getLine();
                call.setSingleLine(startLine == endLine || (args.size() <= 1 && firstInLine));
                statement = new CallStatement(call);
                break;
            case EQUALS:
                consume();
                while (token.kind == Kind.NEWLINE) consume();
                statement = new Assignment(ident, parseExpression());
                break;
            default:
                LOG.error("Unexpected statement token: " + token);
                statement = null;
        }
        return statement;
    }

    /**
     * FUNCTION_ARGS ::=
     *     ()
     *     ( ARG )
     *     ( ARG ARGS )
     * ARGS ::=
     *     , ARG
     *     , ARG ARGS
     * ARG ::=
     *     IDENT = EXPRESSION
     *     EXPRESSION
     */
    private List<Argument> parseFunctionArgs() {
        List<Argument> args = new LinkedList<>();
        consume(Kind.LPAREN);
        while (token.kind == Kind.NEWLINE) consume();
        while (token.kind != Kind.RPAREN) {
            Argument argument;
            while (token.kind == Kind.NEWLINE) consume();
            if (token.kind == Kind.IDENT && peek(Kind.EQUALS)) {
                TokenizerToken name = token;
                consume(Kind.IDENT);
                consume(Kind.EQUALS);
                argument = new Argument(name, parseExpression());
            } else {
                argument = new Argument(parseExpression());
            }
            args.add(argument);
            while (token.kind == Kind.NEWLINE) consume();
            if (token.kind != Kind.RPAREN) {
                consume(Kind.COMMA);
                while (token.kind == Kind.NEWLINE) consume();
            }
        }
        return args;
    }

    /**
     *  EXPRESSION ::=
     *      PRIMARY
     *      PRIMARY * EXPRESSION
     *      PRIMARY % EXPRESSION
     */
    private Expression parseExpression() {
        Expression expression = parsePrimary();
        while (token.kind == Kind.NEWLINE) consume();
        switch (token.kind) {
            case PLUS:
            case PERCENT:
                TokenizerToken op = token;
                consume();
                expression = new BinaryExpression(expression, op, parseExpression());
                break;
        }

        return expression;
    }

    /**
     *  primary ::=
     *      NUMBER
     *      STRING
     *      IDENT
     *      IDENT FUNCTION_ARGS
     *      LIST
     *      DICT
     */
    private Expression parsePrimary() {
        Expression expression = null;
        switch (token.kind) {
            case NUMBER:
                expression = new LiteralExpression(token);
                consume();
                break;
            case STRING:
                expression = new LiteralExpression(token);
                consume();
                break;
            case IDENT:
                TokenizerToken ident = token;
                consume();
                if (token.kind == Kind.LPAREN) {
                    boolean firstInLine = !peek(Kind.NEWLINE);
                    int startLine = token.getLine();
                    List<Argument> arguments = parseFunctionArgs();
                    CallExpression call = new CallExpression(ident, arguments, token);
                    int endLine = consume(Kind.RPAREN).getLine();
                    call.setSingleLine(startLine == endLine || (arguments.size() <= 1 && firstInLine));
                    expression = call;
                } else {
                    expression = new LiteralExpression(ident);
                }
                break;
            case LSQUARE:
                expression = parseList();
                break;
            case LCURLY:
                expression = parseDict();
                break;
            default:
                LOG.error(token.asError());
        }
        return expression;
    }

    /**
     * DICT ::=
     *     {}
     *     { ENTRY }
     *     { ENTRY ENTRIES}
     * ENTRIES ::=
     *     , ENTRY
     *     , ENTRY ENTRIES
     * ENTRY ::=
     *     EXPRESSION : EXPRESSION
     */
    private Expression parseDict() {
        TokenizerToken first = consume(Kind.LCURLY);
        List<Expression> keys = new ArrayList<>();
        List<Expression> expressions = new ArrayList<>();
        while (token.kind == Kind.NEWLINE) consume();
        while (token.kind != Kind.RCURLY) {
            Expression key = parseExpression();
            keys.add(key);
            consume(Kind.COLON);
            Expression expression = parseExpression();
            expressions.add(expression);
            while (token.kind == Kind.NEWLINE) consume();
            if (token.kind != Kind.RCURLY) {
                consume(Kind.COMMA);
                while (token.kind == Kind.NEWLINE) consume();
            }
        }
        TokenizerToken last = consume(Kind.RCURLY);

        DictExpression dict = new DictExpression(first, last, keys, expressions);
        dict.setSingleLine(first.getLine() == last.getLine());
        return dict;
    }

    /**
     * LIST ::=
     *     []
     *     [ EXPRESSION ]
     *     [ EXPRESSION EXPRESSIONS ]
     * EXPRESSIONS ::=
     *     , EXPRESSION
     *     , EXPRESSION EXPRESSIONS
     */
    private Expression parseList() {
        TokenizerToken first = consume(Kind.LSQUARE);
        List<Expression> expressions = new ArrayList<>();
        boolean firstInline = token.kind != Kind.NEWLINE;
        while (token.kind == Kind.NEWLINE) consume();
        while (token.kind != Kind.RSQUARE) {
            Expression expression = parseExpression();
            expressions.add(expression);
            while (token.kind == Kind.NEWLINE) consume();
            if (token.kind != Kind.RSQUARE) {
                consume(Kind.COMMA);
                while (token.kind == Kind.NEWLINE) consume();
            }
        }
        TokenizerToken last = consume(Kind.RSQUARE);
        ListExpression list = new ListExpression(first, last, expressions);
        list.setSingleLine(firstInline);
        return list;
    }


    private boolean peek(Kind kind) {
        return tokenizer.peek(kind);
    }

    private TokenizerToken consume() {
        TokenizerToken last = token;
        token = tokenizer.nextToken();
        return last;
    }

    private TokenizerToken consume(Kind kind) {
        if (token.kind != kind) {
            LOG.error(token.asError());
        }
        return consume();
    }
}
