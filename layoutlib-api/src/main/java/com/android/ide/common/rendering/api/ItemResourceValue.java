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
package com.android.ide.common.rendering.api;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;

/**
 * Represents each item in the android style resource.
 */
public class ItemResourceValue extends ResourceValue {

    private final boolean mIsFrameworkAttr;
    /**
     * If the value is a reference to a framework resource or not is NOT represented with a boolean!
     * but can be deduced with:
     *
     * <pre>{@code
     * boolean isFrameworkValue = item.isFramework() ||
     *     item.getValue().startsWith(SdkConstants.ANDROID_PREFIX) ||
     *     item.getValue().startsWith(SdkConstants.ANDROID_THEME_PREFIX);
     * }</pre>
     *
     * For {@code <item name="foo">bar</item>}, item in a style resource, the values of the
     * parameters will be as follows:
     *
     * @param attributeName foo
     * @param isFrameworkAttr is foo in framework namespace.
     * @param value bar (in case of a reference, the value may include the namespace. if the
     *     namespace is absent, default namespace is assumed based on isFrameworkStyle (android
     *     namespace when isFrameworkStyle=true and app namespace when isFrameworkStyle=false))
     * @param isFrameworkStyle if the style is a framework file or project file.
     */
    public ItemResourceValue(
            String attributeName,
            boolean isFrameworkAttr,
            String value,
            boolean isFrameworkStyle,
            String libraryName) {
        // Style items don't have a name of their own, but reference things that do. We abuse the
        // abstraction a little to store all the necessary pieces of information. This will have to
        // be reworked, as currently the namespace doesn't match isFramework(). It seems that this
        // should not be represented as a ResourceValue at all.
        super(
                ResourceUrl.create(ResourceType.STYLE_ITEM, attributeName, isFrameworkStyle),
                value,
                libraryName);
        mIsFrameworkAttr = isFrameworkAttr;
    }

    public boolean isFrameworkAttr() {
        return mIsFrameworkAttr;
    }

    @Override
    public String toString() {
        return super.toString() + " (mIsFrameworkAttr=" + mIsFrameworkAttr + ")";
    }
}
