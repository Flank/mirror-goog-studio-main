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

import static com.android.ddmlib.IDevice.PROP_DEVICE_MODEL;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.deploy.service.proto.Deploy;
import com.android.deploy.service.proto.DeployServiceGrpc;
import com.google.common.annotations.VisibleForTesting;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Objects;

public class DeployServer extends DeployServiceGrpc.DeployServiceImplBase {

    private static final int GET_DEBUG_PORT_RETRY_LIMIT = 10;
    private AndroidDebugBridge myActiveBridge;
    private Server myServer;

    /**
     * This function is responsible for starting a GRPC server and blocks until the server is
     * terminated.
     *
     * @param port to open grpc server on
     * @param adbPath full path to adb.exe required by {@link AndroidDebugBridge}.
     */
    public void start(int port, String adbPath) throws InterruptedException, IOException {
        AndroidDebugBridge.init(true);
        myActiveBridge = AndroidDebugBridge.createBridge(adbPath, false);

        NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port);
        serverBuilder.addService(this);
        myServer = serverBuilder.build();
        myServer.start();
        myServer.awaitTermination();
    }

    public DeployServer() {}

    @VisibleForTesting
    public DeployServer(@NonNull AndroidDebugBridge bridge) {
        myActiveBridge = bridge;
    }

    @Override
    public void getDevices(
            Deploy.DeviceRequest request, StreamObserver<Deploy.DeviceResponse> responseObserver) {
        IDevice[] devices = myActiveBridge.getDevices();
        Deploy.DeviceResponse.Builder response = Deploy.DeviceResponse.newBuilder();
        for (IDevice device : devices) {
            response.addDevices(ddmDeviceToRpcDevice(device));
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getClients(
            Deploy.ClientRequest request, StreamObserver<Deploy.ClientResponse> responseObserver) {
        Deploy.ClientResponse.Builder response = Deploy.ClientResponse.newBuilder();
        for (IDevice device : myActiveBridge.getDevices()) {
            for (Client client : device.getClients()) {
                response.addClients(ddmClientToRpcClient(client));
            }
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getDebugPort(
            Deploy.DebugPortRequest request,
            StreamObserver<Deploy.DebugPortResponse> responseObserver) {
        Deploy.DebugPortResponse.Builder response = Deploy.DebugPortResponse.newBuilder();
        IDevice selectedDevice = null;
        Client selectedClient = null;
        for (IDevice device : myActiveBridge.getDevices()) {
            if (device.getSerialNumber().equals(request.getDeviceId())) {
                selectedDevice = device;
                break;
            }
        }
        if (selectedDevice == null) {
            // TODO (gijosh): Respond with error.
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
            return;
        }
        for (Client client : selectedDevice.getClients()) {
            if (client.getClientData().getPid() == request.getPid()) {
                selectedClient = client;
                break;
            }
        }
        if (selectedClient == null) {
            // TODO (gijosh): Respond with error.
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
            return;
        }
        // This logic mirros logic in the ConnectDebuggerTask used by Android Studio
        // When a client is created it will immediately start a debugging server. The
        // server then waits for a WAIT jdwp packet to indicicate the client is
        // waiting for a debugger to attach. If this loop timesout and returns early
        // the client may connect a debugger to a client that is not in this state.
        // The behavior at that point is undefined. Most of the time it will work,
        // some breakpoints may get missed.
        for (int i = 0; i < GET_DEBUG_PORT_RETRY_LIMIT; i++) {
            try {
                // Wait until we receive the WAIT packet.
                ClientData.DebuggerStatus status =
                        selectedClient.getClientData().getDebuggerConnectionStatus();
                if (status == ClientData.DebuggerStatus.WAITING) {
                    break;
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Sleep interrupted this is okay.
                break;
            }
        }
        response.setPort(selectedClient.getDebuggerListenPort());
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private static Deploy.Client ddmClientToRpcClient(Client client) {
        return Deploy.Client.newBuilder()
                .setPid(client.getClientData().getPid())
                .setName(client.getClientData().getPackageName())
                .build();
    }

    private Deploy.Device ddmDeviceToRpcDevice(IDevice device) {
        String avdOrEmpty = Objects.toString(device.getAvdName(), "");
        String modelOrEmpty = Objects.toString(device.getProperty(PROP_DEVICE_MODEL), "");
        return Deploy.Device.newBuilder()
                .addAllAbis(device.getAbis())
                .setAvd(avdOrEmpty)
                .setDevice(device.getName())
                .setIsEmulator(device.isEmulator())
                .setModel(modelOrEmpty)
                .setSerialNumber(device.getSerialNumber())
                .setStatus(device.getState().getState())
                .build();
    }
}
