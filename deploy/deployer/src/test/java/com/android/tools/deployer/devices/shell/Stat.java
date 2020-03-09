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
import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;

public class Stat extends ShellCommand {
    @Override
    public String getExecutable() {
        return "stat";
    }

    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        try {
            return run(context.getDevice(), new Arguments(args), stdin, stdout);
        } catch (IllegalArgumentException e) {
            stdout.println(e.getMessage());
            return 1;
        }
    }

    private int run(FakeDevice device, Arguments args, InputStream stdin, PrintStream stdout)
            throws IOException {
        String option = args.nextOption();
        if (option == null) {
            int ret = 0;
            for (String arg = args.nextArgument(); arg != null; arg = args.nextArgument()) {
                if (device.hasFile(arg)) {
                    stdout.printf("1234 1234 ... %s\n", arg);
                } else {
                    stdout.printf("stat: %s: No such file or directory\n", arg);
                    ret = 1;
                }
            }
            return ret;
        } else if ("-c".equals(option)) {
            String cArg = args.nextArgument();
            if (cArg.length() != 2 || !cArg.startsWith("%")) {
                return printUsage(stdout);
            }

            if (!cArg.startsWith("%u")) {
                stdout.println(
                        "stat: Mandatory %u file escape sequence not present (needs implementation)");
                return 1;
            }

            String file = args.nextArgument();
            if (file == null) {
                return printUsage(stdout);
            }

            // Hack to support /proc/*
            if (file.matches("/proc/\\d+/?")) {
                byte[] contents =
                        Files.readAllBytes(
                                new File(device.getStorage().getPath() + file)
                                        .toPath()
                                        .resolve(".uid"));
                stdout.println(new String(contents, Charsets.UTF_8));
                return 0;
            }

            stdout.println("stat: Unhandled file (needs implementation)");
            return 0;
        }

        return printUsage(stdout);
    }

    private static int printUsage(PrintStream stdout) {
        stdout.println("stat: Needs 1 argument (see \"stat --help\")");
        return 1;
    }
}
