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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Service;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public class AdbServerServiceTest {
    private static final int PROBE_TIMEOUT_MS = 50;
    private static final int START_TIMEOUT_MS = 200;

    @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    private AdbServerOptions options;
    @Mock private Launcher launcher;
    @Mock private Probe probe;
    @Mock private Endpoint endpoint;
    private ExecutorService executorService;

    private AdbServerService adbService;

    @BeforeClass
    public static void setupLoggers() {
        Logger logger = Logger.getLogger(AdbServerService.class.getName());

        // disable the default console logger as some of our tests check for exceptional conditions
        // and we don't want logs on the console in such cases..
        logger.setUseParentHandlers(false);
    }

    @Before
    public void setUp() {
        executorService = Executors.newCachedThreadPool();
        options = new AdbServerOptions(9999, null, PROBE_TIMEOUT_MS, START_TIMEOUT_MS);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    // Tests that the service lifecycle performs the expected operations when connecting to an
    // already running adb server
    @Test
    public void lifecycle_existingServer()
            throws TimeoutException, IOException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(endpoint);
        adbService = new AdbServerService(options, launcher, probe, executorService);

        // start the server
        adbService.startAsync().awaitRunning(START_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // verify interactions during launch
        assertThat(adbService.state()).isEqualTo(Service.State.RUNNING);
        verify(launcher, never()).launch(anyInt(), anyLong(), any());

        // now attempt to stop
        adbService.stopAsync().awaitTerminated(1, TimeUnit.MILLISECONDS);

        // verify interactions during termination
        assertThat(adbService.state()).isEqualTo(Service.State.TERMINATED);
        verifyZeroInteractions(endpoint); // shouldn't have attempted to kill the server
    }

    // Tests that the service lifecycle performs the expected operations when it has to launch
    // a new server
    @Test
    public void lifecycle_newServer() throws TimeoutException, IOException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(null);
        when(launcher.launch(anyInt(), anyLong(), any())).thenReturn(endpoint);
        adbService = new AdbServerService(options, launcher, probe, executorService);

        // start the server
        adbService.startAsync().awaitRunning(START_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // verify interactions during launch
        assertThat(adbService.state()).isEqualTo(Service.State.RUNNING);
        verify(launcher, times(1))
                .launch(
                        eq(options.getPort()),
                        eq(options.getStartTimeout(TimeUnit.MILLISECONDS)),
                        eq(TimeUnit.MILLISECONDS));

        // now attempt to stop
        adbService.stopAsync().awaitTerminated(10, TimeUnit.MILLISECONDS);

        // verify interactions during termination
        assertThat(adbService.state()).isEqualTo(Service.State.TERMINATED);
        // TODO: verify that kill server was invoked
    }

    @Test
    public void start_launcherError() throws IOException, TimeoutException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(null);
        when(launcher.launch(anyInt(), anyLong(), any())).thenThrow(IOException.class);
        adbService = new AdbServerService(options, launcher, probe, executorService);

        try {
            adbService.startAsync().awaitRunning(START_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            fail("Shouldn't have been able to start the server if the launcher threw an error");
        } catch (IllegalStateException ignored) {
            assertThat(adbService.state()).isEqualTo(Service.State.FAILED);
        }
    }
}
