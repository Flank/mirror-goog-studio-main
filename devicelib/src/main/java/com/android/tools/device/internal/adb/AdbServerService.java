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

import com.android.annotations.NonNull;
import com.android.tools.device.internal.ScopedThreadNameRunnable;
import com.android.tools.device.internal.adb.commands.AdbCommand;
import com.android.tools.device.internal.adb.commands.KillServer;
import com.google.common.util.concurrent.AbstractService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class AdbServerService extends AbstractService {
    private static final Logger LOG = Logger.getLogger(AdbServerService.class.getName());

    private final AdbServerOptions options;
    private final ExecutorService executorService;
    private final Launcher launcher;
    private final Probe probe;

    private volatile Endpoint endpoint;
    private volatile boolean terminateServerOnStop;

    public AdbServerService(
            @NonNull AdbServerOptions options,
            @NonNull Launcher launcher,
            @NonNull Probe probe,
            @NonNull ExecutorService executorService) {
        this.options = options;
        this.launcher = launcher;
        this.probe = probe;
        this.executorService = executorService;
    }

    @Override
    protected void doStart() {
        executorService.submit(
                ScopedThreadNameRunnable.wrap(this::startServerIfNeeded, "starting-adb-service"));
    }

    @Override
    protected void doStop() {
        executorService.submit(this::stopServerIfNeeded);
    }

    @NonNull
    public <T> CompletableFuture<T> execute(@NonNull AdbCommand<T> command) {
        CompletableFuture<T> cf = new CompletableFuture<>();

        State state = state();
        if (state != State.RUNNING) {
            String msg =
                    String.format(
                            Locale.US,
                            "%1$s cannot accept commands while in %2$s state.",
                            getClass().getSimpleName(),
                            state);
            cf.completeExceptionally(new IllegalStateException(msg));
            return cf;
        }

        executorService.submit(
                ScopedThreadNameRunnable.wrap(
                        () -> {
                            LOG.fine("Executing " + command.getName());
                            try {
                                cf.complete(doExecute(command));
                            } catch (Exception e) {
                                String msg =
                                        String.format(
                                                Locale.US,
                                                "Exception while executing %1$s: %2$s",
                                                command.getName(),
                                                e.toString());
                                LOG.fine(msg);
                                cf.completeExceptionally(e);
                            } finally {
                                LOG.fine("Completed executing " + command.getName());
                            }
                        },
                        command.getName()));

        return cf;
    }

    private <T> T doExecute(@NonNull AdbCommand<T> command) throws IOException {
        try (Connection connection = endpoint.newConnection()) {
            return command.execute(connection);
        }
    }

    private void startServerIfNeeded() {
        try {
            TimeUnit unit = TimeUnit.MILLISECONDS;

            LOG.info("Probing for existing adb server at port " + options.getPort());
            InetSocketAddress address =
                    new InetSocketAddress(
                            InetAddress.getByName(options.getHostName()), options.getPort());
            endpoint = probe.probe(address, options.getProbeTimeout(unit), unit);
            if (endpoint == null) {
                LOG.info("No existing server detected, launching adb server");
                endpoint =
                        launcher.launch(
                                options.getPort(),
                                options.shouldUseLibUsb(),
                                options.getStartTimeout(unit),
                                unit);
                terminateServerOnStop = true;
            } else {
                LOG.info("Server already running");
            }

            // TODO start device list monitor task
            // TODO start heartbeat task
            // TODO wait for a response from the device list monitor task

            // finally mark the service as having started
            LOG.info("Started AdbServerService");
            notifyStarted();
        } catch (Throwable t) {
            notifyFailed(t);
        }
    }

    private void stopServerIfNeeded() {
        if (terminateServerOnStop) {
            try {
                doExecute(new KillServer());
            } catch (Exception e) {
                notifyFailed(e);
                LOG.log(Level.INFO, "Terminating " + getClass().getSimpleName() + " failed.", e);
                return;
            }
        }

        notifyStopped();
        LOG.info(getClass().getSimpleName() + " stopped.");
    }
}
