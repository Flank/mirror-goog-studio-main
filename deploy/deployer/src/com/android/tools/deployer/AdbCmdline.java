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

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class AdbCmdline implements AdbClient {

    // TODO: Bazel strips PATH environment variable and option --action_env=... does not seem to
    // work. Using ANDROID_NDK_HOME instead for now.
    private static final String ADB_EXECUTABLE = "../platform-tools/adb";
    private static final String ENV_PATH = "ANDROID_NDK_HOME";

    public AdbCmdline() {}

    private String getADBPath() throws DeployerException {
        String[] prospects = System.getenv(ENV_PATH).split(Pattern.quote(File.pathSeparator));
        for (String path : prospects) {
            Path p = FileSystems.getDefault().getPath(path, ADB_EXECUTABLE);
            if (Files.exists(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        throw new DeployerException("Unable to find 'adb' executable.");
    }

    @Override
    public void shell(String[] parameters) throws DeployerException {
        String[] command = new String[parameters.length + 1];
        command[0] = "shell";
        for (int i = 0; i < parameters.length; i++) {
            command[i + 1] = parameters[i];
        }
        run(command);
    }

    @Override
    public void pull(String srcDirectory, String dstDirectory) throws DeployerException {
        String[] parameters = new String[] {"pull", srcDirectory, dstDirectory};
        run(parameters);
    }

    @Override
    public void installMultiple(List<Apk> apks) throws DeployerException {
        String[] parameters = new String[apks.size() + 2];
        parameters[0] = "install-multiple";
        parameters[1] = "-r";
        for (int i = 0; i < apks.size(); i++) {
            parameters[i + 2] = apks.get(i).getPath();
        }
        run(parameters);
    }

    private void run(String[] parameters) throws DeployerException {

        String[] command = new String[parameters.length + 1];
        command[0] = getADBPath();
        for (int i = 0; i < parameters.length; i++) {
            command[i + 1] = parameters[i];
        }
        ShellRunner runner = new ShellRunner(command, true);
        runner.run(line -> {});
    }
}
