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

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.tools.javac.JavaStubCompilationUnit;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import groovy.lang.GroovyClassLoader;

/**
 * Generates .java stubs for all the given groovy files. The .java files are placed inside
 * a .jar file.
 */
public class GroovyStubGenerator {

    public static void main(String[] args) throws IOException {
        System.exit(new GroovyStubGenerator().run(Arrays.asList(args)));
    }

    private void usage(String message) {
        System.err.println("Error: " + message);
        System.err.println("Usage: groovy_stub_gen -o <out_file> <files>...");
    }

    private int run(List<String> args) throws IOException {
        File file = null;
        List<String> files = new LinkedList<>();
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("-o") && it.hasNext()) {
                file = new File(it.next());
            } else {
                files.add(arg);
            }
        }
        if (file == null) {
            usage("Output file name not specified.");
            System.exit(1);
        }
        if (files.isEmpty()) {
            usage("No input files specified.");
            System.exit(1);
        }
        generateStubs(file, files);
        return 0;
    }

    private void generateStubs(File file, List<String> files) throws IOException {
        CompilerConfiguration config = new CompilerConfiguration();
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        GroovyClassLoader gcl = new GroovyClassLoader(parent, config);
        File tmp = new File(file.getAbsolutePath() + ".dir");
        JavaStubCompilationUnit cu = new JavaStubCompilationUnit(config, gcl, tmp);

        for (String name : files) {
            cu.addSource(new File(name));
        }
        cu.compile();

        new JarGenerator().directoryToJar(tmp, file);
    }
}
