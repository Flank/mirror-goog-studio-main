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
package com.android.tools.device.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.android.testutils.TestResources;
import com.android.tools.device.internal.adb.AdbTestUtils;
import com.android.tools.device.internal.adb.AdbVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class OsProcessRunnerTest {
    @Test
    public void getBaseName_unknown() {
        assertThat(OsProcessRunner.getBaseName(ImmutableList.of())).isEqualTo("unknown");
    }

    @Test
    public void getBaseName_windows() {
        assume().withFailureMessage("Is Windows?").that(isWindows()).isTrue();
        assertThat(OsProcessRunner.getBaseName(ImmutableList.of("C:\\path\\to\\adb.exe")))
                .isEqualTo("adb.exe");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.US).contains("win");
    }

    @Test
    public void getBaseName_unix() {
        assume().withFailureMessage("Is Unix?").that(isWindows()).isFalse();
        assertThat(OsProcessRunner.getBaseName(ImmutableList.of("/path/to/adb"))).isEqualTo("adb");
    }

    @Test
    public void getStdout_isValid() throws IOException, InterruptedException {
        List<String> cmd = Arrays.asList(AdbTestUtils.getPathToAdb().toString(), "version");
        ProcessBuilder pb = new ProcessBuilder(cmd);

        OsProcessRunner runner = new OsProcessRunner();
        runner.start(pb);
        assertThat(runner.waitFor(10, TimeUnit.SECONDS)).isTrue();

        assertThat(runner.getStderr()).isEmpty();
        assertThat(runner.getStdout()).startsWith("Android Debug Bridge");
    }

    @Test
    public void getStderr_isValid() throws IOException, InterruptedException {
        Path pathToAdb = AdbTestUtils.getPathToAdb();
        List<String> cmd = Arrays.asList(pathToAdb.toString(), "invalid-command");
        ProcessBuilder pb = new ProcessBuilder(cmd);

        OsProcessRunner runner = new OsProcessRunner();
        runner.start(pb);
        assertThat(runner.waitFor(10, TimeUnit.SECONDS)).isTrue();

        // 1.0.39+ of adb print the help message on stdout
        AdbVersion adbVersion = AdbVersion.get(pathToAdb);
        if (adbVersion.micro >= 39) {
            assertThat(runner.getStdout()).isNotEmpty();
            assertThat(runner.getStderr()).isEmpty();
        } else {
            assertThat(runner.getStderr()).isNotEmpty();
            assertThat(runner.getStdout()).isEmpty();
        }
    }

    @Test
    public void waitFor_neverEndingProcess()
            throws IOException, InterruptedException, URISyntaxException {
        File script = TestResources.getFile(OsProcessRunnerTest.class, "/process/yes.py");

        // first, lets make sure we can exec the script properly
        ProcessBuilder pb =
                new ProcessBuilder(ImmutableList.of("python", script.getAbsolutePath(), "1"));
        OsProcessRunner runner = new OsProcessRunner();
        runner.start(pb);
        assertThat(runner.waitFor(10, TimeUnit.SECONDS)).isTrue();

        // for this test, we assume that python was available, and we were able to run the script
        assume().that(runner.getStderr()).startsWith("Usage: yes");

        // now we can really do the test where we launch the script that doesn't terminate
        pb = new ProcessBuilder(ImmutableList.of("python", script.getAbsolutePath()));
        runner = new OsProcessRunner();
        runner.start(pb);

        // the process wasn't expected to terminate in a short time
        assertThat(runner.waitFor(1, TimeUnit.SECONDS)).isFalse();
        runner.destroyForcibly();
    }
}
