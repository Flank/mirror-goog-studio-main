/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle

import com.android.annotations.NonNull
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.builder.core.AndroidBuilder
import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * 'android' extension for 'com.android.test' project.
 */
@CompileStatic
public class TestExtension extends BaseExtension {

    private final DefaultDomainObjectSet<ApplicationVariant> applicationVariantList =
        new DefaultDomainObjectSet<ApplicationVariant>(ApplicationVariant.class)

    private String targetProjectPath = null
    private String targetVariant = "debug"

    TestExtension(
            @NonNull ProjectInternal project,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<GroupableProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isLibrary) {
        super(project, instantiator, androidBuilder, sdkHandler, buildTypes, productFlavors,
                signingConfigs, extraModelInfo, isLibrary)
    }

    /**
     * Returns the list of Application variants. Since the collections is built after evaluation,
     * it should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DefaultDomainObjectSet<ApplicationVariant> getApplicationVariants() {
        return applicationVariantList
    }

    @Override
    void addVariant(BaseVariant variant) {
        applicationVariantList.add((ApplicationVariant) variant)
    }

    /**
     * Returns the Gradle path of the project that this test project tests.
     */
    String getTargetProjectPath() {
        return targetProjectPath
    }

    void setTargetProjectPath(String targetProjectPath) {
        checkWritability()
        this.targetProjectPath = targetProjectPath
    }

    void targetProjectPath(String targetProjectPath) {
        setTargetProjectPath(targetProjectPath)
    }

    /**
     * Returns the variant of the tested project.
     *
     * Default is 'debug'
     */
    String getTargetVariant() {
        return targetVariant
    }

    void setTargetVariant(String targetVariant) {
        checkWritability()
        this.targetVariant = targetVariant
    }

    void targetVariant(String targetVariant) {
        setTargetVariant(targetVariant)
    }
}
