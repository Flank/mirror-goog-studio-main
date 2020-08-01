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

import static com.android.ide.common.gradle.model.impl.IdeAndroidProjectImpl.getDefaultVariant;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.createEqualsVerifier;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.expectUnsupportedOperationException;
import static com.android.ide.common.gradle.model.impl.IdeModelTestUtils.verifyUsageOfImmutableCollections;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub;
import com.android.ide.common.gradle.model.stubs.VariantStub;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAndroidProjectImpl}. */
public class IdeAndroidProjectImplTest {
    private ModelCache myModelCache;
    private IdeDependenciesFactory myDependenciesFactory;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
        myDependenciesFactory = new IdeDependenciesFactory();
    }

    @Test
    public void model1_dot_5() {
        AndroidProjectStub original =
                new AndroidProjectStub("1.5.0") {
                    @Override
                    @NonNull
                    public String getBuildToolsVersion() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: AndroidProject.getBuildToolsVersion()");
                    }

                    @Override
                    public int getPluginGeneration() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: AndroidProject.getPluginGeneration()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getModelVersion(),
                                getName(),
                                getDefaultConfig(),
                                getBuildTypes(),
                                getProductFlavors(),
                                getSyncIssues(),
                                getVariants(),
                                getFlavorDimensions(),
                                getCompileTarget(),
                                getBootClasspath(),
                                getNativeToolchains(),
                                getSigningConfigs(),
                                getLintOptions(),
                                getUnresolvedDependencies(),
                                getJavaCompileOptions(),
                                getBuildFolder(),
                                getResourcePrefix(),
                                getApiVersion(),
                                isLibrary(),
                                getProjectType(),
                                isBaseSplit(),
                                getViewBindingOptions());
                    }
                };
        IdeAndroidProject androidProject =
                IdeAndroidProjectImpl.create(
                        original,
                        myModelCache,
                        myDependenciesFactory,
                        null,
                        Collections.emptyList());
        expectUnsupportedOperationException(androidProject::getBuildToolsVersion);
    }

    @Test
    public void constructor() throws Throwable {
        AndroidProject original = new AndroidProjectStub("2.4.0");
        IdeAndroidProjectImpl copy =
                IdeAndroidProjectImpl.create(
                        original,
                        myModelCache,
                        myDependenciesFactory,
                        null,
                        Collections.emptyList());
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void constructorWithVariant() throws Throwable {
        AndroidProject original = new AndroidProjectStub("2.4.0");
        original.getVariants().clear();
        Variant variant = new VariantStub();
        IdeAndroidProjectImpl copy =
                IdeAndroidProjectImpl.create(
                        original,
                        myModelCache,
                        myDependenciesFactory,
                        singletonList(variant),
                        Collections.emptyList());

        original.getVariants().add(variant);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void addVariants() throws Throwable {
        AndroidProject original = new AndroidProjectStub("3.2.0");
        original.getVariants().clear();
        Variant variant = new VariantStub();
        IdeAndroidProjectImpl copy =
                IdeAndroidProjectImpl.create(
                        original,
                        myModelCache,
                        myDependenciesFactory,
                        singletonList(variant),
                        Collections.emptyList());

        // Verify that new variant is added.
        copy.addVariants(singletonList(mock(IdeVariant.class)));
        assertThat(copy.getVariants()).hasSize(2);

        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void defaultVariantBackwardCompatibilityTest() {
        AndroidProjectStub original =
                new AndroidProjectStub("1.5.0") {
                    @NonNull
                    @Override
                    public Collection<Variant> getVariants() {
                        return ImmutableList.of(
                                new VariantStub("alphaRelease", "release", "alpha"),
                                new VariantStub("betaDebug", "debug", "beta"),
                                new VariantStub("betaRelease", "release", "beta"));
                    }

                    @NonNull
                    @Override
                    public Collection<String> getVariantNames() {
                        throw new UnsupportedOperationException();
                    }

                    @Nullable
                    @Override
                    public String getDefaultVariant() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getModelVersion(),
                                getName(),
                                getDefaultConfig(),
                                getBuildTypes(),
                                getProductFlavors(),
                                getBuildToolsVersion(),
                                getSyncIssues(),
                                getVariants(),
                                getFlavorDimensions(),
                                getCompileTarget(),
                                getBootClasspath(),
                                getNativeToolchains(),
                                getSigningConfigs(),
                                getLintOptions(),
                                getUnresolvedDependencies(),
                                getJavaCompileOptions(),
                                getBuildFolder(),
                                getResourcePrefix(),
                                getApiVersion(),
                                isLibrary(),
                                getProjectType(),
                                isBaseSplit(),
                                getViewBindingOptions());
                    }
                };
        IdeAndroidProject androidProject =
                IdeAndroidProjectImpl.create(
                        original,
                        myModelCache,
                        myDependenciesFactory,
                        null,
                        Collections.emptyList());
        assertThat(androidProject.getDefaultVariant()).isEqualTo("betaDebug");
    }

    @Test
    public void defaultVariantCurrentTest() {
        AndroidProjectStub original =
                new AndroidProjectStub("3.5.0") {
                    @Override
                    public String getDefaultVariant() {
                        return "release";
                    }
                };
        IdeAndroidProject androidProject =
                IdeAndroidProjectImpl.create(
                        original,
                        myModelCache,
                        myDependenciesFactory,
                        null,
                        Collections.emptyList());
        assertThat(androidProject.getDefaultVariant()).isEqualTo("release");
    }

    @Test
    public void defaultVariantHeuristicTest_allVariantsRemoved() {
        assertThat(getDefaultVariant(ImmutableList.of())).isNull();
    }

    @Test
    public void defaultVariantHeuristicTest_picksDebug() {
        assertThat(getDefaultVariant(ImmutableList.of("a", "z", "debug", "release")))
                .isEqualTo("debug");
    }

    @Test
    public void defaultVariantHeuristicTest_picksDebugWithFlavors() {
        assertThat(getDefaultVariant(ImmutableList.of("aRelease", "bRelease", "bDebug", "cDebug")))
                .isEqualTo("bDebug");
    }

    @Test
    public void defaultVariantHeuristicTest_alphabeticalFallback() {
        assertThat(getDefaultVariant(ImmutableList.of("a", "b"))).isEqualTo("a");
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeAndroidProjectImpl.class).verify();
    }
}
