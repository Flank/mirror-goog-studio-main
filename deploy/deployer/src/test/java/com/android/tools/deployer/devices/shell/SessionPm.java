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
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class SessionPm extends ShellCommand {

    @Override
    public void execute(FakeDevice device, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        try {
            run(device, new Arguments(args), stdin, stdout);
        } catch (IllegalArgumentException e) {
            stdout.println(e.getMessage());
        }
    }

    public void run(FakeDevice device, Arguments args, InputStream stdin, PrintStream stdout)
            throws IOException {
        String action = args.nextArgument();
        if (action == null) {
            stdout.println("Usage\n...message...");
            return;
        }

        switch (action) {
                // eg: pm install-create -r -t -S 5047
            case "install-create":
                {
                    stdout.format(
                            "Success: created install session [%d]\n", device.createSession());
                    return;
                }
                // eg: install-write -S 5047 100000000 0_sample -
            case "install-write":
                {
                    int size = 0;
                    String opt = args.nextOption();
                    if ("-S".equals(opt)) {
                        // This should be a long, but we keep all the files in memory, so we use it as int for an array.
                        size = parse(args.nextArgument(), "Invalid long");
                    }
                    int session = parseSession(device, args);
                    if (args.nextArgument() == null) {
                        stdout.println(
                                "Error: java.lang.IllegalArgumentException: Invalid name: null");
                        return;
                    }

                    String path = args.nextArgument();
                    byte[] apk;
                    if (path == null || path.equals("-")) {
                        apk = new byte[size];
                        ByteStreams.readFully(stdin, apk);
                    } else {
                        apk = device.readFile(path);
                    }
                    device.writeToSession(session, apk);
                    stdout.format("Success: streamed %d bytes\n", size);
                    return;
                }
            case "install-commit":
                {
                    device.commitSession(parseSession(device, args));
                    stdout.println("Success");
                    return;
                }
            case "install-abandon":
                {
                    device.abandonSession(parseSession(device, args));
                    stdout.println("Success");
                    return;
                }
        }
        stdout.println("Usage\n...message...");
    }

    public int parseSession(FakeDevice device, Arguments args) {
        int session = parse(args.nextArgument(), "Invalid int");
        if (!device.isValidSession(session)) {
            throw new IllegalArgumentException(
                    "Error: java.lang.SecurityException: Caller has no access to session "
                            + session);
        }
        return session;
    }

    public int parse(String value, String message) {
        int size;
        try {
            size = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Error: java.lang.NumberFormatException: %s: \"%s\"", message, value));
        }
        return size;
    }

    @Override
    public String getExecutable() {
        return "pm";
    }
}
