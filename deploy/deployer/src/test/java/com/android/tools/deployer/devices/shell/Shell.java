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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Shell {

    private final Map<String, ShellCommand> commands;
    private final List<String> history;

    public Shell() {
        this.commands = new HashMap<>();
        this.history = new ArrayList<>();
    }

    public void addCommand(ShellCommand command) {
        commands.put(command.getExecutable(), command);
    }

    public void execute(String command, OutputStream output, InputStream input, FakeDevice device)
            throws IOException {
        // The most basic shell interpreter ever
        history.add(command);
        PrintStream stdout = new PrintStream(output);
        String[] split = command.split(" ", 2);
        String cmd = split[0];
        List<String> cmdArgs = new ArrayList<>();
        if (split.length > 1) {
            String allArguments = split[1];
            cmdArgs = splitArguments(allArguments);
        }

        ShellCommand shellCommand = commands.get(cmd);
        if (shellCommand != null) {
            shellCommand.execute(device, cmdArgs.toArray(new String[] {}), input, stdout);
        } else {
            // Adb does not return an error code, just this:
            stdout.format("/system/bin/sh: %s: not found\n", cmd);
        }
    }

    public static List<String> splitArguments(String allArguments) {
        // Basic bash un-quoting
        StringBuilder arg = new StringBuilder();
        List<String> cmdArgs = new ArrayList<>();
        boolean inQuotes = false;
        for (int i = 0; i < allArguments.length(); i++) {
            char c = allArguments.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ' ' && !inQuotes) {
                if (arg.length() > 0) {
                    cmdArgs.add(arg.toString());
                    arg = new StringBuilder();
                }
                continue;
            }
            arg.append(c);
        }
        if (arg.length() > 0) {
            cmdArgs.add(arg.toString());
        }
        return cmdArgs;
    }

    public List<String> getHistory() {
        return history;
    }
}
