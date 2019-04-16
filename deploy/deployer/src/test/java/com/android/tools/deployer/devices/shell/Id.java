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

public class Id extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout) {
        FakeDevice.User user = context.getUser();
        if (args.length == 0) {
            stdout.printf(
                    "uid=%d(%s) gid=%d(%s) groups=%d(%s),1004(input)\n",
                    user.uid, user.name, user.uid, user.name, user.uid, user.name);
        } else if (args.length == 1 && args[0].equals("-u")) {
            stdout.println(user.uid);
        } else {
            stdout.println("id: unknown option " + String.join(" ", args));
            return 1;
        }
        return 0;
    }

    @Override
    public String getExecutable() {
        return "id";
    }
}
