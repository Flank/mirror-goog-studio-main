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

package com.android.tools.deployer.tasks;

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.Installer;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * The purpose of this class is to provide a facet to updating Live Literals on the device.
 *
 * <p>Structurally very similar ot {@Deployer}, the main operation of this class is to invoke the
 * Java installer to execute a command on the on-device installer.
 *
 * <p>The fundamental difference, however, is that {@Deployer} operates right after a build is
 * finished and APK steadily available. Here, we need to operate without the APK and concentrate our
 * effort on respond time instead.
 */
public class LiveLiteralDeployer {
    public static class UpdateLiveLiteralParam {
        final String key;
        final String type;
        final String value;
        final int offset;
        final String helper;

        public UpdateLiveLiteralParam(
                String key, int offset, String helper, String type, String value) {
            this.key = key;
            this.offset = offset;
            this.helper = helper;
            this.type = type;
            this.value = value;
        }
    }

    /**
     * Everything is an error at the moment. While they are hard error that might cause the update
     * to be aborted. These should not be presented to the user with any sense of urgency due to the
     * agreed "best effort" nature of LL updates.
     */
    public static class UpdateLiveLiteralError {
        public final String msg;

        public UpdateLiveLiteralError(String msg) {
            this.msg = msg;
        }
    }

    /** Temp solution. Going to refactor / move this elsewhere later. */
    public List<UpdateLiveLiteralError> updateLiveLiteral(
            Installer installer,
            AdbClient adb,
            String packageName,
            Collection<UpdateLiveLiteralParam> params) {

        List<Integer> pids = adb.getPids(packageName);
        Deploy.Arch arch = adb.getArch(pids);

        Deploy.LiveLiteralUpdateRequest.Builder requestBuilder =
                Deploy.LiveLiteralUpdateRequest.newBuilder();
        for (UpdateLiveLiteralParam param : params) {
            requestBuilder
                    .addUpdates(
                            Deploy.LiveLiteral.newBuilder()
                                    .setKey(param.key)
                                    .setOffset(param.offset)
                                    .setHelperClass(param.helper)
                                    .setType(param.type)
                                    .setValue(param.value));
        }

        requestBuilder.setPackageName(packageName);
        requestBuilder.addAllProcessIds(pids);
        requestBuilder.setArch(arch);

        Deploy.LiveLiteralUpdateRequest request = requestBuilder.build();

        List<UpdateLiveLiteralError> errors = new LinkedList<>();
        try {
            Deploy.LiveLiteralUpdateResponse response = installer.updateLiveLiterals(request);
            for (Deploy.AgentResponse failure : response.getFailedAgentsList()) {
                errors.add(new UpdateLiveLiteralError(failure.getLiveLiteralResponse().getExtra()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return errors;
    }
}
