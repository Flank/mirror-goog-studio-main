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

package com.android.tools.groovy;

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A tool to compile groovy files directly into a jar.
 */
public class GroovyCompiler {

    public static void main(String[] args) throws IOException {
        System.exit(new GroovyCompiler().run(Arrays.asList(args)));
    }

    private void usage(String message) {
        System.err.println("Error: " + message);
        System.err.println("Usage: groovyc [-cp class_path] -o jar_file <files>...");
    }

    private int run(List<String> args) throws IOException {
        File out = null;
        List<String> files = new LinkedList<>();
        Iterator<String> it = args.iterator();
        String classPath = "";
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("-o") && it.hasNext()) {
                out = new File(it.next());
            } else if (arg.equals("-cp") && it.hasNext()) {
                classPath = it.next();
            } else {
                files.add(arg);
            }
        }
        if (out == null) {
            usage("Output file not specified.");
            System.exit(1);
        }
        if (files.isEmpty()) {
            usage("No input files specified.");
            System.exit(1);
        }
        compile(out, files, classPath);
        return 0;
    }

    private void compile(File out, List<String> files, String classPath) throws IOException {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setClasspath(classPath);
        File tmp = new File(out.getAbsolutePath() + ".dir");
        config.setTargetDirectory(tmp);
        CompilationUnit cu = new CompilationUnit(config);
        for (String name : files) {
            cu.addSource(new File(name));
        }
        cu.compile();
        new JarGenerator().directoryToJar(tmp, out);
    }
}
