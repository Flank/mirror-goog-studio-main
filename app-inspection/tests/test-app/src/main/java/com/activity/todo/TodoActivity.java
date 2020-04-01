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
import androidx.annotation.Nullable;
import com.activity.TransportTestActivity;
import java.util.ArrayList;
import java.util.List;

/** A fake activity that tracks multiple to-do lists. */
@SuppressWarnings("unused") // Accessed via reflection by perf-test
public final class TodoActivity extends TransportTestActivity {
    private final List<TodoGroup> groups = new ArrayList<TodoGroup>();
    @Nullable private TodoGroup activeGroup = null;

    public TodoActivity() {
        super("TodoActivity");
    }

    @NonNull
    public TodoGroup newGroup() {
        TodoGroup group = new TodoGroup("Group #" + groups.size() + 1);
        groups.add(group);
        activeGroup = group;
        return group;
    }

    @NonNull
    public TodoItem newItem() {
        if (activeGroup == null) {
            newGroup();
        }
        TodoItem item = new TodoItem("Item #" + activeGroup.getItems().size() + 1);
        activeGroup.addItem(item);
        return item;
    }

    public void removeOldestGroup() {
        removeGroup(0);
    }

    public void removeNewestGroup() {
        removeGroup(groups.size() - 1);
    }

    public void removeGroup(int index) {
        if (index < 0 || index >= groups.size()) {
            throw new IllegalArgumentException("Invalid group index to remove");
        }

        TodoGroup removed = groups.remove(index);
        if (activeGroup == removed) {
            activeGroup = null;
        }
    }

    public void clearAllItems() {
        groups.clear();
        activeGroup = null;
    }

    public int getItemsCount() {
        int sum = 0;
        for (TodoGroup group : groups) {
            sum += group.getItems().size();
        }
        return sum;
    }
}
