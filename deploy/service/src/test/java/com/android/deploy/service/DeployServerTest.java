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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.deploy.service.proto.Deploy;
import com.android.tools.deployer.DeployMetric;
import com.android.tools.deployer.DeployerRunner;

import io.grpc.stub.StreamObserver;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;

public class DeployServerTest {
    final String packageName = "com.example.myapp";
    final String processName = "com.example.myapp:process";

    @Test
    public void getDevices() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Deploy.DeviceResponse> response = new FakeStreamObserver<>();
        server.getDevices(Deploy.DeviceRequest.getDefaultInstance(), response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getDevicesCount()).isEqualTo(devices.length);
        assertThat(response.getResponse().getDevices(0).getSerialNumber())
                .isEqualTo(devices[0].getSerialNumber());
    }

    @Test
    public void getClients() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Deploy.ClientResponse> response = new FakeStreamObserver<>();
        Deploy.ClientRequest request =
                Deploy.ClientRequest.newBuilder().setDeviceId("1234").build();
        server.getClients(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getClientsCount()).isEqualTo(1);
        assertThat(response.getResponse().getClients(0).getPid()).isEqualTo(1234);
        assertThat(response.getResponse().getClients(0).getName()).isEqualTo(packageName);
        assertThat(response.getResponse().getClients(0).getDescription()).isEqualTo(processName);
    }

    @Test
    public void getDebugPort() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Deploy.DebugPortResponse> response = new FakeStreamObserver<>();
        Deploy.DebugPortRequest request =
                Deploy.DebugPortRequest.newBuilder().setDeviceId("1234").setPid(1234).build();
        server.getDebugPort(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getPort()).isEqualTo(4321);
    }

    @Test
    public void installApkNoDevice() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Deploy.InstallApkResponse> response = new FakeStreamObserver<>();
        Deploy.InstallApkRequest request =
                Deploy.InstallApkRequest.newBuilder().setDeviceId("4321").build();
        server.installApk(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getExitStatus()).isEqualTo(-1);
        assertThat(response.getResponse().getMessageCount()).isEqualTo(1);
        assertThat(response.getResponse().getMessage(0)).isNotEmpty();
    }

    @Test
    public void installApk() {
        String apkPath = "/fake/path.apk";
        String packageName = "com.example.app";
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployerRunner runner = mock(DeployerRunner.class);
        ArgumentCaptor<IDevice> deviceCaptor = ArgumentCaptor.forClass(IDevice.class);
        ArgumentCaptor<String[]> argsCaptor = ArgumentCaptor.forClass(String[].class);
        when(runner.run(deviceCaptor.capture(), argsCaptor.capture(), any())).thenReturn(0);
        ArrayList<DeployMetric> metrics = new ArrayList<>();
        metrics.add(new DeployMetric("Test", 1, 2));
        when(runner.getMetrics()).thenReturn(metrics);
        DeployServer server = new DeployServer(bridge, runner);
        FakeStreamObserver<Deploy.InstallApkResponse> response = new FakeStreamObserver<>();
        Deploy.InstallApkRequest request =
                Deploy.InstallApkRequest.newBuilder()
                        .setDeviceId("1234")
                        .addApk(apkPath)
                        .setPackageName(packageName)
                        .build();
        server.installApk(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getExitStatus()).isEqualTo(0);
        assertThat(deviceCaptor.getValue()).isEqualTo(devices[0]);
        String[] args = argsCaptor.getValue();
        assertThat(args[0]).isEqualTo("install");
        assertThat(args[1]).isEqualTo(packageName);
        assertThat(args[2]).isEqualTo(apkPath);
        assertThat(response.getResponse().getMetricCount()).isEqualTo(1);
        Deploy.DeployMetric actualMetric = response.getResponse().getMetric(0);
        assertThat(actualMetric.getName()).isEqualTo(metrics.get(0).getName());
        assertThat(actualMetric.getStartNs()).isEqualTo(metrics.get(0).getStartTimeNs());
        assertThat(actualMetric.getEndNs()).isEqualTo(metrics.get(0).getEndTimeNs());
    }

    private IDevice mockDevice(@NonNull String serial, @NonNull IDevice.DeviceState state) {
        IDevice device = mock(IDevice.class);
        when(device.getSerialNumber()).thenReturn(serial);
        when(device.getState()).thenReturn(state);
        when(device.getName()).thenReturn(serial);
        when(device.isEmulator()).thenReturn(false);
        when(device.getAbis()).thenReturn(Collections.singletonList("armeabi"));
        Client[] clients = new Client[] {mockClient(packageName, processName)};
        when(device.getClients()).thenReturn(clients);
        return device;
    }

    private Client mockClient(@NonNull String clientName, @NonNull String clientDescription) {
        Client client = mock(Client.class);
        ClientData clientData = mock(ClientData.class);
        when(client.getClientData()).thenReturn(clientData);
        when(clientData.getPid()).thenReturn(1234);
        when(clientData.getPackageName()).thenReturn(clientName);
        when(clientData.getClientDescription()).thenReturn(clientDescription);
        when(client.getDebuggerListenPort()).thenReturn(4321);
        return client;
    }

    class FakeStreamObserver<T> implements StreamObserver<T> {

        private T myResponse;

        public T getResponse() {
            return myResponse;
        }

        @Override
        public void onNext(T t) {
            myResponse = t;
        }

        @Override
        public void onError(Throwable throwable) {}

        @Override
        public void onCompleted() {}
    }
}
