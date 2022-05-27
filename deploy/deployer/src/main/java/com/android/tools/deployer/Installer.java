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

import com.android.annotations.NonNull;
import com.android.tools.deploy.proto.Deploy;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Installer {

    // The Installer protocol must return an InstallerResponse tagged with the same InstallerRequest
    // id.
    public static final long FIRST_ID = 1000;
    private AtomicLong id = new AtomicLong(FIRST_ID);

    protected final ILogger logger;

    @NonNull
    protected abstract Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) throws IOException;

    protected abstract void onAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp)
            throws IOException;

    protected Installer() {
        this(new NullLogger());
    }

    protected Installer(ILogger logger) {
        this.logger = logger;
    }

    private void errorAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp)
            throws IOException {
        onAsymetry(req, resp);
        String msg =
                String.format(
                        Locale.US,
                        "Unexpected response '%s' for request '%s'",
                        resp.getExtraCase().name(),
                        req.getRequestCase().name());
        if (mismatch(req, resp)) {
            msg +=
                    String.format(
                            Locale.US,
                            ", SeqNumber mismatched req=(%d), res=(%d)",
                            resp.getId(),
                            req.getId());
        }
        throw new IOException(msg);
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
        Deploy.InstallerRequest.Builder reqBuilder =
                buildRequest("installcoroutineagent")
                        .setInstallCoroutineAgentRequest(installCoroutineAgentRequestBuilder);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_INSTALL_COROUTINE);
        if (!resp.hasInstallCoroutineAgentResponse()) {
            errorAsymetry(req, resp);
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
        Deploy.InstallerRequest.Builder reqBuilder =
                buildRequest("dump").setDumpRequest(dumpRequestBuilder);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_DUMP_MS);
        if (!resp.hasDumpResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.DumpResponse response = resp.getDumpResponse();
        logger.verbose("installer dump: " + response.getStatus().toString());
        return response;
    }

    public Deploy.SwapResponse swap(Deploy.SwapRequest swapRequest) throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("swap");
        reqBuilder.setSwapRequest(swapRequest);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_SWAP_MS);
        if (!resp.hasSwapResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.SwapResponse response = resp.getSwapResponse();
        logger.verbose("installer swap: " + response.getStatus().toString());
        return response;
    }

    public Deploy.SwapResponse overlaySwap(Deploy.OverlaySwapRequest overlaySwapRequest)
            throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("overlayswap");
        reqBuilder.setOverlaySwapRequest(overlaySwapRequest);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_OSWAP_MS);
        if (!resp.hasSwapResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.SwapResponse response = resp.getSwapResponse();
        logger.verbose("installer overlayswap: " + response.getStatus().toString());
        return response;
    }

    public Deploy.OverlayInstallResponse overlayInstall(
            Deploy.OverlayInstallRequest overlayInstallRequest) throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("overlayinstall");
        reqBuilder.setOverlayInstall(overlayInstallRequest);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_OINSTALL_MS);
        if (!resp.hasOverlayInstallResponse()) {
            errorAsymetry(req, resp);
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
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("overlayidpush");
        reqBuilder.setOverlayIdPush(overlayIdPushRequest);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_VERIFY_OID_MS);
        if (!resp.hasOverlayidpushResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.OverlayIdPushResponse response = resp.getOverlayidpushResponse();
        logger.verbose("installer overlayidpush: " + response.getStatus().toString());
        return response;
    }

    public Deploy.NetworkTestResponse networkTest(Deploy.NetworkTestRequest testParams)
            throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("networktest");
        reqBuilder.setNetworkTestRequest(testParams);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_NETTEST);
        if (!resp.hasNetworkTestResponse()) {
            errorAsymetry(req, resp);
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
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("deltapreinstall");
        reqBuilder.setInstallInfoRequest(info);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_DELTA_PREINSTALL_MS);
        if (!resp.hasDeltapreinstallResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.DeltaPreinstallResponse response = resp.getDeltapreinstallResponse();
        logger.verbose("installer deltapreinstall: " + response.getStatus().toString());
        return response;
    }

    public Deploy.DeltaInstallResponse deltaInstall(Deploy.InstallInfo info) throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("deltainstall");
        reqBuilder.setInstallInfoRequest(info);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_DELTA_INSTALL_MS);
        if (!resp.hasDeltainstallResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.DeltaInstallResponse response = resp.getDeltainstallResponse();
        logger.verbose("installer deltainstall: " + response.getStatus().toString());
        return response;
    }

    public Deploy.LiveLiteralUpdateResponse updateLiveLiterals(
            Deploy.LiveLiteralUpdateRequest liveLiterals) throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("liveliteralupdate");
        reqBuilder.setLiveLiteralRequest(liveLiterals);
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_UPDATE_LL);
        if (!resp.hasLiveLiteralResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.LiveLiteralUpdateResponse response = resp.getLiveLiteralResponse();
        logger.verbose("installer liveliteralupdate: " + response.getStatus().toString());
        return response;
    }

    public Deploy.LiveEditResponse liveEdit(Deploy.LiveEditRequest ler) throws IOException {
        Deploy.InstallerRequest.Builder requestBuilder = buildRequest("liveedit");
        requestBuilder.setLeRequest(ler);
        Deploy.InstallerRequest req = requestBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_LIVE_EDIT);
        if (!resp.hasLeResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.LiveEditResponse response = resp.getLeResponse();
        logger.verbose("installer liveEdit: " + response.getStatus().toString());
        return response;
    }

    /**
     * Request the Installer to remain inactive for a the timeout duration. This is only used for
     * testing desync detection system.
     *
     * @param timeout Duration to wait before returning.
     * @return Nothing very interesting
     * @throws IOException on Host timeout
     */
    public Deploy.TimeoutResponse timeout(long timeoutMs) throws IOException {
        Deploy.InstallerRequest.Builder reqBuilder = buildRequest("timeout");
        Deploy.TimeoutRequest.Builder timeoutBuilder = Deploy.TimeoutRequest.newBuilder();
        timeoutBuilder.setTimeoutMs(timeoutMs);
        reqBuilder.setTimeoutRequest(timeoutBuilder.build());
        Deploy.InstallerRequest req = reqBuilder.build();

        Deploy.InstallerResponse resp = send(req, Timeouts.CMD_TIMEOUT);
        if (!resp.hasTimeoutResponse()) {
            errorAsymetry(req, resp);
        }

        Deploy.TimeoutResponse response = resp.getTimeoutResponse();
        return response;
    }

    public String getVersion() {
        return Version.hash();
    }

    private Deploy.InstallerRequest.Builder buildRequest(String commandName) {
        Deploy.InstallerRequest.Builder request =
                Deploy.InstallerRequest.newBuilder()
                        .setCommandName(commandName)
                        .setId(id.getAndIncrement())
                        .setVersion(getVersion());
        return request;
    }

    private static boolean mismatch(Deploy.InstallerRequest req, Deploy.InstallerResponse resp) {
        return req.getId() != resp.getId();
    }

    private Deploy.InstallerResponse send(Deploy.InstallerRequest req, long timeOutMs)
            throws IOException {
        Deploy.InstallerResponse resp = sendInstallerRequest(req, timeOutMs);
        logger.verbose("Sent request %s", req.getRequestCase().name());
        Deploy.InstallerResponse.ExtraCase respCase = resp.getExtraCase();
        logger.verbose("Received response %s", respCase.name());

        if (mismatch(req, resp)) {
            errorAsymetry(req, resp);
        }

        return resp;
    }
}
