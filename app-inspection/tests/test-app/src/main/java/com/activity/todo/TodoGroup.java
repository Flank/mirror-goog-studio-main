/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.activity.todo;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

public final class TodoGroup {
    @NonNull private String description;
    private final List<TodoItem> items = new ArrayList<TodoItem>();

    public TodoGroup(@NonNull String description) {
        this.description = description;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@NonNull String description) {
        this.description = description;
    }

    public void addItem(@NonNull TodoItem item) {
        items.add(item);
    }

    @NonNull
    public List<TodoItem> getItems() {
        return new ArrayList<TodoItem>(items);
    }

    public void clearCompleted() {
        for (int i = 0; i < items.size(); i++) {
            TodoItem item = items.get(i);
            if (item.isCompleted()) {
                items.remove(i);
                --i;
            }
        }
    }
}
