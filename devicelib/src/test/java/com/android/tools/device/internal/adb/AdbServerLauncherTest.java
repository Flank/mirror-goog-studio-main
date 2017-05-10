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

package com.android.tools.device.internal.adb;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.device.internal.ProcessRunner;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public class AdbServerLauncherTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    private AdbServerLauncher launcher;
    @Mock private ProcessRunner runner;
    private MockProcess process;

    @Before
    public void setUp() {
        launcher = new AdbServerLauncher(AdbTestUtils.getPathToAdb(), runner);
        process = new MockProcess(0);
    }

    @Test
    public void launch_arguments() throws InterruptedException, TimeoutException, IOException {
        when(runner.start(any())).thenReturn(process);
        when(runner.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);

        launcher.launch(1234, false, 10, TimeUnit.SECONDS);
        ImmutableList<String> expectedArgs =
                ImmutableList.of(
                        AdbTestUtils.getPathToAdb().toString(),
                        "-P",
                        Integer.toString(1234),
                        "start-server");
        verify(runner)
                .start(
                        argThat(
                                pb ->
                                        pb.command().equals(expectedArgs)
                                                && pb.environment().get("ADB_LIBUSB").equals("0")));
    }

    @Test
    public void launch_withLibUsb() throws Exception {
        when(runner.start(any())).thenReturn(process);
        when(runner.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        launcher.launch(1234, true, 10, TimeUnit.SECONDS);
        verify(runner).start(argThat(pb -> pb.environment().get("ADB_LIBUSB").equals("1")));
    }

    @Test
    public void launch_commandTakesTooLong() throws IOException, InterruptedException {
        when(runner.start(any())).thenReturn(process);
        when(runner.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(runner.getStdout()).thenReturn("");
        when(runner.getStderr()).thenReturn("can't start adb");

        try {
            launcher.launch(1234, false, 2, TimeUnit.SECONDS);
            fail("Expected to timeout if runner.waitFor fails");
        } catch (TimeoutException e) {
            String msg =
                    String.format(
                            "Timed out (2 seconds) starting adb server [%1$s -P 1234 start-server]: can't start adb",
                            AdbTestUtils.getPathToAdb().toString());
            assertThat(e.getMessage()).isEqualTo(msg);
        }
    }

    @Test
    public void launch_negativeExitCode()
            throws IOException, InterruptedException, TimeoutException {
        Process process = new MockProcess(-1);
        when(runner.start(any())).thenReturn(process);
        when(runner.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(runner.getStdout()).thenReturn("");
        when(runner.getStderr()).thenReturn("can't start adb");

        try {
            launcher.launch(1234, false, 1, TimeUnit.MILLISECONDS);
            fail("Expected an IOException if the process failed to launch");
        } catch (IOException e) {
            String msg =
                    String.format(
                            "Error starting adb server [%1$s -P 1234 start-server]: can't start adb",
                            AdbTestUtils.getPathToAdb().toString());
            assertThat(e.getMessage()).isEqualTo(msg);
        }
    }

    private static class MockProcess extends Process {
        private final int exitValue;

        private MockProcess(int exitValue) {
            this.exitValue = exitValue;
        }

        @Override
        public OutputStream getOutputStream() {
            return null;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[] {});
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[] {});
        }

        @Override
        public int waitFor() throws InterruptedException {
            return 0;
        }

        @Override
        public int exitValue() {
            return exitValue;
        }

        @Override
        public void destroy() {}
    }
}
