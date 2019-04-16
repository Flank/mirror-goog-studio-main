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
import java.util.Map;

public class GetProp extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout) {
        FakeDevice device = context.getDevice();
        if (args.length == 0) {
            stdout.println("# This is some build info");
            for (Map.Entry<String, String> entry : device.getProps().entrySet()) {
                stdout.format("[%s]: [%s]\n", entry.getKey(), entry.getValue());
            }
        } else {
            String value = device.getProps().get(args[0]);
            stdout.println(value == null ? "" : value);
            // It always returns 0 even if it doesn't find it
        }
        return 0;
    }

    @Override
    public String getExecutable() {
        return "getprop";
    }
}
