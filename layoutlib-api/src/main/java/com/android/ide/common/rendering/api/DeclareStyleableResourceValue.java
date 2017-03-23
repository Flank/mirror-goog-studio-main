/*
 * Copyright (C) 2011 The Android Open Source Project
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
import java.util.ArrayList;
import java.util.List;

/**
 * A Resource value representing a declare-styleable resource.
 *
 * {@link #getValue()} will return null, instead use {@link #getAllAttributes()} to
 * get the list of attributes defined in the declare-styleable.
 */
public class DeclareStyleableResourceValue extends ResourceValue {

    @NonNull
    private List<AttrResourceValue> mAttrs = new ArrayList<>();

    public DeclareStyleableResourceValue(@NonNull ResourceUrl url, @Nullable String value) {
        this(url, value, null);
    }

    public DeclareStyleableResourceValue(
            @NonNull ResourceUrl url, @Nullable String value, @Nullable String libraryName) {
        super(url, value, libraryName);
        assert url.type == ResourceType.DECLARE_STYLEABLE;
    }

    @NonNull
    public List<AttrResourceValue> getAllAttributes() {
        return mAttrs;
    }

    public void addValue(@NonNull AttrResourceValue attr) {
        assert attr.isFramework() || !isFramework()
                : "Can't add non-framework attributes to framework resource.";
        mAttrs.add(attr);
    }
}
