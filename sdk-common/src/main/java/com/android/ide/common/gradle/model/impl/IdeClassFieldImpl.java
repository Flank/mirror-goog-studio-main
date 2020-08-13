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

import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.IdeClassField;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a `ClassField`. */
public final class IdeClassFieldImpl implements IdeClassField, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myName;
    @NonNull private final String myType;
    @NonNull private final String myValue;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @VisibleForTesting
    @SuppressWarnings("unused")
    public IdeClassFieldImpl() {
        myName = "";
        myType = "";
        myValue = "";

        myHashCode = 0;
    }

    public IdeClassFieldImpl(@NotNull String name, @NotNull String type, @NotNull String value) {
        myName = name;
        myType = type;
        myValue = value;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getType() {
        return myType;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getValue() {
        return myValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeClassFieldImpl)) {
            return false;
        }
        IdeClassFieldImpl field = (IdeClassFieldImpl) o;
        return Objects.equals(myName, field.myName)
                && Objects.equals(myType, field.myType)
                && Objects.equals(myValue, field.myValue);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myName, myType, myValue);
    }

    @Override
    public String toString() {
        return "IdeClassField{"
                + "myName='"
                + myName
                + '\''
                + ", myType='"
                + myType
                + '\''
                + ", myValue='"
                + myValue
                + '\''
                + '}';
    }
}
