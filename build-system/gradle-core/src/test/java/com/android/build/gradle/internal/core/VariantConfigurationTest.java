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

package com.android.build.gradle.internal.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.variant2.FakeDslScope;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.FakeEvalIssueReporter;
import com.android.builder.model.ApiVersion;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class VariantConfigurationTest {

    private DefaultConfig mDefaultConfig;
    private ProductFlavor mFlavorConfig;
    private BuildType mBuildType;
    private EvalIssueReporter mIssueReporter;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File srcDir;

    @Mock Project project;
    @Mock ProjectOptions projectOptions;
    private DslScope dslScope = FakeDslScope.createFakeDslScope();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Mockito.when(projectOptions.get(Mockito.any(IntegerOption.class))).thenReturn(null);
        Mockito.when(projectOptions.get(Mockito.any(StringOption.class))).thenReturn(null);

        mDefaultConfig = new DefaultConfig("main", project, dslScope);
        mFlavorConfig = new ProductFlavor("flavor", project, dslScope);
        mFlavorConfig.setDimension("dimension1");
        mBuildType = new BuildType("debug", project, dslScope);
        srcDir = tmp.newFolder("src");
        mIssueReporter = new FakeEvalIssueReporter();
    }

    @Test
    public void testPackageOverrideNone() {
        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isNull();
    }

    @Test
    public void testIdOverrideIdFromFlavor() {
        mFlavorConfig.setApplicationId("foo.bar");

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar");
    }

    @Test
    public void testPackageOverridePackageFromFlavorWithSuffix() {
        mFlavorConfig.setApplicationId("foo.bar");
        mBuildType.setApplicationIdSuffix(".fortytwo");

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar.fortytwo");
    }

    @Test
    public void testPackageOverridePackageFromFlavorWithSuffix2() {
        mFlavorConfig.setApplicationId("foo.bar");
        mBuildType.setApplicationIdSuffix("fortytwo");

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar.fortytwo");
    }

    @Test
    public void testVersionNameFromFlavorWithSuffix() {
        mFlavorConfig.setVersionName("1.0");
        mBuildType.setVersionNameSuffix("-DEBUG");

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getVersionName()).isEqualTo("1.0-DEBUG");
    }

    @Test
    public void testSigningBuildTypeOverride() {
        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig debugSigning = new SigningConfig("debug");
        debugSigning.setStorePassword("debug");
        mBuildType.setSigningConfig(debugSigning);

        SigningConfig override = new SigningConfig("override");
        override.setStorePassword("override");

        GradleVariantConfiguration variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testSigningProductFlavorOverride() {
        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig defaultConfig = new SigningConfig("defaultConfig");
        defaultConfig.setStorePassword("debug");
        mDefaultConfig.setSigningConfig(defaultConfig);

        SigningConfig override = new SigningConfig("override");
        override.setStorePassword("override");

        GradleVariantConfiguration variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testGetMinSdkVersion() {

        ApiVersion minSdkVersion = DefaultApiVersion.create(5);
        mDefaultConfig.setMinSdkVersion(minSdkVersion);

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getMinSdkVersion())
                .isEqualTo(
                        new AndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
    }

    @Test
    public void testGetMinSdkVersionDefault() {

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getMinSdkVersion()).isEqualTo(new AndroidVersion(1, null));
    }

    @Test
    public void testGetTargetSdkVersion() {

        ApiVersion targetSdkVersion = DefaultApiVersion.create(9);
        mDefaultConfig.setTargetSdkVersion(targetSdkVersion);

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersionDefault() {

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(DefaultApiVersion.create(-1));
    }

    @Test
    public void testGetVersionCode() {

        mDefaultConfig.setVersionCode(42);

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getVersionCode()).isEqualTo(42);
    }

    @Test
    public void testGetVersionName() {

        mDefaultConfig.setVersionName("foo");
        mDefaultConfig.setVersionNameSuffix("-bar");
        mBuildType.setVersionNameSuffix("-baz");

        GradleVariantConfiguration variant = getVariant();

        assertThat(variant.getVersionName()).isEqualTo("foo-bar-baz");
    }


    private GradleVariantConfiguration getVariant() {
        return getVariant(null /*signingOverride*/);
    }

    private GradleVariantConfiguration getVariant(SigningConfig signingOverride) {
        VariantBuilder builder =
                VariantBuilder.getBuilder(
                        VariantTypeImpl.BASE_APK,
                        mDefaultConfig,
                        new MockSourceProvider("main"),
                        mBuildType,
                        new MockSourceProvider("debug"),
                        signingOverride,
                        null /*manifest supplier*/,
                        projectOptions,
                        mIssueReporter,
                        () -> true);

        builder.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"));

        return builder.createVariantConfig();
    }
}
