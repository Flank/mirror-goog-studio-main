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
import java.io.InputStream;
import java.io.PrintStream;

public class BasicPm extends ShellCommand {
    @Override
    public boolean execute(
            FakeDevice device, String[] args, InputStream stdin, PrintStream stdout) {
        Arguments arguments = new Arguments(args);
        String action = arguments.nextArgument();
        if ("install".equals(action)) {
            while (arguments.nextOption() != null) {
                // Do nothing
            }
            String pkg = arguments.nextArgument();
            if (pkg == null) {
                stdout.print("\tpkg: null\nError: no package specified\n");
                return false;
            }
            byte[] file = device.readFile(pkg);
            if (file == null) {
                stdout.print(
                        "\tpkg: /data/local/tmp/sample.apk2\nFailure [INSTALL_FAILED_INVALID_URI]\n");
                return false;
            }
            device.addApk(file);
            stdout.println("Success");
            return true;
        } else {
            stdout.println("pm usage:\n...");
            return false;
        }
    }

    @Override
    public String getExecutable() {
        return "pm";
    }
}
