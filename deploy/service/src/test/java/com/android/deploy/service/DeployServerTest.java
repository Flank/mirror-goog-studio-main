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

import static com.android.deploy.service.DeployServer.MAX_BUFFER_SIZE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.deploy.service.proto.Service;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.DeployMetric;
import com.android.tools.deployer.DeployerRunner;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DeployServerTest {

    String myPackageName = "com.example.myapp";

    String myProcessName = "com.example.myapp:process";

    @Test
    public void getDevices() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Service.DeviceResponse> response = new FakeStreamObserver<>();
        server.getDevices(Service.DeviceRequest.getDefaultInstance(), response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getDevicesCount()).isEqualTo(devices.length);
        assertThat(response.getResponse().getDevices(0).getSerialNumber())
                .isEqualTo(devices[0].getSerialNumber());
    }

    @Test
    public void getClientsForAllDevices() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices =
                new IDevice[] {
                    mockDevice("1234", IDevice.DeviceState.ONLINE),
                    mockDevice("5678", IDevice.DeviceState.ONLINE)
                };
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Service.ClientResponse> response = new FakeStreamObserver<>();
        Service.ClientRequest request = Service.ClientRequest.newBuilder().build();
        server.getClients(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getClientsCount()).isEqualTo(2);
        for (int i = 0; i < 2; i++) {
            assertThat(response.getResponse().getClients(i).getPid()).isEqualTo(1234);
            assertThat(response.getResponse().getClients(i).getName()).isEqualTo(myPackageName);
            assertThat(response.getResponse().getClients(i).getDescription())
                    .isEqualTo(myProcessName);
        }
    }

    @Test
    public void getClientsForOneDevice() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices =
                new IDevice[] {
                    mockDevice("1234", IDevice.DeviceState.ONLINE),
                    mockDevice("5678", IDevice.DeviceState.ONLINE)
                };
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Service.ClientResponse> response = new FakeStreamObserver<>();
        Service.ClientRequest request =
                Service.ClientRequest.newBuilder().setDeviceId("1234").build();
        server.getClients(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getClientsCount()).isEqualTo(1);
        assertThat(response.getResponse().getClients(0).getPid()).isEqualTo(1234);
        assertThat(response.getResponse().getClients(0).getName()).isEqualTo(myPackageName);
        assertThat(response.getResponse().getClients(0).getDescription()).isEqualTo(myProcessName);
    }

    @Test
    public void getClients_NullProcessAndPackageName() {
        myPackageName = null;
        myProcessName = null;

        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Service.ClientResponse> response = new FakeStreamObserver<>();
        Service.ClientRequest request =
                Service.ClientRequest.newBuilder().setDeviceId("1234").build();
        server.getClients(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getClientsCount()).isEqualTo(1);
        assertThat(response.getResponse().getClients(0).getPid()).isEqualTo(1234);
        assertThat(response.getResponse().getClients(0).getName()).isEmpty();
        assertThat(response.getResponse().getClients(0).getDescription()).isEmpty();
    }

    @Test
    public void getDebugPort() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge, null);
        FakeStreamObserver<Service.DebugPortResponse> response = new FakeStreamObserver<>();
        Service.DebugPortRequest request =
                Service.DebugPortRequest.newBuilder().setDeviceId("1234").setPid(1234).build();
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
        FakeStreamObserver<Service.InstallApkResponse> response = new FakeStreamObserver<>();
        Service.InstallApkRequest request =
                Service.InstallApkRequest.newBuilder().setDeviceId("4321").build();
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
        FakeStreamObserver<Service.InstallApkResponse> response = new FakeStreamObserver<>();
        Service.InstallApkRequest request =
                Service.InstallApkRequest.newBuilder()
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
        Service.DeployMetric actualMetric = response.getResponse().getMetric(0);
        assertThat(actualMetric.getName()).isEqualTo(metrics.get(0).getName());
        assertThat(actualMetric.getStartNs()).isEqualTo(metrics.get(0).getStartTimeNs());
        assertThat(actualMetric.getEndNs()).isEqualTo(metrics.get(0).getEndTimeNs());
    }

    @Test
    public void testBandwidthTestToDevice() {
        int bytesToSend = MAX_BUFFER_SIZE * 5;
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployerRunner runner = mock(DeployerRunner.class);
        DeployServer server = new DeployServer(bridge, runner);
        Service.NetworkTest request =
                Service.NetworkTest.newBuilder()
                        .setType(Service.NetworkTest.Type.BANDWIDTH)
                        .setHostToDevice(true)
                        .setNumberOfBytes(bytesToSend)
                        .build();
        FakeInstaller installer = new FakeInstaller();
        Service.NetworkTestResponse response = server.doBandwidthTest(installer, request).build();
        List<Deploy.NetworkTestRequest> requestList = installer.getCapturedNetworkRequest();
        assertThat(requestList).hasSize(5);
        assertThat(requestList.get(0).getData()).hasSize(MAX_BUFFER_SIZE);
        assertThat(requestList.get(0).getCurrentTimeNs()).isGreaterThan(0L);
        assertThat(response.getSentBytes()).isGreaterThan((long) bytesToSend);
        assertThat(response.getDurationNs()).isGreaterThan(0L);
    }

    @Test
    public void testBandwidthTestToHost() {
        int bytesToReceive = 10;
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployerRunner runner = mock(DeployerRunner.class);
        DeployServer server = new DeployServer(bridge, runner);
        Service.NetworkTest request =
                Service.NetworkTest.newBuilder()
                        .setType(Service.NetworkTest.Type.BANDWIDTH)
                        .setHostToDevice(false)
                        .setNumberOfBytes(bytesToReceive)
                        .build();
        FakeInstaller installer = new FakeInstaller();
        Service.NetworkTestResponse response = server.doBandwidthTest(installer, request).build();
        List<Deploy.NetworkTestRequest> requestList = installer.getCapturedNetworkRequest();
        assertThat(requestList).hasSize(5);
        assertThat(requestList.get(0).getCurrentTimeNs()).isGreaterThan(0L);
        assertThat(response.getReceivedBytes()).isAtLeast((long) bytesToReceive);
        assertThat(response.getDurationNs()).isGreaterThan(0L);
    }

    private IDevice mockDevice(@NonNull String serial, @NonNull IDevice.DeviceState state) {
        IDevice device = mock(IDevice.class);
        when(device.getSerialNumber()).thenReturn(serial);
        when(device.getState()).thenReturn(state);
        when(device.getName()).thenReturn(serial);
        when(device.isEmulator()).thenReturn(false);
        when(device.getAbis()).thenReturn(Collections.singletonList("armeabi"));
        Client[] clients = new Client[] {mockClient(myPackageName, myProcessName)};
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
