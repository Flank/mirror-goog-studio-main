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
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.deployer.DeployerException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class AppComponent {

    // The timeout is quite large to accommodate ARM emulators.
    private final long SHELL_TIMEOUT = 15;
    private final TimeUnit SHELL_TIMEUNIT = TimeUnit.SECONDS;

    protected final IDevice device;

    protected AppComponent(IDevice device) { this.device = device; }

    public abstract void activate(
            @NonNull String extraFlags, Mode activationMode, @NonNull IShellOutputReceiver receiver)
            throws DeployerException;

    protected void runShellCommand(String command, IShellOutputReceiver receiver)
            throws DeployerException {
        try {
            device.executeShellCommand(command, receiver, SHELL_TIMEOUT, SHELL_TIMEUNIT);
        } catch (TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException e) {
            throw DeployerException.componentActivationException(e.getMessage());
        }
    }

    public enum Mode {
        RUN,
        DEBUG
    }
}
