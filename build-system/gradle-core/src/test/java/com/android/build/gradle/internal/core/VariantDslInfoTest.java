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

package com.android.build.gradle.internal.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter;
import com.android.build.gradle.internal.variant.DimensionCombinationImpl;
import com.android.build.gradle.internal.variant2.FakeDslScope;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.model.ApiVersion;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import kotlin.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

/** Test cases for {@link VariantDslInfo}. */
public class VariantDslInfoTest {

    private DefaultConfig defaultConfig;
    private ProductFlavor flavorConfig;
    private BuildType buildType;
    private FakeSyncIssueReporter issueReporter = new FakeSyncIssueReporter();

    private DslScope dslScope = FakeDslScope.createFakeDslScope(issueReporter);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        defaultConfig = new DefaultConfig("main", dslScope);
        flavorConfig = new ProductFlavor("flavor", dslScope);
        flavorConfig.setDimension("dimension1");
        buildType = new BuildType("debug", dslScope);
    }

    @Test
    public void testPackageOverrideNone() {
        VariantDslInfo variant = getVariant();

        assertThat(variant.getIdOverride()).isNull();
    }

    @Test
    public void testIdOverrideIdFroflavor() {
        flavorConfig.setApplicationId("foo.bar");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar");
    }

    @Test
    public void testPackageOverridePackageFroflavorWithSuffix() {
        flavorConfig.setApplicationId("foo.bar");
        buildType.setApplicationIdSuffix(".fortytwo");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar.fortytwo");
    }

    @Test
    public void testPackageOverridePackageFroflavorWithSuffix2() {
        flavorConfig.setApplicationId("foo.bar");
        buildType.setApplicationIdSuffix("fortytwo");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar.fortytwo");
    }

    @Test
    public void testVersionNameFroflavorWithSuffix() {
        flavorConfig.setVersionName("1.0");
        buildType.setVersionNameSuffix("-DEBUG");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getVersionName()).isEqualTo("1.0-DEBUG");
    }

    @Test
    public void testSigningBuildTypeOverride() {
        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig debugSigning = new SigningConfig("debug");
        debugSigning.setStorePassword("debug");
        buildType.setSigningConfig(debugSigning);

        SigningConfig override = new SigningConfig("override");
        override.setStorePassword("override");

        VariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testSigningProductFlavorOverride() {
        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig defaultSigning = new SigningConfig("defaultConfig");
        defaultSigning.setStorePassword("debug");
        defaultConfig.setSigningConfig(defaultSigning);

        SigningConfig override = new SigningConfig("override");
        override.setStorePassword("override");

        VariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testGetMinSdkVersion() {
        ApiVersion minSdkVersion = DefaultApiVersion.create(5);
        defaultConfig.setMinSdkVersion(minSdkVersion);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion())
                .isEqualTo(
                        new AndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
    }

    @Test
    public void testGetMinSdkVersionDefault() {
        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion()).isEqualTo(new AndroidVersion(1, null));
    }

    @Test
    public void testGetTargetSdkVersion() {
        ApiVersion targetSdkVersion = DefaultApiVersion.create(9);
        defaultConfig.setTargetSdkVersion(targetSdkVersion);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersionDefault() {
        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(DefaultApiVersion.create(-1));
    }

    @Test
    public void testGetVersionCode() {
        defaultConfig.setVersionCode(42);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getVersionCode()).isEqualTo(42);
    }

    @Test
    public void testGetVersionName() {
        defaultConfig.setVersionName("foo");
        defaultConfig.setVersionNameSuffix("-bar");
        buildType.setVersionNameSuffix("-baz");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getVersionName()).isEqualTo("foo-bar-baz");
    }

    @Test
    public void testGetMinSdkVersion_MultiDexEnabledNonDebuggable() {
        defaultConfig.setMinSdkVersion(new DefaultApiVersion(16));
        defaultConfig.setTargetSdkVersion(new DefaultApiVersion(20));
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(false);

        VariantDslInfo variant = createVariant(18);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(16);
    }

    @Test
    public void testGetMinSdkVersion_MultiDexDisabledIsDebuggable() {
        defaultConfig.setMinSdkVersion(new DefaultApiVersion(16));
        defaultConfig.setTargetSdkVersion(new DefaultApiVersion(20));
        buildType.setMultiDexEnabled(false);
        buildType.setDebuggable(true);

        VariantDslInfo variant = createVariant(18);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(16);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiLessSdkVersion() {
        defaultConfig.setMinSdkVersion(new DefaultApiVersion(16));
        defaultConfig.setTargetSdkVersion(new DefaultApiVersion(20));
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(true);

        VariantDslInfo variant = createVariant(18);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiGreaterSdkVersion() {
        defaultConfig.setMinSdkVersion(new DefaultApiVersion(16));
        defaultConfig.setTargetSdkVersion(new DefaultApiVersion(20));
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(true);

        VariantDslInfo variant = createVariant(22);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(20);
    }

    private VariantDslInfo createVariant(int deviceApiVersion) {
        return createVariant(deviceApiVersion, null);
    }

    private VariantDslInfo getVariant() {
        return createVariant(null, null /*signingOverride*/);
    }

    private VariantDslInfo getVariant(SigningConfig signingOverride) {
        return createVariant(null, signingOverride /*signingOverride*/);
    }

    private VariantDslInfo createVariant(Integer deviceApiVersion, SigningConfig signingOverride) {

        ProjectOptions projectOptions;
        if (deviceApiVersion == null) {
            projectOptions = new ProjectOptions(ImmutableMap.of());
        } else {
            projectOptions =
                    new ProjectOptions(
                            ImmutableMap.of(
                                    IntegerOption.IDE_TARGET_DEVICE_API.getPropertyName(),
                                    deviceApiVersion));
        }

        List<Pair<String, String>> flavors = ImmutableList.of(new Pair<>("dimension1", "flavor"));
        VariantBuilder builder =
                VariantBuilder.getBuilder(
                        new DimensionCombinationImpl("debug", flavors),
                        VariantTypeImpl.BASE_APK,
                        defaultConfig,
                        new MockSourceProvider("main"),
                        buildType,
                        new MockSourceProvider("debug"),
                        signingOverride,
                        null /*manifest supplier*/,
                        projectOptions,
                        issueReporter,
                        () -> true);

        builder.addProductFlavor(flavorConfig, new MockSourceProvider("custom"));

        return builder.createVariantDslInfo();
    }
}
