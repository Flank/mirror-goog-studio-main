/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DSL object for configuring per-language splits options.
 *
 * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide/apk-splits">APK Splits</a>.
 */
public class LanguageSplitOptions {

    private boolean enable = false;
    private Set<String> include;

    /**
     * Collection of include patterns.
     */
    public Set<String> getInclude() {
        return include;
    }

    public void setInclude(@NonNull List<String> list) {
        include = Sets.newHashSet(list);
    }

    /**
     * Adds an include pattern.
     */
    public void include(@NonNull String... includes) {
        if (include == null) {
            include = Sets.newHashSet(includes);
            return;
        }

        include.addAll(Arrays.asList(includes));
    }

    @NonNull
    public Set<String> getApplicationFilters() {
        return include == null ? new HashSet<String>() : include;
    }
}
