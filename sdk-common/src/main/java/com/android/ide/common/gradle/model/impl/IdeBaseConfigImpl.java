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
import com.android.ide.common.gradle.model.IdeBaseConfig;
import com.android.ide.common.gradle.model.IdeClassField;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public abstract class IdeBaseConfigImpl implements IdeBaseConfig, Serializable {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myName;
    @NonNull private final Map<String, IdeClassField> myResValues;
    @NonNull private final Collection<File> myProguardFiles;
    @NonNull private final Collection<File> myConsumerProguardFiles;
    @NonNull private final Map<String, String> myManifestPlaceholders;
    @Nullable private final String myApplicationIdSuffix;
    @Nullable private final String myVersionNameSuffix;
    @Nullable private final Boolean myMultiDexEnabled;
    private final int hashCode;

    // Used for serialization by the IDE.
    IdeBaseConfigImpl() {
        myName = "";
        myResValues = Collections.emptyMap();
        myProguardFiles = Collections.emptyList();
        myConsumerProguardFiles = Collections.emptyList();
        myManifestPlaceholders = Collections.emptyMap();
        myApplicationIdSuffix = null;
        myVersionNameSuffix = null;
        myMultiDexEnabled = null;

        hashCode = 0;
    }

    protected IdeBaseConfigImpl(
            @NotNull String name,
            @NotNull Map<String, IdeClassField> resValues,
            @NotNull List<File> proguardFiles,
            @NotNull List<File> consumerProguardFiles,
            @NotNull Map<String, String> manifestPlaceholders,
            @Nullable String applicationIdSuffix,
            @Nullable String versionNameSuffix,
            @Nullable Boolean multiDexEnabled) {
        myName = name;
        myResValues = resValues;
        myProguardFiles = proguardFiles;
        myConsumerProguardFiles = consumerProguardFiles;
        myManifestPlaceholders = manifestPlaceholders;
        myApplicationIdSuffix = applicationIdSuffix;
        myVersionNameSuffix = versionNameSuffix;
        myMultiDexEnabled = multiDexEnabled;

        hashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Map<String, IdeClassField> getResValues() {
        return myResValues;
    }

    @Override
    @NonNull
    public Collection<File> getProguardFiles() {
        return myProguardFiles;
    }

    @Override
    @NonNull
    public Collection<File> getConsumerProguardFiles() {
        return myConsumerProguardFiles;
    }

    @Override
    @NonNull
    public Map<String, String> getManifestPlaceholders() {
        return myManifestPlaceholders;
    }

    @Override
    @Nullable
    public String getApplicationIdSuffix() {
        return myApplicationIdSuffix;
    }

    @Override
    @Nullable
    public String getVersionNameSuffix() {
        return myVersionNameSuffix;
    }

    @Override
    @Nullable
    public Boolean getMultiDexEnabled() {
        return myMultiDexEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeBaseConfigImpl)) {
            return false;
        }
        IdeBaseConfigImpl config = (IdeBaseConfigImpl) o;
        return config.canEqual(this)
                && Objects.equals(myName, config.myName)
                && Objects.deepEquals(myResValues, config.myResValues)
                && Objects.deepEquals(myProguardFiles, config.myProguardFiles)
                && Objects.deepEquals(myConsumerProguardFiles, config.myConsumerProguardFiles)
                && Objects.deepEquals(myManifestPlaceholders, config.myManifestPlaceholders)
                && Objects.equals(myApplicationIdSuffix, config.myApplicationIdSuffix)
                && Objects.equals(myVersionNameSuffix, config.myVersionNameSuffix)
                && Objects.equals(myMultiDexEnabled, config.myMultiDexEnabled);
    }

    public boolean canEqual(Object other) {
        // See: http://www.artima.com/lejava/articles/equality.html
        return other instanceof IdeBaseConfigImpl;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myName,
                myResValues,
                myProguardFiles,
                myConsumerProguardFiles,
                myManifestPlaceholders,
                myApplicationIdSuffix,
                myVersionNameSuffix,
                myMultiDexEnabled);
    }

    @Override
    public String toString() {
        return "myName='"
                + myName
                + '\''
                + ", myResValues="
                + myResValues
                + ", myProguardFiles="
                + myProguardFiles
                + ", myConsumerProguardFiles="
                + myConsumerProguardFiles
                + ", myManifestPlaceholders="
                + myManifestPlaceholders
                + ", myApplicationIdSuffix='"
                + myApplicationIdSuffix
                + '\''
                + ", myVersionNameSuffix='"
                + myVersionNameSuffix
                + '\''
                + ", myMultiDexEnabled="
                + myMultiDexEnabled;
    }
}
