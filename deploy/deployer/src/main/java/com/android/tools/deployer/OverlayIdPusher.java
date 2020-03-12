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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import java.io.IOException;

public class OverlayIdPusher {
    private final Installer installer;

    public OverlayIdPusher(Installer installer) {
        this.installer = installer;
    }

    public boolean pushOverlayId(String packageName, OverlayId oid, boolean clearOverlays) {
        try {
            Deploy.OverlayIdPushResponse resp =
                    installer.pushOverlayId(packageName, oid.getSha(), clearOverlays);
            // TODO Needs new DeployerExceptions
            return resp.getStatus() != Deploy.OverlayIdPushResponse.Status.OK;
        } catch (IOException e) {
            // TODO Need new DeployerException.
            return false;
        }
    }
}
