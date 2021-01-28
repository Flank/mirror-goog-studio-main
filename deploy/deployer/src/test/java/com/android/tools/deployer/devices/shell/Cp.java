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

import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Cp extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        if (args.length < 2) {
            stdout.println("cp: Need 2 arguments (see \"cp --help\")");
            return 1;
        }
        List<String> sources = new ArrayList<>();
        String dest = null;
        boolean recursive = false;
        boolean deleteBefore = false;
        boolean no_clobber = false;

        int start = 0;
        if (args[0].startsWith("-")) {
            for (int j = 1; j < args[0].length(); j++) {
                char flag = args[0].charAt(j);
                switch (flag) {
                  case 'r': recursive = true; break;
                    case 'n':
                        no_clobber = true;
                        break;
                  case 'F': deleteBefore = true; break;
                  default:
                      stdout.println("cp: Unknown option " + flag + "(see \"cp --help\")");
                      return 1;
                }
            }
            start = 1;
        }
        for (int i = start; i < args.length - 1; i++) {
            sources.add(clean(args[i]));
        }
        dest = clean(args[args.length - 1]);

        if (sources.isEmpty()) {
            stdout.println("cp: Need 2 arguments (see \"cp --help\")");
            return 1;
        }

        boolean destDirectory = context.getDevice().isDirectory(dest);
        if (sources.size() > 1 && !destDirectory) {
            stdout.println("cp: '" + dest + "' not directory");
            return 1;
        }

        for (String source : sources) {
            String name = new File(source).getName();
            if (context.getDevice().isDirectory(source)) {
                if (recursive) {
                    if (context.getDevice().hasFile(dest)) {
                        if (destDirectory) {
                            context.getDevice().copyDirRecursively(source, dest + "/" + name);
                        } else {
                            stdout.println("cp: dir at '" + source + "'");
                            return 1;
                        }
                    } else {
                        context.getDevice().copyDirRecursively(source, dest);
                    }
                }
                else {
                    if (!destDirectory) {
                        stdout.println("cp: dir at '" + source + "'");
                        return 1;
                    }
                    stdout.println("cp: Skipped dir '" + dest + "/" + source + "': No such file or directory");
                }
            } else {
                if (destDirectory) {
                    context.getDevice().copyFile(source, dest + "/" + name);
                } else {
                    if (no_clobber && Files.exists(Paths.get(dest))) {
                        continue;
                    }
                    context.getDevice().copyFile(source, dest);
                }
            }
        }
        return 0;
    }

    private String clean(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public String getExecutable() {
        return "cp";
    }
}
