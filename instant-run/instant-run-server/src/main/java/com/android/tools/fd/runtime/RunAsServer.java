/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.fd.runtime;

import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_EOF;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_PING;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_SEND_FILE;
import static com.android.tools.fd.common.ProtocolConstants.MESSAGE_SHELL_COMMAND;
import static com.android.tools.fd.common.ProtocolConstants.PROTOCOL_IDENTIFIER;
import static com.android.tools.fd.common.ProtocolConstants.PROTOCOL_VERSION;
import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;

import com.android.annotations.NonNull;

import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Server running in an independent process, which can run arbitrary shell
 * commands as the app's userid.  Used to work around devices that have broken
 * run-as functionality.  Has to run in a separate process so that it can copy
 * files when the app is dead.
 */
public class RunAsServer extends Service {

    // Debugging: have the server emit a message to the log every 30 seconds to
    // indicate whether it's still alive.
    private static final boolean POST_ALIVE_STATUS = false;
    private static final long POST_ALIVE_INTERVAL = 30000L;

    private LocalServerSocket mServerSocket;

    private static int sWrongTokenCount;

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        // TODO:  startForeground() with a notification, to make the service
        // robust against restarts from low-mem-pressure, etc.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Application app = getApplication();
        // This has to match the value in InstantRunClient.startRunAsService().
        String socketName = app.getClass().getPackage().getName() + ".runas";
        start(socketName);
    }

    @Override
    public IBinder onBind (Intent intent) {
        // Don't allow anyone to bind to this service.
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            // Try shutting down the serversocket.
            mServerSocket.close();
        } catch (Throwable t) {
            // No big deal.
        }
    }

    /**
     * Create the local server socket and start the acceptor thread.
     */
    private void start(@NonNull String socketName) {
        try {
            mServerSocket = new LocalServerSocket(socketName);
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Starting run-as server socket listening for package " +
                        socketName + " on " + mServerSocket.getLocalSocketAddress());
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "IO Error creating local socket at " + socketName, e);
            return;
        }

        try {
            Thread socketServerThread = new Thread(new SocketServerThread());
            socketServerThread.start();
        } catch (Throwable e) {
            // Make sure an exception doesn't cause the rest of the user's
            // onCreate() method to be invoked
            if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                Log.e(LOG_TAG, "Fatal error starting Instant Run Run-As server", e);
            }
        }

        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, "Started run-as server for package " + socketName);
        }
    }

    private class SocketServerThread extends Thread {
        @Override
        public void run() {
            if (POST_ALIVE_STATUS) {
                try {
                    startStatusThread();
                } catch (Throwable t) {
                    if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                        Log.v(LOG_TAG, "Run-as server unable to start status thread: " + t);
                    }
                }
            }
            try {
                runLoop();
            } finally {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Run-as server exiting run loop");
                }
            }
        }

        private void runLoop() {
            while (true) {
                try {
                    LocalServerSocket serverSocket = mServerSocket;
                    if (serverSocket == null) {
                        break;
                    }
                    LocalSocket socket = serverSocket.accept();

                    if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                        Log.v(LOG_TAG,
                              "Received run-as connection from IDE: spawning connection thread");
                    }

                    SocketServerReplyThread socketServerReplyThread =
                            new SocketServerReplyThread(socket);
                    socketServerReplyThread.run();

                    if (sWrongTokenCount > 50) {
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG,
                                  "Stopping run-as server: too many wrong token connections");
                        }
                        mServerSocket.close();
                        break;
                    }
                } catch (Throwable e) {
                    if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                        Log.v(LOG_TAG,
                              "Fatal error accepting run-as connection on local socket", e);
                    }
                }
            }
        }

        private void startStatusThread() {
            // I was unable to get this to print anything using a Looper+Handler,
            // as is done in ./Server.java.  But this approach works fine.
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        Log.v(LOG_TAG, "Instant Run Run-As server still here...");
                    }
                };
            timer.schedule(task, 1, POST_ALIVE_INTERVAL);
        }
    }

    private class SocketServerReplyThread extends Thread {

        private final LocalSocket mSocket;

        SocketServerReplyThread(LocalSocket socket) {
            mSocket = socket;
        }

        @Override
        public void run() {
            try {
                DataInputStream input = new DataInputStream(mSocket.getInputStream());
                DataOutputStream output = new DataOutputStream(mSocket.getOutputStream());
                try {
                    handle(input, output);
                } finally {
                    try {
                        input.close();
                    } catch (IOException ignore) {
                    }
                    try {
                        output.close();
                    } catch (IOException ignore) {
                    }
                }
            } catch (IOException e) {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Fatal error receiving messages for run-as server", e);
                }
            }
        }

        private void handle(DataInputStream input, DataOutputStream output) throws IOException {
            long magic = input.readLong();
            if (magic != PROTOCOL_IDENTIFIER) {
                Log.w(LOG_TAG, "Unrecognized header format " + Long.toHexString(magic));
                return;
            }
            int version = input.readInt();

            // Send current protocol version to the IDE so it can decide what to do
            output.writeInt(PROTOCOL_VERSION);

            if (version != PROTOCOL_VERSION) {
                Log.w(LOG_TAG, "Run-as: Mismatched protocol versions; app is "
                        + "using version " + PROTOCOL_VERSION + " and tool is using version "
                        + version);
                return;
            }

            while (true) {
                int message = input.readInt();
                switch (message) {
                    case MESSAGE_EOF: {
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG, "Run-as: Received EOF from the IDE");
                        }
                        return;
                    }

                    case MESSAGE_PING: {
                        // Send an "ack" back to the IDE.
                        output.writeBoolean(true);
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG, "Received Ping message from the IDE; " +
                                    "returned active = true");
                        }
                        continue;
                    }

                    case MESSAGE_SHELL_COMMAND: {
                        if (!authenticate(input)) {
                            output.writeBoolean(false);
                            return;
                        }
                        String cmd = input.readUTF();
                        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                            Log.v(LOG_TAG, "Run-as: read shell command: " + cmd);
                        }
                        // Try executing the shell command, but keep going on failure.
                        try {
                            executeShellCommand(cmd);
                            output.writeBoolean(true);
                        } catch (Exception x) {
                            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                                Log.v(LOG_TAG, "Run-as: Error executing command: " + x);
                            }
                            output.writeBoolean(false);
                        }
                        continue;
                    }

                    default: {
                        if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                            Log.e(LOG_TAG, "Unexpected message type: " + message);
                        }
                        // If we hit unexpected message types we can't really continue
                        // the conversation: we can misinterpret data for the unexpected
                        // command as separate messages with different meanings than intended
                        return;
                    }
                }
            }
        }

        private boolean authenticate(@NonNull DataInputStream input) throws IOException {
            long token = input.readLong();
            if (token != AppInfo.token) {
                Log.w(LOG_TAG, "Mismatched identity token from client; received " + token
                        + " and expected " + AppInfo.token);
                sWrongTokenCount++;
                return false;
            }
            return true;
        }
    }

    private static void executeShellCommand(String command)
            throws IOException, InterruptedException {
        String[] cmd = {
            "/system/bin/sh",
            "-c",
            command
        };
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.e(LOG_TAG, "Run-as:  executing /system/bin/sh -c " + command);
        }
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
        int result = p.exitValue();
        if (result == 0) {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.e(LOG_TAG, "Run-as:  command executed successfully");
            }
        } else {
            if (Log.isLoggable(LOG_TAG, Log.ERROR)) {
                Log.e(LOG_TAG, "");
            }
        }
    }
}
