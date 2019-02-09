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

public class Cmd extends ShellCommand {

    private final boolean reportCommitSuccess;

    public Cmd(boolean reportSuccessOnCommit) {
        reportCommitSuccess = reportSuccessOnCommit;
    }

    public Cmd() {
        this(true);
    }

    @Override
    public String execute(FakeDevice device, String[] args, InputStream input) throws IOException {
        try {
            return run(device, new Arguments(args), input);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public String run(FakeDevice device, Arguments args, InputStream input) throws IOException {
        String service = args.nextArgument();
        if (service == null) {
            return "cmd: no service specified; use -l to list all services\n";
        }

        switch (service) {
            case "package":
                String action = args.nextArgument();
                if (action == null) {
                    return "Usage\n...message...\n";
                }
                switch (action) {
                        // eg: pm install-create -r -t -S 5047
                    case "install-create":
                        return String.format(
                                "Success: created install session [%d]\n", device.createSession());
                        // eg: install-write -S 5047 100000000 0_sample -
                    case "install-write":
                        {
                            String opt = args.nextOption();
                            if (opt == null) {
                                return "Error: must specify a APK size\n";
                            } else if (!opt.equals("-S")) {
                                return String.format(
                                        "\nException occurred while dumping:\n"
                                                + "java.lang.IllegalArgumentException: Unknown option %s\n\tat com...\n",
                                        opt);
                            }
                            // This should be a long, but we keep all the files in memory. Int is enough for tests.
                            int size = parseInt(args.nextArgument());
                            int session = parseSession(device, args);
                            String name = args.nextArgument();
                            if (name == null) {
                                return "\nException occurred while dumping:\n"
                                        + "java.lang.IllegalArgumentException: Invalid name: null\n\tat com...\n";
                            }
                            String path = args.nextArgument();
                            byte[] apk;
                            if (path == null || path.equals("-")) {
                                apk = new byte[size];
                                input.read(apk);
                            } else {
                                return "Error: APK content must be streamed\n";
                            }
                            device.writeToSession(session, apk);
                            return String.format("Success: streamed %d bytes\n", size);
                        }
                    case "install-commit":
                        {
                            device.commitSession(parseSession(device, args));
                            // On some APIs the "Success" part of install-commit is not printed. We allow this
                            // to be configured so we can reproduce that odd behaviour.
                            return (reportCommitSuccess ? "Success\n" : "");
                        }
                    case "install-abandon":
                        device.abandonSession(parseSession(device, args));
                        return "Success\n";
                }
                break;
        }
        return "Can't find service: " + service;
    }

    public int parseSession(FakeDevice device, Arguments args) {
        int session = parseInt(args.nextArgument());
        if (!device.isValidSession(session)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Security exception: Caller has no access to session %d\n\n"
                                    + "java.lang.SecurityException: Caller has no access to session %d\n\tat com...\n",
                            session, session));
        }
        return session;
    }

    public int parseInt(String argument) {
        int size;
        try {
            size = Integer.parseInt(argument);
        } catch (NumberFormatException e) {
            if (argument == null) {
                throw new IllegalArgumentException(
                        "\nException occurred while dumping:\n java.lang.NumberFormatException: null\n\tat java...\n");
            } else {
                throw new IllegalArgumentException(
                        String.format(
                                "\nException occurred while dumping:\n java.lang.NumberFormatException: For input string: \"%s\", \n\tat java...\n",
                                argument));
            }
        }
        return size;
    }

    @Override
    public String getExecutable() {
        return "cmd";
    }
}
