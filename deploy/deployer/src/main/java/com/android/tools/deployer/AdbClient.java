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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdbClient {
    private IDevice device;

    public AdbClient(IDevice device) {
        this.device = device;
    }

    public void shell(String[] parameters) throws DeployerException {
        try {
            device.executeShellCommand(String.join(" ", parameters), new NullOutputReceiver());
        } catch (Exception e) {
            throw new DeployerException("Unable to run shell command.", e);
        }
    }

    /**
     * Executes the given command and sends {@code input} to stdin and returns stdout as a byte[]
     */
    public byte[] shell(String[] parameters, byte[] input) throws DeployerException {
        try {
            ByteArrayOutputReceiver receiver = new ByteArrayOutputReceiver();
            device.executeShellCommand(
                    String.join(" ", parameters),
                    receiver,
                    DdmPreferences.getTimeOut(),
                    TimeUnit.MILLISECONDS,
                    input == null ? null : new ByteArrayInputStream(input));
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException
                | TimeoutException e) {
            throw new DeployerException("Unable to run shell command.", e);
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
