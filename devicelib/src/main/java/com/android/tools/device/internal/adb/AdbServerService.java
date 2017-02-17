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
import com.android.tools.device.internal.ScopedThreadName;
import com.google.common.util.concurrent.AbstractService;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
                () ->
                        ScopedThreadName.create("starting-adb-service")
                                .run(this::startServerIfNeeded));
    }

    @Override
    protected void doStop() {
        executorService.submit(this::stopServerIfNeeded);
    }

    private void startServerIfNeeded() {
        try {
            TimeUnit unit = TimeUnit.MILLISECONDS;

            LOG.info("Probing for existing adb server");
            InetSocketAddress address =
                    new InetSocketAddress(
                            InetAddress.getByName(options.getHostName()), options.getPort());
            endpoint = probe.probe(address, options.getProbeTimeout(unit), unit);
            if (endpoint == null) {
                LOG.info("No existing server detected, launching adb server");
                endpoint = launcher.launch(options.getPort(), options.getStartTimeout(unit), unit);
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
            // TODO doKillServer();
        }

        notifyStopped();
    }
}
