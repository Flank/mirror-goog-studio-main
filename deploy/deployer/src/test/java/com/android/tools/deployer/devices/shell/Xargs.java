/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Xargs extends ShellCommand {
    @Override
    public String getExecutable() {
        return "xargs";
    }

    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        int argLimit = Integer.MAX_VALUE;
        String terminator = "\n";

        Arguments arguments = new Arguments(args);
        for (String option = arguments.nextOption();
                option != null;
                option = arguments.nextOption()) {
            switch (option) {
                case "-0":
                    terminator = "\0";
                    break;
                case "-n":
                    String limitString = arguments.nextArgument();
                    if (limitString == null) {
                        stdout.println("xargs: Missing argument to -n (see \"xargs --help\")");
                        return 1;
                    }
                    try {
                        argLimit = Integer.parseInt(limitString);
                    } catch (NumberFormatException e) {
                        stdout.println("xargs: not integer: " + limitString);
                        return 1;
                    }
                    if (argLimit < 1) {
                        stdout.println("xargs: -n < 1 (see \"xargs --help\")");
                        return 1;
                    }
                    break;
                default:
                    stdout.println(
                            String.format(
                                    "xargs: Unknown option %s (see \"xargs --help\")", option));
                    return 1;
            }
        }

        List<String> command = new ArrayList<>();
        for (String remainder = arguments.nextArgument();
                remainder != null;
                remainder = arguments.nextArgument()) {
            command.add(remainder);
        }

        int stdAvailable = stdin.available();
        byte[] stdBuffer = null;
        if (stdAvailable > 0) {
            stdBuffer = new byte[stdAvailable];
            ByteStreams.readFully(stdin, stdBuffer);
        }

        if (stdBuffer == null) {
            stdout.println();
            return 0;
        }
        List<String> xargs = Arrays.asList(new String(stdBuffer, Charsets.UTF_8).split(terminator));

        for (int i = 0; i < xargs.size(); i++) {
            String commandString =
                    String.join(" ", command)
                            + " "
                            + String.join(
                                    " ", xargs.subList(i, Math.min(i + argLimit, xargs.size())));
            FakeDevice.RunResult result =
                    context.getDevice().executeScript(commandString, new byte[] {});
            stdout.print(new String(result.output, Charsets.UTF_8));
            if (result.value != 0) {
                return 0; // Xargs returns 0 even if the subcommand errors.
            }
        }
        stdout.println(); // Xargs prints a newline at the end.
        return 0;
    }
}
