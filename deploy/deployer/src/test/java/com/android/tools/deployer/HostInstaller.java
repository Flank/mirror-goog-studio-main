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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Arrays;

public class HostInstaller {

    private static final String LOCALHOST = "localhost";

    static SocketChannel spawn(Path installerPath) {
        return spawn(installerPath, new String[0]);
    }

    static SocketChannel spawn(Path installerPath, String[] options) {
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(LOCALHOST, 0));
            new Thread(() -> runServerSocket(installerPath, options, serverSocket)).start();

            SocketChannel socket = SocketChannel.open();
            socket.connect(new InetSocketAddress(LOCALHOST, serverSocket.getLocalPort()));
            return socket;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runServerSocket(Path execPath, String[] options, ServerSocket s) {
        Thread inputForward = null;
        Thread outputForward = null;
        try (ServerSocket serverSocket = s) {
            // Spawn the installer process
            String[] args = new String[options.length + 1];
            args[0] = execPath.toString();
            System.arraycopy(options, 0, args, 1, options.length);

            ProcessBuilder builder = new ProcessBuilder(Arrays.asList(args));
            Process pro = builder.start();
            InputStream in = pro.getInputStream();
            OutputStream out = pro.getOutputStream();

            // Accept the connection and start forwarding input/outputs.
            Socket socket = serverSocket.accept();
            inputForward = new ThreadInputForward(in, socket);
            inputForward.start();

            outputForward = new ThreadOutputForward(out, socket);
            outputForward.start();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Join threads when process is finished.
            try {
                if (inputForward != null) {
                    inputForward.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                if (outputForward != null) {
                    outputForward.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ThreadInputForward extends Thread {
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
                    socket.getOutputStream().write(buffer, 0, read);
                    socket.getOutputStream().flush();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private static class ThreadOutputForward extends Thread {

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
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
