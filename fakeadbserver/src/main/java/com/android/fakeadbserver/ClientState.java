/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.fakeadbserver;

import com.android.annotations.NonNull;

public class ClientState {

    private final int mPid;

    private final int mUid;

    @NonNull private final String mProcessName;

    @NonNull
    private final String mPackageName;

    // Whether this client is waiting for a debugger connection or not
    private boolean mWaiting;

    ClientState(
            int pid,
            int uid,
            @NonNull String processName,
            @NonNull String packageName,
            boolean isWaiting) {
        mPid = pid;
        mUid = uid;
        mProcessName = processName;
        mPackageName = packageName;
        mWaiting = isWaiting;
    }

    public int getPid() {
        return mPid;
    }

    public int getUid() {
        return mUid;
    }

    @NonNull
    public String getProcessName() {
        return mProcessName;
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    public boolean getIsWaiting() {
        return mWaiting;
    }
}
