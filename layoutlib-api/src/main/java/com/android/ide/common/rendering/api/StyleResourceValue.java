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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Collection;

/**
 * Represents an android style resource with a name and a list of children {@link ResourceValue}.
 */
public final class StyleResourceValue extends ResourceValue {

    /**
     * Contents of the {@code parent} XML attribute. May be empty or null.
     *
     * @see #StyleResourceValue(ResourceReference, String, String)
     */
    @Nullable private String mParentStyle;

    /**
     * Items defined in this style, indexed by the namespace and name of the attribute they define.
     */
    private final Table<ResourceNamespace, String, ItemResourceValue> mItems =
            HashBasedTable.create();

    /**
     * Creates a new {@link StyleResourceValue}.
     *
     * <p>Note that names of styles have more meaning than other resources: if the parent attribute
     * is not set, aapt looks for a dot in the style name and treats the string up to the last dot
     * as the name of a parent style. So {@code <style name="Foo.Bar.Baz">} has an implicit parent
     * called {@code Foo.Bar}. Setting the {@code parent} XML attribute disables this feature, even
     * if it's set to an empty string. See {@code ResourceParser::ParseStyle} in aapt for details.
     */
    public StyleResourceValue(
            @NonNull ResourceReference reference,
            @Nullable String parentStyle,
            @Nullable String libraryName) {
        super(reference, null, libraryName);
        assert reference.getResourceType() == ResourceType.STYLE;
        mParentStyle = parentStyle;
    }

    /**
     * Returns value of the {@code parent} XML attribute of this style. Does not look at the name of
     * the style itself or dots in it.
     */
    @Nullable
    public String getParentStyleName() {
        return mParentStyle;
    }

    /**
     * Returns a reference to the parent style, if it can be determined based on the explicit parent
     * reference in XML or by splitting the name of this {@link StyleResourceValue} by dots.
     */
    @Nullable
    public ResourceReference getParentStyle() {
        if (mParentStyle != null) {
            ResourceUrl url = ResourceUrl.parseStyleParentReference(mParentStyle);
            if (url == null) {
                return null;
            }

            return url.resolve(getNamespace(), mNamespaceResolver);
        }

        int lastDot = getName().lastIndexOf('.');
        if (lastDot != -1) {
            String parent = getName().substring(0, lastDot);
            if (parent.isEmpty()) {
                return null;
            }

            return new ResourceReference(getNamespace(), ResourceType.STYLE, parent);
        }

        return null;
    }

    /**
     * Finds the item for the given qualified attr name in this style (if it's defined in this
     * style).
     */
    @Nullable
    public ItemResourceValue getItem(@NonNull ResourceNamespace namespace, @NonNull String name) {
        return mItems.get(namespace, name);
    }

    /** Finds the item for the given attr in this style (if it's defined in this style). */
    @Nullable
    public ItemResourceValue getItem(@NonNull ResourceReference attr) {
        assert attr.getResourceType() == ResourceType.ATTR;
        return mItems.get(attr.getNamespace(), attr.getName());
    }

    public void addItem(ItemResourceValue item) {
        ResourceReference attr = item.getAttr();
        if (attr == null) {
            return;
        }
        mItems.put(attr.getNamespace(), attr.getName(), item);
    }

    @Override
    public void replaceWith(ResourceValue style) {
        assert style instanceof StyleResourceValue
                : style.getClass() + " is not StyleResourceValue";
        super.replaceWith(style);

        //noinspection ConstantConditions
        if (style instanceof StyleResourceValue) {
            mItems.clear();
            mItems.putAll(((StyleResourceValue) style).mItems);
        }
    }

    /**
     * Returns a list of all items defined in this Style. This doesn't return items inherited from
     * the parent.
     */
    public Collection<ItemResourceValue> getDefinedItems() {
        return mItems.values();
    }
}
