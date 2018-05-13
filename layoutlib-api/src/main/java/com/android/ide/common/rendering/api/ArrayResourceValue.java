/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents an android array resource with a name and a list of children {@link ResourceValue}
 * items, one for array element.
 */
public class ArrayResourceValue extends ResourceValueImpl implements Iterable<String> {
    private final List<String> mItems = new ArrayList<>();

    public ArrayResourceValue(@NonNull ResourceReference reference, @Nullable String libraryName) {
        super(reference, null, libraryName);
        assert reference.getResourceType() == ResourceType.ARRAY;
    }

    public ArrayResourceValue(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String libraryName) {
        super(namespace, type, name, null, libraryName);
        assert type == ResourceType.ARRAY;
    }

    /**
     * Adds an element into the array
     */
    public void addElement(String value) {
        mItems.add(value);
    }

    /**
     * Returns the number of elements in this array
     *
     * @return the element count
     */
    public int getElementCount() {
        return mItems.size();
    }

    /**
     * Returns the array element value at the given index position.
     *
     * @param index index, which must be in the range [0..getElementCount()].
     * @return the corresponding element
     */
    public String getElement(int index) {
        return mItems.get(index);
    }

    /**
     * Returns an iterator over the resource values
     */
    @Override
    public Iterator<String> iterator() {
        return mItems.iterator();
    }

    /**
     * Returns the index of the element to pick by default if a client
     * of layoutlib asks for the {@link #getValue()} rather than the more
     * specific {@linkplain ArrayResourceValue} iteration methods
     */
    protected int getDefaultIndex() {
        return 0;
    }

    @Override
    public String getValue() {
        // Clients should normally not call this method on ArrayResourceValues; they should
        // pick the specific array element they want. However, for compatibility with older
        // layout libs, return the first array element's value instead.

        //noinspection VariableNotUsedInsideIf
        if (super.getValue() == null) {
            if (!mItems.isEmpty()) {
                return mItems.get(getDefaultIndex());
            }
        }

        return super.getValue();
    }
}
