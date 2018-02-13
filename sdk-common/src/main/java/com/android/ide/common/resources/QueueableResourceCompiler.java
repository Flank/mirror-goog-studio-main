/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import java.io.Closeable;
import java.io.File;

/**
 * A specialization of the {@link ResourceCompiler} that can queue compile request and execute them
 * all using slave threads or processes.
 */
public interface QueueableResourceCompiler extends ResourceCompiler, Closeable {

    /**
     * Obtains the file that will receive the compilation output of a given file. This method will
     * return a unique file in the output directory for each input file.
     *
     * @param request the compile resource request containing the input, output and folder name
     * @return the output file
     */
    File compileOutputFor(@NonNull CompileResourceRequest request);
}
