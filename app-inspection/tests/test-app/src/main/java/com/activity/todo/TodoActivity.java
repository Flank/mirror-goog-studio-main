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

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.activity.TransportTestActivity;
import java.util.ArrayList;
import java.util.List;

/** A fake activity that tracks multiple to-do lists. */
@SuppressWarnings("unused") // Accessed via reflection by perf-test
public final class TodoActivity extends TransportTestActivity {
    public final class InnerItem {
        @NonNull
        public TodoItem newItem() {
            if (activeGroup == null) {
                newGroup();
            }
            TodoItem item = new TodoItem("Item #" + (activeGroup.getItems().size() + 1));
            activeGroup.addItem(item);
            return item;
        }
    }

    private final List<TodoGroup> groups = new ArrayList<TodoGroup>();
    @Nullable private TodoGroup activeGroup = null;

    public TodoActivity() {
        super("TodoActivity");
    }

    @NonNull
    public TodoGroup newGroup() {
        return newGroupInternal("Group #" + (groups.size() + 1));
    }

    @NonNull
    public TodoGroup newHighPriorityGroup() {
        return newGroup("High Priority Group");
    }

    @NonNull
    public TodoGroup newGroup(@NonNull String name) {
        return newGroupInternal(name);
    }

    // this method is a bit artificial, because we could have inlined it
    // but then we add hooks to methods that call each other
    // and it makes harder to read and follow tests.
    private TodoGroup newGroupInternal(@NonNull String name) {
        TodoGroup group = new TodoGroup(name);
        groups.add(group);
        activeGroup = group;
        return group;
    }

    @NonNull
    public TodoItem newItem() {
        if (activeGroup == null) {
            newGroup();
        }
        TodoItem item = new TodoItem("Item #" + (activeGroup.getItems().size() + 1));
        activeGroup.addItem(item);
        return item;
    }

    // tests overloads
    @NonNull
    public TodoItem newItem(String name) {
        if (activeGroup == null) {
            newGroup();
        }
        TodoItem item = new TodoItem(name);
        activeGroup.addItem(item);
        return item;
    }

    /** Returns same result with [newItem()] but delegates [InnerItem.newItem(String)]. */
    @NonNull
    public TodoItem newInnerItem() {
        return new InnerItem().newItem();
    }

    public void newCustomNamedItem() {
        newItem("Custom named todo");
    }

    @NonNull
    public TodoItem newRedItem() {
        if (activeGroup == null) {
            newGroup();
        }
        TodoItem item =
                new ColoredTodoItem("Item #" + activeGroup.getItems().size() + 1, 0xffff0000);
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

    // we have this special internal method instead of simply
    // calling getItemsCount from getLongItemsCount to avoid
    // sending two events once we call getLongItemsCount.
    private int countItemsInternal() {
        int sum = 0;
        for (TodoGroup group : groups) {
            sum += group.getItems().size();
        }
        return sum;
    }

    public int getItemsCount() {
        return countItemsInternal();
    }

    public byte getByteItemsCount() {
        return (byte) countItemsInternal();
    }

    public short getShortItemsCount() {
        return (short) countItemsInternal();
    }

    public long getLongItemsCount() {
      return countItemsInternal();
    }

    public char getActiveGroupTrailingChar() {
        if (activeGroup == null || activeGroup.getDescription().length() == 0) return '\0';
        return activeGroup.getDescription().charAt(activeGroup.getDescription().length() - 1);
    }

    public boolean hasEmptyTodoList() {
        return groups.isEmpty();
    }

    // we have this special internal method instead of simply calling getAverageItemCount from
    // getDoubleAverageItemCount
    // to avoid sending two events once we call getLongItemsCount.
    private float getAverageItemCountInternal() {
        if (groups.isEmpty()) return 0f;

        int totalItemCount = 0;
        for (TodoGroup group : groups) {
            totalItemCount += group.getItems().size();
        }
        return (float) totalItemCount / (float) groups.size();
    }

    public float getAverageItemCount() {
        return getAverageItemCountInternal();
    }

    public double getDoubleAverageItemCount() {
        return getAverageItemCountInternal();
    }

    // this function allocates more than 16 variables
    // it checks that we don't fail once high registers
    // are in the picture.
    // returns number of prefilled items;
    public int prefillItems() {
        TodoItem item1 = new TodoItem("1");
        TodoItem item2 = new TodoItem("2");
        TodoItem item3 = new TodoItem("3");
        TodoItem item4 = new TodoItem("4");
        TodoItem item5 = new TodoItem("5");
        TodoItem item6 = new TodoItem("6");
        TodoItem item7 = new TodoItem("7");

        TodoItem item8 = new TodoItem("8");
        TodoItem item9 = new TodoItem("9");
        TodoItem item10 = new TodoItem("10");
        TodoItem item11 = new TodoItem("11");
        TodoItem item12 = new TodoItem("12");

        TodoGroup group1 = new TodoGroup("group #1");
        TodoGroup group2 = new TodoGroup("group #2");
        TodoGroup group3 = new TodoGroup("group #3");
        TodoGroup group4 = new TodoGroup("group #4");
        group1.addItem(item1);
        group1.addItem(item2);
        group1.addItem(item3);
        group2.addItem(item4);
        group2.addItem(item5);
        group2.addItem(item6);
        group2.addItem(item7);
        group2.addItem(item8);
        group2.addItem(item9);
        group2.addItem(item10);
        group3.addItem(item11);
        group4.addItem(item12);
        groups.add(group1);
        groups.add(group2);
        groups.add(group3);
        groups.add(group4);
        activeGroup = group4;
        return 12;
    }

    // A function that needs at max one register,
    // except params (and that one can be optimized away)
    // it is important to keep it this way, because it tests
    // special code path in slicer/instrumentation.cc
    // that allocates additional registers to add entry hook
    public static void logItem(int severity, String tag, TodoItem item) {
        Log.printlns(severity, tag, item.getDescription());
    }

    public void logFirstItem() {
        if (groups.isEmpty() || groups.get(0).getItems().isEmpty()) {
            Log.printlns(Log.VERBOSE, "todo_activity", "first item doesn't exist");
        } else {
            logItem(Log.VERBOSE, "todo_activity", groups.get(0).getItems().get(0));
        }
    }

    public void selectFirstGroup() {
        activeGroup = groups.get(0);
    }

    public void selectLastGroup() {
        int size = groups.size();
        if (size != 0) {
            activeGroup = groups.get(size - 1);
        }
    }

    // function that needs exactly 2 registries
    // it forces exitHooks to allocate additional registry
    // for storing signature
    public static long echo(long input) {
        return input;
    }

    public static void callEcho() {
        echo(5L);
    }
}
