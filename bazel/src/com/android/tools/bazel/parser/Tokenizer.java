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

import com.android.tools.bazel.parser.ast.Token.Kind;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    List<TokenizerToken> tokens = new ArrayList<>();

    private int line;
    private int start;
    private int prefix;
    private int index;
    private int tokenIndex;
    private TokenizerToken token;
    private String string;

    public Tokenizer(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        string = new String(bytes, StandardCharsets.UTF_8);
        start = 0;
        prefix = 0;
        index = 0;
        line = 1;
        token = null;
        tokenize();
        token = new TokenizerToken(this, Kind.EOF);
        closeToken();
        tokenIndex = 0;
    }

    private void tokenize() {
        // We need to preserve whitespaces and preComments as we will write back the original file.
        boolean comment = false;
        while (index < string.length()) {
            char c = string.charAt(index);
            if (c == '\n') line++;
            if (token != null) {
                switch (token.kind) {
                    case IDENT:
                        if (!(Character.isLetter(c) || Character.isDigit(c) || c == '_')) {
                            rewind();
                            closeToken();
                        }
                        break;
                    case NUMBER:
                        if (!Character.isDigit(c)) {
                            rewind();
                            closeToken();
                        }
                        break;
                    case STRING:
                        if (c == '\\' && index < string.length() - 1) {
                            index++;
                        } else if (c == '\"') {
                            closeToken();
                        }
                        break;
                    case NEWLINE:
                        if (comment && c == '\n') {
                            comment = false;
                        }
                        if (!comment && !Character.isWhitespace(c) && c != '\n') {
                            rewind();
                            closeToken();
                        }
                        break;
                    default:
                        rewind();
                        closeToken();
                }
            } else {
                if (c == '\n') {
                    token = new TokenizerToken(this, Kind.NEWLINE);
                    comment = false;
                } else if (c == '#') {
                    token = new TokenizerToken(this, Kind.NEWLINE);
                    comment = true;
                } else if (Character.isWhitespace(c)) {
                    start++;
                } else if (Character.isLetter(c) || c == '_') {
                    token = new TokenizerToken(this, Kind.IDENT);
                } else if (Character.isDigit(c)) {
                    token = new TokenizerToken(this, Kind.NUMBER);
                } else if (c == '(') {
                    token = new TokenizerToken(this, Kind.LPAREN);
                } else if (c == ')') {
                    token = new TokenizerToken(this, Kind.RPAREN);
                } else if (c == '[') {
                    token = new TokenizerToken(this, Kind.LSQUARE);
                } else if (c == ']') {
                    token = new TokenizerToken(this, Kind.RSQUARE);
                } else if (c == '{') {
                    token = new TokenizerToken(this, Kind.LCURLY);
                } else if (c == '}') {
                    token = new TokenizerToken(this, Kind.RCURLY);
                } else if (c == '\"') {
                    token = new TokenizerToken(this, Kind.STRING);
                } else if (c == '=') {
                    token = new TokenizerToken(this, Kind.EQUALS);
                } else if (c == ',') {
                    token = new TokenizerToken(this, Kind.COMMA);
                } else if (c == '+') {
                    token = new TokenizerToken(this, Kind.PLUS);
                } else if (c == '%') {
                    token = new TokenizerToken(this, Kind.PERCENT);
                } else if (c == ':') {
                    token = new TokenizerToken(this, Kind.COLON);
                } else {
                    throw new RuntimeException("Unexpected token: " + c);
                }
            }
            index++;
        }
        if (token != null) {
            rewind();
            closeToken();
        }
    }

    private void rewind() {
        if (index < string.length() && string.charAt(index) == '\n') line--;
        index--;
    }

    private void closeToken() {
        token.close(prefix, start, index + 1, line);
        prefix = index + 1;
        start = index + 1;
        tokens.add(token);
        token = null;
    }

    public String range(int prefix, int end) {
        return string.substring(prefix, end);
    }

    public TokenizerToken nextToken() {
        return tokenIndex < tokens.size() ? tokens.get(tokenIndex++) : null;
    }

    public boolean peek(Kind kind) {
        return tokenIndex < tokens.size() && tokens.get(tokenIndex).kind == kind;
    }

    public String getString() {
        return string;
    }

    public TokenizerToken firstToken() {
        tokenIndex = 0;
        return nextToken();
    }

    public TokenizerToken lastToken() {
        return tokens.get(tokens.size() - 1);
    }
}
