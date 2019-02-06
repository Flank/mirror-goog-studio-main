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

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

public class ExternalNativeBuildTaskUtilsTest {
    static final String CMAKE_FILE_NAME =
            SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS ? "cmake.exe" : "cmake";
    File sdkFolder;

    static final Consumer<String> NOOP_DOWNLOADER = (v) -> {};

    @Before
    public void setUp() {
        sdkFolder = TestUtils.getSdk();
    }

    @Test
    public void testCMakeFolderFromSdkHandler() {
        File expectedCmakeFolder = new File(sdkFolder, "cmake");

        File actualReturn =
                ExternalNativeBuildTaskUtils.doFindCmakeExecutableFolder(
                        null, expectedCmakeFolder, sdkFolder, NOOP_DOWNLOADER, null);
        assertThat(actualReturn).isEqualTo(expectedCmakeFolder);
    }

    @Test
    public void testCMakeFolderFromFoldersToSearch() {
        // Go through all the cmake versions available in the test Sdk folder and get their
        // versions.
        for (File file : FileUtils.getAllFiles(new File(sdkFolder, "cmake"))) {
            if (!file.getName().equals(CMAKE_FILE_NAME) || !file.canExecute()) {
                continue;
            }
            File cmakeBinFolder = new File(file.getParent());
            File expectedCmakeFolder = new File(cmakeBinFolder.getParent());

            List<File> fileListToSearch = new ArrayList<>();
            fileListToSearch.add(new File(cmakeBinFolder.getAbsolutePath()));

            File actualReturn =
                    ExternalNativeBuildTaskUtils.doFindCmakeExecutableFolder(
                            getInstalledCmakeVersion(cmakeBinFolder),
                            null,
                            sdkFolder,
                            NOOP_DOWNLOADER,
                            fileListToSearch);
            assertThat(actualReturn).isEqualTo(expectedCmakeFolder);
        }
    }

    /** Returns the CMake version for the CMake in the given folder.. */
    private String getInstalledCmakeVersion(@Nullable File cmakeBinFile) {
        try {
            return CmakeUtils.getVersion(cmakeBinFile).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
