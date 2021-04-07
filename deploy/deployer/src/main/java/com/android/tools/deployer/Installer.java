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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import java.io.IOException;
import java.util.List;

public interface Installer {

    Deploy.DumpResponse dump(List<String> packageNames) throws IOException;

    Deploy.SwapResponse swap(Deploy.SwapRequest request) throws IOException;

    Deploy.SwapResponse overlaySwap(Deploy.OverlaySwapRequest request) throws IOException;

    Deploy.DeltaPreinstallResponse deltaPreinstall(Deploy.InstallInfo info) throws IOException;

    Deploy.DeltaInstallResponse deltaInstall(Deploy.InstallInfo info) throws IOException;

    Deploy.OverlayInstallResponse overlayInstall(Deploy.OverlayInstallRequest request)
            throws IOException;

    Deploy.LiveLiteralUpdateResponse updateLiveLiterals(
            Deploy.LiveLiteralUpdateRequest liveLiterals) throws IOException;

    /**
     * Verify the App's current OverlayID. The app's OverlayID will not be change should it differs.
     */
    Deploy.OverlayIdPushResponse verifyOverlayId(String packageName, String oid) throws IOException;

    /**
     * Extracts the coroutine debugger agent .so from the installer binary to the app's code_cache
     * folder
     */
    Deploy.InstallCoroutineAgentResponse installCoroutineAgent(String packageName, Deploy.Arch arch)
            throws IOException;
}
