/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.MultiReceiver;
import com.android.tools.deployer.DeployerException;
import com.android.tools.manifest.parser.components.ManifestAppComponentInfo;
import com.android.utils.ILogger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public abstract class WearComponent extends AppComponent {

    public static class ShellCommand {
        public static final String GET_WEAR_DEBUG_SURFACE_VERSION =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version";
        public static final String DEBUG_SURFACE_SET_DEBUG_APP =
                "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-debug-app --ecn component"; // + component name

        public static final String AM_SET_DEBUG_APP = "am set-debug-app -w";
    }

    public static final class CommandResultReceiver extends MultiLineReceiver {
        public static final int SUCCESS_CODE = 1;

        private int resultCode = -1;
        private final @NotNull Pattern resultCodePattern = Pattern.compile("result=(\\d+)");

        public int getResultCode() {
            return resultCode;
        }

        @Override
        public void processNewLines(@NotNull String[] lines) {
            for (String line : lines) {
                Matcher matcher = resultCodePattern.matcher(line);
                if (matcher.find()) {
                    resultCode = Integer.parseInt(matcher.group(1));
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    protected WearComponent(
            @NonNull IDevice device,
            @NonNull String appId,
            @NonNull ManifestAppComponentInfo info,
            @NonNull ILogger logger) {
        super(device, appId, info, logger);
    }
    public static class DebugCommandReceiver extends MultiLineReceiver {
        private final @NotNull Pattern exceptionPattern = Pattern.compile("(Exception)");
        private boolean exceptionStatus = false;
        public boolean hasException() {
            return exceptionStatus;
        }

        @Override
        public void processNewLines(@NotNull String[] lines) {
            for (String line : lines) {
                Matcher matcher = exceptionPattern.matcher(line);
                if (matcher.find()) {
                    exceptionStatus = true;
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    protected void setUpAmDebugApp()
            throws DeployerException {
        DebugCommandReceiver amReceiver = new DebugCommandReceiver();
        runShellCommand(String.format("%s '%s'", ShellCommand.AM_SET_DEBUG_APP, appId), amReceiver);
        if (amReceiver.hasException()) {
            throw DeployerException.componentActivationException(
                    "Activity Manager failed to set up the app for debugging.");
        }
    }

    // Set up the app for debugging in the DebugSurface so that timeouts of SysUi and WCS can be
    // increased accordingly (see go/wear-service-debug-timeout).
    protected void setUpDebugSurfaceDebugApp() throws DeployerException {
        CommandResultReceiver surfaceReceiver = new CommandResultReceiver();
        runShellCommand(String.format("%s '%s'", ShellCommand.DEBUG_SURFACE_SET_DEBUG_APP, appId),
                        surfaceReceiver);
        if (surfaceReceiver.resultCode != CommandResultReceiver.SUCCESS_CODE) {
            this.logger.warning("Warning: Debug Surface failed to set the debug app.");
        }
    }

    protected void runStartCommand(
            @NonNull String command,
            @NonNull IShellOutputReceiver receiver,
            @NonNull ILogger logger)
            throws DeployerException {
        logger.info("$ adb shell " + command);
        CommandResultReceiver resultReceiver = new CommandResultReceiver();
        MultiReceiver multiReceiver = new MultiReceiver(resultReceiver, receiver);
        runShellCommand(command, multiReceiver);
        if (resultReceiver.getResultCode() != CommandResultReceiver.SUCCESS_CODE) {
            throw DeployerException.componentActivationException(
                    String.format("Invalid Success code `%d`", resultReceiver.getResultCode()));
        }
    }
}
