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
import com.android.ide.common.gradle.model.IdeSigningConfig;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a `SigningConfig`. */
public final class IdeSigningConfigImpl implements IdeSigningConfig, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myName;
    @Nullable private final File myStoreFile;
    @Nullable private final String myStorePassword;
    @Nullable private final String myKeyAlias;
    @Nullable private final Boolean myV1SigningEnabled;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeSigningConfigImpl() {
        myName = "";
        myStoreFile = null;
        myStorePassword = null;
        myKeyAlias = null;
        myV1SigningEnabled = null;

        myHashCode = 0;
    }

    public IdeSigningConfigImpl(
            @NotNull String name,
            @Nullable File storeFile,
            @Nullable String storePassword,
            @Nullable String keyAlias,
            @Nullable Boolean v1SigningEnabled) {
        myName = name;
        myStoreFile = storeFile;
        myStorePassword = storePassword;
        myKeyAlias = keyAlias;
        myV1SigningEnabled = v1SigningEnabled;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @Nullable
    public File getStoreFile() {
        return myStoreFile;
    }

    @Override
    @Nullable
    public String getStorePassword() {
        return myStorePassword;
    }

    @Override
    @Nullable
    public String getKeyAlias() {
        return myKeyAlias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeSigningConfigImpl)) {
            return false;
        }
        IdeSigningConfigImpl config = (IdeSigningConfigImpl) o;
        return Objects.equals(myV1SigningEnabled, config.myV1SigningEnabled)
                && Objects.equals(myName, config.myName)
                && Objects.equals(myStoreFile, config.myStoreFile)
                && Objects.equals(myStorePassword, config.myStorePassword)
                && Objects.equals(myKeyAlias, config.myKeyAlias);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myName, myStoreFile, myStorePassword, myKeyAlias, myV1SigningEnabled);
    }

    @Override
    public String toString() {
        return "IdeSigningConfig{"
                + "myName='"
                + myName
                + '\''
                + ", myStoreFile="
                + myStoreFile
                + ", myStorePassword='"
                + myStorePassword
                + '\''
                + ", myKeyAlias='"
                + myKeyAlias
                + '\''
                + ", myV1SigningEnabled="
                + myV1SigningEnabled
                + '}';
    }
}
