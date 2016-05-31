/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.chunkio.codegen;

import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

final class CodeSnippet {
    private final List<String> parts;
    private final List<Object> values;

    private CodeSnippet(Builder builder) {
        parts = Utils.immutableCopy(builder.parts);
        values = Utils.immutableCopy(builder.values);
    }

    public static Builder builder() {
        return new Builder();
    }

    List<String> getParts() {
        return parts;
    }

    List<Object> getValues() {
        return values;
    }

    public static final class Builder {
        private final List<String> parts = new ArrayList<>();
        private final List<Object> values = new ArrayList<>();

        private Builder() {
        }

        CodeSnippet build() {
            return new CodeSnippet(this);
        }

        public Builder add(CodeSnippet snippet) {
            parts.addAll(snippet.parts);
            values.addAll(snippet.values);
            return this;
        }

        public Builder add(String format, Object... args) {
            Iterator<Object> values = Arrays.asList(args).iterator();

            int index = 0;
            while (index < format.length()) {
                int prevIndex = index;
                if (format.charAt(index) == '$') {
                    if (index + 1 >= format.length()) {
                        throw new IllegalArgumentException("Malformed $ expression in format " +
                                "string: " + format);
                    }

                    switch (format.charAt(index + 1)) {
                        case 'L':
                            this.values.add(asLiteral(values.next()));
                            break;
                        case 'N':
                            this.values.add(asName(values.next()));
                            break;
                        case 'S':
                            this.values.add(asString(values.next()));
                            break;
                        case 'T':
                            this.values.add(asType(values.next()));
                            break;
                        case '$':
                        case '<':
                        case '>':
                        case '[':
                        case ']':
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape sequence $" +
                                    format.charAt(index + 1) + " in format string: " + format);
                    }

                    index += 2;
                    parts.add(format.substring(prevIndex, index));
                } else {
                    index = format.indexOf('$', index + 1);
                    if (index == -1) index = format.length();
                    parts.add(format.substring(prevIndex, index));
                }
            }

            return this;
        }

        Builder addStatement(String format, Object... args) {
            add("$[");
            add(format, args);
            add(";\n$]");
            return this;
        }

        Builder beginControlStatement(String format, Object... args) {
            add(format, args);
            add(" {\n");
            indent();
            return this;
        }

        Builder continueControlStatement(String format, Object... args) {
            unindent();
            add("} ");
            add(format, args);
            add(" {\n");
            indent();
            return this;
        }

        Builder endControlStatement() {
            return endBlock();
        }

        private Builder indent() {
            parts.add("$>");
            return this;
        }

        private Builder unindent() {
            parts.add("$<");
            return this;
        }

        Builder beginBlock() {
            add("{\n");
            indent();
            return this;
        }

        Builder endBlock() {
            unindent();
            add("}\n");
            return this;
        }

        private static Object asType(Object value) {
            if (value instanceof TypeDef) return value;
            if (value instanceof Type) return TypeDef.of((Type) value);
            if (value instanceof TypeMirror) return TypeDef.of((TypeMirror) value);
            throw new IllegalArgumentException("Invalid type: " + value.getClass());
        }

        private static Object asName(Object value) {
            if (value instanceof CharSequence) {
                return String.valueOf(value);
            }
            if (value instanceof MethodDef) {
                return ((MethodDef) value).getName();
            }
            throw new IllegalArgumentException("Name expected in format string, got " +
                    (value == null ? "null" : value.getClass()));
        }

        private static Object asLiteral(Object value) {
            return value;
        }

        private static Object asString(Object value) {
            return value == null ? null : value.toString();
        }
    }
}
