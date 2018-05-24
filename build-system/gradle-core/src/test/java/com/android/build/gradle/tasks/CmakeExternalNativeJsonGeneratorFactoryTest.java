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
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.build.gradle.internal.cxx.configure.NativeBuildSystemVariantConfig;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.builder.core.AndroidBuilder;
import com.android.repository.Revision;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class CmakeExternalNativeJsonGeneratorFactoryTest {
    File sdkDirectory;
    NdkHandler ndkHandler;
    int minSdkVersion;
    String variantName;
    List<JsonGenerationAbiConfiguration> abis;
    AndroidBuilder androidBuilder;
    File sdkFolder;
    File ndkFolder;
    File soFolder;
    File objFolder;
    File jsonFolder;
    File makeFile;
    File cmakeFolder;
    File ninjaFolder;
    boolean debuggable;
    List<String> buildArguments;
    List<String> cFlags;
    List<String> cppFlags;
    List<File> nativeBuildConfigurationsJsons;
    GradleBuildVariant.Builder stats;

    @Before
    public void setUp() throws Exception {
        sdkDirectory = Mockito.mock(File.class);
        ndkHandler = Mockito.mock(NdkHandler.class);
        minSdkVersion = 123;
        variantName = "dummy variant name";
        abis = Lists.newArrayList();
        for (Abi abi : Abi.values()) {
            abis.add(
                    new JsonGenerationAbiConfiguration(
                            abi,
                            new File("./json"),
                            new File("./obj"),
                            NativeBuildSystem.CMAKE,
                            31));
        }
        androidBuilder = Mockito.mock(AndroidBuilder.class);
        sdkFolder = Mockito.mock(File.class);
        ndkFolder = Mockito.mock(File.class);
        soFolder = Mockito.mock(File.class);
        objFolder = new File("./obj");
        jsonFolder = new File("./json");
        makeFile = Mockito.mock(File.class);
        cmakeFolder = Mockito.mock(File.class);
        ninjaFolder = Mockito.mock(File.class);
        stats = GradleBuildVariant.newBuilder();
        debuggable = true;
        buildArguments = Mockito.mock(List.class);
        cFlags = Mockito.mock(List.class);
        cppFlags = Mockito.mock(List.class);
        nativeBuildConfigurationsJsons = Mockito.mock(List.class);
    }

    @Test
    public void testCmakeStrategy() {
        Mockito.when(androidBuilder.getLogger()).thenReturn(Mockito.mock(ILogger.class));
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
        stats = GradleBuildVariant.newBuilder();
        JsonGenerationVariantConfiguration config =
                new JsonGenerationVariantConfiguration(
                        new NativeBuildSystemVariantConfig(
                                new HashSet<>(), new HashSet<>(), buildArguments, cFlags, cppFlags),
                        variantName,
                        makeFile,
                        sdkFolder,
                        ndkFolder,
                        soFolder,
                        objFolder,
                        jsonFolder,
                        debuggable,
                        abis,
                        nativeBuildConfigurationsJsons);
        ExternalNativeJsonGenerator generator =
                CmakeExternalNativeJsonGeneratorFactory.createCmakeStrategy(
                        config, cmakeRevision, ndkHandler, androidBuilder, cmakeFolder, stats);
        assertThat(stats.getNativeCmakeVersion()).isEqualTo(cmakeRevision.toShortString());
        return generator;
    }
}
