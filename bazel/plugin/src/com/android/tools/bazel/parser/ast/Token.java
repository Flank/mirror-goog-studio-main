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

public class Token {

    public static final Token NONE = new Token(Kind.NONE);

    public enum Kind {
        NONE,
        EOF,
        NEWLINE,
        IDENT,
        LPAREN,
        LSQUARE,
        RPAREN,
        RSQUARE,
        STRING,
        EQUALS,
        COMMA,
        PLUS,
        LCURLY,
        RCURLY,
        COLON,
        NUMBER,
        PERCENT,
    }

    public final Kind kind;
    private final String value;

    public Token(Kind kind) {
        this(null, kind);
    }

    public Token(String value, Kind kind) {
        this.kind = kind;
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isFromFile() {
        return false;
    }

    @Override
    public String toString() {
        return kind + ": " + value();
    }

    public static Token ident(String name) {
        return new Token(name, Kind.IDENT);
    }

    public static Token string(String value) {
        return new Token("\"" + value + "\"", Kind.STRING);
    }
}
