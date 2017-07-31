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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An object that contain a BuildConfig configuration
 */
public abstract class BaseConfigImpl implements Serializable, BaseConfig {
    private static final long serialVersionUID = 1L;

    private String mApplicationIdSuffix = null;
    private String mVersionNameSuffix = null;
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

    @NonNull
    private List<File> mJarJarRuleFiles = Lists.newArrayList();

    /**
     * @see #getApplicationIdSuffix()
     */
    @NonNull
    public BaseConfigImpl setApplicationIdSuffix(@Nullable String applicationIdSuffix) {
        mApplicationIdSuffix = applicationIdSuffix;
        return this;
    }

    /**
     * Application id suffix. It is appended to the "base" application id when calculating the final
     * application id for a variant.
     *
     * <p>In case there are product flavor dimensions specified, the final application id suffix
     * will contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on. All of these will have a dot in
     * between e.g. &quot;defaultSuffix.dimension1Suffix.dimensions2Suffix&quot;.
     */
    @Override
    @Nullable
    public String getApplicationIdSuffix() {
        return mApplicationIdSuffix;
    }

    /**
     * @see #getVersionNameSuffix()
     */
    @NonNull
    public BaseConfigImpl setVersionNameSuffix(@Nullable String versionNameSuffix) {
        mVersionNameSuffix = versionNameSuffix;
        return this;
    }

    /**
     * Version name suffix. It is appended to the "base" version name when calculating the final
     * version name for a variant.
     *
     * <p>In case there are product flavor dimensions specified, the final version name suffix will
     * contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on.
     */
    @Override
    @Nullable
    public String getVersionNameSuffix() {
        return mVersionNameSuffix;
    }

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

    private Map<String, String> flavorSelection;

    /**
     * Selects a flavor value for a given dimension for dependency resolution.
     *
     * <p>If the module does not have a flavor dimension of a consumer, it's required to manual
     * select which value to select during the resolution.
     *
     * <p>To learn more, read <a href="d.android.com/r/tools/use-flavorSelection.html">Select
     * default flavors for missing dimensions</a>.
     *
     * @param dimension the name of the dimension
     * @param value the name of the flavor value
     */
    public void flavorSelection(@NonNull String dimension, @NonNull String value) {
        if (flavorSelection == null) {
            flavorSelection = Maps.newHashMap();
        }

        flavorSelection.put(dimension, value);
    }

    public void addFlavorSelections(@NonNull Map<String, String> values) {
        if (flavorSelection == null) {
            flavorSelection = Maps.newHashMap();
        }

        flavorSelection.putAll(values);
    }

    @Override
    @NonNull
    public Map<String, String> getFlavorSelections() {
        if (flavorSelection == null) {
            return ImmutableMap.of();
        }

        return ImmutableMap.copyOf(flavorSelection);
    }

    /**
     * Returns ProGuard configuration files to be used.
     *
     * <p>There are 2 default rules files
     *
     * <ul>
     *   <li>proguard-android.txt
     *   <li>proguard-android-optimize.txt
     * </ul>
     *
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code>
     * will return the full path to the files. They are identical except for enabling optimizations.
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
     * <p>See <a href="https://developer.android.com/studio/build/manifest-build-variables.html">
     *     Inject Build Variables into the Manifest</a>.
     */
    @NonNull
    @Override
    public Map<String, Object> getManifestPlaceholders() {
        return mManifestPlaceholders;
    }

    /**
     * Adds manifest placeholders.
     *
     * <p>See <a href="https://developer.android.com/studio/build/manifest-build-variables.html">
     *     Inject Build Variables into the Manifest</a>.
     */
    public void addManifestPlaceholders(@NonNull Map<String, Object> manifestPlaceholders) {
        mManifestPlaceholders.putAll(manifestPlaceholders);
    }

    /**
     * Sets a new set of manifest placeholders.
     *
     * <p>See <a href="https://developer.android.com/studio/build/manifest-build-variables.html">
     *     Inject Build Variables into the Manifest</a>.
     */
    public void setManifestPlaceholders(@NonNull Map<String, Object> manifestPlaceholders) {
        mManifestPlaceholders.clear();
        this.mManifestPlaceholders.putAll(manifestPlaceholders);
    }

    protected void _initWith(@NonNull BaseConfig that) {
        setBuildConfigFields(that.getBuildConfigFields());
        setResValues(that.getResValues());

        if (that.getFlavorSelections().isEmpty()) {
            flavorSelection = null;
        } else {
            if (flavorSelection == null) {
                flavorSelection = new HashMap<>();
            }
            flavorSelection.clear();
            flavorSelection.putAll(that.getFlavorSelections());
        }

        mApplicationIdSuffix = that.getApplicationIdSuffix();
        mVersionNameSuffix = that.getVersionNameSuffix();

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

        mJarJarRuleFiles = that.getJarJarRuleFiles();
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

    /**
     * Text file that specifies additional classes that will be compiled into the main dex file.
     *
     * <p>Classes specified in the file are appended to the main dex classes computed using
     * {@code aapt}.
     *
     * <p>If set, the file should contain one class per line, in the following format:
     * {@code com/example/MyClass.class}
     */
    @Override
    @Nullable
    public File getMultiDexKeepFile() {
        return mMultiDexKeepFile;
    }

    public void setMultiDexKeepFile(@Nullable File file) {
        mMultiDexKeepFile = file;
    }

    /**
     * Text file with additional ProGuard rules to be used to determine which classes are compiled
     * into the main dex file.
     *
     * <p>If set, rules from this file are used in combination with the default rules used by the
     * build system.
     */
    @Override
    @Nullable
    public File getMultiDexKeepProguard() {
        return mMultiDexKeepProguard;
    }

    public void setMultiDexKeepProguard(@Nullable File file) {
        mMultiDexKeepProguard = file;
    }

    public void setJarJarRuleFiles(@NonNull List<File> files) {
        mJarJarRuleFiles = files;
    }

    @NonNull
    @Override
    public List<File> getJarJarRuleFiles() {
        return mJarJarRuleFiles;
    }

    @Override
    public String toString() {
        return "BaseConfigImpl{" +
                "applicationIdSuffix=" + mApplicationIdSuffix +
                ", versionNameSuffix=" + mVersionNameSuffix+
                ", mBuildConfigFields=" + mBuildConfigFields +
                ", mResValues=" + mResValues +
                ", mProguardFiles=" + mProguardFiles +
                ", mConsumerProguardFiles=" + mConsumerProguardFiles +
                ", mManifestPlaceholders=" + mManifestPlaceholders +
                ", mMultiDexEnabled=" + mMultiDexEnabled +
                ", mMultiDexKeepFile=" + mMultiDexKeepFile +
                ", mMultiDexKeepProguard=" + mMultiDexKeepProguard +
                ", mJarJarRuleFiles=" + mJarJarRuleFiles +
                '}';
    }
}
