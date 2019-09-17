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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class Stat extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        int ret = 0;
        if (args.length == 0) {
            stdout.println("1234 1234 ... (stdin)");
        } else {
            for (int i = 0; i < args.length; i++) {
                if (context.getDevice().hasFile(args[i])) {
                    stdout.printf("1234 1234 ... %s\n", args[i]);
                } else {
                    stdout.printf("stat: %s: No such file or directory\n", args[i]);
                    ret = 1;
                }
            }
        }
        return ret;
    }

    @Override
    public String getExecutable() {
        return "stat";
    }
}
