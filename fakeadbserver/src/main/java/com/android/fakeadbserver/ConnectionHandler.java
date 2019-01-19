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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ShellCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ShellHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

final class ConnectionHandler implements Runnable {

    // The ADB protocol allows certain commands to address a single existing device on a/any
    // transport. The following are commands that don't rely on wildcards on a transport level.
    private static final Set<String> NON_WILDCARD_TRANSPORT_DEVICE_COMMANDS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(
                    "version", "kill", "devices", "devices-l", "track-devices", "emulator",
                    "transport", "transport-usb", "transport-local", "transport-any"
            )));

    @NonNull
    private final FakeAdbServer mServer;

    @NonNull
    private final Socket mSocket;

    @NonNull
    private final Map<String, Supplier<HostCommandHandler>> mHostCommandHandlers;

    @NonNull
    private final Map<String, Supplier<DeviceCommandHandler>> mDeviceCommandHandlers;

    @NonNull
    private final Map<String, Supplier<ShellCommandHandler>> mShellCommandHandlers;

    @NonNull private final Map<Pattern, Supplier<ShellHandler>> mShellHandlers;

    ConnectionHandler(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull Map<String, Supplier<HostCommandHandler>> hostCommandHandlers,
            @NonNull Map<String, Supplier<DeviceCommandHandler>> deviceCommandHandlers,
            @NonNull Map<String, Supplier<ShellCommandHandler>> shellCommandHandlers,
            @NonNull Map<Pattern, Supplier<ShellHandler>> shellHandlers) {
        mServer = server;
        mSocket = socket;
        mHostCommandHandlers = hostCommandHandlers;
        mDeviceCommandHandlers = deviceCommandHandlers;
        mShellCommandHandlers = shellCommandHandlers;
        mShellHandlers = shellHandlers;
    }

    @Override
    public void run() {
        boolean keepRunning = true;
        DeviceState targetDevice = null;
        try {
            while (keepRunning) {
                if (targetDevice == null) {
                    HostRequest request = parseHostRequest();
                    if (request == null) {
                        // Something went wrong with the request, and parseHostRequest already
                        // sent a failure message with the context.
                        return;
                    }

                    if (request.mCommand.startsWith("transport")) {
                        targetDevice = request.mTargetDevice;
                        sendOkay();
                    } else if (mHostCommandHandlers.containsKey(request.mCommand)) {
                        keepRunning = mHostCommandHandlers.get(request.mCommand).get()
                                .invoke(mServer, mSocket, request.mTargetDevice,
                                        request.mArguments);
                    } else {
                        sendFailWithReason(
                                "Unimplemented host command received: " + request.mCommand);
                    }
                } else {
                    Request request = parseDeviceRequest();
                    if (request == null) {
                        // Something went wrong with the request, and parseDeviceRequest already
                        // sent a failure message with the context.
                        return;
                    }
                    if (request.mCommand.equals("shell")) {
                        String[] splitShellString = request.mArguments.split(" ", 2);
                        if (mShellCommandHandlers.containsKey(splitShellString[0])) {
                            if (!mShellCommandHandlers.get(splitShellString[0]).get()
                                    .invoke(mServer, mSocket, targetDevice,
                                            splitShellString.length > 1 ? splitShellString[1]
                                                    : null)) {
                                return;
                            }
                        } else {
                            // We can't find a ShellCommandHandler to process the shell command,
                            // so we fall back to generalized handlers that are installed.
                            boolean matched = false;
                            for (Pattern pattern : mShellHandlers.keySet()) {
                                if (pattern.matcher(request.mArguments).matches()) {
                                    matched = true;
                                    if (!mShellHandlers
                                            .get(pattern)
                                            .get()
                                            .invoke(
                                                    mServer,
                                                    mSocket,
                                                    targetDevice,
                                                    request.mArguments)) {
                                        return;
                                    }
                                }
                            }
                            if (!matched) {
                                sendFailWithReason(
                                        "Unimplemented shell command received: "
                                                + request.mArguments);
                            }
                        }
                    } else if (mDeviceCommandHandlers.containsKey(request.mCommand)) {
                        if (!mDeviceCommandHandlers.get(request.mCommand).get()
                                .invoke(mServer, mSocket, targetDevice, request.mArguments)) {
                            return;
                        }
                    } else {
                        sendFailWithReason(
                                "Unimplemented device command received: " + request.mCommand);
                    }
                }
            }
        } catch (RuntimeException e) {
            sendFailWithReason("Bad request received: " + e.toString());
        } catch (IOException ignored) {
            sendFailWithReason("IOException occurred when processing request.");
        } finally {
            try {
                mSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Nullable
    private HostRequest parseHostRequest() throws IOException {
        byte[] lengthString = new byte[4];
        readFully(lengthString);
        int requestLength = Integer.parseInt(new String(lengthString), 16);
        assert requestLength > "host:".length();

        byte[] payloadBytes = new byte[requestLength];
        readFully(payloadBytes);
        String payload = new String(payloadBytes, US_ASCII);

        String[] splitPayload = payload.split(":", 2);
        if (splitPayload.length < 2) {
            sendFailWithReason("Invalid host command: " + payload);
            return null;
        }

        DeviceState device = null;
        switch (splitPayload[0]) {
            case "host":
                String[] transportSplit = splitPayload[1].split(":");
                String command = transportSplit[0];
                if (!NON_WILDCARD_TRANSPORT_DEVICE_COMMANDS.contains(command)) {
                    device =
                            findAnyDeviceWithProtocol(
                                    Arrays.asList(
                                            DeviceState.HostConnectionType.LOCAL,
                                            DeviceState.HostConnectionType.USB));
                    if (device == null) {
                        return null;
                    }
                } else if (command.startsWith("transport")) {
                    switch (command) {
                        case "transport":
                            device = findDeviceWithSerial(transportSplit[1]);
                            break;
                        case "transport-usb":
                            device =
                                    findAnyDeviceWithProtocol(
                                            Collections.singletonList(
                                                    DeviceState.HostConnectionType.USB));
                            break;
                        case "transport-local":
                            device =
                                    findAnyDeviceWithProtocol(
                                            Collections.singletonList(
                                                    DeviceState.HostConnectionType.LOCAL));
                            break;
                        case "transport-any":
                            device =
                                    findAnyDeviceWithProtocol(
                                            Arrays.asList(
                                                    DeviceState.HostConnectionType.LOCAL,
                                                    DeviceState.HostConnectionType.USB));
                            break;
                        default:
                            sendFailWithReason("Invalid command specified in payload: " + payload);
                            return null;
                    }

                    if (device == null) {
                        return null;
                    }
                }
                break;
            case "host-usb":
                device =
                        findAnyDeviceWithProtocol(
                                Collections.singletonList(DeviceState.HostConnectionType.USB));
                if (device == null) {
                    return null;
                }
                break;
            case "host-local":
                device =
                        findAnyDeviceWithProtocol(
                                Collections.singletonList(DeviceState.HostConnectionType.LOCAL));
                if (device == null) {
                    return null;
                }
                break;
            case "host-serial":
                splitPayload = splitPayload[1].split(":", 2);
                String serial = splitPayload[0];
                device = findDeviceWithSerial(serial);
                break;
            default:
                sendFailWithReason("Invalid transport specified in payload: " + payload);
                return null;
        }

        splitPayload = splitPayload[1].split(":", 2);
        return new HostRequest(device, splitPayload[0],
                splitPayload.length > 1 ? splitPayload[1] : "");
    }

    @Nullable
    private DeviceState findAnyDeviceWithProtocol(
            @NonNull List<DeviceState.HostConnectionType> acceptibleConnectionTypes) {
        List<DeviceState> deviceList;
        try {
            deviceList = mServer.getDeviceListCopy().get();
        } catch (ExecutionException | InterruptedException ignored) {
            sendFailWithReason("Internal server failure while processing command.");
            return null;
        }

        DeviceState foundDevice = null;
        for (DeviceState device : deviceList) {
            if (!acceptibleConnectionTypes.contains(device.getHostConnectionType())) {
                continue;
            }

            if (foundDevice == null) {
                foundDevice = device;
            } else {
                sendFailWithReason("More than one device on the USB bus. Please specify which.");
                return null;
            }
        }

        if (foundDevice == null) {
            sendFailWithReason("No devices available on the USB bus.");
            return null;
        }
        return foundDevice;
    }

    @Nullable
    private DeviceState findDeviceWithSerial(@NonNull String serial) {
        try {
            List<DeviceState> devices = mServer.getDeviceListCopy().get();
            Optional<DeviceState> streamResult = devices.stream()
                    .filter(streamDevice -> serial.equals(streamDevice.getDeviceId()))
                    .findAny();
            if (!streamResult.isPresent()) {
                sendFailWithReason("No device with serial: " + serial + " is connected.");
                return null;
            }
            return streamResult.get();
        } catch (InterruptedException | ExecutionException e) {
            sendFailWithReason("Internal server error while retrieving device list.");
            return null;
        }
    }

    @Nullable
    private Request parseDeviceRequest() throws IOException {
        byte[] lengthString = new byte[4];
        readFully(lengthString);
        int requestLength = Integer.parseInt(new String(lengthString), 16);

        byte[] payloadBytes = new byte[requestLength];
        readFully(payloadBytes);
        String payload = new String(payloadBytes, US_ASCII);

        // The track-jdwp packet comes without a trailing colon, so we need to special-case it
        String[] splitPayload =
                payload.equals("track-jdwp") ? new String[] {payload, ""} : payload.split(":", 2);
        if (splitPayload.length < 2) {
            sendFailWithReason("Invalid host command: " + payload);
            return null;
        }

        return new Request(splitPayload[0], splitPayload[1]);
    }

    private void readFully(@NonNull byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            bytesRead += mSocket.getInputStream()
                    .read(buffer, bytesRead, buffer.length - bytesRead);
        }
    }

    private void sendOkay() throws IOException {
        OutputStream stream = mSocket.getOutputStream();
        stream.write("OKAY".getBytes(US_ASCII));
    }

    private void sendFailWithReason(@NonNull String reason) {
        try {
            OutputStream stream = mSocket.getOutputStream();
            stream.write("FAIL".getBytes(US_ASCII));
            byte[] reasonBytes = reason.getBytes(UTF_8);
            assert reasonBytes.length < 65536;
            stream.write(String.format("%04x", reason.length()).getBytes(US_ASCII));
            stream.write(reasonBytes);
            stream.flush();
        } catch (IOException ignored) {
        }
    }

    private static class Request {

        @NonNull
        protected String mCommand;

        @NonNull
        protected String mArguments;

        private Request(@NonNull String command, @NonNull String arguments) {
            mCommand = command;
            mArguments = arguments;
        }
    }

    private static class HostRequest extends Request {

        @Nullable
        protected DeviceState mTargetDevice;

        private HostRequest(@Nullable DeviceState targetDevice, @NonNull String command,
                @NonNull String arguments) {
            super(command, arguments);
            mTargetDevice = targetDevice;
        }
    }
}
