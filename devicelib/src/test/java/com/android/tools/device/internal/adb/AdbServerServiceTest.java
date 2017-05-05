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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.device.internal.adb.commands.AdbCommand;
import com.android.tools.device.internal.adb.commands.ServerVersion;
import com.google.common.base.Charsets;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.util.concurrent.Service;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

public class AdbServerServiceTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    /**
     * Each test can take a maximum of this time to complete before it times out. The value is set
     * to be high enough such that the tests succeed on CI servers.
     */
    @Rule public Timeout testTimeout = new Timeout(5, TimeUnit.SECONDS);

    private AdbServerOptions options;
    @Mock private Launcher launcher;
    @Mock private Probe probe;
    @Mock private Endpoint endpoint;
    @Mock private Connection connection;
    @Mock private AdbCommand<?> command;
    private VirtualTimeScheduler virtualScheduler;

    private AdbServerService adbService;

    @BeforeClass
    public static void setupLoggers() {
        Logger logger = Logger.getLogger(AdbServerService.class.getName());

        // disable the default console logger as some of our tests check for exceptional conditions
        // and we don't want logs on the console in such cases..
        logger.setUseParentHandlers(false);
    }

    @Before
    public void setUp() throws IOException {
        virtualScheduler = new VirtualTimeScheduler();
        // we can use a smaller amount than in production code in tests since they are a more
        // controlled environment
        int probeTimeoutMs = 50;
        options =
                new AdbServerOptions(
                        AdbConstants.ANY_PORT, AdbConstants.DEFAULT_HOST, probeTimeoutMs);
    }

    @After
    public void tearDown() {
        virtualScheduler.shutdownNow();
    }

    // Tests that the service lifecycle performs the expected operations when connecting to an
    // already running adb server
    @Test
    public void lifecycle_existingServer()
            throws TimeoutException, IOException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(endpoint);
        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);

        // start the server
        adbService.startAsync();
        assertThat(adbService.state()).isEqualTo(Service.State.STARTING);
        virtualScheduler.advanceBy(1);

        // verify interactions during launch
        assertThat(adbService.state()).isEqualTo(Service.State.RUNNING);
        verify(launcher, never()).launch(anyInt(), anyLong(), any());

        // now attempt to stop
        adbService.stopAsync();
        assertThat(adbService.state()).isEqualTo(Service.State.STOPPING);
        virtualScheduler.advanceBy(1);

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
        when(endpoint.newConnection()).thenReturn(connection);
        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);

        // start the server
        adbService.startAsync();
        virtualScheduler.advanceBy(1);

        // verify interactions during launch
        assertThat(adbService.state()).isEqualTo(Service.State.RUNNING);
        verify(launcher, times(1))
                .launch(
                        eq(options.getPort()),
                        eq(options.getStartTimeout(TimeUnit.MILLISECONDS)),
                        eq(TimeUnit.MILLISECONDS));

        // now attempt to stop
        adbService.stopAsync();
        virtualScheduler.advanceBy(1);

        // verify interactions during termination
        assertThat(adbService.state()).isEqualTo(Service.State.TERMINATED);
        verify(connection, times(1))
                .issueCommand(
                        argThat(
                                cb ->
                                        new String(cb.toByteArray(), Charsets.UTF_8)
                                                .contains("host:kill")));
    }

    @Test
    public void start_launcherError() throws IOException, TimeoutException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(null);
        when(launcher.launch(anyInt(), anyLong(), any())).thenThrow(IOException.class);
        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);

        try {
            adbService.startAsync();
            virtualScheduler.advanceBy(1);
            adbService.awaitRunning();
            fail("Shouldn't have been able to start the server if the launcher threw an error");
        } catch (IllegalStateException ignored) {
            assertThat(adbService.state()).isEqualTo(Service.State.FAILED);
        }
    }

    @Test
    public void stop_error() throws IOException, TimeoutException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(null);
        when(launcher.launch(anyInt(), anyLong(), any())).thenReturn(endpoint);
        when(endpoint.newConnection()).thenReturn(connection);
        doThrow(new IOException()).when(connection).issueCommand(any());

        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);

        adbService.startAsync();
        virtualScheduler.advanceBy(1);
        assertThat(adbService.state()).isEqualTo(Service.State.RUNNING);

        try {
            adbService.stopAsync();
            virtualScheduler.advanceBy(1);
            adbService.awaitTerminated();
            fail("Shouldn't have been able to terminate if we couldn't issue the kill command");
        } catch (IllegalStateException e) {
            assertThat(adbService.state()).isEqualTo(Service.State.FAILED);
        }
    }

    // should not be able to execute commands when the service is not in running state
    @Test
    public void execute_whenNotRunning()
            throws IOException, InterruptedException, TimeoutException {
        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);

        CompletableFuture<UnsignedInteger> future = adbService.execute(new ServerVersion());
        try {
            future.get();
            fail("Shouldn't be able to execute a command when service is not running");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    public void execute_commandThrowsException()
            throws IOException, TimeoutException, InterruptedException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(endpoint);
        when(endpoint.newConnection()).thenReturn(connection);
        when(command.getName()).thenReturn("mock");
        when(command.execute(connection)).thenThrow(new NullPointerException("foo"));

        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);
        adbService.startAsync();
        virtualScheduler.advanceBy(1);

        CompletableFuture<?> cf = adbService.execute(command);
        virtualScheduler.advanceBy(1);

        try {
            cf.get();
            fail(
                    "Should not have been able to obtain the result of a command that threw an exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(NullPointerException.class);
            assertThat(e.getCause().getMessage()).isEqualTo("foo");
        }
    }

    @Test
    public void execute_nominal()
            throws IOException, InterruptedException, TimeoutException, ExecutionException {
        when(probe.probe(any(), anyLong(), any())).thenReturn(endpoint);
        when(endpoint.newConnection()).thenReturn(connection);
        when(command.getName()).thenReturn("mock");

        adbService = new AdbServerService(options, launcher, probe, virtualScheduler);
        adbService.startAsync();
        virtualScheduler.advanceBy(1);

        CompletableFuture<?> cf = adbService.execute(command);
        virtualScheduler.advanceBy(1);
        cf.get();

        // verify that the execute method of the command was invoked
        verify(command, times(1)).execute(connection);
    }
}
