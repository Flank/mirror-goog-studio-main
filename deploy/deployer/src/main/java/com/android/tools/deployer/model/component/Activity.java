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
import com.android.tools.manifest.parser.components.ManifestActivityInfo;
import com.android.utils.ILogger;

public class Activity extends AppComponent {


    @NonNull
    private final ILogger logger;

    public Activity(@NonNull ManifestActivityInfo info,
            @NonNull String appId,
            @NonNull IDevice device,
            @NonNull ILogger logger) {
        super(device, appId, info);
        this.logger = logger;
    }

    @Override
    public void activate(
            @NonNull String extraFlags,
            @NonNull Mode activationMode,
            @NonNull IShellOutputReceiver receiver)
            throws DeployerException {
        extraFlags = extraFlags.trim();
        validate(extraFlags, activationMode);
        logger.info("Activating Activity '%s' %s",
                    info.getQualifiedName(),
                    activationMode.equals(Mode.DEBUG) ? "for debug" : "");
        if (activationMode.equals(Mode.DEBUG)
                && !extraFlags.contains(Flag.ENABLE_DEBUGGING.string)) {
            extraFlags = "-D" + (extraFlags.isEmpty() ? "" : (" " + extraFlags));
        }
        String command = getStartActivityCommand(extraFlags);
        logger.info("$ adb shell " + command);
        runShellCommand(command, receiver);
    }

    private void validate(@NonNull String extraFlags, @NonNull Mode activationMode)
            throws DeployerException {
        validateFlags(extraFlags, activationMode);

        // TODO: write validation of component
    }

    private void validateFlags(String rawFlags, Mode mode) throws DeployerException {
        if (rawFlags.isEmpty()) {
            return;
        }
        String[] flags = rawFlags.split("\\s+");
        boolean hasArgument = false;
        for (String current : flags) {
            if (hasArgument) {
                hasArgument = false;
                continue;
            } try {
                Flag validFlag = Flag.getFlag(current);
                hasArgument = validFlag.hasArgument;
            }
            catch (Exception e) {
                throw DeployerException.componentActivationException(
                        String.format("Unknown flag '%s'", current));
            }
        }
        if (hasArgument) {
            throw DeployerException.componentActivationException("Invalid flags");
        }
    }

    @NonNull
    private String getStartActivityCommand(@NonNull String extraFlags) {
        return "am start"
                + " -n "
                + getFQEscapedName()
                + " -a android.intent.action.MAIN"
                + " -c android.intent.category.LAUNCHER"
                + (extraFlags.isEmpty() ? "" : " " + extraFlags);
    }

    private enum Flag {
        ENABLE_DEBUGGING("-D", false),
        WAIT_FOR_LAUNCH("-W", false),
        REPEAT("-R", true),
        STOP_FORCE("-S", false),
        START_PROFILING_WITH_STOP("-P", true),
        START_PROFILING("--START-PROFILER", true),
        OPENGL_TRACE("--OPENGL-TRACE", false),
        USER("--USER", true);

        public final String string;

        public final boolean hasArgument;

        Flag(String flag, boolean hasArgument) {
            this.string = flag;
            this.hasArgument = hasArgument;
        }

        public static Flag getFlag(String value) {
            for (Flag v : values()) {
                if (v.string.equalsIgnoreCase(value)) return v;
            }
            throw new IllegalArgumentException();
        }
    }
}
