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

package com.android.build.gradle.internal.test;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.incremental.BuildContext;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.builder.model.SourceProvider;
import com.android.builder.testing.TestData;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.file.FileCollection;
import org.xml.sax.SAXException;

/**
 * Implementation of {@link TestData} for separate test modules.
 */
public class TestApplicationTestData extends  AbstractTestDataImpl {

    private final String testApplicationId;
    private final BuildContext testedBuildContext;
    private final File testApk;
    private final FileCollection testedApks;
    private final GradleVariantConfiguration variantConfiguration;

    public TestApplicationTestData(
            GradleVariantConfiguration variantConfiguration,
            String testApplicationId,
            File testApk,
            FileCollection testedApks) {
        super(variantConfiguration);
        this.variantConfiguration = variantConfiguration;
        this.testedBuildContext = new BuildContext();
        this.testApplicationId = testApplicationId;
        this.testApk = testApk;
        this.testedApks = testedApks;
    }

    @Override
    public void loadFromMetadataFile(File metadataFile)
            throws ParserConfigurationException, SAXException, IOException {
        testedBuildContext.loadFromXmlFile(metadataFile);
        if (testedBuildContext.getLastBuild() == null) {
            throw new RuntimeException("No build information in build-info.xml" +
                    metadataFile.getAbsolutePath());
        }
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return testApplicationId;
    }

    @Nullable
    @Override
    public String getTestedApplicationId() {
        return testedBuildContext.getPackageId();
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @NonNull
    @Override
    public ImmutableList<File> getTestedApks(
            @NonNull ProcessExecutor processExecutor,
            @Nullable File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            @NonNull ILogger logger) throws ProcessException {

        // use a Set to remove duplicate entries.
        ImmutableList.Builder<File> testedApks = ImmutableList.builder();
        // retrieve all the published files.
        Set<File> testedApkFiles = this.testedApks.getFiles();
        // if we have more than one, that means pure splits are in the equation.
        if (testedApkFiles.size() > 1 && splitSelectExe != null) {

            List<String> testedSplitApksPath = getSplitApks();
            testedApks.addAll(
                    SplitOutputMatcher.computeBestOutput(processExecutor,
                            splitSelectExe,
                            deviceConfigProvider,
                            getMainApk(),
                            testedSplitApksPath));
        } else {
            // if we have only one or no split-select tool available, just install them all
            // it's not efficient but it's correct.
            if (testedApkFiles.size() > 1) {
                logger.warning("split-select tool unavailable, all split APKs will be installed");
            }
            testedApks.addAll(testedApkFiles);
        }
        return testedApks.build();
    }

    @NonNull
    @Override
    public File getTestApk() {
        return testApk;
    }

    @NonNull
    @Override
    public List<File> getTestDirectories() {
        // For now we check if there are any test sources. We could inspect the test classes and
        // apply JUnit logic to see if there's something to run, but that would not catch the case
        // where user makes a typo in a test name or forgets to inherit from a JUnit class
        ImmutableList.Builder<File> javaDirectories = ImmutableList.builder();
        for (SourceProvider sourceProvider : variantConfiguration.getSortedSourceProviders()) {
            javaDirectories.addAll(sourceProvider.getJavaDirectories());
        }
        return javaDirectories.build();
    }

    @NonNull
    public List<String> getSplitApks() {
        return testedBuildContext.getAllArtifactsOfAllBuild(FileType.SPLIT)
                .map(artifact -> artifact.getLocation().getAbsolutePath())
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the main APK from the list of APKs published by the tested configuration. There can
     * be multiple split APKs along the main APK returned by the configuration.
     *
     * @return the tested main APK
     */
    @NonNull
    private File getMainApk() {
        Optional<BuildContext.Artifact> mainApk = testedBuildContext
                .getAllArtifactsOfAllBuild(FileType.SPLIT_MAIN).findFirst();
        if (mainApk.isPresent()) {
            return mainApk.get().getLocation();
        }
        mainApk = testedBuildContext.getAllArtifactsOfAllBuild(FileType.MAIN).findFirst();
        if (mainApk.isPresent()) {
            return mainApk.get().getLocation();
        }
        throw new RuntimeException("Cannot retrieve main APK");
    }
}
