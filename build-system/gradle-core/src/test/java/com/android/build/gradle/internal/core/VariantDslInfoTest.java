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
import com.android.build.api.variant.AndroidVersion;
import com.android.build.api.variant.impl.MutableAndroidVersion;
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo;
import com.android.build.gradle.internal.core.dsl.impl.DslInfoBuilder;
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.InternalApplicationExtension;
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
import com.android.build.gradle.internal.services.VariantServices;
import com.android.build.gradle.internal.variant.DimensionCombinationImpl;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.ComponentTypeImpl;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.model.ApiVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.List;
import kotlin.Pair;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.junit.Test;
import org.mockito.Mockito;

public class VariantDslInfoTest {

    private DefaultConfig defaultConfig;
    private ProductFlavor flavorConfig;
    private BuildType buildType;
    private DslServices dslServices;
    private VariantServices variantServices;

    @Test
    public void testSigningBuildTypeOverride() {
        initNoDeviceApiInjection();

        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig debugSigning = signingConfig("debug");
        debugSigning.storePassword("debug");
        buildType.setSigningConfig(debugSigning);

        SigningConfig override = signingConfig("override");
        override.storePassword("override");

        ApplicationVariantDslInfo variant = getVariant(override);

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

        ApplicationVariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testGetMinSdkVersion() {
        initNoDeviceApiInjection();

        AndroidVersion minSdkVersion = new MutableAndroidVersion(5);
        defaultConfig.setMinSdkVersion(minSdkVersion.getApiLevel());

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion()).isEqualTo(minSdkVersion);
    }

    @Test
    public void testGetMinSdk() {
        initNoDeviceApiInjection();

        ApiVersion minSdkVersion = DefaultApiVersion.create(5);
        defaultConfig.setMinSdk(5);

        assertThat(defaultConfig.getMinSdk()).isEqualTo(5);
        assertThat(defaultConfig.getMinSdkPreview()).isNull();

        ApplicationVariantDslInfo variant = getVariant();

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

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion())
                .isEqualTo(
                        new MutableAndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
    }

    @Test
    public void testGetMinSdkVersionDefault() {
        initNoDeviceApiInjection();

        ApplicationVariantDslInfo variant = getVariant();
        assertThat(variant.getMinSdkVersion()).isEqualTo(new MutableAndroidVersion(1));
    }

    @Test
    public void testGetTargetSdk() {
        initNoDeviceApiInjection();

        AndroidVersion targetSdkVersion = new MutableAndroidVersion(5);
        defaultConfig.setTargetSdk(5);

        assertThat(defaultConfig.getTargetSdk()).isEqualTo(5);
        assertThat(defaultConfig.getTargetSdkPreview()).isNull();

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkPreview() {
        initNoDeviceApiInjection();

        AndroidVersion targetSdkVersion = new MutableAndroidVersion(25, "O");
        defaultConfig.setTargetSdkPreview("O");

        assertThat(defaultConfig.getTargetSdk()).isEqualTo(25);
        assertThat(defaultConfig.getTargetSdkPreview()).isEqualTo("O");

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersion() {
        initNoDeviceApiInjection();

        MutableAndroidVersion targetSdkVersion = new MutableAndroidVersion(9);
        defaultConfig.setTargetSdkVersion(targetSdkVersion.getApiLevel());

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersionDefault() {
        initNoDeviceApiInjection();

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isNull();
    }

    @Test
    public void testGetMinSdkVersion_MultiDexEnabledNonDebuggable() {
        initWithInjectedDeviceApi(18);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(false);

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getDexingDslInfo().getTargetDeployApiFromIDE()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_MultiDexDisabledIsDebuggable() {
        initWithInjectedDeviceApi(18);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(false);
        buildType.setDebuggable(true);

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getDexingDslInfo().getTargetDeployApiFromIDE()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiLessSdkVersion() {
        initWithInjectedDeviceApi(18);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(true);

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getDexingDslInfo().getTargetDeployApiFromIDE()).isEqualTo(18);
    }

    @Test
    public void testGetMinSdkVersion_deviceApiGreaterSdkVersion() {
        initWithInjectedDeviceApi(22);

        defaultConfig.setMinSdkVersion(16);
        defaultConfig.setTargetSdkVersion(20);
        buildType.setMultiDexEnabled(true);
        buildType.setDebuggable(true);

        ApplicationVariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getDexingDslInfo().getTargetDeployApiFromIDE()).isEqualTo(22);
    }

    @Test
    public void testEmptyApplicationIdSuffix() {
        initNoDeviceApiInjection();

        defaultConfig.applicationId("com.example.mapp");
        buildType.applicationIdSuffix("");

        ApplicationVariantDslInfo variant = getVariant();
        assertThat(variant.getApplicationId().get()).isEqualTo("com.example.mapp");
    }

    private ApplicationVariantDslInfo getVariant() {
        return createVariant(null /*signingOverride*/);
    }

    private ApplicationVariantDslInfo getVariant(SigningConfig signingOverride) {
        return createVariant(signingOverride /*signingOverride*/);
    }

    private ApplicationVariantDslInfo createVariant(SigningConfig signingOverride) {

        InternalApplicationExtension extension = Mockito.mock(InternalApplicationExtension.class);
        Mockito.when(extension.getPublishing())
                .thenReturn(Mockito.mock(ApplicationPublishingImpl.class));

        List<Pair<String, String>> flavors = ImmutableList.of(new Pair<>("dimension1", "flavor"));
        DslInfoBuilder<?, ApplicationVariantDslInfo> builder =
                DslInfoBuilder.getBuilder(
                        new DimensionCombinationImpl("debug", flavors),
                        ComponentTypeImpl.BASE_APK,
                        defaultConfig,
                        new MockSourceProvider("main"),
                        buildType,
                        new MockSourceProvider("debug"),
                        signingOverride,
                        Mockito.mock(LazyManifestParser.class),
                        variantServices,
                        extension,
                        Mockito.mock(DirectoryProperty.class),
                        dslServices);

        builder.addProductFlavor(flavorConfig, new MockSourceProvider("custom"));

        return builder.createDslInfo();
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
        variantServices = FakeServices.createVariantPropertiesApiServices(projectServices);

        defaultConfig = dslServices.newDecoratedInstance(DefaultConfig.class, "main", dslServices);
        defaultConfig.applicationId("com.foo");
        flavorConfig = dslServices.newDecoratedInstance(ProductFlavor.class, "flavor", dslServices);
        flavorConfig.dimension("dimension1");
        buildType =
                dslServices.newDecoratedInstance(
                        BuildType.class, "debug", dslServices, ComponentTypeImpl.BASE_APK);
    }

    private SigningConfig signingConfig(String name) {
        return dslServices.newDecoratedInstance(SigningConfig.class, name, dslServices);
    }
}
