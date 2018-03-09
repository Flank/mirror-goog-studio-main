/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import com.google.common.annotations.Beta;

/** Temporarily here for compat purposes */
@SuppressWarnings({"deprecation", "unused"})
@Beta
@Deprecated
public abstract class JavaParser {
    @Deprecated public static final String TYPE_OBJECT = JavaEvaluatorKt.TYPE_OBJECT;
    @Deprecated public static final String TYPE_STRING = JavaEvaluatorKt.TYPE_STRING;
    @Deprecated public static final String TYPE_INT = JavaEvaluatorKt.TYPE_INT;
    @Deprecated public static final String TYPE_LONG = JavaEvaluatorKt.TYPE_LONG;
    @Deprecated public static final String TYPE_CHAR = JavaEvaluatorKt.TYPE_CHAR;
    @Deprecated public static final String TYPE_FLOAT = JavaEvaluatorKt.TYPE_FLOAT;
    @Deprecated public static final String TYPE_DOUBLE = JavaEvaluatorKt.TYPE_DOUBLE;
    @Deprecated public static final String TYPE_BOOLEAN = JavaEvaluatorKt.TYPE_BOOLEAN;
    @Deprecated public static final String TYPE_SHORT = JavaEvaluatorKt.TYPE_SHORT;
    @Deprecated public static final String TYPE_BYTE = JavaEvaluatorKt.TYPE_BYTE;
    @Deprecated public static final String TYPE_NULL = JavaEvaluatorKt.TYPE_NULL;

    @Deprecated
    public static final String TYPE_INTEGER_WRAPPER = JavaEvaluatorKt.TYPE_INTEGER_WRAPPER;

    @Deprecated
    public static final String TYPE_BOOLEAN_WRAPPER = JavaEvaluatorKt.TYPE_BOOLEAN_WRAPPER;

    @Deprecated public static final String TYPE_BYTE_WRAPPER = JavaEvaluatorKt.TYPE_BYTE_WRAPPER;
    @Deprecated public static final String TYPE_SHORT_WRAPPER = JavaEvaluatorKt.TYPE_SHORT_WRAPPER;
    @Deprecated public static final String TYPE_LONG_WRAPPER = JavaEvaluatorKt.TYPE_LONG_WRAPPER;

    @Deprecated
    public static final String TYPE_DOUBLE_WRAPPER = JavaEvaluatorKt.TYPE_DOUBLE_WRAPPER;

    @Deprecated public static final String TYPE_FLOAT_WRAPPER = JavaEvaluatorKt.TYPE_FLOAT_WRAPPER;

    @Deprecated
    public static final String TYPE_CHARACTER_WRAPPER = JavaEvaluatorKt.TYPE_CHARACTER_WRAPPER;
}
