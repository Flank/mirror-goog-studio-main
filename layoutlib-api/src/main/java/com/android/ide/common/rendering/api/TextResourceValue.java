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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.utils.HashCodes;
import java.util.Objects;

/** A {@link ResourceValue} intended for text nodes where we need access to the raw XML text. */
public abstract class TextResourceValue extends ResourceValueImpl {
    private String mRawXmlValue;

    public TextResourceValue(
            @NonNull ResourceReference reference,
            @Nullable String textValue,
            @Nullable String rawXmlValue,
            @Nullable String libraryName) {
        super(reference, textValue, libraryName);
        mRawXmlValue = rawXmlValue;
    }

    public TextResourceValue(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String textValue,
            @Nullable String rawXmlValue,
            @Nullable String libraryName) {
        super(namespace, type, name, textValue, libraryName);
        mRawXmlValue = rawXmlValue;
    }

    @Override
    public String getRawXmlValue() {
        if (mRawXmlValue != null) {
            return mRawXmlValue;
        }
        return super.getValue();
    }

    public void setRawXmlValue(String value) {
        mRawXmlValue = value;
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(super.hashCode(), Objects.hashCode(mRawXmlValue));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        TextResourceValue other = (TextResourceValue) obj;
        return Objects.equals(mRawXmlValue, other.mRawXmlValue);
    }
}
