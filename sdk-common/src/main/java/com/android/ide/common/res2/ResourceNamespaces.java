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
package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;

/** Utilities for dealing with resource namespaces. */
public class ResourceNamespaces {

    /**
     * Turns both ways of referring to the default namespace (null and empty string) into the same:
     * empty string.
     *
     * <p>This is useful, because most collections don't deal well with null keys/values.
     */
    @NonNull
    public static String normalizeNamespace(@Nullable String namespace) {
        return Strings.nullToEmpty(namespace);
    }

    public static boolean isDefaultNamespace(@Nullable String namespace) {
        return normalizeNamespace(namespace).isEmpty();
    }

    public static boolean isSameNamespace(@Nullable String a, @Nullable String b) {
        return normalizeNamespace(a).equals(normalizeNamespace(b));
    }
}
