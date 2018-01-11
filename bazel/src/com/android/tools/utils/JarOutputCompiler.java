/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class JarOutputCompiler {

    private final String name;

    protected JarOutputCompiler(String name) {
        this.name = name;
    }

    private void usage(String message) {
        System.err.println("Error: " + message);
        System.err.println("Usage: " + name + " [-cp class_path|@<file>] -o jar_file <files>...|@<file>");
    }

    protected int run(List<String> args) throws IOException {
        args = filterOptions(args);
        Options options = parseOptions(args.iterator());
        if (options.out == null) {
            usage("Output file not specified.");
            return 1;
        }
        if (options.files.isEmpty()) {
            usage("No input files specified.");
            return 1;
        }
        File tmp = new File(options.out.getAbsolutePath() + ".dir");
        if (!compile(options.files, options.classPath, tmp)) {
            return 1;
        }
        new Zipper().directoryToZip(tmp, options.out);
        return 0;
    }

    protected List<String> filterOptions(List<String> args) {
        return args;
    }

    private Options parseOptions(Iterator<String> it) throws IOException {
        Options options = new Options();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("-o") && it.hasNext()) {
                options.out = new File(it.next());
            } else if (arg.equals("-cp") && it.hasNext()) {
                String cpArg = it.next();
                if (cpArg.startsWith("@")) {
                    options.classPath = Files.readAllLines(Paths.get(cpArg.substring(1)))
                            .stream()
                            .reduce("", (String a, String b) -> a + b);
                } else {
                    options.classPath = cpArg;
                }
            } else {
                if (arg.startsWith("@")) {
                    options.files.addAll(Files.readAllLines(Paths.get(arg.substring(1))));
                } else {
                    options.files.add(arg);
                }
            }
        }
        return options;
    }

    private static class Options {
        public File out;
        public List<String> files = new LinkedList<>();
        public String classPath = "";
    }

    protected abstract boolean compile(List<String> files, String classPath, File outDir)
            throws IOException;
}
