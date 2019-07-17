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

import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class Cp extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        if (args.length < 2) {
            stdout.println("Unsupported arguments");
            return 1;
        }
        if (args.length == 2) {
            context.getDevice().copyFile(args[0], args[1]);
        }
        String last = args[args.length - 1];
        if (last.endsWith("/")) {
            last = last.substring(0, last.length() - 1);
        }
        if (context.getDevice().isDirectory(last)) {
            for (int i = 0; i < args.length - 1; i++) {
                String name = new File(args[i]).getName();
                context.getDevice().copyFile(args[i], last + "/" + name);
            }
            return 0;
        }
        stdout.println("Unsupported arguments");
        return 1;
    }

    @Override
    public String getExecutable() {
        return "cp";
    }
}
