/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deploy.service;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.deploy.service.proto.Deploy;
import com.android.utils.ILogger;

/**
 * A logger that captures values and stores them in the DeployLog proto. This is useful for
 * debugging errors with the {@link DeployServer}.
 */
public class DeployLogger implements ILogger {

    /**
     * Error level to capture. Levels are inclusive of levels defined before them. Eg Level.INFO
     * will capture INFO, WARNING, ERROR logs but will not capture VERBOSE.
     */
    enum Level {
        ERROR,
        WARNING,
        INFO,
        VERBOSE
    }

    private final Deploy.DeployLog.Builder myLog = Deploy.DeployLog.newBuilder();
    private final Level myLevel;

    public DeployLogger(Level level) {
        myLevel = level;
    }

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        if (t != null && msgFormat != null) {
            myLog.addError(t.getMessage() + "\n" + String.format(msgFormat, args));
        }
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        if (myLevel.ordinal() <= Level.WARNING.ordinal()) {
            myLog.addWarning(String.format(msgFormat, args));
        }
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        if (myLevel.ordinal() <= Level.INFO.ordinal()) {
            myLog.addInfo(String.format(msgFormat, args));
        }
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        if (myLevel.ordinal() <= Level.VERBOSE.ordinal()) {
            myLog.addVerbose(String.format(msgFormat, args));
        }
    }

    public Deploy.DeployLog toProto() {
        return myLog.build();
    }
}
