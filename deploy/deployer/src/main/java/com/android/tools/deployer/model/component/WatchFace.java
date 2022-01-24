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

public class WatchFace extends WearComponent {

    public static class ShellCommand {
        public static final String SHOW_WATCH_FACE =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface";

        public static final String SET_WATCH_FACE =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component "; // + componentName

        public static final String UNSET_WATCH_FACE =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface";
    }

    static final String DEBUG_COMMAND = "am set-debug-app -w";

    public WatchFace(
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
            @NonNull IShellOutputReceiver receiver)
            throws DeployerException {
        validate(extraFlags);
        logger.info("Activating WatchFace '%s' %s",
                    info.getQualifiedName(),
                    activationMode.equals(Mode.DEBUG) ? "for debug" : "");

        if (activationMode.equals(Mode.DEBUG)) {
            String debug_command = DEBUG_COMMAND + " '" + appId + "'";
            logger.info("$ adb shell " + debug_command);
            runShellCommand(debug_command, receiver);
        }
        String command = getStartWatchFaceCommand();
        runStartCommand(command, receiver, logger);
    }

    private void validate(String extraFlags) throws DeployerException {
        if (!extraFlags.isEmpty()) {
            throw DeployerException.componentActivationException(
                    String.format("Extra flags are not supported by Watch Face. Detected flags `%s`",
                                  extraFlags));
        }
    }

    @NonNull
    private String getStartWatchFaceCommand() {
        return ShellCommand.SET_WATCH_FACE + getFQEscapedName();
    }
}
