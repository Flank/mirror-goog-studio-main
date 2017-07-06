/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an android style resource with a name and a list of children {@link ResourceValue}.
 */
public final class StyleResourceValue extends ResourceValue {

    private String mParentStyle = null;
    private final Map<String, ItemResourceValue> mItems = new HashMap<>();

    public StyleResourceValue(ResourceUrl url, String parentStyle, String libraryName) {
        super(url, null, libraryName);
        assert url.type == ResourceType.STYLE;
        mParentStyle = parentStyle;
    }

    /**
     * Returns the parent style name or <code>null</code> if unknown.
     */
    public String getParentStyle() {
        return mParentStyle;
    }

    /**
     * Finds a value in the list by name
     * @param name the name of the resource
     *
     * @deprecated use {@link #getItem(String, boolean)}
     */
    @Deprecated
    public ResourceValue findValue(String name) {
        return getItem(name, isFramework());
    }

    /**
     * Finds a value in the list by name
     * @param name the name of the resource
     *
     * @deprecated use {@link #getItem(String, boolean)}
     */
    @Deprecated
    public ResourceValue findValue(String name, boolean isFrameworkAttr) {
        return getItem(name, isFrameworkAttr);
    }

    @NonNull
    private static String getItemKey(@NonNull String name, boolean isFrameworkAttr) {
        if (isFrameworkAttr) {
            return SdkConstants.PREFIX_ANDROID + name;
        }

        return name;
    }

    /**
     * Finds a value in the list of items by name.
     *
     * @param name the name of the resource
     * @param isFrameworkAttr is it in the framework namespace
     */
    public ItemResourceValue getItem(@NonNull String name, boolean isFrameworkAttr) {
        return mItems.get(getItemKey(name, isFrameworkAttr));
    }

    public void addItem(ItemResourceValue value) {
        mItems.put(getItemKey(value.getName(), value.isFrameworkAttr()), value);
    }

    @Override
    public void replaceWith(ResourceValue value) {
        assert value instanceof StyleResourceValue :
                value.getClass() + " is not StyleResourceValue";
        super.replaceWith(value);

        //noinspection ConstantConditions
        if (value instanceof StyleResourceValue) {
            mItems.clear();
            mItems.putAll(((StyleResourceValue) value).mItems);
        }
    }

    /** Returns the names available in this style, intended for diagnostic purposes */
    public List<String> getNames() {
        return new ArrayList<>(mItems.keySet());
    }

    /**
     * Returns a list of all values defined in this Style. This doesn't return the values
     * inherited from the parent.
     */
    public Collection<ItemResourceValue> getValues() {
        return mItems.values();
    }
}
