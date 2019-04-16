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
import java.io.PrintStream;

public class Mkdir extends ShellCommand {
    @Override
    public boolean execute(FakeDevice device, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        if (args.length == 0) {
            stdout.println("Usage mkdir...");
        } else {
            boolean parents = false;
            for (int i = 0; i < args.length; i++) {
                if (i == 0 && args[0].equals("-p")) {
                    parents = true;
                } else {
                    device.mkdir(args[i], parents);
                }
            }
        }
        return true;
    }

    @Override
    public String getExecutable() {
        return "mkdir";
    }
}
