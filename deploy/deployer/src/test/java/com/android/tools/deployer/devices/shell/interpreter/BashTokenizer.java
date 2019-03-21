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
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BashTokenizer {
    @NonNull private final String command;
    @NonNull private String residual; // The remainder of {@code command} yet to be processed.

    public BashTokenizer(@NonNull String command) {
        this.command = command;
        residual = command.trim();
    }

    @NonNull
    public Token peekToken(@NonNull TokenType... expectedTokenTypes) {
        for (TokenType tokenType : expectedTokenTypes) {
            Pattern p = tokenType.getPattern();
            Matcher m = p.matcher(residual);
            if (!m.find()) {
                continue;
            }
            if (tokenType.hasCapture) {
                String capture = null;
                // We loop over the captures and take the first valid one
                // because some of the regexs are difficult to condense into one capture group.
                for (int i = 0; i < m.groupCount() && capture == null; i++) {
                    capture = m.group(i + 1);
                }
                if (capture == null) {
                    throw new RuntimeException("No capture group found");
                }
                return new Token(tokenType, capture);
            }
            return new Token(tokenType, "");
        }
        throw new RuntimeException(
                String.format(
                        "Could not parse the next token (expected: %s): %s",
                        Arrays.toString(expectedTokenTypes), residual));
    }

    @NonNull
    public Token parseToken(@NonNull TokenType... expectedTokenTypes) {
        Token token = peekToken(expectedTokenTypes);
        Matcher m = token.getType().getPattern().matcher(residual);
        StringBuffer buffer = new StringBuffer();
        if (m.find()) {
            m.appendReplacement(buffer, "");
        }
        residual = m.appendTail(buffer).toString().trim();
        return token;
    }

    @NonNull
    public String getCommand() {
        return command;
    }

    @NonNull
    public String getResidual() {
        return residual;
    }

    public enum TokenType {
        BACKTICK("`"),
        SEMICOLON(";"),
        CONDITIONAL_AND("&&"),
        PIPE("\\|"),
        DOUBLE_CONDITIONAL_START("\\[\\[", "[\\s|$]"),
        DOUBLE_CONDITIONAL_END("\\]\\]"),

        IF("if"),
        FI("fi"),
        FOR("for"),
        IN("in"),
        DO("do"),
        DONE("done"),
        THEN("then"),

        WORD("\\p{Alpha}\\w*", "\\b"),
        VAR("\\p{Alpha}\\w*="),
        NUMBER("\\d+"),
        PUNCTUATION("\\p{Punct}+"),
        OPERATOR("\\p{Punct}+", "\\s"),
        FILE_PATH("[\\S&&[^=;]]+"),
        QUOTED_STRING(Pattern.compile("^'((?:\\.|[^'])*)'|^\"((?:\\.|[^\"])*)\""), true),
        WHITESPACE_SEMICOLON_DELIMITED("[\\S&&[^;]]+"),
        DOUBLE_CONDITIONAL_UNARY("-z|-n"),
        DOUBLE_CONDITIONAL_BINARY(
                "<|>|==|!=|<=|>=|-eq|-ne|-gt|-ge|-lt|-le"), // this only works for [[ ]], and no "="
        EOF(Pattern.compile("^$"), false);

        Pattern pattern;
        boolean hasCapture;

        TokenType(@NonNull String patternString) {
            this(patternString, "");
        }

        TokenType(@NonNull String patternString, @NonNull String nonCapturingSuffix) {
            pattern = Pattern.compile(String.format("^(%s)%s", patternString, nonCapturingSuffix));
            hasCapture = true;
        }

        TokenType(@NonNull Pattern pattern, boolean hasCapture) {
            this.pattern = pattern;
            this.hasCapture = hasCapture;
        }

        @NonNull
        public Pattern getPattern() {
            return pattern;
        }
    }

    public static class Token {
        private final String token;
        private final TokenType tokenType;

        public Token(@NonNull TokenType tokenType, @NonNull String token) {
            this.token = token;
            this.tokenType = tokenType;
        }

        @NonNull
        public String getText() {
            return token;
        }

        @NonNull
        public TokenType getType() {
            return tokenType;
        }
    }
}
