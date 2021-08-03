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

public class FakeInstaller implements Installer {

    List<Deploy.NetworkTestRequest> myNetworkRequestReceived = new ArrayList<>();

    public List<Deploy.NetworkTestRequest> getCapturedNetworkRequest() {
        return myNetworkRequestReceived;
    }

    public Deploy.NetworkTestResponse networkTest(Deploy.NetworkTestRequest request)
            throws IOException {
        myNetworkRequestReceived.add(request);
        return Deploy.NetworkTestResponse.newBuilder().setProcessingDurationNs(1L).build();
    }

    public Deploy.DumpResponse dump(List<String> packageNames) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.SwapResponse swap(Deploy.SwapRequest request) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.SwapResponse overlaySwap(Deploy.OverlaySwapRequest request) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.DeltaPreinstallResponse deltaPreinstall(Deploy.InstallInfo info)
            throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.DeltaInstallResponse deltaInstall(Deploy.InstallInfo info) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.OverlayInstallResponse overlayInstall(Deploy.OverlayInstallRequest request)
            throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.LiveLiteralUpdateResponse updateLiveLiterals(
            Deploy.LiveLiteralUpdateRequest liveLiterals) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.OverlayIdPushResponse verifyOverlayId(String packageName, String oid)
            throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.InstallCoroutineAgentResponse installCoroutineAgent(
            String packageName, Deploy.Arch arch) throws IOException {
        throw new IllegalStateException("Not implemented");
    }

    public Deploy.LiveEditResponse liveEdit(Deploy.LiveEditRequest ler) throws IOException {
        throw new IllegalStateException("Not implemented");
    }
}
