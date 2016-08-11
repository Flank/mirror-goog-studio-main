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
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;

/**
 * A tool to compile groovy files directly into a jar.
 */
public class GroovyCompiler extends JarOutputCompiler {

    GroovyCompiler() {
        super("groovyc");
    }

    public static void main(String[] args) throws IOException {
        System.exit(new GroovyCompiler().run(Arrays.asList(args)));
    }

    @Override
    protected void compile(List<String> files, String classPath, File outDir) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setClasspath(classPath);
        config.setTargetDirectory(outDir);
        CompilationUnit cu = new CompilationUnit(config);
        for (String name : files) {
            cu.addSource(new File(name));
        }
        cu.compile();
    }
}
