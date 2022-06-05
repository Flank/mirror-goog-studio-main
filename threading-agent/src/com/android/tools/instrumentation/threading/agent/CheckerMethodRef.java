/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.instrumentation.threading.agent;

import com.android.annotations.NonNull;
import java.util.Objects;

/**
 * Defines a static method that should be called before a threading-annotated method call
 * (e.g. @UiThread)
 */
final class CheckerMethodRef {
    @NonNull private final String className;

    @NonNull private final String methodName;

    public CheckerMethodRef(@NonNull String className, @NonNull String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    @NonNull
    public String getClassName() {
        return className;
    }

    @NonNull
    public String getMethodName() {
        return methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckerMethodRef that = (CheckerMethodRef) o;
        return Objects.equals(className, that.className)
                && Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName);
    }
}
