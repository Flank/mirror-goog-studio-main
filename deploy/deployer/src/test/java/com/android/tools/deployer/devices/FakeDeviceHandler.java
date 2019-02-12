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
import java.io.PrintStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class FakeDeviceHandler extends DeviceCommandHandler {
    private final FakeDevice device;

    public FakeDeviceHandler(FakeDevice device) {
        super("");
        this.device = device;
    }

    @Override
    public boolean accept(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull DeviceState device,
            @NonNull String command,
            @NonNull String args) {
        try {
            switch (command) {
                case "shell":
                case "exec": // exec is different to shell, but we can interpret the same
                    return shell(args, socket);
                case "sync":
                    return sync(args, socket);
            }
            return false;
        } catch (IOException e) {
        }
        return false;
    }

    private boolean sync(String args, Socket socket) throws IOException {
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
                device.writeFile(name, data.toByteArray());
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

    private boolean shell(String args, Socket socket) throws IOException {
        // Interpret basic commands
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();
        String[] split = args.split(" ", 2);
        String cmd = split[0];
        List<String> cmdArgs = new ArrayList<>();
        if (split.length > 1) {
            String allArguments = split[1];
            cmdArgs = splitArguments(allArguments);
        }
        CommandHandler.writeOkay(output);
        device.getShell()
                .execute(
                        device,
                        cmd,
                        cmdArgs.toArray(new String[] {}),
                        input,
                        new PrintStream(output));
        return true;
    }

    private List<String> splitArguments(String allArguments) {
        // Basic bash un-quoting
        StringBuilder arg = new StringBuilder();
        List<String> cmdArgs = new ArrayList<>();
        boolean inQuotes = false;
        for (int i = 0; i < allArguments.length(); i++) {
            char c = allArguments.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ' ' && !inQuotes) {
                if (arg.length() > 0) {
                    cmdArgs.add(arg.toString());
                    arg = new StringBuilder();
                }
                continue;
            }
            arg.append(c);
        }
        if (arg.length() > 0) {
            cmdArgs.add(arg.toString());
        }
        return cmdArgs;
    }
}
