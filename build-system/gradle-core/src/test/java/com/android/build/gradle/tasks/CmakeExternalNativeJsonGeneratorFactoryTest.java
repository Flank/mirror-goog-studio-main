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

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.repository.Revision;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class CmakeExternalNativeJsonGeneratorFactoryTest {
    File sdkDirectory;
    NdkHandler ndkHandler;
    int minSdkVersion;
    String variantName;
    Collection<Abi> abis;
    AndroidBuilder androidBuilder;
    File sdkFolder;
    File ndkFolder;
    File soFolder;
    File objFolder;
    File jsonFolder;
    File makeFile;
    boolean debuggable;
    List<String> buildArguments;
    List<String> cFlags;
    List<String> cppFlags;
    List<File> nativeBuildConfigurationsJsons;

    @Before
    public void setUp() throws Exception {
        sdkDirectory = Mockito.mock(File.class);
        ndkHandler = Mockito.mock(NdkHandler.class);
        minSdkVersion = 123;
        variantName = "dummy variant name";
        abis = Mockito.mock(Collection.class);
        androidBuilder = Mockito.mock(AndroidBuilder.class);
        sdkFolder = Mockito.mock(File.class);
        ndkFolder = Mockito.mock(File.class);
        soFolder = Mockito.mock(File.class);
        objFolder = Mockito.mock(File.class);
        jsonFolder = Mockito.mock(File.class);
        makeFile = Mockito.mock(File.class);
        debuggable = true;
        buildArguments = Mockito.mock(List.class);
        cFlags = Mockito.mock(List.class);
        cppFlags = Mockito.mock(List.class);
        nativeBuildConfigurationsJsons = Mockito.mock(List.class);
    }

    @Test
    public void testCmakeStrategy() {
        Revision revision = Revision.parseRevision("3.6.0-rc2", Revision.Precision.MICRO);
        assertThat(getCmakeStrategy(revision))
                .isInstanceOf(CmakeAndroidNinjaExternalNativeJsonGenerator.class);

        assertThat(getCmakeStrategy(new Revision(3, 7)))
                .isInstanceOf(CmakeServerExternalNativeJsonGenerator.class);
        assertThat(getCmakeStrategy(new Revision(3, 8)))
                .isInstanceOf(CmakeServerExternalNativeJsonGenerator.class);
        assertThat(getCmakeStrategy(new Revision(3, 9)))
                .isInstanceOf(CmakeServerExternalNativeJsonGenerator.class);
        assertThat(getCmakeStrategy(new Revision(4, 0)))
                .isInstanceOf(CmakeServerExternalNativeJsonGenerator.class);
        assertThat(getCmakeStrategy(new Revision(5, 6)))
                .isInstanceOf(CmakeServerExternalNativeJsonGenerator.class);
    }

    @Test(expected = RuntimeException.class)
    public void testCmakeStrategyUnsupportedCmakeVersion() {
        assertThat(getCmakeStrategy(new Revision(0, 0))).isNull();
        assertThat(getCmakeStrategy(new Revision(1, 1))).isNull();
        assertThat(getCmakeStrategy(new Revision(2, 9))).isNull();
        assertThat(getCmakeStrategy(new Revision(3, 0))).isNull();
        assertThat(getCmakeStrategy(new Revision(3, 6))).isNull();
        assertThat(getCmakeStrategy(new Revision(3, 6, 2))).isNull();
    }

    private ExternalNativeJsonGenerator getCmakeStrategy(@NonNull Revision cmakeRevision) {
        return CmakeExternalNativeJsonGeneratorFactory.createCmakeStrategy(
                cmakeRevision,
                ndkHandler,
                minSdkVersion,
                variantName,
                abis,
                androidBuilder,
                sdkFolder,
                ndkFolder,
                soFolder,
                objFolder,
                jsonFolder,
                makeFile,
                debuggable,
                buildArguments,
                cFlags,
                cppFlags,
                nativeBuildConfigurationsJsons);
    }
}
