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
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter;
import com.android.build.gradle.internal.fixtures.FakeLogger;
import com.android.build.gradle.internal.fixtures.FakeObjectFactory;
import com.android.build.gradle.internal.fixtures.FakeProviderFactory;
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter;
import com.android.build.gradle.internal.fixtures.ProjectFactory;
import com.android.build.gradle.internal.manifest.LazyManifestParser;
import com.android.build.gradle.internal.scope.ProjectInfo;
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
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collections;
import java.util.List;
import kotlin.Pair;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Test cases for {@link VariantDslInfo}. */
public class VariantDslInfoTest {

    private DefaultConfig defaultConfig;
    private ProductFlavor flavorConfig;
    private BuildType buildType;
    private DslServices dslServices;
    private VariantPropertiesApiServices variantPropertiesApiServices;
    private Provider<String> namespace;
    private String testNamespace;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSigningBuildTypeOverride() {
        initNoDeviceApiInjection();

        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig debugSigning = new SigningConfig("debug");
        debugSigning.storePassword("debug");
        buildType.setSigningConfig(debugSigning);

        SigningConfig override = new SigningConfig("override");
        override.storePassword("override");

        VariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testSigningProductFlavorOverride() {
        initNoDeviceApiInjection();

        // SigningConfig doesn't compare the name, so put some content.
        SigningConfig defaultSigning = new SigningConfig("defaultConfig");
        defaultSigning.storePassword("debug");
        defaultConfig.setSigningConfig(defaultSigning);

        SigningConfig override = new SigningConfig("override");
        override.storePassword("override");

        VariantDslInfo variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testGetMinSdkVersion() {
        initNoDeviceApiInjection();

        ApiVersion minSdkVersion = DefaultApiVersion.create(5);
        defaultConfig.setMinSdkVersion(minSdkVersion.getApiLevel());

        VariantDslInfo variant = getVariant();

        assertThat(variant.getMinSdkVersion())
                .isEqualTo(
                        new AndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
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
                        new AndroidVersion(
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
                        new AndroidVersion(
                                minSdkVersion.getApiLevel(), minSdkVersion.getCodename()));
    }

    @Test
    public void testGetMinSdkVersionDefault() {
        initNoDeviceApiInjection();

        VariantDslInfo variant = getVariant();
        assertThat(variant.getMinSdkVersion()).isEqualTo(new AndroidVersion(1, null));
    }

    @Test
    public void testGetTargetSdk() {
        initNoDeviceApiInjection();

        ApiVersion targetSdkVersion = DefaultApiVersion.create(5);
        defaultConfig.setTargetSdk(5);

        assertThat(defaultConfig.getTargetSdk()).isEqualTo(5);
        assertThat(defaultConfig.getTargetSdkPreview()).isNull();

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkPreview() {
        initNoDeviceApiInjection();

        ApiVersion targetSdkVersion = DefaultApiVersion.create("O");
        defaultConfig.setTargetSdkPreview("O");

        assertThat(defaultConfig.getTargetSdk()).isEqualTo(25);
        assertThat(defaultConfig.getTargetSdkPreview()).isEqualTo("O");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersion() {
        initNoDeviceApiInjection();

        ApiVersion targetSdkVersion = DefaultApiVersion.create(9);
        defaultConfig.setTargetSdkVersion(targetSdkVersion.getApiLevel());

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(targetSdkVersion);
    }

    @Test
    public void testGetTargetSdkVersionDefault() {
        initNoDeviceApiInjection();

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTargetSdkVersion()).isEqualTo(DefaultApiVersion.create(-1));
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
        assertThat(variant.getMinSdkVersionFromIDE()).isEqualTo(18);
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
        assertThat(variant.getMinSdkVersionFromIDE()).isEqualTo(18);
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
        assertThat(variant.getMinSdkVersionFromIDE()).isEqualTo(18);
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
        assertThat(variant.getMinSdkVersionFromIDE()).isEqualTo(22);
    }

    @Test
    public void testEmptyApplicationIdSuffix() {
        initNoDeviceApiInjection();

        defaultConfig.applicationId("com.example.mapp");
        buildType.applicationIdSuffix("");

        VariantDslInfo variant = getVariant();
        assertThat(variant.getApplicationId().get()).isEqualTo("com.example.mapp");
    }

    @Test
    public void testNamespace() {
        initNoDeviceApiInjection();

        namespace = FakeProviderFactory.getFactory().provider(() -> "com.example.myNamespace");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getNamespace().get()).isEqualTo("com.example.myNamespace");
    }

    @Test
    public void testTestNamespace() {
        initNoDeviceApiInjection();

        testNamespace = "com.example.myTestNamespace";

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTestNamespace()).isEqualTo("com.example.myTestNamespace");
    }

    @Test
    public void testDefaultTestNamespace() {
        initNoDeviceApiInjection();

        namespace = FakeProviderFactory.getFactory().provider(() -> "com.example.myNamespace");

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTestNamespace()).isEqualTo("com.example.myNamespace.test");
    }

    @Test
    public void testNullTestNamespace() {
        initNoDeviceApiInjection();

        VariantDslInfo variant = getVariant();

        assertThat(variant.getTestNamespace()).isEqualTo(null);
    }

    private VariantDslInfo getVariant() {
        return createVariant(null /*signingOverride*/);
    }

    private VariantDslInfo getVariant(SigningConfig signingOverride) {
        return createVariant(signingOverride /*signingOverride*/);
    }

    private VariantDslInfo createVariant(SigningConfig signingOverride) {

        List<Pair<String, String>> flavors = ImmutableList.of(new Pair<>("dimension1", "flavor"));
        VariantDslInfoBuilder builder =
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
                        namespace,
                        testNamespace,
                        null, /* BuildType */
                        Mockito.mock(BaseExtension.class),
                        false,
                        Collections.emptyMap(),
                        false /* enableTestFixtures */,
                        null /* testFixtureMainVariantName */);

        builder.addProductFlavor(flavorConfig, new MockSourceProvider("custom"));

        return builder.createVariantDslInfo(
                Mockito.mock(CommonExtension.class),
                Mockito.mock(DirectoryProperty.class));
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

        ProjectServices projectServices =
                FakeServices.createProjectServices(
                        new FakeSyncIssueReporter(),
                        new FakeDeprecationReporter(),
                        FakeObjectFactory.getFactory(),
                        new FakeLogger(),
                        FakeProviderFactory.getFactory(),
                        ProjectFactory.getProject().getLayout(),
                        projectOptions,
                        ProjectFactory.getProject().getGradle().getSharedServices(),
                        new ProjectInfo(ProjectFactory.getProject()),
                        it -> new File(it.toString()));
        dslServices = FakeServices.createDslServices(projectServices);
        variantPropertiesApiServices =
                FakeServices.createVariantPropertiesApiServices(projectServices);

        defaultConfig = new DefaultConfig("main", dslServices);
        defaultConfig.applicationId("com.foo");
        flavorConfig = dslServices.newInstance(ProductFlavor.class, "flavor", dslServices);
        flavorConfig.dimension("dimension1");
        buildType = dslServices.newInstance(BuildType.class, "debug", dslServices);
        namespace = null;
        testNamespace = null;
    }
}
