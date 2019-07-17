/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.deployer.devices;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.google.common.base.Charsets;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class FakeDeviceHandler extends DeviceCommandHandler {
    //@GuardedBy("devices")
    private final Set<FakeDevice> devices = new HashSet<>();

    public FakeDeviceHandler() {
        super("");
    }

    public void connect(@NonNull FakeDevice device, @NonNull FakeAdbServer server)
            throws ExecutionException, InterruptedException {
        device.connectTo(server);
        synchronized (devices) {
            devices.add(device);
        }
    }

    @Override
    public boolean accept(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull DeviceState deviceState,
            @NonNull String command,
            @NonNull String args) {
        try {
            synchronized (devices) {
                for (FakeDevice device : devices) {
                    if (!device.isDevice(deviceState)) {
                        continue;
                    }

                    switch (command) {
                        case "shell":
                        case "exec": // exec is different to shell, but we can interpret the same
                            return shell(device, args, socket);
                        case "sync":
                            return sync(device, args, socket);
                    }
                    return false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return false;
    }

    private boolean sync(FakeDevice device, String args, Socket socket) throws IOException {
        OutputStream output = socket.getOutputStream();
        CommandHandler.writeOkay(output);
        InputStream input = socket.getInputStream();
        String command = readString(input, 4);
        int length = readLength(input);
        String file = readString(input, length);

        switch (command) {
            case "SEND":
                int ix = file.lastIndexOf(',');
                String name = file.substring(0, ix);
                String mode = file.substring(ix + 1);
                ByteArrayDataOutput data = ByteStreams.newDataOutput();
                String chunkId = readString(input, 4);
                while (chunkId.equals("DATA")) {
                    int chunk = readLength(input);
                    byte[] bytes = new byte[chunk];
                    ByteStreams.readFully(input, bytes);
                    data.write(bytes);
                    chunkId = readString(input, 4);
                }
                int modtime = readLength(input);
                device.writeFile(name, data.toByteArray(), mode);
                CommandHandler.writeOkay(output);
                output.write(new byte[] {0, 0, 0, 0});
        }

        return true;
    }

    private int readLength(InputStream input) throws IOException {
        byte[] lengthb = new byte[4];
        input.read(lengthb);
        return ByteBuffer.wrap(lengthb).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private String readString(InputStream input, int i) throws IOException {
        byte[] commandb = new byte[i];
        input.read(commandb);
        return new String(commandb, Charsets.UTF_8);
    }

    private boolean shell(FakeDevice device, String args, Socket socket) throws IOException {
        OutputStream output = socket.getOutputStream();
        InputStream input = new ShutdownSocketInputStream(socket);
        CommandHandler.writeOkay(output);
        device.getShell().execute(args, device.getShellUser(), output, input, device);
        return true;
    }

    public static class ShutdownSocketInputStream extends InputStream {
        private final InputStream stream;
        private final Socket socket;

        public ShutdownSocketInputStream(Socket socket) throws IOException {
            this.socket = socket;
            this.stream = socket.getInputStream();
        }

        @Override
        public int read() throws IOException {
            return stream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            // The default implementation of read array will wait to fill up the array
            // until a -1 is returned, the socket one however will return whatever it
            // has available and wait only when there is nothing in the socket.
            // This allows the running process to continue.
            return stream.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            socket.shutdownInput();
        }
    }
}
