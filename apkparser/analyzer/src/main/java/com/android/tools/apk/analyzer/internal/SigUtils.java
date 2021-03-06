/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.apk.analyzer.internal;

import com.android.annotations.NonNull;

public abstract class SigUtils {
    private static class SigReader {
        final String buffer;
        int pos = 0;

        SigReader(String s) {
            buffer = s;
        }

        int get() {
            return buffer.charAt(pos++);
        }

        int peek() {
            return buffer.charAt(pos);
        }

        boolean eof() {
            return buffer.length() <= pos;
        }

        String getSignature() {
            if (eof()) return "";

            switch (get()) {
                case 'Z':
                    return "boolean";
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'S':
                    return "short";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'F':
                    return "float";
                case 'D':
                    return "double";
                case 'V':
                    return "void";
                case 'L':
                    int start = pos;
                    pos = buffer.indexOf(';', start) + 1;
                    return buffer.substring(start, pos - 1).replace('/', '.');
                case '[':
                    return getSignature() + "[]";
                case '(':
                    StringBuilder result = new StringBuilder("(");
                    String separator = "";
                    while (peek() != ')') {
                        result.append(separator);
                        result.append(getSignature());
                        separator = ", ";
                    }
                    get();
                    result.append(")");
                    return getSignature()
                            + " "
                            + getClassName()
                            + "."
                            + getMethodName()
                            + " "
                            + result;
                default:
                    //          LOG.assertTrue(false, "unknown signature " + buffer);
                    return null;
            }
        }

        String getMethodName() {
            return "";
        }

        String getClassName() {
            return "";
        }
    }

    public static String getSimpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    public static String methodName(
            final String className, final String methodName, final String signature) {
        try {
            return new SigReader(signature) {
                @Override
                String getMethodName() {
                    return methodName;
                }

                @Override
                String getClassName() {
                    return className;
                }
            }.getSignature();
        } catch (Exception ignored) {
            return className + "." + methodName;
        }
    }

    public static String signatureToName(String s) {
        return new SigReader(s).getSignature();
    }

    /**
     * Takes a fully qualified class name (e.g."com.example.MyClass") and converts it into JVM-style
     * signature (e.g. "Lcom/example/MyClass;").
     *
     * @param fqcn dot-separated fully qualified class name or simple type
     * @return JVM type signature
     */
    @NonNull
    public static String typeToSignature(@NonNull String fqcn) {
        StringBuilder sb = new StringBuilder(fqcn.length() + 2);
        while (fqcn.endsWith("[]")) {
            fqcn = fqcn.substring(0, fqcn.length() - "[]".length());
            sb.append('[');
        }
        if ("boolean".equals(fqcn)) {
            sb.append('Z');
        } else if ("byte".equals(fqcn)) {
            sb.append('B');
        } else if ("char".equals(fqcn)) {
            sb.append('C');
        } else if ("short".equals(fqcn)) {
            sb.append('S');
        } else if ("int".equals(fqcn)) {
            sb.append('I');
        } else if ("long".equals(fqcn)) {
            sb.append('J');
        } else if ("float".equals(fqcn)) {
            sb.append('F');
        } else if ("double".equals(fqcn)) {
            sb.append('D');
        } else if ("void".equals(fqcn)) {
            sb.append('V');
        } else {
            sb.append('L');
            sb.append(fqcn.replace('.', '/'));
            sb.append(';');
        }

        return sb.toString();
    }
}
