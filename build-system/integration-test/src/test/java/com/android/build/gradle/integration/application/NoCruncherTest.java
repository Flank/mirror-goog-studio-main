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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for the cruncherEnabled settings. */
public class NoCruncherTest {

    @ClassRule
    public static GradleTestProject noPngCrunch =
            GradleTestProject.builder()
                    .withName("noPngCrunch")
                    .fromTestProject("noPngCrunch")
                    .create();

    @Test
    public void testWithAapt() throws Exception {
        checkNotCrunched(false);
    }

    @Test
    public void testWithAapt2() throws Exception {
        checkNotCrunched(true);
    }

    private void checkNotCrunched(boolean enableAapt2) throws Exception {
        noPngCrunch.executor().withEnabledAapt2(enableAapt2).run("clean", "assembleDebug");
        // Check crunchable PNG is not crunched
        checkResource("drawable/icon.png", false);
        checkResource("drawable/lib_bg.9.png", true);
    }

    private void checkResource(@NonNull String fileName, boolean shouldBeProcessed)
            throws IOException {
        Path srcFile = noPngCrunch.file("src/main/res/" + fileName).toPath();
        Path destFile = noPngCrunch.getApk(GradleTestProject.ApkType.DEBUG).getResource(fileName);
        assertThat(srcFile).exists();
        assertThat(destFile).exists();

        if (shouldBeProcessed) {
            assertThat(Files.readAllBytes(destFile)).isNotEqualTo(Files.readAllBytes(srcFile));
        } else {
            assertThat(destFile).hasContents(Files.readAllBytes(srcFile));
        }
    }
}