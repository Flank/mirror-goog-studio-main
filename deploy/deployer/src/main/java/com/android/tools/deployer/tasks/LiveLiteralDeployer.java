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

    private static final Object LOCK = new Object();

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

    /** Temp solution. Going to refactor / move this elsewhere later. */
    public void updateLiveLiteral(
            Installer installer,
            AdbClient adb,
            String packageName,
            Collection<UpdateLiveLiteralParam> params) {

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
                                    .setValue(param.value))
                    .setArch(Deploy.Arch.ARCH_64_BIT);
        }

        requestBuilder.setPackageName(packageName);
        requestBuilder.addAllProcessIds(adb.getPids(packageName));
        Deploy.LiveLiteralUpdateRequest request = requestBuilder.build();

        try {
            synchronized (LOCK) {
                // All of the installer pipeline should be thread (multi-process) safe except how we
                // interact with DDMLib. For that communication, all request shares the same socket
                // so we could potentially end up reading part of a reply for a different request.
                //
                // This is very unlikely to happen. While recoverable, we still want to avoid this
                // as much as possible. Therefore we are going to have a global lock on the quest.
                //
                // TODO: This can possible race with a real deployment (one of them would fail)
                installer.updateLiveLiterals(request);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
