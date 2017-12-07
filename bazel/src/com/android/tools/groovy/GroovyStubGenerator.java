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

import com.android.tools.utils.JarOutputCompiler;
import groovy.lang.GroovyClassLoader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.tools.javac.JavaStubCompilationUnit;

/**
 * Generates .java stubs for all the given groovy files. The .java files are placed inside a .jar
 * file.
 */
public class GroovyStubGenerator extends JarOutputCompiler {

    GroovyStubGenerator() {
        super("groovy_stub_gen");
    }

    public static void main(String[] args) throws IOException {
        System.exit(new GroovyStubGenerator().run(Arrays.asList(args)));
    }

    @Override
    protected boolean compile(List<String> files, String classPath, File outDir)
            throws IOException {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setClasspath(classPath.replaceAll(":", File.pathSeparator));
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        GroovyClassLoader gcl = new GroovyClassLoader(parent, config);
        JavaStubCompilationUnit cu = new JavaStubCompilationUnit(config, gcl, outDir);
        for (String name : files) {
            cu.addSource(new File(name));
        }
        cu.compile(); // Throws if there is an error.
        return true;
    }
}
