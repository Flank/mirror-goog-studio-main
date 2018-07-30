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

import com.android.tools.deploy.proto.Common.AgentConfig;
import com.android.tools.deploy.proto.Common.ClassDef;
import com.android.tools.deploy.proto.Common.SwapRequest;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

class AgentBasedClassRedefiner extends ClassRedefiner {
    private final String deviceID;
    private final String packageName;
    private final boolean shouldRestart;
    private final String instrumentationLocation;

    AgentBasedClassRedefiner(
            String deviceID,
            String packageName,
            boolean shouldRestart,
            String instrumentationLocation) {
        this.deviceID = deviceID;
        this.packageName = packageName;
        this.shouldRestart = shouldRestart;
        this.instrumentationLocation = instrumentationLocation;
    }

    protected String pushToDevice(AgentConfig message) {
        String tmpLoc =
                System.getProperty("java.io.tmpdir")
                        + File.pathSeparator
                        + "instant_run_agent_cfg.pb";
        try {
            File pb = new File(tmpLoc);
            if (pb.exists()) {
                pb.delete();
            }
            pb.createNewFile();
            message.writeTo(new FileOutputStream(pb));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // TODO(acleung): Use com.android.tools.deployer.ADB* to actually do these.
        System.out.println(
                "adb -s "
                        + deviceID
                        + " push instant_run_agent_cfg /data/data/"
                        + packageName
                        + "/instant_run_agent_cfg.pb");
        System.out.println(
                "adb -s "
                        + deviceID
                        + " shell am attach-agent <PID> /data/local/tmp/.ir2/libswap.so="
                        + "/data/data/"
                        + packageName
                        + "/instant_run_agent_cfg.pb");
        return tmpLoc;
    }

    private AgentConfig createMessage(Map<String, byte[]> classesToRedefine) {
        AgentConfig.Builder agentConfig = AgentConfig.newBuilder();
        agentConfig.setInstrumentDex(instrumentationLocation);

        SwapRequest.Builder swapRequest = SwapRequest.newBuilder();
        swapRequest.setPackageName(packageName);
        swapRequest.setRestartActivity(shouldRestart);
        for (Map.Entry<String, byte[]> entry : classesToRedefine.entrySet()) {
            swapRequest.addClasses(
                    ClassDef.newBuilder()
                            .setName(entry.getKey())
                            .setDex(ByteString.copyFrom(entry.getValue()))
                            .build());
        }

        return agentConfig.setSwapRequest(swapRequest).build();
    }

    @Override
    public void commit() {
        pushToDevice(createMessage(classesToRedefine));
        super.commit();
    }
}
