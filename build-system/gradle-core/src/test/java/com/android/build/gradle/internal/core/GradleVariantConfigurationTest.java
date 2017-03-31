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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.core.DefaultVectorDrawablesOptions;
import com.android.builder.core.VariantType;
import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

/** Test cases for {@link GradleVariantConfiguration}. */
public class GradleVariantConfigurationTest {

    @Test
    public void testGetMinSdkVersion() {
        // Case 1: buildType.getMultiDexEnabled() == true, buildType.isDebuggable() == false
        ProjectOptions projectOptions =
                new ProjectOptions(
                        ImmutableMap.of(IntegerOption.IDE_TARGET_DEVICE_API.getPropertyName(), 18));
        CoreBuildType buildType = mock(BuildType.class);
        when(buildType.getMultiDexEnabled()).thenReturn(true);
        when(buildType.isDebuggable()).thenReturn(false);
        GradleVariantConfiguration variant = createVariant(projectOptions, buildType);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(16);

        // Case 2: buildType.getMultiDexEnabled() == false, buildType.isDebuggable() == true
        buildType = mock(BuildType.class);
        when(buildType.getMultiDexEnabled()).thenReturn(false);
        when(buildType.isDebuggable()).thenReturn(true);
        variant = createVariant(projectOptions, buildType);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(16);

        // Case 3: buildType.getMultiDexEnabled() == true, buildType.isDebuggable() == true,
        // IDE_TARGET_DEVICE_API < targetSdkVersion
        buildType = mock(BuildType.class);
        when(buildType.getMultiDexEnabled()).thenReturn(true);
        when(buildType.isDebuggable()).thenReturn(true);
        variant = createVariant(projectOptions, buildType);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(18);

        // Case 4: buildType.getMultiDexEnabled() == true, buildType.isDebuggable() == true,
        // IDE_TARGET_DEVICE_API > targetSdkVersion
        projectOptions =
                new ProjectOptions(
                        ImmutableMap.of(IntegerOption.IDE_TARGET_DEVICE_API.getPropertyName(), 22));
        variant = createVariant(projectOptions, buildType);

        assertThat(variant.getMinSdkVersion().getApiLevel()).isEqualTo(16);
        assertThat(variant.getMinSdkVersionWithTargetDeviceApi().getApiLevel()).isEqualTo(20);
    }

    private GradleVariantConfiguration createVariant(
            @NonNull ProjectOptions projectOptions, @NonNull CoreBuildType buildType) {
        return new GradleVariantConfiguration(
                projectOptions,
                null,
                mockCoreProductFlavor(),
                mock(SourceProvider.class),
                null,
                buildType,
                null,
                VariantType.DEFAULT,
                null);
    }

    private CoreProductFlavor mockCoreProductFlavor() {
        CoreProductFlavor coreProductFlavor = mock(CoreProductFlavor.class);
        when(coreProductFlavor.getVectorDrawables())
                .thenReturn(new DefaultVectorDrawablesOptions());
        when(coreProductFlavor.getMinSdkVersion()).thenReturn(new DefaultApiVersion(16));
        when(coreProductFlavor.getTargetSdkVersion()).thenReturn(new DefaultApiVersion(20));
        return coreProductFlavor;
    }
}
