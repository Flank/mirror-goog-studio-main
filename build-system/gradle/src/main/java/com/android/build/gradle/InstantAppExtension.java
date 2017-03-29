/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.InstantAppVariant;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.internal.reflect.Instantiator;

/** {@code android} extension for {@code com.android.instantapp} projects. */
class InstantAppExtension extends TestedExtension {

    private final DefaultDomainObjectSet<InstantAppVariant> instantAppVariantList
            = new DefaultDomainObjectSet<InstantAppVariant>(InstantAppVariant.class);

    public InstantAppExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo) {
        super(
                project,
                projectOptions,
                instantiator,
                androidBuilder,
                sdkHandler,
                buildTypes,
                productFlavors,
                signingConfigs,
                buildOutputs,
                extraModelInfo,
                false);
    }

    /**
     * Returns the list of Instant App variants. Since the collections is built after evaluation, it
     * should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DomainObjectSet<InstantAppVariant> getInstantAppVariants() {
        return instantAppVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant) {
        instantAppVariantList.add((InstantAppVariant) variant);
    }

}
