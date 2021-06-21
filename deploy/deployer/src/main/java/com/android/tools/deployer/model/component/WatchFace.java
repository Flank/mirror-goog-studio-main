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
import com.android.tools.deployer.ComponentActivationException;
import com.android.tools.manifest.parser.components.ManifestServiceInfo;
import com.android.utils.ILogger;

public class WatchFace extends AppComponent {

    static final String LAUNCH_COMMAND =
            "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component";

    static final String DEBUG_COMMAND = "am set-debug-app -w";

    @NonNull
    private final ManifestServiceInfo manifestServiceInfo;

    @NonNull
    private final String appId;

    @NonNull private final ILogger logger;

    public WatchFace(
            @NonNull ManifestServiceInfo info,
            @NonNull String appId,
            @NonNull IDevice device, @NonNull ILogger logger
    ) {
        super(device);
        manifestServiceInfo = info;
        this.appId = appId;
        this.logger = logger;
    }

    @Override
    public void activate(
            @NonNull String extraFlags,
            @NonNull Mode activationMode,
            @NonNull IShellOutputReceiver receiver
    ) throws ComponentActivationException {
        validate(extraFlags);
        logger.info("Activating WatchFace %s %s",
                    manifestServiceInfo.getQualifiedName(),
                    activationMode.equals(Mode.DEBUG) ? "for debug" : "");

        if (activationMode.equals(Mode.DEBUG)) {
            String debug_command = DEBUG_COMMAND + " '" + appId + "'";
            logger.info("$ adb shell " + debug_command);
            runShellCommand(debug_command, receiver);
        }
        String command = getStartWatchFaceCommand();
        logger.info("$ adb shell " + command);
        runShellCommand(command, receiver);
    }

    private void validate(String extraFlags) throws ComponentActivationException {
        if (!extraFlags.isEmpty()) {
            throw new ComponentActivationException("Extra flags are not supported by WatchFace");
        }
        // TODO: write validation
    }

    @NonNull
    private String getStartWatchFaceCommand() {
        // Escape activity declared as inner class name (resulting in foo.bar.Activity$SubActivity).
        String watchFacePath = appId + "/" + manifestServiceInfo.getQualifiedName()
                .replace("$", "\\$");
        return LAUNCH_COMMAND + " " + watchFacePath;
    }
}
