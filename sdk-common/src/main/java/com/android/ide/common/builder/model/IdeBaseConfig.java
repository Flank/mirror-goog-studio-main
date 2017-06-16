/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.ide.common.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of a {@link BaseConfig}. */
public abstract class IdeBaseConfig extends IdeModel implements BaseConfig {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myName;
    @NonNull private final Map<String, ClassField> myResValues;
    @NonNull private final Map<String, String> myFlavorSelections;
    @NonNull private final Collection<File> myProguardFiles;
    @NonNull private final Collection<File> myConsumerProguardFiles;
    @NonNull private final Map<String, Object> myManifestPlaceholders;
    @Nullable private final String myApplicationIdSuffix;
    @Nullable private final String myVersionNameSuffix;
    @Nullable private final Boolean myMultiDexEnabled;
    private final int myHashCode;

    protected IdeBaseConfig(@NonNull BaseConfig config, @NonNull ModelCache modelCache) {
        super(config, modelCache);
        myName = config.getName();
        myResValues =
                copy(
                        config.getResValues(),
                        modelCache,
                        classField -> new IdeClassField(classField, modelCache));
        myProguardFiles = ImmutableList.copyOf(config.getProguardFiles());
        myConsumerProguardFiles = ImmutableList.copyOf(config.getConsumerProguardFiles());
        myManifestPlaceholders = ImmutableMap.copyOf(config.getManifestPlaceholders());
        myApplicationIdSuffix = config.getApplicationIdSuffix();
        myVersionNameSuffix = copyNewProperty(config::getVersionNameSuffix, null);
        myMultiDexEnabled = copyNewProperty(config::getMultiDexEnabled, null);
        myFlavorSelections = copyFlavorSelections(config);

        myHashCode = calculateHashCode();
    }

    @NonNull
    private static Map<String, String> copyFlavorSelections(@NonNull BaseConfig config) {
        try {
            return ImmutableMap.copyOf(config.getFlavorSelections());
        } catch (UnsupportedMethodException e) {
            // This method was introduced in 3.0.0 canary 4.
            // TODO check for plugin version instead of "try/catch" once 3.0.0 final is released.
            return ImmutableMap.of();
        }
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getBuildConfigFields() {
        throw new UnusedModelMethodException("getBuildConfigFields");
    }

    @Override
    @NonNull
    public Map<String, ClassField> getResValues() {
        return myResValues;
    }

    @Override
    @NonNull
    public Map<String, String> getFlavorSelections() {
        return myFlavorSelections;
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
    public Collection<File> getTestProguardFiles() {
        throw new UnusedModelMethodException("getTestProguardFiles");
    }

    @Override
    @NonNull
    public Map<String, Object> getManifestPlaceholders() {
        return myManifestPlaceholders;
    }

    @Override
    @NonNull
    public List<File> getJarJarRuleFiles() {
        throw new UnusedModelMethodException("getJarJarRuleFiles");
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
    @Nullable
    public File getMultiDexKeepFile() {
        throw new UnusedModelMethodException("getMultiDexKeepFile");
    }

    @Override
    @Nullable
    public File getMultiDexKeepProguard() {
        throw new UnusedModelMethodException("getMultiDexKeepProguard");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeBaseConfig)) {
            return false;
        }
        IdeBaseConfig config = (IdeBaseConfig) o;
        return config.canEqual(this)
                && Objects.equals(myName, config.myName)
                && Objects.deepEquals(myResValues, config.myResValues)
                && Objects.deepEquals(myFlavorSelections, config.myFlavorSelections)
                && Objects.deepEquals(myProguardFiles, config.myProguardFiles)
                && Objects.deepEquals(myConsumerProguardFiles, config.myConsumerProguardFiles)
                && Objects.deepEquals(myManifestPlaceholders, config.myManifestPlaceholders)
                && Objects.equals(myApplicationIdSuffix, config.myApplicationIdSuffix)
                && Objects.equals(myVersionNameSuffix, config.myVersionNameSuffix)
                && Objects.equals(myMultiDexEnabled, config.myMultiDexEnabled);
    }

    public boolean canEqual(Object other) {
        // See: http://www.artima.com/lejava/articles/equality.html
        return other instanceof IdeBaseConfig;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myName,
                myResValues,
                myFlavorSelections,
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
                + ", myFlavorSelections="
                + myFlavorSelections
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
