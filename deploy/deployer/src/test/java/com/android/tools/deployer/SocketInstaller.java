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

import com.android.annotations.NonNull;
import com.android.tools.deploy.proto.Deploy;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

public class SocketInstaller extends Installer implements AutoCloseable {

    private AdbInstallerChannel channel;
    private final ILogger logger = new StdLogger(StdLogger.Level.INFO);

    private ServerSocket serverSocket;

    private static final String LOCALHOST = "localhost";

    private final Path installerPath;

    public SocketInstaller(Path path) {
        this.installerPath = path;
        prepareInstaller();
    }

    private void prepareInstaller() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(LOCALHOST, 0));
            new Thread(() -> startServerSocket(installerPath)).start();

            SocketChannel socket = SocketChannel.open();
            socket.connect(new InetSocketAddress(LOCALHOST, serverSocket.getLocalPort()));
            channel = new AdbInstallerChannel(socket, logger);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int startServerSocket(Path execPath) {
        try {
            // Spawn the installer process
            ProcessBuilder builder = new ProcessBuilder(execPath.toString(), "-daemon");
            Process pro = builder.start();
            InputStream in = pro.getInputStream();
            OutputStream out = pro.getOutputStream();

            // Accept the connection and start forwarding input/outputs.
            Socket socket = serverSocket.accept();
            Thread inputForward = new ThreadInputForward(in, socket);
            inputForward.start();

            Thread outputForward = new ThreadOutputForward(out, socket);
            outputForward.start();

            // Join threads when process is finished.
            try {
                inputForward.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                outputForward.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return serverSocket.getLocalPort();
    }

    @Override
    @NonNull
    protected Deploy.InstallerResponse sendInstallerRequest(
            Deploy.InstallerRequest request, long timeOutMs) throws IOException {
        if (channel.isClosed()) {
            close();
            prepareInstaller();
        }
        try {
            channel.lock();
            channel.writeRequest(request, timeOutMs);
            Deploy.InstallerResponse resp = channel.readResponse(timeOutMs);
            if (resp == null) {
                throw new IOException(
                        "Unable to read response for " + request.getRequestCase().name());
            }
            return resp;
        } catch (TimeoutException e) {
            e.printStackTrace();
        } finally {
            channel.unlock();
        }
        throw new IOException(
                "Unable to complete request '" + request.getRequestCase().name() + "'");
    }

    @Override
    protected void onAsymetry(Deploy.InstallerRequest req, Deploy.InstallerResponse resp) {
        try {
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        prepareInstaller();
    }

    @Override
    public void close() {
        try (ServerSocket s = serverSocket;
                AdbInstallerChannel c = channel; ) {
            System.out.println("Closing serverSocket");
            System.out.println("Closing channel");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class ThreadInputForward extends Thread {
        private final InputStream in;
        private final Socket socket;

        public ThreadInputForward(InputStream in, Socket socket) {
            this.in = in;
            this.socket = socket;
            this.setName("Socket -> Installer");
        }

        @Override
        public void run() {
            try (InputStream in = this.in) {
                byte[] buffer = new byte[2038];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    System.out.println("Read " + read + " bytes from installer -> socket");
                    socket.getOutputStream().write(buffer, 0, read);
                    socket.getOutputStream().flush();
                }
                System.out.println("ThreadInputForward ending");
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private class ThreadOutputForward extends Thread {

        private final OutputStream out;
        private final Socket socket;

        public ThreadOutputForward(OutputStream out, Socket socket) {
            this.out = out;
            this.socket = socket;
            this.setName("Installer -> Socket");
        }

        @Override
        public void run() {
            try (OutputStream out = this.out) {
                byte[] buffer = new byte[2038];
                int read;
                while ((read = socket.getInputStream().read(buffer)) != -1) {
                    System.out.println("Read " + read + " bytes from socket -> Installer");
                    out.write(buffer, 0, read);
                    out.flush();
                }
                System.out.println("ThreadOutputForward ending");
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
