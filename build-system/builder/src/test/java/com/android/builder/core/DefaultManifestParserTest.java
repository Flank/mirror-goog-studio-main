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

import com.android.builder.model.ApiVersion;
import com.android.testutils.TestResources;
import java.io.File;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DefaultManifestParserTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    private BooleanSupplier canParseManifest = () -> true;

    private DefaultManifestParser defaultManifestParser;

    @Before
    public void before() throws Exception {
        File manifestFile = TestResources.getFile("/testData/core/AndroidManifest.xml");
        defaultManifestParser = new DefaultManifestParser(manifestFile, canParseManifest);
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
    public void getVersionName() {
        String versionName = defaultManifestParser.getVersionName();
        assertThat(versionName).isEqualTo("1.0");
    }

    @Test
    public void getVersionCode() {
        int versionCode = defaultManifestParser.getVersionCode();
        assertThat(versionCode).isEqualTo(1);
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
}
