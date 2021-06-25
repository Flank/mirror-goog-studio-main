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
    private boolean forceFullInstall = false;
    private boolean optimisticInstall = false;
    private String installersPath = null;

    private String applicationId;
    private final List<String> targetDevices = new ArrayList<>();
    private final List<String> apkPaths = new ArrayList<>();

    private DeployRunnerParameters() {}

    private void parseFlag(String arg) {
        if ("--force-full-install".equals(arg)) {
            forceFullInstall = true;
        } else if (arg.startsWith("--installers-path=")) {
            installersPath = arg.substring("--installers-path=".length());
        } else if (arg.startsWith("--optimistic-install")) {
            optimisticInstall = true;
        } else if (arg.startsWith("--device=")) {
            targetDevices.add(arg.substring("--device=".length()));
        } else {
            throw new RuntimeException("Unknown flag: '" + arg + "'");
        }
    }

    private void parseCommand(String arg) {
        try {
            command = Command.valueOf(arg.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Unknown command: '" + arg + "'");
        }
    }

    public static DeployRunnerParameters parse(String[] args) {
        DeployRunnerParameters drp = new DeployRunnerParameters();
        drp.parseCommand(args[0]);
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                drp.parseFlag(args[i]);
            } else if (drp.applicationId == null) {
                drp.applicationId = args[i];
            } else {
                drp.apkPaths.add(args[i]);
            }
        }
        return drp;
    }

    public Command getCommand() {
        return command;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public List<String> getTargetDevices() {
        return targetDevices;
    }

    public List<String> getApks() {
        return apkPaths;
    }

    public boolean isForceFullInstall() {
        return forceFullInstall;
    }

    public String getInstallersPath() {
        return installersPath;
    }

    public boolean isOptimisticInstall() {
        return optimisticInstall;
    }
}
