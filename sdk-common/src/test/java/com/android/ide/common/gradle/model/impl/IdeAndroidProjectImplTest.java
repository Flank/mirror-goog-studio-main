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

import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.singletonList;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub;
import com.android.ide.common.gradle.model.stubs.VariantStub;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAndroidProjectImpl}. */
public class IdeAndroidProjectImplTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
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
                myModelCache.androidProjectFrom(
                        original,
                        original.getVariants().stream()
                                .map(it -> it.getName())
                                .collect(Collectors.toList()));
        assertThat(androidProject.getBuildToolsVersion()).isNull();
    }

    @Test
    public void constructor() throws Throwable {
        AndroidProject original = new AndroidProjectStub("2.4.0");
        IdeAndroidProjectImpl copy =
                myModelCache.androidProjectFrom(
                        original,
                        original.getVariants().stream()
                                .map(it -> it.getName())
                                .collect(Collectors.toList()));
    }

    @Test
    public void constructorWithVariant() throws Throwable {
        AndroidProject original = new AndroidProjectStub("2.4.0");
        original.getVariants().clear();
        Variant variant = new VariantStub();
        IdeAndroidProjectImpl copy =
                myModelCache.androidProjectFrom(
                        original, singletonList(variant.getName()));

        original.getVariants().add(variant);
    }

    @Test
    public void defaultVariantHeuristicTest_allVariantsRemoved() {
        assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of())).isNull();
    }

    @Test
    public void defaultVariantHeuristicTest_picksDebug() {
      assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of("a", "z", "debug", "release")))
                .isEqualTo("debug");
    }

    @Test
    public void defaultVariantHeuristicTest_picksDebugWithFlavors() {
      assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of("aRelease", "bRelease", "bDebug", "cDebug")))
                .isEqualTo("bDebug");
    }

    @Test
    public void defaultVariantHeuristicTest_alphabeticalFallback() {
        assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of("a", "b"))).isEqualTo("a");
    }
}
