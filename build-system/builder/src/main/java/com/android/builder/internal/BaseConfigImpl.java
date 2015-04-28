/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * An object that contain a BuildConfig configuration
 */
public abstract class BaseConfigImpl implements Serializable, BaseConfig {
    private static final long serialVersionUID = 1L;

    private final Map<String, ClassField> mBuildConfigFields = Maps.newTreeMap();
    private final Map<String, ClassField> mResValues = Maps.newTreeMap();
    private final List<File> mProguardFiles = Lists.newArrayList();
    private final List<File> mConsumerProguardFiles = Lists.newArrayList();
    private final List<File> mTestProguardFiles = Lists.newArrayList();
    private final Map<String, Object> mManifestPlaceholders = Maps.newHashMap();
    @Nullable
    private Boolean mMultiDexEnabled;

    @Nullable
    private File mMultiDexKeepProguard;

    @Nullable
    private File mMultiDexKeepFile;

    /**
     * Adds a BuildConfig field.
     */
    public void addBuildConfigField(@NonNull ClassField field) {
        mBuildConfigFields.put(field.getName(), field);
    }

    /**
     * Adds a generated resource value.
     */
    public void addResValue(@NonNull ClassField field) { mResValues.put(field.getName(), field);
    }

    /**
     * Adds a generated resource value.
     */
    public void addResValues(@NonNull Map<String, ClassField> values) {
        mResValues.putAll(values);
    }

    /**
     * Returns the BuildConfig fields.
     */
    @Override
    @NonNull
    public Map<String, ClassField> getBuildConfigFields() {
        return mBuildConfigFields;
    }

    /**
     * Adds BuildConfig fields.
     */
    public void addBuildConfigFields(@NonNull Map<String, ClassField> fields) {
        mBuildConfigFields.putAll(fields);
    }

    /**
     * Returns the generated resource values.
     */
    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return mResValues;
    }

    /**
     * Returns ProGuard configuration files to be used.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     *
     * <p>See similarly named methods to specify the files.
     */
    @Override
    @NonNull
    public List<File> getProguardFiles() {
        return mProguardFiles;
    }

    /**
     * ProGuard rule files to be included in the published AAR.
     *
     * <p>These proguard rule files will then be used by any application project that consumes the
     * AAR (if ProGuard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    @Override
    @NonNull
    public List<File> getConsumerProguardFiles() {
        return mConsumerProguardFiles;
    }

    @NonNull
    @Override
    public List<File> getTestProguardFiles() {
        return mTestProguardFiles;
    }

    /**
     * Returns the manifest placeholders.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide/manifest-merger#TOC-Placeholder-support">
     *     Manifest merger</a>.
     */
    @NonNull
    @Override
    public Map<String, Object> getManifestPlaceholders() {
        return mManifestPlaceholders;
    }

    /**
     * Adds manifest placeholders.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide/manifest-merger#TOC-Placeholder-support">
     *     Manifest merger</a>.
     */
    public void addManifestPlaceholders(@NonNull Map<String, Object> manifestPlaceholders) {
        mManifestPlaceholders.putAll(manifestPlaceholders);
    }

    /**
     * Sets a new set of manifest placeholders.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide/manifest-merger#TOC-Placeholder-support">
     *     Manifest merger</a>.
     */
    public void setManifestPlaceholders(@NonNull Map<String, Object> manifestPlaceholders) {
        mManifestPlaceholders.clear();
        this.mManifestPlaceholders.putAll(manifestPlaceholders);
    }

    protected void _initWith(@NonNull BaseConfig that) {
        setBuildConfigFields(that.getBuildConfigFields());
        setResValues(that.getResValues());

        mProguardFiles.clear();
        mProguardFiles.addAll(that.getProguardFiles());

        mConsumerProguardFiles.clear();
        mConsumerProguardFiles.addAll(that.getConsumerProguardFiles());

        mTestProguardFiles.clear();
        mTestProguardFiles.addAll(that.getTestProguardFiles());

        mManifestPlaceholders.clear();
        mManifestPlaceholders.putAll(that.getManifestPlaceholders());

        mMultiDexEnabled = that.getMultiDexEnabled();

        mMultiDexKeepFile = that.getMultiDexKeepFile();
        mMultiDexKeepProguard = that.getMultiDexKeepProguard();
    }

    private void setBuildConfigFields(@NonNull Map<String, ClassField> fields) {
        mBuildConfigFields.clear();
        mBuildConfigFields.putAll(fields);
    }

    private void setResValues(@NonNull Map<String, ClassField> fields) {
        mResValues.clear();
        mResValues.putAll(fields);
    }

    /**
     * Whether Multi-Dex is enabled for this variant.
     */
    @Override
    @Nullable
    public Boolean getMultiDexEnabled() {
        return mMultiDexEnabled;
    }

    public void setMultiDexEnabled(@Nullable Boolean multiDex) {
        mMultiDexEnabled = multiDex;
    }

    @Override
    @Nullable
    public File getMultiDexKeepFile() {
        return mMultiDexKeepFile;
    }

    public void setMultiDexKeepFile(@Nullable File file) {
        mMultiDexKeepFile = file;
    }

    @Override
    @Nullable
    public File getMultiDexKeepProguard() {
        return mMultiDexKeepProguard;
    }

    public void setMultiDexKeepProguard(@Nullable File file) {
        mMultiDexKeepProguard = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseConfigImpl)) {
            return false;
        }

        BaseConfigImpl that = (BaseConfigImpl) o;

        if (!mBuildConfigFields.equals(that.mBuildConfigFields)) {
            return false;
        }
        if (!mConsumerProguardFiles.equals(that.mConsumerProguardFiles)) {
            return false;
        }
        if (!mManifestPlaceholders.equals(that.mManifestPlaceholders)) {
            return false;
        }
        if (mMultiDexEnabled != null ? !mMultiDexEnabled.equals(that.mMultiDexEnabled) :
                that.mMultiDexEnabled != null) {
            return false;
        }
        if (mMultiDexKeepFile != null ? !mMultiDexKeepFile.equals(that.mMultiDexKeepFile) :
                that.mMultiDexKeepFile != null) {
            return false;
        }
        if (mMultiDexKeepProguard != null ? !mMultiDexKeepProguard.equals(that.mMultiDexKeepProguard) :
                that.mMultiDexKeepProguard != null) {
            return false;
        }
        if (!mProguardFiles.equals(that.mProguardFiles)) {
            return false;
        }
        if (!mResValues.equals(that.mResValues)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = mBuildConfigFields.hashCode();
        result = 31 * result + mResValues.hashCode();
        result = 31 * result + mProguardFiles.hashCode();
        result = 31 * result + mConsumerProguardFiles.hashCode();
        result = 31 * result + mManifestPlaceholders.hashCode();
        result = 31 * result + (mMultiDexEnabled != null ? mMultiDexEnabled.hashCode() : 0);
        result = 31 * result + (mMultiDexKeepFile != null ? mMultiDexKeepFile.hashCode() : 0);
        result = 31 * result + (mMultiDexKeepProguard != null ? mMultiDexKeepProguard.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BaseConfigImpl{" +
                "mBuildConfigFields=" + mBuildConfigFields +
                ", mResValues=" + mResValues +
                ", mProguardFiles=" + mProguardFiles +
                ", mConsumerProguardFiles=" + mConsumerProguardFiles +
                ", mManifestPlaceholders=" + mManifestPlaceholders +
                ", mMultiDexEnabled=" + mMultiDexEnabled +
                ", mMultiDexKeepFile=" + mMultiDexKeepFile +
                ", mMultiDexKeepProguard=" + mMultiDexKeepProguard +
                '}';
    }
}
