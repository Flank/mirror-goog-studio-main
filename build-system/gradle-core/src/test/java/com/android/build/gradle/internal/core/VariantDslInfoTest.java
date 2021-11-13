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

import com.android.annotations.Nullable;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.variant.AndroidVersion;
import com.android.build.api.variant.impl.MutableAndroidVersion;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter;
import com.android.build.gradle.internal.fixtures.FakeLogger;
import com.android.build.gradle.internal.fixtures.FakeProviderFactory;
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter;
import com.android.build.gradle.internal.fixtures.ProjectFactory;
import com.android.build.gradle.internal.manifest.LazyManifestParser;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.FakeServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.internal.variant.DimensionCombinationImpl;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.model.ApiVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collections;
import java.util.List;
import kotlin.Pair;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.junit.Test;
import org.mockito.Mockito;

/** Test cases for {@link VariantDslInfo}. */
public class VariantDslInfoTest {

    private DefaultConfig defaultConfig;
    private ProductFlavor flavorConfig;
    private BuildType buildType;
    private DslServices dslServices;
    private VariantPropertiesApiServices variantPropertiesApiServices;

    @Test
    public void testSigningBuildTypeOverride() {
        initNoDeviceApiInjection();

        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig debugSigning = signingConfig("debug");
        debugSigning.storePassword("debug");
        buildType.setSigningConfig(debugSigning);

        SigningConfig override = signingConfig("override");
        override.storePassword("override");

        VariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testSigningProductFlavorOverride() {
        initNoDeviceApiInjection();

        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig defaultSigning = signingConfig("defaultConfig");
        defaultSigning.storePassword("debug");
        defaultConfig.setSigningConfig(defaultSigning);

        SigningConfig override = signingConfig("override");
        override.storePassword("override");

        VariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testGetMinSdkVersion() {
        initNoDeviceApiInjection();

        AndroidVersion minSdkVersion = new MutableAndroidVersion(5);
        defaultConfig.setMinSdkVersion(minSdkVersion.getApiLevel());

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion()).isEqualTo(minSdkVersion);
    }

    @Test
    public void testGetMinSdk() {
        initNoDeviceApiInjection();

        ApiVersion minSdkVersion = DefaultApiVersion.create(5);
        defaultConfig.setMinSdk(5);

        assertThat(defaultConfig.getMinSdk()).isEqualTo(5);
        assertThat(defaultConfig.getMinSdkPreview()).isNull();

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion())
                .isEqualTo(
                        new MutableAndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
    }

    @Test
    public void testGetMinSdkPreview() {
        initNoDeviceApiInjection();

        ApiVersion minSdkVersion = DefaultApiVersion.create("O");
        defaultConfig.setMinSdkPreview("O");

        assertThat(defaultConfig.getMinSdk()).isEqualTo(25);
        assertThat(defaultConfig.getMinSdkPreview()).isEqualTo("O");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion())
                .isEqualTo(
                        new MutableAndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
    }

    @Test
    public void testGetMinSdkVersionDefault() {
        initNoDeviceApiInjection();

        VariantDslInfo variant = getVariant();
        assertThat(variant.getMinSdkVersion()).isEqualTo(new MutableAndroidVersion(1));
    }

    @Test
    public void testGetTargetSdk() {
        initNoDeviceApiInjection();

        AndroidVersion targetSdkVersion = new MutableAndroidVersion(5);
        defaultConfig.setTargetSdk(5);

        assertThat(defaultConfig.getTargetSdk()).isEqualTo(5);
        assertThat(defaultConfig.getTargetSdkPreview()).isNull();

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkPreview() {
        initNoDeviceApiInjection();

        AndroidVersion targetSdkVersion = new MutableAndroidVersion(25, "O");
        defaultConfig.setTargetSdkPreview("O");

        assertThat(defaultConfig.getTargetSdk()).isEqualTo(25);
        assertThat(defaultConfig.getTargetSdkPreview()).isEqualTo("O");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersion() {
        initNoDeviceApiInjection();

        MutableAndroidVersion targetSdkVersion = new MutableAndroidVersion(9);
        defaultConfig.setTargetSdkVersion(targetSdkVersion.getApiLevel());

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersionDefault() {
        initNoDeviceApiInjection();

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isNull();
    }

    @Test
    public void testGetMinSdkVersion_MultiDexEnabledNonDebuggable() {
        initWithInjectedDeviceApi(18);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(false);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getTargetDeployApiFromIDE()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_MultiDexDisabledIsDebuggable() {
        initWithInjectedDeviceApi(18);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(false);
        buildType.setDebuggable(true);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getTargetDeployApiFromIDE()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiLessSdkVersion() {
        initWithInjectedDeviceApi(18);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(true);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getTargetDeployApiFromIDE()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiGreaterSdkVersion() {
        initWithInjectedDeviceApi(22);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(true);

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getTargetDeployApiFromIDE()).isEqualTo(22);
    }

    @Test
    public void testEmptyApplicationIdSuffix() {
        initNoDeviceApiInjection();

        defaultConfig.applicationId("com.example.mapp");
        buildType.applicationIdSuffix("");

        VariantDslInfo variant = getVariant();
        assertThat(variant.getApplicationId().get()).isEqualTo("com.example.mapp");
    }

    private VariantDslInfo getVariant() {
        return createVariant(null /*signingOverride*/);
    }

    private VariantDslInfo getVariant(SigningConfig signingOverride) {
        return createVariant(signingOverride /*signingOverride*/);
    }

    private VariantDslInfo createVariant(SigningConfig signingOverride) {

        List<Pair<String, String>> flavors = ImmutableList.of(new Pair<>("dimension1", "flavor"));
        VariantDslInfoBuilder<?> builder =
                VariantDslInfoBuilder.getBuilder(
                        new DimensionCombinationImpl("debug", flavors),
                        VariantTypeImpl.BASE_APK,
                        defaultConfig,
                        new MockSourceProvider("main"),
                        buildType,
                        new MockSourceProvider("debug"),
                        signingOverride,
                        Mockito.mock(LazyManifestParser.class),
                        dslServices,
                        variantPropertiesApiServices,
                        null, /* BuildType */
                        Mockito.mock(CommonExtension.class),
                        false,
                        Collections.emptyMap(),
                        null /* testFixtureMainVariantName */);

        builder.addProductFlavor(flavorConfig, new MockSourceProvider("custom"));

        return builder.createVariantDslInfo(Mockito.mock(DirectoryProperty.class));
    }

    private void initWithInjectedDeviceApi(int deviceApi) {
        init(deviceApi);
    }

    private void initNoDeviceApiInjection() {
        init(null);
    }

    private void init(@Nullable Integer injectedDeviceApi) {
        ProjectOptions projectOptions;
        if (injectedDeviceApi == null) {
            projectOptions =
                    new ProjectOptions(
                            ImmutableMap.of(),
                            new FakeProviderFactory(
                                    FakeProviderFactory.getFactory(), ImmutableMap.of()));
        } else {
            ImmutableMap<String, Object> gradleProperties =
                    ImmutableMap.of(
                            IntegerOption.IDE_TARGET_DEVICE_API.getPropertyName(),
                            injectedDeviceApi);
            projectOptions =
                    new ProjectOptions(
                            ImmutableMap.of(),
                            new FakeProviderFactory(
                                    FakeProviderFactory.getFactory(), gradleProperties));
        }

        Project project = ProjectFactory.getProject();
        ProjectServices projectServices =
                FakeServices.createProjectServices(
                        project,
                        new FakeSyncIssueReporter(),
                        new FakeDeprecationReporter(),
                        project.getObjects(),
                        new FakeLogger(),
                        project.getProviders(),
                        projectOptions,
                        it -> new File(it.toString()));
        dslServices = FakeServices.createDslServices(projectServices);
        variantPropertiesApiServices =
                FakeServices.createVariantPropertiesApiServices(projectServices);

        defaultConfig = dslServices.newDecoratedInstance(DefaultConfig.class, "main", dslServices);
        defaultConfig.applicationId("com.foo");
        flavorConfig = dslServices.newDecoratedInstance(ProductFlavor.class, "flavor", dslServices);
        flavorConfig.dimension("dimension1");
        buildType = dslServices.newDecoratedInstance(BuildType.class, "debug", dslServices);
    }

    private SigningConfig signingConfig(String name) {
        return dslServices.newDecoratedInstance(SigningConfig.class, name, dslServices);
    }
}
