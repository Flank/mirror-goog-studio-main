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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
        } catch (IOException
                | TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException e) {
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
        } catch (Exception e) {
            throw new DeployerException("Unable to pull files.", e);
        }
    }

    public void installMultiple(List<Apk> apks) throws DeployerException {
        List<File> files = new ArrayList<>();
        for (Apk apk : apks) {
            files.add(new File(apk.getLocalArchive().getPath()));
        }
        try {
            device.installPackages(files, true, Arrays.asList("-t", "-r"), 10, TimeUnit.SECONDS);
        } catch (InstallException e) {
            throw new DeployerException("Unable to install packages.", e);
        }
    }
}
