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

import com.android.annotations.NonNull;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.interpreter.Parser;
import com.android.tools.deployer.devices.shell.interpreter.ShellEnv;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Shell {
    private final Map<String, ShellCommand> commands;
    private final List<String> history;

    public Shell() {
        commands = new HashMap<>();
        history = new ArrayList<>();
    }

    public void addCommand(ShellCommand command) {
        commands.put(command.getExecutable(), command);
    }

    public void execute(
            @NonNull String script,
            @NonNull OutputStream output,
            @NonNull InputStream input,
            @NonNull FakeDevice device)
            throws IOException {
        history.add(script);
        ShellEnv env = new ShellEnv(device, commands, input, output);
        Parser.parse(script).execute(env);
        output.write(env.readAllBytesFromPipe());
    }

    @NonNull
    public List<String> getHistory() {
        return history;
    }

    public void clearHistory() {
        history.clear();
    }
}

