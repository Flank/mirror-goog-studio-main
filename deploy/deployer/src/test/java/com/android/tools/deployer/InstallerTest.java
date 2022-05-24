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
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;

public class InstallerTest {

    @Test
    public void testDesyncInstallCoroutineAgent() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            programmableInstaller.installCoroutineAgent("foo.bar", Deploy.Arch.ARCH_32_BIT);
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setInstallCoroutineAgentResponse(
                Deploy.InstallCoroutineAgentResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.installCoroutineAgent("foo.bar", Deploy.Arch.ARCH_32_BIT);
    }

    @Test
    public void testDesyncDumpDetection() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            programmableInstaller.dump(new ArrayList<>());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setDumpResponse(Deploy.DumpResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.dump(new ArrayList<>());
    }

    @Test
    public void testDesyncSwap() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.SwapRequest.Builder reqBuilder = Deploy.SwapRequest.newBuilder();
            programmableInstaller.swap(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setSwapResponse(Deploy.SwapResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.swap(Deploy.SwapRequest.newBuilder().build());
    }

    @Test
    public void testDesyncOverlaySwap() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.OverlaySwapRequest.Builder reqBuilder = Deploy.OverlaySwapRequest.newBuilder();
            programmableInstaller.overlaySwap(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setSwapResponse(Deploy.SwapResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.overlaySwap(Deploy.OverlaySwapRequest.newBuilder().build());
    }

    @Test
    public void testDesyncOverlayInstall() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.OverlayInstallRequest.Builder reqBuilder =
                    Deploy.OverlayInstallRequest.newBuilder();
            programmableInstaller.overlayInstall(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setOverlayInstallResponse(Deploy.OverlayInstallResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.overlayInstall(Deploy.OverlayInstallRequest.newBuilder().build());
    }

    @Test
    public void testDesyncVerifyOverlayId() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            programmableInstaller.verifyOverlayId("foo.bar", "id");
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setOverlayidpushResponse(Deploy.OverlayIdPushResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.verifyOverlayId("foo.bar", "id");
    }

    @Test
    public void testDesyncNetworkTest() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.NetworkTestRequest.Builder reqBuilder = Deploy.NetworkTestRequest.newBuilder();
            programmableInstaller.networkTest(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setNetworkTestResponse(Deploy.NetworkTestResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.networkTest(Deploy.NetworkTestRequest.newBuilder().build());
    }

    @Test
    public void testDeltaPreinstall() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.InstallInfo.Builder reqBuilder = Deploy.InstallInfo.newBuilder();
            programmableInstaller.deltaPreinstall(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setDeltapreinstallResponse(Deploy.DeltaPreinstallResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.deltaPreinstall(Deploy.InstallInfo.newBuilder().build());
    }

    @Test
    public void testDeltaInstall() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.InstallInfo.Builder reqBuilder = Deploy.InstallInfo.newBuilder();
            programmableInstaller.deltaInstall(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setDeltainstallResponse(Deploy.DeltaInstallResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.deltaInstall(Deploy.InstallInfo.newBuilder().build());
    }

    @Test
    public void testUpdateLiveLiteral() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.LiveLiteralUpdateRequest.Builder reqBuilder =
                    Deploy.LiveLiteralUpdateRequest.newBuilder();
            programmableInstaller.updateLiveLiterals(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setLiveLiteralResponse(Deploy.LiveLiteralUpdateResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.updateLiveLiterals(
                Deploy.LiveLiteralUpdateRequest.newBuilder().build());
    }

    @Test
    public void testLiveEdit() throws Exception {
        ProgrammableInstaller programmableInstaller = new ProgrammableInstaller();
        try {
            Deploy.LiveEditRequest.Builder reqBuilder = Deploy.LiveEditRequest.newBuilder();
            programmableInstaller.liveEdit(reqBuilder.build());
            Assert.fail("Desync not detected");
        } catch (IOException e) {
            // Expected
        }

        Deploy.InstallerResponse.Builder respBuilder = Deploy.InstallerResponse.newBuilder();
        respBuilder.setLeResponse(Deploy.LiveEditResponse.newBuilder().build());
        programmableInstaller.setResp(respBuilder.build());
        programmableInstaller.liveEdit(Deploy.LiveEditRequest.newBuilder().build());
    }
}
