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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.deployer.model.component.ComponentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeployRunnerParameters {

    public enum Command {
        INSTALL,
        CODESWAP,
        FULLSWAP,
        ACTIVATE,
        UNKNOWN
    }

    private final List<Command> commands = new ArrayList<>();
    private boolean forceFullInstall = false;
    private boolean optimisticInstall = false;
    private boolean skipPostInstallTasks = false;
    private boolean useRootPushInstall = false;
    private boolean jdwpClientSupport = true;
    private String installersPath = null;
    private String adbExecutablePath = null;
    private String targetUserId = null;
    private String applicationId;
    private final List<String> targetDevices = new ArrayList<>();
    private final List<String> apkPaths = new ArrayList<>();
    private Component componentToActivate = null;

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
        } else if (arg.startsWith("--activate=")) {
            parseComponentToActivate(arg);
        } else if (arg.startsWith("--adb=")) {
            adbExecutablePath = arg.substring("--adb=".length());
        } else if (arg.startsWith("--user=")) {
            targetUserId = arg.substring("--user=".length());
        } else if (arg.startsWith("--skip-post-install")) {
            skipPostInstallTasks = true;
        } else if (arg.startsWith("--use-root-push-install")) {
            useRootPushInstall = true;
        } else if (arg.startsWith("--no-jdwp-client-support")) {
            jdwpClientSupport = false;
        } else {
            throw new RuntimeException("Unknown flag: '" + arg + "'");
        }
    }

    private void parseCommand(String arg) {
        try {
            commands.add(Command.valueOf(arg.toUpperCase()));
        } catch (Exception e) {
            throw new RuntimeException("Unknown command: '" + arg + "'");
        }
    }

    private void parseComponentToActivate(String arg) {
        String[] typeAndName = arg.substring("--activate=".length()).split(",");
        if (typeAndName.length != 2) {
            throw new RuntimeException(
                    "Incorrect parameters for --activate flag. Usage: --activate=type,name");
        }
        commands.add(Command.ACTIVATE);
        final ComponentType type;
        try {
            type = ComponentType.valueOf(typeAndName[0].trim().toUpperCase(Locale.US));
        } catch (Exception e) {
            throw new RuntimeException("Unknown component type");
        }

        final String name = typeAndName[1].trim();
        componentToActivate = new Component(type, name);
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
        if (drp.commands.contains(Command.ACTIVATE) && drp.componentToActivate == null) {
            throw new RuntimeException("App component for activation is not specified");
        }
        return drp;
    }

    public List<Command> getCommands() {
        return commands;
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

    public String getAdbExecutablePath() {
        return adbExecutablePath;
    }

    public boolean getSkipPostInstallTasks() {
        return skipPostInstallTasks;
    }

    public boolean getUseRootPushInstall() {
        return useRootPushInstall;
    }

    public boolean getJdwpClientSupport() {
        return jdwpClientSupport;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    @Nullable
    public Component getComponentToActivate() {
        return componentToActivate;
    }

    static class Component {
        @NonNull final ComponentType type;
        @NonNull final String name;

        private Component(@NonNull ComponentType type, @NonNull String name) {
            this.type = type;
            this.name = name;
        }
    }
}
