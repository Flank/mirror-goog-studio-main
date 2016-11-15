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

package com.android.tools.jasmin;

import com.android.tools.utils.JarOutputCompiler;
import jasmin.Main;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A wrapper for the Kotlin compiler.
 */
public class JasminCompiler extends JarOutputCompiler {

    protected JasminCompiler() {
        super("jasmin");
    }

    public static void main(String[] args) throws IOException {
        System.exit(new JasminCompiler().run(Arrays.asList(args)));
    }

    @Override
    protected boolean compile(List<String> files, String classPath, File outDir) {
        for (String file: files) {
            // Calls system.exit on failure
            Main.main(new String[]{file, "-d", outDir.getAbsolutePath()});
        }
        return true;
    }
}
