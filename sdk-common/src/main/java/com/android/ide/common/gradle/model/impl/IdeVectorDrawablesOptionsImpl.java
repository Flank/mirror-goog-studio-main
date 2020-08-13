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
package com.android.ide.common.gradle.model.impl;

import com.android.annotations.Nullable;
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of a `VectorDrawablesOptions`. */
public final class IdeVectorDrawablesOptionsImpl
        implements IdeVectorDrawablesOptions, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @Nullable private final Boolean myUseSupportLibrary;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeVectorDrawablesOptionsImpl() {
        myUseSupportLibrary = null;

        myHashCode = 0;
    }

    public IdeVectorDrawablesOptionsImpl(@Nullable Boolean useSupportLibrary) {
        myUseSupportLibrary = useSupportLibrary;

        myHashCode = calculateHashCode();
    }

    @Override
    @Nullable
    public Boolean getUseSupportLibrary() {
        return myUseSupportLibrary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeVectorDrawablesOptionsImpl)) {
            return false;
        }
        IdeVectorDrawablesOptionsImpl options = (IdeVectorDrawablesOptionsImpl) o;
        return Objects.equals(myUseSupportLibrary, options.myUseSupportLibrary);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myUseSupportLibrary);
    }

    @Override
    public String toString() {
        return "IdeVectorDrawablesOptions{"
                + ", myUseSupportLibrary="
                + myUseSupportLibrary
                + "}";
    }
}
