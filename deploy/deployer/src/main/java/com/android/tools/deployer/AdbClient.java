/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.ddmlib.*;
import com.android.utils.ILogger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdbClient {
    private final IDevice device;
    private final ILogger logger;

    public AdbClient(IDevice device, ILogger logger) {
        this.device = device;
        this.logger = logger;
    }

    /**
     * Executes the given command and sends {@code input} to stdin and returns stdout as a byte[]
     */
    public byte[] shell(String[] parameters, InputStream input) throws DeployerException {
        logger.info("SHELL: " + String.join(" ", parameters));
        ByteArrayOutputReceiver receiver;
        try {
            receiver = new ByteArrayOutputReceiver();
            device.executeShellCommand(
                    String.join(" ", parameters),
                    receiver,
                    DdmPreferences.getTimeOut(),
                    TimeUnit.MILLISECONDS,
                    input);
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException
                | TimeoutException e) {
            throw new DeployerException("Unable to run shell command", e);
        }
    }

    public void pull(String srcDirectory, String dstDirectory) throws DeployerException {
        try {
            // TODO: Move to a protobuf stdout:
            // Pulling a directory is super slow and hacky with ddmlib, which effectively
            // does this:
            List<String> files = new ArrayList<>();
            device.executeShellCommand(
                    "ls -A1 " + srcDirectory,
                    new MultiLineReceiver() {
                        @Override
                        public void processNewLines(@NonNull String[] lines) {
                            for (String line : lines) {
                                if (!line.trim().isEmpty()) {
                                    files.add(line.trim());
                                }
                            }
                        }

                        @Override
                        public boolean isCancelled() {
                            return false;
                        }
                    });
            String dirName = new File(srcDirectory).getName();
            File dstDir = new File(dstDirectory, dirName);
            dstDir.mkdirs();
            for (String file : files) {
                device.pullFile(srcDirectory + "/" + file, dstDir.getPath() + "/" + file);
            }
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException
                | SyncException
                | TimeoutException e) {
            throw new DeployerException("Unable to pull files.", e);
        }
    }

    public void installMultiple(List<ApkFull> apks, boolean kill) throws DeployerException {
        List<File> files = new ArrayList<>();
        for (ApkFull apk : apks) {
            files.add(new File(apk.getPath()));
        }
        try {
            List<String> options = new ArrayList<>();
            options.add("-t");
            options.add("-r");
            if (!kill) {
                options.add("--dont-kill");
            }
            device.installPackages(files, true, options, 10, TimeUnit.SECONDS);
        } catch (InstallException e) {
            throw new DeployerException("Unable to install packages.", e);
        }
    }

    public List<String> getAbis() {
        return device.getAbis();
    }

    public void push(String from, String to) {
        try {
            device.pushFile(from, to);
        } catch (IOException | SyncException | TimeoutException | AdbCommandRejectedException e) {
            throw new DeployerException("Unable to push files", e);
        }
    }

    // TODO: Replace this to void copying the full byte[] incurred when calling stream.toByteArray()
    private class ByteArrayOutputReceiver implements IShellOutputReceiver {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            stream.write(data, offset, length);
        }

        @Override
        public void flush() {}

        @Override
        public boolean isCancelled() {
            return false;
        }

        byte[] toByteArray() {
            return stream.toByteArray();
        }
    }
}
