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
import java.io.IOException;
import java.io.InputStream;

public class SessionPm extends ShellCommand {

    @Override
    public String execute(FakeDevice device, String[] args, InputStream input) throws IOException {
        try {
            return run(device, new Arguments(args), input);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public String run(FakeDevice device, Arguments args, InputStream input) throws IOException {
        String action = args.nextArgument();
        if (action == null) {
            return "Usage\n...message...\n";
        }

        switch (action) {
                // eg: pm install-create -r -t -S 5047
            case "install-create":
                {
                    return String.format(
                            "Success: created install session [%d]\n", device.createSession());
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
                        return "Error: java.lang.IllegalArgumentException: Invalid name: null";
                    }

                    String path = args.nextArgument();
                    byte[] apk;
                    if (path == null || path.equals("-")) {
                        apk = new byte[size];
                        input.read(apk);
                    } else {
                        apk = device.readFile(path);
                    }
                    device.writeToSession(session, apk);
                    return String.format("Success: streamed %d bytes\n", size);
                }
            case "install-commit":
                {
                    device.commitSession(parseSession(device, args));
                    return "Success\n";
                }
            case "install-abandon":
                {
                    device.abandonSession(parseSession(device, args));
                    return "Success\n";
                }
        }
        return "Usage\n...message...\n";
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
