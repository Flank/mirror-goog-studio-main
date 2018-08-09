/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deploy.swapper;

import com.android.tools.deploy.proto.Deploy.SwapRequest;
import com.android.tools.deployer.AdbClient;

public class AgentBasedClassRedefiner extends ClassRedefiner {
    private final AdbClient adb;

    public AgentBasedClassRedefiner(AdbClient adb) {
        this.adb = adb;
    }

    @Override
    public void redefine(SwapRequest request) {
        byte[] data = request.toByteArray();
        String[] cmd = {
            "/data/local/tmp/.ir2/bin/ir2_installer", "swap", "0", String.valueOf(data.length)
        };
        adb.shell(cmd, data);
    }
}
