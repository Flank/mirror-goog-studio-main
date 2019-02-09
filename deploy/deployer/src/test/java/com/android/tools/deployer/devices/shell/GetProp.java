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
import java.util.Map;

public class GetProp extends ShellCommand {
    @Override
    public String execute(FakeDevice device, String[] args, InputStream input) {
        if (args.length == 0) {
            StringBuilder out = new StringBuilder("# This is some build info\n");
            for (Map.Entry<String, String> entry : device.getProps().entrySet()) {
                out.append(String.format("[%s]: [%s]\n", entry.getKey(), entry.getValue()));
            }
            return out.toString();
        } else {
            String value = device.getProps().get(args[0]);
            return (value == null ? "" : value) + "\n";
        }
    }

    @Override
    public String getExecutable() {
        return "getprop";
    }
}
