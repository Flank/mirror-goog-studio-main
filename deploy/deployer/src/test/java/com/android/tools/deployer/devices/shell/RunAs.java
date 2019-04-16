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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;

public class RunAs extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        FakeDevice device = context.getDevice();
        if (args.length == 0) {
            stdout.println(
                    "run-as: usage: run-as <package-name> [--user <uid>] <command> [<args>]");
            return 1;
        } else {
            String pkg = args[0];

            FakeDevice.Application app = device.getApplication(pkg);
            if (app == null) {
                stdout.printf("run-as: Package '%s' is unknown\n", pkg);
                return 1;
            }
            String cmd = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            device.getShell().execute(cmd, app.user, stdout, stdin, device);
            return 0;
        }
    }

    @Override
    public String getExecutable() {
        return "/system/bin/run-as";
    }
}
