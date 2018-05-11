/*
 * Copyright (C) 2018 The Android Open Source Project
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
import org.jetbrains.annotations.Nullable;

/** This class will replace {@link ItemResourceValue} when the latter is removed. */
public class StyleItemResourceValueImpl extends ItemResourceValue {
    public StyleItemResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull String attributeName,
            @Nullable String value,
            @Nullable String libraryName) {
        super(namespace, attributeName, value, libraryName);
    }
}
