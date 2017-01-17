/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(BazelIntegrationTestSuiteRunner.class)
public class BazelIntegrationTestsSuite {

    public static final Path OFFLINE_REPO = Paths.get("offlineRepo").toAbsolutePath();
    public static final Path PREBUILTS_REPO = Paths.get("prebuiltsRepo").toAbsolutePath();
    public static final Path NDK_IN_TMP =
            Paths.get(System.getenv("TEST_TMPDIR")).resolve("ndk").toAbsolutePath();

    @BeforeClass
    public static void unzipOfflineRepo() throws Exception {
        if (System.getenv(GradleTestProject.ENV_CUSTOM_REPO) != null) {
            // We're running under Gradle.
            return;
        }

        unzip(OFFLINE_REPO, "tools/base/bazel/offline_repo_repo.zip");
        unzip(PREBUILTS_REPO, "tools/base/build-system/integration-test/prebuilts_repo_repo.zip");
    }

    /**
     * Copies the NDK to a temporary directory.
     *
     * <p>This is a workaround for the fact that Ninja (used by some native code projects) doesn't
     * support paths longer than 32 segments. When running under Bazel, the temporary directory is a
     * couple of levels higher in the file system than "runfiles".
     */
    @BeforeClass
    public static void copyNdkToTmp() throws Exception {
        assertThat(NDK_IN_TMP).doesNotExist();
        FileUtils.copyDirectory(
                new File(GradleTestProject.ANDROID_HOME, SdkConstants.FD_NDK), NDK_IN_TMP.toFile());
    }

    private static void unzip(@NonNull Path repoPath, @NonNull String zipName) throws IOException {
        File offlineRepoZip = TestUtils.getWorkspaceFile(zipName);
        Files.createDirectory(repoPath);

        InstallerUtil.unzip(
                offlineRepoZip,
                repoPath.toFile(),
                FileOpUtils.create(),
                offlineRepoZip.length(),
                new FakeProgressIndicator());
    }
}
