/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer.devices.shell;

import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Ls extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout) {
        boolean longFormat = false;
        List<String> files = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("-l")) {
                longFormat = true;
            } else {
                files.add(arg);
            }
        }
        for (String file : files) {
            Pattern p = Pattern.compile("/proc/([0-9]+)/exe");
            Matcher matcher = p.matcher(file);
            if (!matcher.matches()) {
                stdout.println("Unsupported arguments");
                return 1;
            }
            int pid = Integer.valueOf(matcher.group(1));
            Optional<FakeDevice.AndroidProcess> process =
                    context.getDevice()
                            .getProcesses()
                            .stream()
                            .filter(x -> x.pid == pid)
                            .findFirst();
            if (!process.isPresent()) {
                String line = "ls: cannot access '%s': No such file or directory";
                stdout.println(String.format(line, file));
                continue;
            }
            if (longFormat) {
                String user = process.get().application.user.name;
                // The exe entry would be pointing to the app process, so we write a line
                // that has the user, the file and treats it as a link.
                String line =
                        "lrwxrwxrwx 1 %s %s 0 2019-06-15 10:35 %s -> /system/bin/app_process";
                stdout.println(String.format(line, user, user, file));
            } else if (process.isPresent()) {
                stdout.println(file);
            }
        }
        return 0;
    }

    @Override
    public String getExecutable() {
        return "ls";
    }
}
