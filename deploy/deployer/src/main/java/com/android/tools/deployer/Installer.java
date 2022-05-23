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
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import java.io.IOException;
import java.util.List;

public abstract class Installer {

    protected final ILogger logger;

    protected abstract Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) throws IOException;

    protected abstract void onAsymmetryDetected(
            String reqType, String resType, Deploy.InstallerResponse resp) throws IOException;

    protected Installer() {
        this(new NullLogger());
    }

    protected Installer(ILogger logger) {
        this.logger = logger;
    }

    /**
     * Extracts the coroutine debugger agent .so from the installer binary to the app's code_cache
     * folder
     */
    public Deploy.InstallCoroutineAgentResponse installCoroutineAgent(
            String packageName, Deploy.Arch arch) throws IOException {
        Deploy.InstallCoroutineAgentRequest.Builder installCoroutineAgentRequestBuilder =
                Deploy.InstallCoroutineAgentRequest.newBuilder();
        installCoroutineAgentRequestBuilder.setPackageName(packageName).setArch(arch);
        Deploy.InstallerRequest.Builder request =
                buildRequest("installcoroutineagent")
                        .setInstallCoroutineAgentRequest(installCoroutineAgentRequestBuilder);
        Deploy.InstallerResponse resp =
                sendInstallerRequest(request.build(), Timeouts.CMD_INSTALL_COROUTINE);
        if (!resp.hasInstallCoroutineAgentResponse()) {
            onAsymmetryDetected(
                    "InstallCoroutineAgentResponse", "InstallCoroutineAgentRequest", resp);
        }
        Deploy.InstallCoroutineAgentResponse response = resp.getInstallCoroutineAgentResponse();
        logger.verbose("installer install coroutine agent: " + response.getStatus().toString());
        return response;
    }

    public Deploy.DumpResponse dump(List<String> packageNames) throws IOException {
        Deploy.DumpRequest.Builder dumpRequestBuilder = Deploy.DumpRequest.newBuilder();
        for (String packageName : packageNames) {
            dumpRequestBuilder.addPackageNames(packageName);
        }
        Deploy.InstallerRequest.Builder req =
                buildRequest("dump").setDumpRequest(dumpRequestBuilder);
        Deploy.InstallerResponse resp = sendInstallerRequest(req.build(), Timeouts.CMD_DUMP_MS);
        if (!resp.hasDumpResponse()) {
            onAsymmetryDetected("DumpResponse", "DumpRequest", resp);
        }
        Deploy.DumpResponse response = resp.getDumpResponse();
        logger.verbose("installer dump: " + response.getStatus().toString());
        return response;
    }

    public Deploy.SwapResponse swap(Deploy.SwapRequest swapRequest) throws IOException {
        Deploy.InstallerRequest.Builder req = buildRequest("swap");
        req.setSwapRequest(swapRequest);
        Deploy.InstallerResponse resp = sendInstallerRequest(req.build(), Timeouts.CMD_SWAP_MS);
        if (!resp.hasSwapResponse()) {
            onAsymmetryDetected("SwapResponse", "SwapRequest", resp);
        }
        Deploy.SwapResponse response = resp.getSwapResponse();
        logger.verbose("installer swap: " + response.getStatus().toString());
        return response;
    }

    public Deploy.SwapResponse overlaySwap(Deploy.OverlaySwapRequest overlaySwapRequest)
            throws IOException {
        Deploy.InstallerRequest.Builder req = buildRequest("overlayswap");
        req.setOverlaySwapRequest(overlaySwapRequest);
        Deploy.InstallerResponse resp = sendInstallerRequest(req.build(), Timeouts.CMD_OSWAP_MS);
        if (!resp.hasSwapResponse()) {
            onAsymmetryDetected("SwapResponse", "SwapRequest", resp);
        }
        Deploy.SwapResponse response = resp.getSwapResponse();
        logger.verbose("installer overlayswap: " + response.getStatus().toString());
        return response;
    }

    public Deploy.OverlayInstallResponse overlayInstall(
            Deploy.OverlayInstallRequest overlayInstallRequest) throws IOException {
        Deploy.InstallerRequest.Builder req = buildRequest("overlayinstall");
        req.setOverlayInstall(overlayInstallRequest);
        Deploy.InstallerResponse resp = sendInstallerRequest(req.build(), Timeouts.CMD_OINSTALL_MS);
        if (!resp.hasOverlayInstallResponse()) {
            onAsymmetryDetected("OverlayInstallResponse", "OverlayInstall", resp);
        }
        Deploy.OverlayInstallResponse response = resp.getOverlayInstallResponse();
        logger.verbose("installer overlayinstall: " + response.getStatus().toString());
        return response;
    }

    /**
     * Verify the App's current OverlayID. The app's OverlayID will not be change should it differs.
     */
    public Deploy.OverlayIdPushResponse verifyOverlayId(String packageName, String oid)
            throws IOException {
        // Doing a overylayid push with both new and old OID as the argument effectively verifies
        // the OID without updating it.
        Deploy.OverlayIdPush overlayIdPushRequest =
                createOidPushRequest(packageName, oid, oid, false);
        Deploy.InstallerRequest.Builder req = buildRequest("overlayidpush");
        req.setOverlayIdPush(overlayIdPushRequest);
        Deploy.InstallerResponse resp =
                sendInstallerRequest(req.build(), Timeouts.CMD_VERIFY_OID_MS);
        if (!resp.hasOverlayidpushResponse()) {
            onAsymmetryDetected("OverlayidpushResponse", "OverlayIdPush", resp);
        }
        Deploy.OverlayIdPushResponse response = resp.getOverlayidpushResponse();
        logger.verbose("installer overlayidpush: " + response.getStatus().toString());
        return response;
    }

    public Deploy.NetworkTestResponse networkTest(Deploy.NetworkTestRequest testParams)
            throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("networktest");
        request.setNetworkTestRequest(testParams);
        Deploy.InstallerResponse resp = sendInstallerRequest(request.build(), Timeouts.CMD_NETTEST);
        if (!resp.hasNetworkTestResponse()) {
            onAsymmetryDetected("NetworkTestResponse", "NetworkTestRequest", resp);
        }
        return resp.getNetworkTestResponse();
    }

    private static Deploy.OverlayIdPush createOidPushRequest(
            String packageName, String prevOid, String nextOid, boolean wipeOverlays) {
        return Deploy.OverlayIdPush.newBuilder()
                .setPackageName(packageName)
                .setPrevOid(prevOid)
                .setNextOid(nextOid)
                .setWipeOverlays(wipeOverlays)
                .build();
    }

    public Deploy.DeltaPreinstallResponse deltaPreinstall(Deploy.InstallInfo info)
            throws IOException {
        Deploy.InstallerRequest.Builder req = buildRequest("deltapreinstall");
        req.setInstallInfoRequest(info);
        Deploy.InstallerResponse resp =
                sendInstallerRequest(req.build(), Timeouts.CMD_DELTA_PREINSTALL_MS);
        if (!resp.hasDeltapreinstallResponse()) {
            onAsymmetryDetected("DeltapreinstallResponse", "InstallInfoRequest", resp);
        }
        Deploy.DeltaPreinstallResponse response = resp.getDeltapreinstallResponse();
        logger.verbose("installer deltapreinstall: " + response.getStatus().toString());
        return response;
    }

    public Deploy.DeltaInstallResponse deltaInstall(Deploy.InstallInfo info) throws IOException {
        Deploy.InstallerRequest.Builder req = buildRequest("deltainstall");
        req.setInstallInfoRequest(info);
        Deploy.InstallerResponse resp =
                sendInstallerRequest(req.build(), Timeouts.CMD_DELTA_INSTALL_MS);
        if (!resp.hasDeltainstallResponse()) {
            onAsymmetryDetected("DeltainstallResponse", "InstallInfoRequest", resp);
        }
        Deploy.DeltaInstallResponse response = resp.getDeltainstallResponse();
        logger.verbose("installer deltainstall: " + response.getStatus().toString());
        return response;
    }

    public Deploy.LiveLiteralUpdateResponse updateLiveLiterals(
            Deploy.LiveLiteralUpdateRequest liveLiterals) throws IOException {
        Deploy.InstallerRequest.Builder req = buildRequest("liveliteralupdate");
        req.setLiveLiteralRequest(liveLiterals);
        Deploy.InstallerResponse resp = sendInstallerRequest(req.build(), Timeouts.CMD_UPDATE_LL);
        if (!resp.hasLiveLiteralResponse()) {
            onAsymmetryDetected("LiveLiteralResponse", "LiveLiteralRequest", resp);
        }
        Deploy.LiveLiteralUpdateResponse response = resp.getLiveLiteralResponse();
        logger.verbose("installer liveliteralupdate: " + response.getStatus().toString());
        return response;
    }

    public Deploy.LiveEditResponse liveEdit(Deploy.LiveEditRequest ler) throws IOException {
        Deploy.InstallerRequest.Builder request = buildRequest("liveedit");
        request.setLeRequest(ler);
        Deploy.InstallerResponse resp =
                sendInstallerRequest(request.build(), Timeouts.CMD_LIVE_EDIT);
        if (!resp.hasLeResponse()) {
            onAsymmetryDetected("LeResponse", "LeRequest", resp);
        }
        Deploy.LiveEditResponse response = resp.getLeResponse();
        logger.verbose("installer liveEdit: " + response.getStatus().toString());
        return response;
    }

    public String getVersion() {
        return Version.hash();
    }

    private Deploy.InstallerRequest.Builder buildRequest(String commandName) {
        Deploy.InstallerRequest.Builder request =
                Deploy.InstallerRequest.newBuilder()
                        .setCommandName(commandName)
                        .setVersion(getVersion());
        return request;
    }
}
