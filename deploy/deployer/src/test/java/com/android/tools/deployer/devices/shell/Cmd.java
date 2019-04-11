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

public class Cmd extends ShellCommand {

    private final boolean reportCommitSuccess;

    public Cmd(boolean reportSuccessOnCommit) {
        reportCommitSuccess = reportSuccessOnCommit;
    }

    public Cmd() {
        this(true);
    }

    @Override
    public boolean execute(FakeDevice device, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        try {
            return run(device, new Arguments(args), stdin, stdout);
        } catch (IllegalArgumentException e) {
            stdout.println(e.getMessage());
            return false;
        }
    }

    public boolean run(FakeDevice device, Arguments args, InputStream stdin, PrintStream stdout)
            throws IOException {
        String service = args.nextArgument();
        if (service == null) {
            stdout.println("cmd: no service specified; use -l to list all services");
            return false;
        }

        switch (service) {
            case "package":
                String action = args.nextArgument();
                if (action == null) {
                    stdout.println("Usage\n...message...");
                    return false;
                }
                switch (action) {
                        // eg: pm install-create -r -t -S 5047
                    case "install-create":
                        stdout.format(
                                "Success: created install session [%d]\n", device.createSession());
                        return true;
                        // eg: install-write -S 5047 100000000 0_sample -
                    case "install-write":
                        {
                            String opt = args.nextOption();
                            if (opt == null) {
                                stdout.println("Error: must specify a APK size");
                                return false;
                            } else if (!opt.equals("-S")) {
                                stdout.format(
                                        "\nException occurred while dumping:\n"
                                                + "java.lang.IllegalArgumentException: Unknown option %s\n\tat com...\n",
                                        opt);
                                return false;
                            }
                            // This should be a long, but we keep all the files in memory. Int is enough for tests.
                            int size = parseInt(args.nextArgument());
                            int session = parseSession(device, args);
                            String name = args.nextArgument();
                            if (name == null) {
                                stdout.println(
                                        "\nException occurred while dumping:\n"
                                                + "java.lang.IllegalArgumentException: Invalid name: null\n\tat com...");
                                return false;
                            }
                            String path = args.nextArgument();
                            byte[] apk;
                            if (path == null || path.equals("-")) {
                                apk = new byte[size];
                                ByteStreams.readFully(stdin, apk);
                            } else {
                                stdout.println("Error: APK content must be streamed");
                                return false;
                            }
                            device.writeToSession(session, apk);
                            stdout.format("Success: streamed %d bytes\n", size);
                            return true;
                        }
                    case "install-commit":
                        {
                            device.commitSession(parseSession(device, args));
                            // On some APIs the "Success" part of install-commit is not printed. We allow this
                            // to be configured so we can reproduce that odd behaviour.
                            stdout.println(reportCommitSuccess ? "Success" : "");
                            return true;
                        }
                    case "install-abandon":
                        {
                            device.abandonSession(parseSession(device, args));
                            stdout.println("Success");
                            return true;
                        }
                    case "path":
                        {
                            String pkg = args.nextArgument();
                            if (pkg == null) {
                                stdout.println(
                                        "\nException occurred while executing:\n"
                                                + "java.lang.IllegalArgumentException: Argument expected after \"path\"\n\tat com...");
                                return false;
                            }
                            String path = device.getAppPath(pkg);
                            if (path != null) {
                                stdout.println("package:" + path + "/base.apk");
                                return true;
                            } else {
                                return false;
                            }
                        }
                }
                break;
        }
        stdout.println("Can't find service: " + service);
        return false;
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

    @Override
    public String getLocation() {
        return "/system/bin";
    }
}
