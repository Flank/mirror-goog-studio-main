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

package com.android.tools.binaries;

import com.android.tools.utils.JarOutputCompiler;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.antlr.Tool;
import org.antlr.tool.ErrorManager;

/**
 * Invokes ANTLR 3.x from prebuilts to create a {@code *.srcjar} with the generated parser code.
 */
public class AntlrCompiler extends JarOutputCompiler {

    public static void main(String[] args) throws IOException {
        System.exit(new AntlrCompiler().run(Arrays.asList(args)));
    }

    public AntlrCompiler() {
        super("antlr");
    }

    @Override
    protected boolean compile(List<String> files, String classPath, File outDir)
            throws IOException {
        List<String> antlrArgs = Lists.newArrayList("-o", outDir.getAbsolutePath());
        antlrArgs.addAll(files);

        Tool antlr = new Tool(Iterables.toArray(antlrArgs, String.class));
        antlr.process();
        return ErrorManager.getNumErrors() == 0;
    }
}
