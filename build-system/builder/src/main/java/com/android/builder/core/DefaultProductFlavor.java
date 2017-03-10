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

package com.android.builder.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.BaseConfigImpl;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The configuration of a product flavor.
 *
 * This is also used to describe the default configuration of all builds, even those that
 * do not contain any flavors.
 */
public class DefaultProductFlavor extends BaseConfigImpl implements ProductFlavor {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String mName;
    @Nullable
    private String mDimension;
    @Nullable
    private ApiVersion mMinSdkVersion;
    @Nullable
    private ApiVersion mTargetSdkVersion;
    @Nullable
    private Integer mMaxSdkVersion;
    @Nullable
    private Integer mRenderscriptTargetApi;
    @Nullable
    private Boolean mRenderscriptSupportModeEnabled;
    @Nullable
    private Boolean mRenderscriptSupportModeBlasEnabled;
    @Nullable
    private Boolean mRenderscriptNdkModeEnabled;
    @Nullable
    private Integer mVersionCode;
    @Nullable
    private String mVersionName;
    @Nullable
    private String mApplicationId;
    @Nullable
    private String mTestApplicationId;
    @Nullable
    private String mTestInstrumentationRunner;
    @NonNull
    private Map<String, String> mTestInstrumentationRunnerArguments = Maps.newHashMap();
    @Nullable
    private Boolean mTestHandleProfiling;
    @Nullable
    private Boolean mTestFunctionalTest;
    @Nullable
    private SigningConfig mSigningConfig;
    @Nullable
    private Set<String> mResourceConfiguration;
    @NonNull
    private DefaultVectorDrawablesOptions mVectorDrawablesOptions;
    @Nullable
    private Boolean mWearAppUnbundled;

    /**
     * Creates a ProductFlavor with a given name.
     *
     * Names can be important when dealing with flavor groups.
     * @param name the name of the flavor.
     *
     * @see BuilderConstants#MAIN
     */
    public DefaultProductFlavor(@NonNull String name) {
        mName = name;
        mVectorDrawablesOptions = new DefaultVectorDrawablesOptions();
    }

    public DefaultProductFlavor(
            @NonNull String name,
            @NonNull DefaultVectorDrawablesOptions vectorDrawablesOptions) {
        mName = name;
        mVectorDrawablesOptions = vectorDrawablesOptions;
    }

    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    public void setDimension(@NonNull String dimension) {
        mDimension = dimension;
    }

    /** Name of the dimension this product flavor belongs to. */
    @Nullable
    @Override
    public String getDimension() {
        return mDimension;
    }

    /**
     * Sets the application id.
     */
    @NonNull
    public ProductFlavor setApplicationId(String applicationId) {
        mApplicationId = applicationId;
        return this;
    }

    /**
     * Returns the application ID.
     *
     * <p>See <a href="https://developer.android.com/studio/build/application-id.html">Set the Application ID</a>
     */
    @Override
    @Nullable
    public String getApplicationId() {
        return mApplicationId;
    }

    /**
     * Sets the version code.
     *
     * @param versionCode the version code
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setVersionCode(Integer versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    /**
     * Version code.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
     */
    @Override
    @Nullable
    public Integer getVersionCode() {
        return mVersionCode;
    }

    /**
     * Sets the version name.
     *
     * @param versionName the version name
     * @return the flavor object
     */
    @NonNull
    public ProductFlavor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    /**
     * Version name.
     *
     * <p>See <a href="http://developer.android.com/tools/publishing/versioning.html">Versioning Your Application</a>
     */
    @Override
    @Nullable
    public String getVersionName() {
        return mVersionName;
    }

    /**
     * Sets the minSdkVersion to the given value.
     */
    @NonNull
    public ProductFlavor setMinSdkVersion(ApiVersion minSdkVersion) {
        mMinSdkVersion = minSdkVersion;
        return this;
    }

    /**
     * Min SDK version.
     */
    @Nullable
    @Override
    public ApiVersion getMinSdkVersion() {
        return mMinSdkVersion;
    }

    /** Sets the targetSdkVersion to the given value. */
    @NonNull
    public ProductFlavor setTargetSdkVersion(@Nullable ApiVersion targetSdkVersion) {
        mTargetSdkVersion = targetSdkVersion;
        return this;
    }

    /**
     * Target SDK version.
     */
    @Nullable
    @Override
    public ApiVersion getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    @NonNull
    public ProductFlavor setMaxSdkVersion(Integer maxSdkVersion) {
        mMaxSdkVersion = maxSdkVersion;
        return this;
    }

    @Nullable
    @Override
    public Integer getMaxSdkVersion() {
        return mMaxSdkVersion;
    }

    @Override
    @Nullable
    public Integer getRenderscriptTargetApi() {
        return mRenderscriptTargetApi;
    }

    /** Sets the renderscript target API to the given value. */
    public void setRenderscriptTargetApi(@Nullable Integer renderscriptTargetApi) {
        mRenderscriptTargetApi = renderscriptTargetApi;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptSupportModeEnabled() {
        return mRenderscriptSupportModeEnabled;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptSupportModeBlasEnabled() {
        return mRenderscriptSupportModeBlasEnabled;
    }

    /**
     * Sets whether the renderscript code should be compiled in support mode to make it compatible
     * with older versions of Android.
     */
    public ProductFlavor setRenderscriptSupportModeEnabled(Boolean renderscriptSupportMode) {
        mRenderscriptSupportModeEnabled = renderscriptSupportMode;
        return this;
    }

    /**
     * Sets whether RenderScript BLAS support lib should be used to make it compatible
     * with older versions of Android.
     */
    public ProductFlavor setRenderscriptSupportModeBlasEnabled(Boolean renderscriptSupportModeBlas) {
        mRenderscriptSupportModeBlasEnabled = renderscriptSupportModeBlas;
        return this;
    }

    @Override
    @Nullable
    public Boolean getRenderscriptNdkModeEnabled() {
        return mRenderscriptNdkModeEnabled;
    }


    /** Sets whether the renderscript code should be compiled to generate C/C++ bindings. */
    public ProductFlavor setRenderscriptNdkModeEnabled(Boolean renderscriptNdkMode) {
        mRenderscriptNdkModeEnabled = renderscriptNdkMode;
        return this;
    }

    /** Sets the test application ID. */
    @NonNull
    public ProductFlavor setTestApplicationId(String applicationId) {
        mTestApplicationId = applicationId;
        return this;
    }

    /**
     * Test application ID.
     *
     * <p>See <a href="https://developer.android.com/studio/build/application-id.html">Set the Application ID</a>
     */
    @Override
    @Nullable
    public String getTestApplicationId() {
        return mTestApplicationId;
    }

    /** Sets the test instrumentation runner to the given value. */
    @NonNull
    public ProductFlavor setTestInstrumentationRunner(String testInstrumentationRunner) {
        mTestInstrumentationRunner = testInstrumentationRunner;
        return this;
    }

    /**
     * Test instrumentation runner class name.
     *
     * <p>This is a fully qualified class name of the runner, e.g.
     * <code>android.test.InstrumentationTestRunner</code>
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public String getTestInstrumentationRunner() {
        return mTestInstrumentationRunner;
    }

    /** Sets the test instrumentation runner custom arguments. */
    @NonNull
    public ProductFlavor setTestInstrumentationRunnerArguments(
            @NonNull Map<String, String> testInstrumentationRunnerArguments) {
        mTestInstrumentationRunnerArguments = checkNotNull(testInstrumentationRunnerArguments);
        return this;
    }

    /**
     * Test instrumentation runner custom arguments.
     *
     * e.g. <code>[key: "value"]</code> will give
     * <code>adb shell am instrument -w <b>-e key value</b> com.example</code>...".
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     *
     * <p>Test runner arguments can also be specified from the command line:
     *
     * <p><pre>
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * </pre>
     */
    @SuppressWarnings("SpellCheckingInspection")
    @Override
    @NonNull
    public Map<String, String> getTestInstrumentationRunnerArguments() {
        return mTestInstrumentationRunnerArguments;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public Boolean getTestHandleProfiling() {
        return mTestHandleProfiling;
    }

    @NonNull
    public ProductFlavor setTestHandleProfiling(boolean handleProfiling) {
        mTestHandleProfiling = handleProfiling;
        return this;
    }

    /**
     * See <a href="http://developer.android.com/guide/topics/manifest/instrumentation-element.html">
     * instrumentation</a>.
     */
    @Override
    @Nullable
    public Boolean getTestFunctionalTest() {
        return mTestFunctionalTest;
    }

    @NonNull
    public ProductFlavor setTestFunctionalTest(boolean functionalTest) {
        mTestFunctionalTest = functionalTest;
        return this;
    }

    /**
     * Signing config used by this product flavor.
     */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    /** Sets the signing configuration. e.g.: {@code signingConfig signingConfigs.myConfig} */
    @NonNull
    public ProductFlavor setSigningConfig(SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    /**
     * Options to configure the build-time support for {@code vector} drawables.
     */
    @NonNull
    @Override
    public DefaultVectorDrawablesOptions getVectorDrawables() {
        return mVectorDrawablesOptions;
    }

    /**
     * Returns whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one
     * distributed by the play store directly.
     */
    @Nullable
    @Override
    public Boolean getWearAppUnbundled() {
        return mWearAppUnbundled;
    }

    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one
     * distributed by the play store directly.
     */
    public void setWearAppUnbundled(@Nullable Boolean wearAppUnbundled) {
        mWearAppUnbundled = wearAppUnbundled;
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    public void addResourceConfiguration(@NonNull String configuration) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.add(configuration);
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    public void addResourceConfigurations(@NonNull String... configurations) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.addAll(Arrays.asList(configurations));
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    public void addResourceConfigurations(@NonNull Collection<String> configurations) {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        mResourceConfiguration.addAll(configurations);
    }

    /**
     * Adds a res config filter (for instance 'hdpi')
     */
    @NonNull
    @Override
    public Collection<String> getResourceConfigurations() {
        if (mResourceConfiguration == null) {
            mResourceConfiguration = Sets.newHashSet();
        }

        return mResourceConfiguration;
    }

    /**
     * Merges the flavors by analyzing the specified one and the list. Flavors whose position in the
     * list is higher will have their values overwritten by the lower-position flavors (in case they
     * have non-null values for some properties). E.g. if flavor at position 1 specifies
     * applicationId &quot;my.application&quot;, and flavor at position 0 specifies
     * &quot;sample.app&quot;, merged flavor will have applicationId &quot;sampleapp&quot; (if there
     * are no other flavors overwriting this value). Flavor {@code lowestPriority}, as the name
     * says, has the lowest priority of them all, and will always be overwritten.
     *
     * @param lowestPriority flavor with the lowest priority
     * @param flavors flavors to merge
     * @return final merged product flavor
     */
    static ProductFlavor mergeFlavors(
            @NonNull ProductFlavor lowestPriority, @NonNull List<? extends ProductFlavor> flavors) {
        DefaultProductFlavor mergedFlavor = DefaultProductFlavor.clone(lowestPriority);
        for (ProductFlavor flavor : Lists.reverse(flavors)) {
            mergedFlavor = DefaultProductFlavor.mergeFlavors(mergedFlavor, flavor);
        }

        /*
         * For variants with product flavor dimensions d1, d2 and flavors f1 of d1 and f2 of d2, we
         * will have final applicationSuffixId suffix(default).suffix(f2).suffix(f1). However, the
         * previous implementation of product flavor merging would produce
         * suffix(default).suffix(f1).suffix(f2). We match that behavior below as we do not want to
         * change application id of developers' applications. The same applies to versionNameSuffix.
         */
        String applicationIdSuffix = lowestPriority.getApplicationIdSuffix();
        String versionNameSuffix = lowestPriority.getVersionNameSuffix();
        for (ProductFlavor mFlavor : flavors) {
            applicationIdSuffix =
                    DefaultProductFlavor.mergeApplicationIdSuffix(
                            mFlavor.getApplicationIdSuffix(), applicationIdSuffix);
            versionNameSuffix =
                    DefaultProductFlavor.mergeVersionNameSuffix(
                            mFlavor.getVersionNameSuffix(), versionNameSuffix);
        }
        mergedFlavor.setApplicationIdSuffix(applicationIdSuffix);
        mergedFlavor.setVersionNameSuffix(versionNameSuffix);

        return mergedFlavor;
    }

    /**
     * Merges two flavors on top of one another and returns a new object with the result.
     *
     * <p>The behavior is that if a value is present in the overlay, then it is used, otherwise we
     * use the value from the base.
     *
     * @param base the flavor to merge on top of
     * @param overlay the flavor to apply on top of the base.
     * @return a new ProductFlavor that represents the merge.
     */
    @NonNull
    private static DefaultProductFlavor mergeFlavors(
            @NonNull ProductFlavor base, @NonNull ProductFlavor overlay) {
        DefaultProductFlavor flavor = new DefaultProductFlavor("");

        flavor.mMinSdkVersion = chooseNotNull(
                overlay.getMinSdkVersion(),
                base.getMinSdkVersion());
        flavor.mTargetSdkVersion = chooseNotNull(
                overlay.getTargetSdkVersion(),
                base.getTargetSdkVersion());
        flavor.mMaxSdkVersion = chooseNotNull(
                overlay.getMaxSdkVersion(),
                base.getMaxSdkVersion());

        flavor.mRenderscriptTargetApi = chooseNotNull(
                overlay.getRenderscriptTargetApi(),
                base.getRenderscriptTargetApi());
        flavor.mRenderscriptSupportModeEnabled = chooseNotNull(
                overlay.getRenderscriptSupportModeEnabled(),
                base.getRenderscriptSupportModeEnabled());
        flavor.mRenderscriptSupportModeBlasEnabled = chooseNotNull(
                overlay.getRenderscriptSupportModeBlasEnabled(),
                base.getRenderscriptSupportModeBlasEnabled());
        flavor.mRenderscriptNdkModeEnabled = chooseNotNull(
                overlay.getRenderscriptNdkModeEnabled(),
                base.getRenderscriptNdkModeEnabled());

        flavor.mVersionCode = chooseNotNull(overlay.getVersionCode(), base.getVersionCode());
        flavor.mVersionName = chooseNotNull(overlay.getVersionName(), base.getVersionName());

        flavor.setVersionNameSuffix(
                mergeVersionNameSuffix(
                        overlay.getVersionNameSuffix(), base.getVersionNameSuffix()));

        flavor.mApplicationId = chooseNotNull(overlay.getApplicationId(), base.getApplicationId());

        flavor.setApplicationIdSuffix(
                mergeApplicationIdSuffix(
                        overlay.getApplicationIdSuffix(), base.getApplicationIdSuffix()));

        flavor.mTestApplicationId = chooseNotNull(
                overlay.getTestApplicationId(),
                base.getTestApplicationId());
        flavor.mTestInstrumentationRunner = chooseNotNull(
                overlay.getTestInstrumentationRunner(),
                base.getTestInstrumentationRunner());

        flavor.mTestInstrumentationRunnerArguments.putAll(
                base.getTestInstrumentationRunnerArguments());
        flavor.mTestInstrumentationRunnerArguments.putAll(
                overlay.getTestInstrumentationRunnerArguments());

        flavor.mTestHandleProfiling = chooseNotNull(
                overlay.getTestHandleProfiling(),
                base.getTestHandleProfiling());

        flavor.mTestFunctionalTest = chooseNotNull(
                overlay.getTestFunctionalTest(),
                base.getTestFunctionalTest());

        flavor.mSigningConfig = chooseNotNull(
                overlay.getSigningConfig(),
                base.getSigningConfig());

        flavor.mWearAppUnbundled = chooseNotNull(
                overlay.getWearAppUnbundled(),
                base.getWearAppUnbundled());

        flavor.addResourceConfigurations(base.getResourceConfigurations());
        flavor.addResourceConfigurations(overlay.getResourceConfigurations());

        flavor.addManifestPlaceholders(base.getManifestPlaceholders());
        flavor.addManifestPlaceholders(overlay.getManifestPlaceholders());

        flavor.addResValues(base.getResValues());
        flavor.addResValues(overlay.getResValues());

        flavor.addBuildConfigFields(base.getBuildConfigFields());
        flavor.addBuildConfigFields(overlay.getBuildConfigFields());

        flavor.setMultiDexEnabled(chooseNotNull(
                overlay.getMultiDexEnabled(), base.getMultiDexEnabled()));

        flavor.setMultiDexKeepFile(chooseNotNull(
                overlay.getMultiDexKeepFile(), base.getMultiDexKeepFile()));

        flavor.setMultiDexKeepProguard(chooseNotNull(
                overlay.getMultiDexKeepProguard(), base.getMultiDexKeepProguard()));

        flavor.setJarJarRuleFiles(ImmutableList.<File>builder()
                .addAll(overlay.getJarJarRuleFiles())
                .addAll(base.getJarJarRuleFiles())
                .build());

        flavor.getVectorDrawables().setGeneratedDensities(
                chooseNotNull(
                        overlay.getVectorDrawables().getGeneratedDensities(),
                        base.getVectorDrawables().getGeneratedDensities()));

        flavor.getVectorDrawables().setUseSupportLibrary(
                chooseNotNull(
                        overlay.getVectorDrawables().getUseSupportLibrary(),
                        base.getVectorDrawables().getUseSupportLibrary()));

        return flavor;
    }

    /**
     * Clone a given product flavor.
     *
     * @param productFlavor the flavor to clone.
     *
     * @return a new instance that is a clone of the flavor.
     */
    @NonNull
    static DefaultProductFlavor clone(@NonNull ProductFlavor productFlavor) {
        DefaultProductFlavor flavor = new DefaultProductFlavor(productFlavor.getName());
        flavor._initWith(productFlavor);
        flavor.mDimension = productFlavor.getDimension();
        flavor.mMinSdkVersion = productFlavor.getMinSdkVersion();
        flavor.mTargetSdkVersion = productFlavor.getTargetSdkVersion();
        flavor.mMaxSdkVersion = productFlavor.getMaxSdkVersion();
        flavor.mRenderscriptTargetApi = productFlavor.getRenderscriptTargetApi();
        flavor.mRenderscriptSupportModeEnabled = productFlavor.getRenderscriptSupportModeEnabled();
        flavor.mRenderscriptSupportModeBlasEnabled = productFlavor.getRenderscriptSupportModeBlasEnabled();
        flavor.mRenderscriptNdkModeEnabled = productFlavor.getRenderscriptNdkModeEnabled();

        flavor.mVersionCode = productFlavor.getVersionCode();
        flavor.mVersionName = productFlavor.getVersionName();
        flavor.setVersionNameSuffix(productFlavor.getVersionNameSuffix());

        flavor.mApplicationId = productFlavor.getApplicationId();

        flavor.mTestApplicationId = productFlavor.getTestApplicationId();
        flavor.mTestInstrumentationRunner = productFlavor.getTestInstrumentationRunner();
        flavor.mTestInstrumentationRunnerArguments = productFlavor.getTestInstrumentationRunnerArguments();
        flavor.mTestHandleProfiling = productFlavor.getTestHandleProfiling();
        flavor.mTestFunctionalTest = productFlavor.getTestFunctionalTest();

        flavor.mSigningConfig = productFlavor.getSigningConfig();

        flavor.mVectorDrawablesOptions =
                DefaultVectorDrawablesOptions.copyOf(productFlavor.getVectorDrawables());
        flavor.mWearAppUnbundled = productFlavor.getWearAppUnbundled();

        flavor.addResourceConfigurations(productFlavor.getResourceConfigurations());
        flavor.addManifestPlaceholders(productFlavor.getManifestPlaceholders());

        flavor.addResValues(productFlavor.getResValues());
        flavor.addBuildConfigFields(productFlavor.getBuildConfigFields());

        flavor.setMultiDexEnabled(productFlavor.getMultiDexEnabled());

        flavor.setMultiDexKeepFile(productFlavor.getMultiDexKeepFile());
        flavor.setMultiDexKeepProguard(productFlavor.getMultiDexKeepProguard());
        flavor.setJarJarRuleFiles(ImmutableList.copyOf(productFlavor.getJarJarRuleFiles()));

        return flavor;
    }

    private static <T> T chooseNotNull(T overlay, T base) {
        return overlay != null ? overlay : base;
    }

    public static String mergeApplicationIdSuffix(@Nullable String overlay, @Nullable String base){
        return Strings.nullToEmpty(joinWithSeparator(overlay, base, '.'));
    }

    public static String mergeVersionNameSuffix(@Nullable String overlay, @Nullable String base){
        return Strings.nullToEmpty(joinWithSeparator(overlay, base, null));
    }

    @Nullable
    private static String joinWithSeparator(@Nullable String overlay, @Nullable String base,
            @Nullable Character separator){
        if (!Strings.isNullOrEmpty(overlay)) {
            String baseSuffix = chooseNotNull(base, "");
            if (separator == null || overlay.charAt(0) == separator) {
                return baseSuffix + overlay;
            } else {
                return baseSuffix + separator + overlay;
            }
        }
        else{
            return base;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultProductFlavor that = (DefaultProductFlavor) o;

        return Objects.equal(mDimension, that.mDimension) &&
                Objects.equal(mApplicationId, that.mApplicationId) &&
                Objects.equal(mMaxSdkVersion, that.mMaxSdkVersion) &&
                Objects.equal(mMinSdkVersion, that.mMinSdkVersion) &&
                Objects.equal(mName, that.mName) &&
                Objects.equal(mRenderscriptNdkModeEnabled, that.mRenderscriptNdkModeEnabled) &&
                Objects.equal(mRenderscriptSupportModeEnabled,
                        that.mRenderscriptSupportModeEnabled) &&
                Objects.equal(mRenderscriptSupportModeBlasEnabled,
                        that.mRenderscriptSupportModeBlasEnabled) &&
                Objects.equal(mRenderscriptTargetApi, that.mRenderscriptTargetApi) &&
                Objects.equal(mResourceConfiguration, that.mResourceConfiguration) &&
                Objects.equal(mSigningConfig, that.mSigningConfig) &&
                Objects.equal(mTargetSdkVersion, that.mTargetSdkVersion) &&
                Objects.equal(mTestApplicationId, that.mTestApplicationId) &&
                Objects.equal(mTestFunctionalTest, that.mTestFunctionalTest) &&
                Objects.equal(mTestHandleProfiling, that.mTestHandleProfiling) &&
                Objects.equal(mTestInstrumentationRunner, that.mTestInstrumentationRunner) &&
                Objects.equal(mTestInstrumentationRunnerArguments,
                        that.mTestInstrumentationRunnerArguments) &&
                Objects.equal(mVersionCode, that.mVersionCode) &&
                Objects.equal(mVersionName, that.mVersionName) &&
                Objects.equal(mWearAppUnbundled, that.mWearAppUnbundled);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                super.hashCode(),
                mName,
                mDimension,
                mMinSdkVersion,
                mTargetSdkVersion,
                mMaxSdkVersion,
                mRenderscriptTargetApi,
                mRenderscriptSupportModeEnabled,
                mRenderscriptSupportModeBlasEnabled,
                mRenderscriptNdkModeEnabled,
                mVersionCode,
                mVersionName,
                mApplicationId,
                mTestApplicationId,
                mTestInstrumentationRunner,
                mTestInstrumentationRunnerArguments,
                mTestHandleProfiling,
                mTestFunctionalTest,
                mSigningConfig,
                mResourceConfiguration,
                mWearAppUnbundled);
    }

    @Override
    @NonNull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", mName)
                .add("dimension", mDimension)
                .add("minSdkVersion", mMinSdkVersion)
                .add("targetSdkVersion", mTargetSdkVersion)
                .add("renderscriptTargetApi", mRenderscriptTargetApi)
                .add("renderscriptSupportModeEnabled", mRenderscriptSupportModeEnabled)
                .add("renderscriptSupportModeBlasEnabled", mRenderscriptSupportModeBlasEnabled)
                .add("renderscriptNdkModeEnabled", mRenderscriptNdkModeEnabled)
                .add("versionCode", mVersionCode)
                .add("versionName", mVersionName)
                .add("applicationId", mApplicationId)
                .add("testApplicationId", mTestApplicationId)
                .add("testInstrumentationRunner", mTestInstrumentationRunner)
                .add("testInstrumentationRunnerArguments", mTestInstrumentationRunnerArguments)
                .add("testHandleProfiling", mTestHandleProfiling)
                .add("testFunctionalTest", mTestFunctionalTest)
                .add("signingConfig", mSigningConfig)
                .add("resConfig", mResourceConfiguration)
                .add("mBuildConfigFields", getBuildConfigFields())
                .add("mResValues", getResValues())
                .add("mProguardFiles", getProguardFiles())
                .add("mConsumerProguardFiles", getConsumerProguardFiles())
                .add("mManifestPlaceholders", getManifestPlaceholders())
                .add("mWearAppUnbundled", mWearAppUnbundled)
                .toString();
    }
}
