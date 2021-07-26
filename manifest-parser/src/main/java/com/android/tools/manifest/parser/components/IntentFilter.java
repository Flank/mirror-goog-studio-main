/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.manifest.parser.components;

import java.util.HashSet;
import java.util.Set;

public class IntentFilter {

    private final Set<String> actions = new HashSet<>();

    private final Set<String> categories = new HashSet<>();

    IntentFilter() { }

    void addAction(String action) {
        actions.add(action);
    }

    void addCategory(String category) {
        categories.add(category);
    }

    public boolean hasAction(String action) {
        return actions.contains(action);
    }

    public boolean hasCategory(String category) {
        return categories.contains(category);
    }

    public Set<String> getActions() {
        return actions;
    }

    public Set<String> getCategories() {
        return categories;
    }
}
