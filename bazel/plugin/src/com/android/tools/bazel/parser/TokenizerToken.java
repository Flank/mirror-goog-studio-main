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

import com.android.tools.bazel.parser.ast.Token;

import java.util.Collections;

/**
 * A token that is backed by a tokenizer.
 */
class TokenizerToken extends Token {

    public int getLine() {
        return line;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    private final Tokenizer tokenizer;
    private int prefix;
    private int start;
    private int end;
    private int line;

    public TokenizerToken(Tokenizer tokenizer, Kind kind) {
        super(kind);
        this.tokenizer = tokenizer;
    }

    public void close(int prefix, int start, int end, int line) {
        this.prefix = prefix;
        this.start = start;
        this.end = end;
        this.line = line;
    }

    public String value() {
        return tokenizer.range(start, end);
    }

    @Override
    public boolean isFromFile() {
        return true;
    }

    @Override
    public String toString() {
        return kind + ":[" + tokenizer.range(prefix, start) + "]" + tokenizer.range(start, end);
    }

    public String asError() {
        // TODO, optimize this:
        String string = tokenizer.getString();
        int line = 1;
        int linestart = 0;
        for (int i = 0; i < start; i++) {
            if (string.charAt(i) == '\n') {
                line++;
                linestart = i+1;
            }
        }
        int lineend = string.indexOf('\n', start);
        int offset = start - linestart;
        String error = "Unexpected token: " + kind + ". Line " + line + ":" + offset + "\n";
        error += lineend != -1 ? string.substring(linestart, lineend) : string.substring(linestart);
        error += "\n" + String.join("", Collections.nCopies(offset, " ")) + "^\n";
        return error;
    }
}
