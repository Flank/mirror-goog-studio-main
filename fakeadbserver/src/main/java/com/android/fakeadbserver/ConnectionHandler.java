/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.fakeadbserver;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Handle a connection according to adb SERVICE.TXT and protocol.txt. In a nutshell, a smart
 * connection is able to request HOST (adb server) services or LOCAL (adbd running on a device)
 * services. The smart part is the "transport" part which allows to "route" service request to a
 * device after the connection has been established with the HOST.
 */

// TODO: Rename this class SmartConnection (not doing it now to help review process but should
// be in next CL).
final class ConnectionHandler implements Runnable {

    @NonNull private final FakeAdbServer mServer;
    @NonNull private final SmartSocket mSmartSocket;
    private DeviceState mTargetDevice = null;
    private boolean mKeepRunning;

    ConnectionHandler(@NonNull FakeAdbServer server, @NonNull SocketChannel socket) {
        mServer = server;
        mSmartSocket = new SmartSocket(socket);
        mKeepRunning = true;
    }

    /**
     * Repeatedly read host service requests and serve them until a local request is received. After
     * service the local request, the connection is closed. Note that a host request can also
     * request the connection to end.
     */
    @Override
    public void run() {
        ServiceRequest request = new ServiceRequest("No request processed");
        try (mSmartSocket) {
            while (mKeepRunning) {
                request = mSmartSocket.readServiceRequest();
                if (request.peekToken().startsWith("host")) {
                    handleHostService(request);
                } else {
                    handleDeviceService(request);
                }
            }
        } catch (Exception e) {
            PrintWriter pw = new PrintWriter(new StringWriter());
            pw.print("Unable to process '" + request.original() + "'\n");
            e.printStackTrace(pw);
            mSmartSocket.sendFailWithReason("Exception occurred when processing request. " + pw);
        }
    }

    private void handleHostService(@NonNull ServiceRequest request) throws IOException {
        switch (request.nextToken()) {
            case "host": // host:command
                //  'host:command' can also be used to run a HOST command target a device. In that
                // case it should be interpreted as 'any single device or emulator connected
                // to/running
                // on the HOST'.
                //  e.g.: Port forwarding is a HOST command which targets a device.
                List<DeviceState> devices = findAnyDevice();
                if (devices.size() == 1) {
                    mTargetDevice = devices.get(0);
                }
                break;
            case "host-usb": // host-usb:command
                List<DeviceState> devicesUSB =
                        findDevicesWithProtocol(DeviceState.HostConnectionType.USB);
                if (devicesUSB.size() != 1) {
                    reportDevicesErrorAndStop(devicesUSB.size());
                    return;
                }
                mTargetDevice = devicesUSB.get(0);
                break;
            case "host-local": // host-local:command
                List<DeviceState> emulators =
                        findDevicesWithProtocol(DeviceState.HostConnectionType.LOCAL);
                if (emulators.size() != 1) {
                    reportDevicesErrorAndStop(emulators.size());
                    return;
                }
                mTargetDevice = emulators.get(0);
                break;
            case "host-serial": // host-serial:serial:command
                String serial = request.nextToken();
                Optional<DeviceState> device = findDeviceWithSerial(serial);
                if (device.isEmpty()) {
                    reportErrorAndStop("No device with serial: '" + serial + "' is connected.");
                    return;
                }
                mTargetDevice = device.get();
                break;
            default:
                String err =
                        String.format(
                                Locale.US,
                                "Command not handled '%s' '%s'",
                                request.currToken(),
                                request.original());
                mSmartSocket.sendFailWithReason(err);
                return;
        }
        dispatchToHostHandlers(request);
    }

    private void reportDevicesErrorAndStop(int numDevices) {
        String msg;
        if (numDevices == 0) {
            msg = "No devices available.";
        } else {
            msg =
                    String.format(
                            Locale.US,
                            "More than one device found (%d). Please specify which.",
                            numDevices);
        }
        reportErrorAndStop(msg);
    }

    private void reportErrorAndStop(@NonNull String msg) {
        mKeepRunning = false;
        mSmartSocket.sendFailWithReason(msg);
    }

    // IN SERVICE.TXT these are called "LOCAL".
    private void handleDeviceService(@NonNull ServiceRequest request) {
        // Regardless of the outcome, this will be the last request this connection handles.
        mKeepRunning = false;

        if (mTargetDevice == null) {
            mSmartSocket.sendFailWithReason("No device available to honor LOCAL service request");
            return;
        }

        String serviceName = request.nextToken();
        String command = request.remaining();
        for (DeviceCommandHandler handler : mServer.getHandlers()) {
            boolean accepted =
                    handler.accept(
                            mServer, mSmartSocket.getSocket(), mTargetDevice, serviceName, command);
            if (accepted) {
                return;
            }
        }
        mSmartSocket.sendFailWithReason("Unknown request " + serviceName + "-" + command);
    }

    private void dispatchToHostHandlers(@NonNull ServiceRequest request) throws IOException {
        // Intercepting the host:transport* service request because it has the special side-effect
        // of changing the target device.
        // TODO: This should be in a host TransportCommandHandler! Following in next CL.
        if (request.peekToken().startsWith("transport")) {
            switch (request.nextToken()) {
                case "transport": // host:transport:serialnumber
                    String serial = request.nextToken();
                    Optional<DeviceState> device = findDeviceWithSerial(serial);
                    if (device.isEmpty()) {
                        reportErrorAndStop("No device with serial: '" + serial + "' is connected.");
                        return;
                    }
                    mTargetDevice = device.get();
                    break;
                case "transport-usb": // host:transport-usb
                    List<DeviceState> devicesUSB =
                            findDevicesWithProtocol(DeviceState.HostConnectionType.USB);
                    if (devicesUSB.size() != 1) {
                        reportDevicesErrorAndStop(devicesUSB.size());
                        return;
                    }
                    mTargetDevice = devicesUSB.get(0);
                    break;
                case "transport-local": // host:transport-local
                    List<DeviceState> emulators =
                            findDevicesWithProtocol(DeviceState.HostConnectionType.LOCAL);
                    if (emulators.size() != 1) {
                        reportDevicesErrorAndStop(emulators.size());
                        return;
                    }
                    mTargetDevice = emulators.get(0);
                    break;
                case "transport-any": // host:transport-any
                    List<DeviceState> allDevices = findAnyDevice();
                    if (allDevices.size() < 1) {
                        reportDevicesErrorAndStop(allDevices.size());
                        return;
                    }
                    mTargetDevice = allDevices.get(0);
                    break;
                default:
                    String err =
                            String.format(
                                    Locale.US, "Unsupported request '%s'", request.original());
                    mSmartSocket.sendFailWithReason(err);
            }
            mSmartSocket.sendOkay();
            return;
        }

        HostCommandHandler handler = mServer.getHostCommandHandler(request.nextToken());
        if (handler == null) {
            String err =
                    String.format(
                            Locale.US,
                            "Unimplemented host command received: '%s'",
                            request.currToken());
            mSmartSocket.sendFailWithReason(err);
            return;
        }

        mKeepRunning =
                handler.invoke(
                        mServer, mSmartSocket.getSocket(), mTargetDevice, request.remaining());
    }

    @NonNull
    private List<DeviceState> findAnyDevice() {
        return findDevicesWithProtocols(
                Arrays.asList(
                        DeviceState.HostConnectionType.LOCAL, DeviceState.HostConnectionType.USB));
    }

    @NonNull
    private List<DeviceState> findDevicesWithProtocol(
            @NonNull DeviceState.HostConnectionType type) {
        return findDevicesWithProtocols(Collections.singletonList(type));
    }

    @NonNull
    private List<DeviceState> findDevicesWithProtocols(
            @NonNull List<DeviceState.HostConnectionType> types) {
        List<DeviceState> candidates;
        List<DeviceState> devices = new ArrayList<>();
        try {
            candidates = mServer.getDeviceListCopy().get();
        } catch (ExecutionException | InterruptedException ignored) {
            mSmartSocket.sendFailWithReason(
                    "Internal server failure while retrieving device list.");
            mKeepRunning = false;
            return new ArrayList<>();
        }
        for (DeviceState device : candidates) {
            if (types.contains(device.getHostConnectionType())) {
                devices.add(device);
            }
        }
        return devices;
    }

    @NonNull
    private Optional<DeviceState> findDeviceWithSerial(@NonNull String serial) {
        try {
            List<DeviceState> devices = mServer.getDeviceListCopy().get();
            Optional<DeviceState> streamResult =
                    devices.stream()
                            .filter(streamDevice -> serial.equals(streamDevice.getDeviceId()))
                            .findAny();
            return streamResult;
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
