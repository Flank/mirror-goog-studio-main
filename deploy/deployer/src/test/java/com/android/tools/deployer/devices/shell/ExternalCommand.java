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
package com.android.tools.deployer.devices.shell;

import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ExternalCommand extends ShellCommand {
    private final String executable;

    public ExternalCommand(String executable) {
        this.executable = executable;
    }

    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        FakeDevice device = context.getDevice();
        int code = 255;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            List<String> command = new ArrayList<>();
            File exe = new File(device.getStorage(), executable);
            command.add(exe.getAbsolutePath());
            command.add("-root=" + device.getStorage().getAbsolutePath());
            command.add("-shell=" + device.getShellBridge().getAbsolutePath());
            command.add("-shell-arg=" + serverSocket.getLocalPort());
            command.addAll(Arrays.asList(args));
            Thread executionThread = Thread.currentThread();
            BlockingQueue<Socket> commands = new LinkedBlockingQueue<>();

            new Thread(
                            () -> {
                                try {
                                    while (true) {
                                        commands.add(serverSocket.accept());
                                        executionThread.interrupt();
                                    }
                                } catch (IOException e) {
                                    // Socket closed, just exit
                                }
                            })
                    .start();

            Process process = new ProcessBuilder(command).start();
            PipeConnector inToProcess = new PipeConnector(stdin, process.getOutputStream());
            inToProcess.start();
            PipeConnector processToOut = new PipeConnector(process.getInputStream(), stdout);
            processToOut.start();
            while (process.isAlive()) {
                if (!commands.isEmpty()) {
                    try (Socket clientSocket = commands.poll()) {
                        InputStream inp = clientSocket.getInputStream();
                        ChunkedOutputStream out =
                                new ChunkedOutputStream(clientSocket.getOutputStream());
                        DataInputStream data = new DataInputStream(inp);
                        int size = data.readInt();
                        byte[] buffer = new byte[size];
                        ByteStreams.readFully(data, buffer);
                        String script = new String(buffer, Charsets.UTF_8);
                        int subCode =
                                device.getShell()
                                        .execute(script, context.getUser(), out, inp, device);
                        out.data.writeInt(0);
                        out.data.writeInt(subCode);
                        out.data.flush();
                    }
                }
                try {
                    code = process.waitFor();
                } catch (InterruptedException e) {
                }
            }
            try {
                // Wait for all the output to be read and sent
                processToOut.join();
            } catch (InterruptedException e) {
            }
        }
        return code;
    }

    private static class PipeConnector extends Thread {
        private final InputStream input;
        private final OutputStream output;

        private PipeConnector(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (IOException e) {
                // Ignore and exit the thread
            }
        }
    }

    private static class ChunkedOutputStream extends OutputStream {

        private final DataOutputStream data;

        private ChunkedOutputStream(OutputStream data) {
            this.data = new DataOutputStream(data);
        }

        @Override
        public void write(int b) throws IOException {
            data.writeInt(1);
            data.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            data.writeInt(len);
            data.write(b, off, len);
        }
    }

    @Override
    public String getExecutable() {
        return executable;
    }
}
