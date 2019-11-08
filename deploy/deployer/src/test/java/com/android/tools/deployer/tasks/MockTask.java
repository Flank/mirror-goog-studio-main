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
package com.android.tools.deployer.tasks;

import com.android.tools.deployer.DeployerException;

public class MockTask {
    private boolean runCalled;
    private boolean failCalled;
    private boolean throwsException;

    public MockTask(boolean throwsException) {
        this.runCalled = false;
        this.failCalled = false;
        this.throwsException = throwsException;
    }

    public String run(@SuppressWarnings("unused") String input) throws DeployerException {
        runCalled = true;
        if (throwsException) {
            throw DeployerException.operationNotSupported("");
        }
        return "";
    }

    public Void fail(@SuppressWarnings("unused") String unused) {
        failCalled = true;
        return null;
    }

    public boolean ran() {
        return runCalled;
    }

    public boolean failRan() {
        return failCalled;
    }
}
