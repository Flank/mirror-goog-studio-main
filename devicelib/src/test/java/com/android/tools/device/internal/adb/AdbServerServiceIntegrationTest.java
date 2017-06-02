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

import com.android.tools.device.internal.OsProcessRunner;
import com.android.tools.device.internal.adb.commands.ServerVersion;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedInteger;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * This is an integration test for the {@link AdbServerService} class. It ensures that the service
 * works properly with a real adb instance.
 *
 * <p>It does however test an internal API and will likely be removed or migrated to use the public
 * API when it becomes available.
 */
public class AdbServerServiceIntegrationTest {
    private static ConsoleHandler handler;
    private ExecutorService executor;

    // When run under a sandboxed environment, $HOME may not exist. adb however relies on $HOME
    // being present and valid. So we explicitly set $HOME to some temporary folder
    @Rule public TemporaryFolder temporaryHome = new TemporaryFolder();

    /**
     * Sets up the logging system to print out logs at all levels, but in a simplified format. For
     * an integration test, doing this step is entirely optional and the chosen format is entirely
     * subjective.
     */
    @BeforeClass
    public static void setupLogger() {
        Logger logger = Logger.getLogger(AdbServerService.class.getPackage().getName());
        logger.setUseParentHandlers(false);

        handler = new ConsoleHandler();
        handler.setFormatter(
                new Formatter() {
                    @Override
                    public String format(LogRecord record) {
                        StringBuilder sb = new StringBuilder();

                        sb.append(String.format(Locale.US, "%10d", record.getMillis() % 100000))
                                .append(' ')
                                .append(record.getLevel().getName().charAt(0))
                                .append(' ')
                                .append(formatMessage(record))
                                .append('\n');

                        if (record.getThrown() != null) {
                            try (StringWriter sw = new StringWriter();
                                    PrintWriter pw = new PrintWriter(sw)) {
                                record.getThrown().printStackTrace(pw);
                                sb.append(sw.toString());
                            } catch (IOException ignored) {
                            }
                        }

                        return sb.toString();
                    }
                });
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    @AfterClass
    public static void removeLogger() {
        Logger logger = Logger.getLogger(AdbServerService.class.getName());
        logger.removeHandler(handler);
    }

    @Before
    public void setUp() {
        executor = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void launchServer()
            throws InterruptedException, ExecutionException, TimeoutException, IOException {
        AdbServerOptions options =
                new AdbServerOptions(getFreePort(), AdbConstants.DEFAULT_HOST, true);
        Launcher launcher =
                new AdbServerLauncher(
                        AdbTestUtils.getPathToAdb(),
                        new OsProcessRunner(executor),
                        ImmutableMap.of("HOME", temporaryHome.getRoot().getAbsolutePath()));
        AdbServerService service =
                new AdbServerService(options, launcher, new SocketProbe(), executor);

        service.startAsync().awaitRunning();

        CompletableFuture<UnsignedInteger> future = service.execute(new ServerVersion());
        assertThat(future.get()).isGreaterThan(UnsignedInteger.valueOf(30));

        service.stopAsync().awaitTerminated();
    }

    private static int getFreePort() throws IOException {
        // TODO https://code.google.com/p/android/issues/detail?id=221925#c5
        // TODO Two instances of this test may be run in parallel, and we need them to not interact
        // with each other's server instances. Picking a port this way is kludgy because there is
        // no guarantee that the port is still free when it is actually used. Eventually, we should
        // move to the correct fix which is to have the adb server automatically pick up a free
        // port when it starts up.
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }
}
