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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.deploy.service.proto.Deploy;
import io.grpc.stub.StreamObserver;
import java.util.Collections;
import org.junit.Test;

public class DeployServerTest {

    @Test
    public void getDevices() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge);
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
        DeployServer server = new DeployServer(bridge);
        FakeStreamObserver<Deploy.ClientResponse> response = new FakeStreamObserver<>();
        Deploy.ClientRequest request =
                Deploy.ClientRequest.newBuilder().setDeviceId("1234").build();
        server.getClients(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getClientsCount()).isEqualTo(1);
        assertThat(response.getResponse().getClients(0).getPid()).isEqualTo(1234);
    }

    @Test
    public void attachDebugger() {
        AndroidDebugBridge bridge = mock(AndroidDebugBridge.class);
        IDevice[] devices = new IDevice[] {mockDevice("1234", IDevice.DeviceState.ONLINE)};
        when(bridge.getDevices()).thenReturn(devices);
        DeployServer server = new DeployServer(bridge);
        FakeStreamObserver<Deploy.AttachResponse> response = new FakeStreamObserver<>();
        Deploy.AttachRequest request =
                Deploy.AttachRequest.newBuilder().setDeviceId("1234").setPid(1234).build();
        server.attachDebugger(request, response);
        assertThat(response.getResponse()).isNotNull();
        assertThat(response.getResponse().getPort()).isEqualTo(4321);
    }

    private IDevice mockDevice(@NonNull String serial, @NonNull IDevice.DeviceState state) {
        IDevice device = mock(IDevice.class);
        when(device.getSerialNumber()).thenReturn(serial);
        when(device.getState()).thenReturn(state);
        when(device.getName()).thenReturn(serial);
        when(device.isEmulator()).thenReturn(false);
        when(device.getAbis()).thenReturn(Collections.singletonList("armeabi"));
        Client[] clients = new Client[] {mockClient("Client1")};
        when(device.getClients()).thenReturn(clients);
        return device;
    }

    private Client mockClient(@NonNull String clientName) {
        Client client = mock(Client.class);
        ClientData clientData = mock(ClientData.class);
        when(client.getClientData()).thenReturn(clientData);
        when(clientData.getPid()).thenReturn(1234);
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
