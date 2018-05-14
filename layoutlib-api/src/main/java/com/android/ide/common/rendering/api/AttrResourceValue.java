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

import com.android.annotations.Nullable;
import java.util.Map;

/**
 * A resource value representing an attr resource.
 *
 * <p>{@link #getValue()} will return null, instead use {@link #getAttributeValues()} to get the
 * enum/flag value associated with an attribute defined in the declare-styleable.
 */
public interface AttrResourceValue extends ResourceValue {
    /**
     * Returns the enum/flag integer values.
     *
     * @return the map of (name, integer) values
     */
    @Nullable
    Map<String, Integer> getAttributeValues();
}
