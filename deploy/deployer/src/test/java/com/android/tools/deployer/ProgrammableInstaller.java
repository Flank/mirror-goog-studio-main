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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class ProgrammableInstaller extends Installer {

    private Deploy.InstallerResponse resp;

    ProgrammableInstaller() {
        resp = Deploy.InstallerResponse.newBuilder().build();
    }

    void setResp(Deploy.InstallerResponse resp) {
        this.resp = resp;
    }

    Deploy.InstallerResponse getResp() {
        return resp;
    }

    @NotNull
    @Override
    protected Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) throws IOException {
        return resp;
    }

    @Override
    protected void onAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp)
            throws IOException {
        // Do nothing
    }
}
