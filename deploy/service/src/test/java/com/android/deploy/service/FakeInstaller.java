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

import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.Installer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FakeInstaller extends Installer {

    List<Deploy.NetworkTestRequest> myNetworkRequestReceived = new ArrayList<>();

    public List<Deploy.NetworkTestRequest> getCapturedNetworkRequest() {
        return myNetworkRequestReceived;
    }

    @Override
    protected Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    protected void onAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Deploy.NetworkTestResponse networkTest(Deploy.NetworkTestRequest request)
            throws IOException {
        myNetworkRequestReceived.add(request);
        return Deploy.NetworkTestResponse.newBuilder().setProcessingDurationNs(1L).build();
    }

}
