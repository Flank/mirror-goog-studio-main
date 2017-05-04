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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.scope.SplitFactory;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.DefaultApiVersion;
import com.android.ide.common.build.ApkData;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link CompatibleScreensManifest} class */
public class CompatibleScreensManifestTest {
    @Rule public TemporaryFolder projectFolder = new TemporaryFolder();
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock VariantScope scope;
    @Mock SplitScope splitScope;
    @Mock GradleVariantConfiguration variantConfiguration;
    @Mock ProductFlavor productFlavor;

    CompatibleScreensManifest task;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(scope.getFullVariantName()).thenReturn("fullVariantName");
        when(scope.getVariantConfiguration()).thenReturn(variantConfiguration);
        when(scope.getSplitScope()).thenReturn(splitScope);
        when(productFlavor.getMinSdkVersion()).thenReturn(new DefaultApiVersion(21, null));
        when(scope.getCompatibleScreensManifestDirectory()).thenReturn(temporaryFolder.getRoot());
        when(variantConfiguration.getMergedFlavor()).thenReturn(productFlavor);
        when(variantConfiguration.getBaseName()).thenReturn("baseName");
        when(variantConfiguration.getFullName()).thenReturn("fullName");

        File testDir = projectFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();

        task = project.getTasks().create("test", CompatibleScreensManifest.class);
    }

    @Test
    public void testConfigAction() throws IOException, ParserConfigurationException {

        CompatibleScreensManifest.ConfigAction configAction =
                new CompatibleScreensManifest.ConfigAction(
                        scope, ImmutableSet.of("xxhpi", "xxxhdpi"));

        configAction.execute(task);

        assertThat(task.getVariantName()).isEqualTo("fullVariantName");
        assertThat(task.getName()).isEqualTo("test");
        assertThat(task.getMinSdkVersion()).isEqualTo("21");
        assertThat(task.getScreenSizes()).containsExactly("xxhpi", "xxxhdpi");
        assertThat(task.getSplits()).isEmpty();
        assertThat(task.getOutputFolder()).isEqualTo(temporaryFolder.getRoot());
    }

    @Test
    public void testNoSplit() throws IOException {

        SplitFactory splitFactory = new SplitFactory(variantConfiguration, splitScope);
        ApkData mainApk = splitFactory.addMainApk();
        when(splitScope.getApkDatas()).thenReturn(ImmutableList.of(mainApk));

        task.setVariantName("variant");
        task.setOutputFolder(temporaryFolder.getRoot());
        task.setMinSdkVersion(() -> "22");
        task.setScreenSizes(ImmutableSet.of("mdpi", "xhdpi"));

        task.generate(mainApk);

        assertThat(temporaryFolder.getRoot().listFiles()).isEmpty();
    }

    @Test
    public void testSingleSplitWithMinSdkVersion() throws IOException {

        SplitFactory splitFactory = new SplitFactory(variantConfiguration, splitScope);
        ApkData splitApk =
                splitFactory.addFullSplit(
                        ImmutableList.of(Pair.of(VariantOutput.FilterType.DENSITY, "xhdpi")));
        when(splitScope.getApkDatas()).thenReturn(ImmutableList.of(splitApk));

        task.setVariantName("variant");
        task.setOutputFolder(temporaryFolder.getRoot());
        task.setMinSdkVersion(() -> "22");
        task.setScreenSizes(ImmutableSet.of("xhdpi"));

        task.generate(splitApk);

        String xml =
                Joiner.on("\n")
                        .join(
                                Files.readAllLines(
                                        findManifest(temporaryFolder.getRoot(), "xhdpi").toPath()));
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"22\"/>");
        assertThat(xml).contains("<compatible-screens>");
        assertThat(xml)
                .contains(
                        "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />");
    }

    @Test
    public void testSingleSplitWithoutMinSdkVersion() throws IOException {

        SplitFactory splitFactory = new SplitFactory(variantConfiguration, splitScope);
        ApkData splitApk =
                splitFactory.addFullSplit(
                        ImmutableList.of(Pair.of(VariantOutput.FilterType.DENSITY, "xhdpi")));
        when(splitScope.getApkDatas()).thenReturn(ImmutableList.of(splitApk));

        task.setVariantName("variant");
        task.setOutputFolder(temporaryFolder.getRoot());
        task.setMinSdkVersion(() -> null);
        task.setScreenSizes(ImmutableSet.of("xhdpi"));

        task.generate(splitApk);

        String xml =
                Joiner.on("\n")
                        .join(
                                Files.readAllLines(
                                        findManifest(temporaryFolder.getRoot(), "xhdpi").toPath()));
        assertThat(xml).doesNotContain("<uses-sdk");
    }

    @Test
    public void testMultipleSplitsWithMinSdkVersion() throws IOException {

        SplitFactory splitFactory = new SplitFactory(variantConfiguration, splitScope);
        ApkData xhdpiSplit =
                splitFactory.addFullSplit(
                        ImmutableList.of(Pair.of(VariantOutput.FilterType.DENSITY, "xhdpi")));
        ApkData xxhdpiSplit =
                splitFactory.addFullSplit(
                        ImmutableList.of(Pair.of(VariantOutput.FilterType.DENSITY, "xxhdpi")));
        when(splitScope.getApkDatas()).thenReturn(ImmutableList.of(xhdpiSplit, xxhdpiSplit));

        task.setVariantName("variant");
        task.setOutputFolder(temporaryFolder.getRoot());
        task.setMinSdkVersion(() -> "23");
        task.setScreenSizes(ImmutableSet.of("xhdpi", "xxhdpi"));

        task.generate(xhdpiSplit);
        task.generate(xxhdpiSplit);

        String xml =
                Joiner.on("\n")
                        .join(
                                Files.readAllLines(
                                        findManifest(temporaryFolder.getRoot(), "xhdpi").toPath()));
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>");
        assertThat(xml).contains("<compatible-screens>");
        assertThat(xml)
                .contains(
                        "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />");

        xml =
                Joiner.on("\n")
                        .join(
                                Files.readAllLines(
                                        findManifest(temporaryFolder.getRoot(), "xxhdpi")
                                                .toPath()));
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>");
        assertThat(xml).contains("<compatible-screens>");
        assertThat(xml)
                .contains("<screen android:screenSize=\"xxhdpi\" android:screenDensity=\"480\" />");
    }

    private static File findManifest(File taskOutputDir, String splitName) {
        File splitDir = new File(taskOutputDir, splitName);
        assertThat(splitDir.exists()).isTrue();
        File manifestFile = new File(splitDir, SdkConstants.ANDROID_MANIFEST_XML);
        assertThat(manifestFile.exists()).isTrue();
        return manifestFile;
    }
}
