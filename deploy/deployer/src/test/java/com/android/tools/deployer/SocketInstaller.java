/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.annotations.NonNull;
import com.android.tools.deploy.proto.Deploy;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

public class SocketInstaller extends Installer implements AutoCloseable {

    private AdbInstallerChannel channel;
    private final ILogger logger = new StdLogger(StdLogger.Level.INFO);
    private final Path path;

    public SocketInstaller(Path path) throws IOException {
        this.path = path;
        channel = new AdbInstallerChannel(HostInstaller.spawn(path), logger);
    }


    @Override
    @NonNull
    protected Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) throws IOException {
        if (channel.isClosed()) {
            close();
            channel = new AdbInstallerChannel(HostInstaller.spawn(path), logger);
        }
        try {
            channel.lock();
            channel.writeRequest(request, timeOutMs);
            Deploy.InstallerResponse resp = channel.readResponse(timeOutMs);
            if (resp == null) {
                throw new IOException(
                        "Unable to read response for " + request.getRequestCase().name());
            }
            return resp;
        } catch (TimeoutException e) {
            e.printStackTrace();
        } finally {
            channel.unlock();
        }
        throw new IOException(
                "Unable to complete request '" + request.getRequestCase().name() + "'");
    }

    @Override
    protected void onAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp) {
        try {
            close();
            channel = new AdbInstallerChannel(HostInstaller.spawn(path), logger);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try (AdbInstallerChannel c = channel; ) {
            System.out.println("Closing AdbInstallerChannel");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
