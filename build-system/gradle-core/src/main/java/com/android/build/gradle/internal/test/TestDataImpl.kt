/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.test;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.testing.TestData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.xml.sax.SAXException;

/**
 * Implementation of {@link TestData} on top of a {@link TestVariantData}
 */
public class TestDataImpl extends AbstractTestDataImpl {

    @NonNull private final AndroidTestPropertiesImpl testVariantData;

    @NonNull private final VariantDslInfo testVariantDslInfo;

    public TestDataImpl(
            @NonNull AndroidTestPropertiesImpl testVariantData,
            @NonNull Provider<Directory> testApkDir,
            @Nullable FileCollection testedApksDir) {
        super(
                testVariantData.getVariantDslInfo(),
                testVariantData.getVariantSources(),
                testApkDir,
                testedApksDir);
        this.testVariantData = testVariantData;
        this.testVariantDslInfo = testVariantData.getVariantDslInfo();
        if (testVariantData
                        .getOutputs()
                        .getSplitsByType(
                                com.android.build.api.variant.VariantOutput.OutputType.ONE_OF_MANY)
                        .size()
                > 1) {
            throw new RuntimeException("Multi-output in test variant not yet supported");
        }
    }

    @Override
    public void load(File metadataFile)
            throws ParserConfigurationException, SAXException, IOException {
        // do nothing, there is nothing in the metadata file we cannot get from the tested scope.
    }

    @NonNull
    @Override
    public Provider<String> getApplicationId() {
        return testVariantData.getApplicationId();
    }

    @NonNull
    @Override
    public Provider<String> getTestedApplicationId() {
        return testVariantData.getTestedConfig().getApplicationId();
    }

    @Override
    public boolean isLibrary() {
        VariantPropertiesImpl testedVariant = testVariantData.getTestedVariant();
        return testedVariant.getVariantType().isAar();
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(
            @NonNull DeviceConfigProvider deviceConfigProvider, @NonNull ILogger logger) {
        VariantPropertiesImpl testedVariant = testVariantData.getTestedVariant();

        ImmutableList.Builder<File> apks = ImmutableList.builder();
        BuiltArtifacts builtArtifacts =
                new BuiltArtifactsLoaderImpl()
                        .load(
                                testedVariant
                                        .getArtifacts()
                                        .getFinalProduct(InternalArtifactType.APK.INSTANCE)
                                        .get());
        if (builtArtifacts == null) {
            return ImmutableList.of();
        }
        apks.addAll(
                BuiltArtifactsSplitOutputMatcher.INSTANCE.computeBestOutput(
                        deviceConfigProvider,
                        builtArtifacts,
                        testedVariant.getVariantDslInfo().getSupportedAbis()));
        return apks.build();
    }
}
