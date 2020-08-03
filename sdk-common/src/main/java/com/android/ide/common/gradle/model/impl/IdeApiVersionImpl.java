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
import com.android.annotations.Nullable;
import com.android.builder.model.ApiVersion;
import com.android.ide.common.gradle.model.IdeApiVersion;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of an {@link IdeApiVersion}. */
public final class IdeApiVersionImpl implements IdeApiVersion, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myApiString;
    @Nullable private final String myCodename;
    private final int myApiLevel;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeApiVersionImpl() {
        myApiString = "";
        myCodename = null;
        myApiLevel = 0;

        myHashCode = 0;
    }

    public IdeApiVersionImpl(@NotNull String apiString, @Nullable String codename, int apiLevel) {
        myApiString = apiString;
        myCodename = codename;
        myApiLevel = apiLevel;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getApiString() {
        return myApiString;
    }

    @Override
    @Nullable
    public String getCodename() {
        return myCodename;
    }

    @Override
    public int getApiLevel() {
        return myApiLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeApiVersionImpl)) {
            return false;
        }
        IdeApiVersionImpl version = (IdeApiVersionImpl) o;
        return myApiLevel == version.myApiLevel
                && Objects.equals(myApiString, version.myApiString)
                && Objects.equals(myCodename, version.myCodename);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myApiString, myCodename, myApiLevel);
    }

    @Override
    public String toString() {
        return "IdeApiVersion{"
                + "myApiString='"
                + myApiString
                + '\''
                + ", myCodename='"
                + myCodename
                + '\''
                + ", myApiLevel="
                + myApiLevel
                + '}';
    }

    public static IdeApiVersionImpl createFrom(@NonNull ApiVersion version) {
        return new IdeApiVersionImpl(
                version.getApiString(), version.getCodename(), version.getApiLevel());
    }
}
