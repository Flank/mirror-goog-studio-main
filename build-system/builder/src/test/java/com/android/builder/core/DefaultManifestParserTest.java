/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.core;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.builder.errors.FakeIssueReporter;
import com.android.builder.model.ApiVersion;
import com.android.testutils.TestResources;
import java.io.File;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;

public class DefaultManifestParserTest {
    private BooleanSupplier canParseManifest = () -> true;

    private DefaultManifestParser defaultManifestParser;

    private FakeIssueReporter issueReporter;
    File manifestFile;

    @Before
    public void before() {
        manifestFile = TestResources.getFile("/testData/core/AndroidManifest.xml");
        issueReporter = new FakeIssueReporter();
        defaultManifestParser =
                new DefaultManifestParser(manifestFile, canParseManifest, false, issueReporter);
    }

    @Test
    public void parseManifestReportsWarning() {
        DefaultManifestParser manifestParser =
                new DefaultManifestParser(manifestFile, () -> false, false, issueReporter);
        manifestParser.getPackage();

        assertThat(issueReporter.getWarnings()).hasSize(1);
        assertThat(issueReporter.getWarnings().get(0))
                .startsWith(
                        "The manifest is being parsed during configuration. Please "
                                + "either remove android.disableConfigurationManifestParsing "
                                + "from build.gradle or remove any build configuration rules "
                                + "that read the android manifest file.\n");
    }

    @Test
    public void testMissingManifestFileAllowed() {
        File nonExistentFile = new File("non-existent-file");
        assertThat(nonExistentFile.exists()).isFalse();

        ManifestAttributeSupplier manifestAttributeSupplier =
                new DefaultManifestParser(nonExistentFile, canParseManifest, false, issueReporter);
        assertThat(manifestAttributeSupplier.getPackage()).isNull();
    }

    @Test
    public void testMissingManifestFileDisallowed() {
        File nonExistentFile = new File("non-existent-file");
        assertThat(nonExistentFile.exists()).isFalse();

        ManifestAttributeSupplier manifestAttributeSupplier =
                new DefaultManifestParser(nonExistentFile, canParseManifest, true, issueReporter);
        try {
            manifestAttributeSupplier.getPackage();
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage().contains("Manifest file does not exist")).isTrue();
        }
    }

    @Test
    public void getPackage() {
        String packageName = defaultManifestParser.getPackage();
        assertThat(packageName).isEqualTo("com.android.tests.builder.core");
    }

    @Test
    public void getSplit() {
        String packageName = defaultManifestParser.getSplit();
        assertThat(packageName).isEqualTo("com.android.tests.builder.core.split");
    }

    @Test
    public void getMinSdkVersion() {
        ApiVersion minSdkVersion =
                DefaultApiVersion.create(defaultManifestParser.getMinSdkVersion());
        assertThat(minSdkVersion.getApiLevel()).isEqualTo(21);
    }

    @Test
    public void getTargetSdkVersion() {
        ApiVersion targetSdkVersion =
                DefaultApiVersion.create(defaultManifestParser.getTargetSdkVersion());
        assertThat(targetSdkVersion.getApiLevel()).isEqualTo(25);
    }

    @Test
    public void getInstrumentationRunner() {
        String name = defaultManifestParser.getInstrumentationRunner();
        assertThat(name).isEqualTo("com.android.tests.builder.core.instrumentation.name");
    }

    @Test
    public void getTargetPackage() {
        String target = defaultManifestParser.getTargetPackage();
        assertThat(target).isEqualTo("com.android.tests.builder.core.instrumentation.target");
    }

    @Test
    public void getTestLabel() {
        String label = defaultManifestParser.getTestLabel();
        assertThat(label).isEqualTo("instrumentation_label");
    }

    @Test
    public void getFunctionalTest() {
        Boolean functionalTest = defaultManifestParser.getFunctionalTest();
        assertThat(functionalTest).isEqualTo(true);
    }

    @Test
    public void getHandleProfiling() {
        Boolean handleProfiling = defaultManifestParser.getHandleProfiling();
        assertThat(handleProfiling).isEqualTo(false);
    }

    @Test
    public void getExtractNativeLibs() {
        Boolean extractNativeLibs = defaultManifestParser.getExtractNativeLibs();
        assertThat(extractNativeLibs).isEqualTo(true);
    }

    @Test
    public void getUseEmbeddedDex() {
        Boolean useEmbeddedDex = defaultManifestParser.getUseEmbeddedDex();
        assertThat(useEmbeddedDex).isEqualTo(true);
    }
}
