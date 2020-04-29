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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class Am extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        try {
            return run(context.getDevice(), new Arguments(args), stdin, stdout);
        } catch (IllegalArgumentException e) {
            stdout.println(e.getMessage());
            return 0;
        }
    }

    public int run(FakeDevice device, Arguments args, InputStream stdin, PrintStream stdout)
            throws IOException {
        String action = args.nextArgument();
        if (action == null) {
            return processUsage(stdout);
        }

        switch (action) {
            // eg: am start -n package.name/activity.name -a intent.NAME -c category.NAME
            case "start":
                String intent = null;
                String name = null;
                String category = null;
                for (String opt = args.nextOption(); opt != null; opt = args.nextOption()) {
                    switch (opt) {
                        case "-a":
                            intent = args.nextArgument();
                            break;
                        case "-n":
                            name = args.nextArgument();
                            break;
                        case "-c":
                            category = args.nextArgument();
                            break;
                    }
                }
                return start(device, name, intent, stdout);
            case "force-stop":
            case "kill":
                String arg = args.nextArgument();
                if (arg == null) {
                    stdout.println("\nException occurred while executing:");
                    stdout.println(
                            String.format(
                                    "java.lang.IllegalArgumentException: Argument expected after \"%s\"",
                                    action));
                    stdout.println("...message...");
                    return 255;
                }
                device.stopApp(arg);
                return 0;
            default:
                return processUsage(stdout);
        }
    }

    @Override
    public String getExecutable() {
        return "am";
    }

    public int start(FakeDevice device, String name, String intent, PrintStream stdout)
            throws IOException {
        if (intent == null && name == null) {
            stdout.println("\nException occurred while executing:\njava.lang.IllegalArgumentException: No intent supplied\n...message...");
            return 255;
        }

        if (name == null) {
            // We don't handle starting intents.
            return processUsage(stdout);
        }

        String[] names = name.split("/", 2);
        if (names.length != 2) {
            stdout.println();
            stdout.println("Exception occurred while executing");
            stdout.println("java.lang.IllegalArgumentException: Bad component name: ");
            stdout.println(name);
            stdout.println("...message...");
            return 255;
        }

        if ("android.intent.action.MAIN".equals(intent)) {
            StringBuilder resultString = new StringBuilder();
            resultString.append("Starting: Intent {");
            if (intent != null) {
                resultString.append(" act=");
                resultString.append(intent);
            }
            resultString.append(" cmp=");
            if (names[1].startsWith(names[0])) {
                resultString.append(names[0]);
                resultString.append("/");
                resultString.append(names[1].substring(names[0].length()));
            }
            else {
                resultString.append(name);
            }
            resultString.append(" }");
            stdout.println(resultString.toString());

            if (device.runApp(names[0])) {
                return 0;
            }

            stdout.print(String.format("Error type 3\nError: Activity class {%s} does not exist.", names[0]));
            return 255;
        }

        return processUsage(stdout);
    }

    public int processUsage(PrintStream stdout) {
        stdout.println("Activity manager (activity) commands:\n...message...");
        return 0;
    }
}
