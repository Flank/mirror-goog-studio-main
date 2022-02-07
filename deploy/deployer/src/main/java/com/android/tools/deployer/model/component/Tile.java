/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deployer.model.component;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.deployer.DeployerException;
import com.android.tools.manifest.parser.components.ManifestServiceInfo;
import com.android.utils.ILogger;

public class Tile extends WearComponent {

    public static class ShellCommand {
        public static String SET_TILE =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component "; // + component name

        public static String UNSET_TILE =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation remove-tile --ecn component "; // + component name

        public static String SHOW_TILE_COMMAND =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index "; // + index
    }

    static final String DEBUG_COMMAND = "am set-debug-app -w";

    public Tile(
            @NonNull ManifestServiceInfo info,
            @NonNull String appId,
            @NonNull IDevice device, @NonNull ILogger logger
    ) {
        super(device, appId, info, logger);
    }

    @Override
    public void activate(
            @NonNull String extraFlags,
            @NonNull Mode activationMode,
            @NonNull IShellOutputReceiver receiver
    ) throws DeployerException {
        validate(extraFlags);
        logger.info("Activating Tile '%s' %s",
                    info.getQualifiedName(),
                    activationMode.equals(Mode.DEBUG) ? "for debug" : "");

        if (activationMode.equals(Mode.DEBUG)) {
            String debugCommand = String.format("%s '%s'", DEBUG_COMMAND, appId);
            logger.info("$ adb shell " + debugCommand);
            runShellCommand(debugCommand, receiver);
        }
        String command = getStartTileCommand();
        runStartCommand(command, receiver, logger);
    }

    private void validate(String extraFlags) throws DeployerException {
        if (!extraFlags.isEmpty()) {
            throw DeployerException.componentActivationException(
                    String.format("Extra flags are not supported by Tile. Detected flags `%s`",
                                  extraFlags));
        }
    }

    @NonNull
    private String getStartTileCommand() {
        return ShellCommand.SET_TILE + getFQEscapedName();
    }
}
