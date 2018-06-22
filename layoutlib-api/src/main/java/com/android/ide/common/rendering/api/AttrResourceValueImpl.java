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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A resource value representing an attr resource.
 */
public class AttrResourceValueImpl extends ResourceValueImpl implements AttrResourceValue {
    /** The keys are enum or flag names, the values are corresponding numeric values. */
    @Nullable private Map<String, Integer> mValueMap;
    /** The keys are enum or flag names, the values are the value descriptions. */
    @Nullable private Map<String, String> mValueDescriptionMap;
    @Nullable private String mDescription;
    @Nullable private String mGroupName;
    @NonNull private Set<AttributeFormat> mFormats = EnumSet.noneOf(AttributeFormat.class);

    public AttrResourceValueImpl(
            @NonNull ResourceReference reference, @Nullable String libraryName) {
        super(reference, null, libraryName);
    }

    public AttrResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String libraryName) {
        super(namespace, type, name, null, libraryName);
    }

    @Override
    @NonNull
    public Map<String, Integer> getAttributeValues() {
        return mValueMap;
    }

    @Override
    @Nullable
    public String getValueDescription(@NonNull String valueName) {
        return mValueDescriptionMap == null ? null : mValueDescriptionMap.get(valueName);
    }

    @Override
    @Nullable
    public String getDescription() {
        return mDescription;
    }

    @Override
    @Nullable
    public String getGroupName() {
        return mGroupName;
    }

    @Override
    @NonNull
    public Set<AttributeFormat> getFormats() {
        return mFormats;
    }

    /**
     * Adds a possible value of the flag or enum attribute.
     *
     * @param valueName the name of the value
     * @param numericValue the corresponding numeric value
     * @param valueName the description of the value
     */
    public void addValue(@NonNull String valueName, @Nullable Integer numericValue, @Nullable String description) {
        if (mValueMap == null) {
            mValueMap = new LinkedHashMap<>();
        }

        mValueMap.put(valueName, numericValue);

        if (description != null) {
            if (mValueDescriptionMap == null) {
                mValueDescriptionMap = new HashMap<>();
            }

            mValueDescriptionMap.put(valueName, description);
        }
    }

    /**
     * Sets the description of the attr resource.
     *
     * @param description the description to set
     */
    public void setDescription(@Nullable String description) {
        mDescription = description;
    }

    /**
     * Sets the name of group the attr resource belongs to.
     *
     * @param groupName the name of the group to set
     */
    public void setGroupName(@Nullable String groupName) {
        mGroupName = groupName;
    }

    /**
     * Sets the formats allowed for the attribute.
     *
     * @param formats the formats to set
     */
    public void setFormats(@NonNull Collection<AttributeFormat> formats) {
        this.mFormats = EnumSet.copyOf(formats);
    }
}
