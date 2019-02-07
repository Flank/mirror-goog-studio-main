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
package com.android.tools.deployer;

import java.util.ArrayList;
import java.util.List;

public class DeployRunnerParameters {

    public enum Command {
        INSTALL,
        CODESWAP,
        FULLSWAP,
        UNKNOWN
    }

    private Command command = Command.UNKNOWN;
    private boolean deltaInstall = false;
    private List<String> parameters = new ArrayList<>();

    private DeployRunnerParameters() {}

    private void parseFlag(String arg) {
        if ("--deltainstall".equals(arg)) {
            deltaInstall = true;
            return;
        }
        throw new RuntimeException("Unknown flag: '" + arg + "'");
    }

    private Command parseCommand(String arg) {
        try {
            command = Command.valueOf(arg.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Unknown command: '" + arg + "'");
        }
        return Command.UNKNOWN;
    }

    public static DeployRunnerParameters parse(String[] args) {
        DeployRunnerParameters drp = new DeployRunnerParameters();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                drp.parseFlag(arg);
                continue;
            }

            if (drp.command == Command.UNKNOWN) {
                drp.parseCommand(arg);
                continue;
            }
            drp.add(arg);
        }
        return drp;
    }

    public int size() {
        return parameters.size();
    }

    public void add(String parameter) {
        parameters.add(parameter);
    }

    public String get(int index) {
        return parameters.get(index);
    }

    public Command getCommand() {
        return command;
    }

    public boolean isDeltaInstall() {
        return deltaInstall;
    }
}
