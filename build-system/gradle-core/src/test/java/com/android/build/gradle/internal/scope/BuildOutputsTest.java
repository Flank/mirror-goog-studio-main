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

package com.android.build.gradle.internal.scope;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType;
import com.google.common.collect.Iterators;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link BuildOutputs} class */
public class BuildOutputsTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock FileCollection fileCollection;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getBuildMetadataFileTest() throws IOException {
        File folder = temporaryFolder.newFolder();
        File outputFile = BuildOutputs.getMetadataFile(folder);
        assertThat(outputFile.getName()).isEqualTo("output.json");
        assertThat(outputFile.getParentFile()).isEqualTo(folder);
    }

    @Test
    public void testNoRequestedTypesLoading() throws IOException {
        File folder = temporaryFolder.newFolder();
        File outputFile = new File(folder, "output.json");
        FileUtils.write(
                outputFile,
                "[{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                        + "\"apkInfo\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                        + "\"outputFile\":{\"path\":\"/foo/bar/AndroidManifest.xml\"},"
                        + "\"properties\":{\"packageId\":\"com.android.tests.basic.debug\","
                        + "\"split\":\"\"}},"
                        + "{\"outputType\":{\"type\":\"DENSITY_OR_LANGUAGE_PACKAGED_SPLIT\"},"
                        + "\"apkInfo\":{\"type\":\"SPLIT\",\"splits\":[{\"filterType\":\"DENSITY\","
                        + "\"value\":\"mdpi\"}],\"versionCode\":12},\"outputFile\":{\"path\":"
                        + "\"/foo/bar/SplitAware-mdpi-debug-unsigned.apk\"},\"properties\":{}},"
                        + "{\"outputType\":{\"type\":\"DENSITY_OR_LANGUAGE_PACKAGED_SPLIT\"},"
                        + "\"apkInfo\":{\"type\":\"SPLIT\",\"splits\":[{\"filterType\":\"DENSITY\","
                        + "\"value\":\"xhdpi\"}],\"versionCode\":14},\"outputFile\":{\"path\":"
                        + "\"/foo/bar/SplitAware-xhdpi-debug-unsigned.apk\"},\"properties\":{}},"
                        + "{\"outputType\":{\"type\":\"DENSITY_OR_LANGUAGE_PACKAGED_SPLIT\"},"
                        + "\"apkInfo\":{\"type\":\"SPLIT\",\"splits\":[{\"filterType\":\"DENSITY\","
                        + "\"value\":\"hdpi\"}],\"versionCode\":13},\"outputFile\""
                        + ":{\"path\":\"/foo/bar/SplitAware-hdpi-debug-unsigned.apk\"},\"properties\""
                        + ":{}}]");

        assertThat(BuildOutputs.load(TaskOutputType.APK, outputFile)).isEmpty();
        Collection<BuildOutput> buildOutputs = BuildOutputs.load(folder);
        assertThat(buildOutputs).hasSize(4);
        assertThat(
                        buildOutputs
                                .stream()
                                .filter(
                                        buildOutput ->
                                                buildOutput.getType()
                                                        == TaskOutputType
                                                                .DENSITY_OR_LANGUAGE_PACKAGED_SPLIT)
                                .collect(Collectors.toList()))
                .hasSize(3);
        assertThat(
                        buildOutputs
                                .stream()
                                .filter(
                                        buildOutput ->
                                                buildOutput.getType()
                                                        == TaskOutputType.MERGED_MANIFESTS)
                                .collect(Collectors.toList()))
                .hasSize(1);
    }

    @Test
    public void testNoFilterNoPropertiesLoading() throws IOException {
        File folder = temporaryFolder.newFolder();
        File outputFile = new File(folder, "output.json");
        FileUtils.write(
                outputFile,
                "[{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                        + "\"apkInfo\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                        + "\"outputFile\":{\"path\":\"/foo/bar/AndroidManifest.xml\"},"
                        + "\"properties\":{\"packageId\":\"com.android.tests.basic.debug\","
                        + "\"split\":\"\"}}]");
        assertThat(BuildOutputs.load(TaskOutputType.APK, outputFile)).isEmpty();
        Collection<BuildOutput> buildOutputs =
                BuildOutputs.load(TaskOutputType.MERGED_MANIFESTS, folder);
        assertThat(buildOutputs).hasSize(1);
        BuildOutput buildOutput = buildOutputs.iterator().next();
        assertThat(buildOutput.getType()).isEqualTo(TaskOutputType.MERGED_MANIFESTS);
        assertThat(buildOutput.getFilters()).isEmpty();
        assertThat(buildOutput.getOutputFile().getAbsolutePath())
                .isEqualTo(new File("/foo/bar/AndroidManifest.xml").getAbsolutePath());
        assertThat(buildOutput.getApkInfo().getType()).isEqualTo(VariantOutput.OutputType.MAIN);
        assertThat(buildOutput.getApkInfo().getFilters()).isEmpty();
    }

    @Test
    public void testBuildOutputPropertiesLoading() throws IOException {
        File folder = temporaryFolder.newFolder();
        File outputFile = new File(folder, "output.json");
        FileUtils.write(
                outputFile,
                "[{\"outputType\":{\"type\":\"MERGED_MANIFESTS\"},"
                        + "\"apkInfo\":{\"type\":\"MAIN\",\"splits\":[],\"versionCode\":12},"
                        + "\"outputFile\":{\"path\":\"/foo/bar/AndroidManifest.xml\"},"
                        + "\"properties\":{\"packageId\":\"com.android.tests.basic\","
                        + "\"split\":\"\"}}]");
        Collection<BuildOutput> buildOutputs =
                BuildOutputs.load(TaskOutputType.MERGED_MANIFESTS, folder);
        assertThat(buildOutputs).hasSize(1);
        BuildOutput buildOutput = Iterators.getOnlyElement(buildOutputs.iterator());
        assertThat(buildOutput.getProperties()).hasSize(2);
        assertThat(buildOutput.getProperties().get(BuildOutputProperty.PACKAGE_ID))
                .isEqualTo("com.android.tests.basic");
        assertThat(buildOutput.getProperties().get(BuildOutputProperty.SPLIT)).isEmpty();
    }

    @Test
    public void testSplitLoading() throws IOException {
        File folder = temporaryFolder.newFolder();
        File outputFile = new File(folder, "output.json");
        FileUtils.write(
                outputFile,
                "[{\"outputType\":{\"type\":\"DENSITY_OR_LANGUAGE_PACKAGED_SPLIT\"},"
                        + "\"apkInfo\":{\"type\":\"SPLIT\",\"splits\":[{\"filterType\":\"DENSITY\","
                        + "\"value\":\"mdpi\"}],\"versionCode\":12},\"outputFile\":{\"path\":"
                        + "\"/foo/bar/SplitAware-mdpi-debug-unsigned.apk\"},\"properties\":{}},"
                        + "{\"outputType\":{\"type\":\"DENSITY_OR_LANGUAGE_PACKAGED_SPLIT\"},"
                        + "\"apkInfo\":{\"type\":\"SPLIT\",\"splits\":[{\"filterType\":\"DENSITY\","
                        + "\"value\":\"xhdpi\"}],\"versionCode\":14},\"outputFile\":{\"path\":"
                        + "\"/foo/bar/SplitAware-xhdpi-debug-unsigned.apk\"},\"properties\":{}},"
                        + "{\"outputType\":{\"type\":\"DENSITY_OR_LANGUAGE_PACKAGED_SPLIT\"},"
                        + "\"apkInfo\":{\"type\":\"SPLIT\",\"splits\":[{\"filterType\":\"DENSITY\","
                        + "\"value\":\"hdpi\"}],\"versionCode\":13},\"outputFile\""
                        + ":{\"path\":\"/foo/bar/SplitAware-hdpi-debug-unsigned.apk\"},\"properties\""
                        + ":{}}]");

        Collection<BuildOutput> buildOutputs =
                BuildOutputs.load(TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT, folder);
        assertThat(buildOutputs).hasSize(3);

        Map<String, Integer> expectedDensitiesAndVersions = new HashMap<>(3);
        expectedDensitiesAndVersions.put("mdpi", 12);
        expectedDensitiesAndVersions.put("hdpi", 13);
        expectedDensitiesAndVersions.put("xhdpi", 14);

        buildOutputs.forEach(
                buildOutput -> {
                    assertThat(buildOutput.getType())
                            .isEqualTo(TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT);
                    assertThat(buildOutput.getOutputType()).isEqualTo(OutputFile.SPLIT);
                    assertThat(buildOutput.getFilters()).hasSize(1);
                    FilterData filterData =
                            Iterators.getOnlyElement(buildOutput.getFilters().iterator());
                    assertThat(filterData.getFilterType()).isEqualTo(OutputFile.DENSITY);
                    assertThat(buildOutput.getOutputFile().getName())
                            .contains(filterData.getIdentifier());
                    assertThat(expectedDensitiesAndVersions.get(filterData.getIdentifier()))
                            .isNotNull();
                    Integer expectedVersion =
                            expectedDensitiesAndVersions.get(filterData.getIdentifier());
                    assertThat(buildOutput.getVersionCode()).isEqualTo(expectedVersion);
                });
    }
}
