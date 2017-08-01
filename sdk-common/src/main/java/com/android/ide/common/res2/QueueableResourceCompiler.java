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

package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A specialization of the {@link ResourceCompiler} that can queue compile request and execute them
 * all using slave threads or processes.
 */
public interface QueueableResourceCompiler extends ResourceCompiler, Closeable {

    QueueableResourceCompiler NONE =
            new QueueableResourceCompiler() {

                @Override
                public void close() throws IOException {
                    // no batching
                }

                @NonNull
                @Override
                public ListenableFuture<File> compile(@NonNull CompileResourceRequest request)
                        throws Exception {
                    // Copy file instead of compiling.
                    File out = compileOutputFor(request);
                    FileUtils.copyFile(request.getInput(), out);
                    return Futures.immediateFuture(out);
                }

                @NonNull
                @Override
                public File compileOutputFor(@NonNull CompileResourceRequest request) {
                    File parentDir = new File(request.getOutput(), request.getFolderName());
                    FileUtils.mkdirs(parentDir);
                    return new File(parentDir, request.getInput().getName());
                }
            };

    /**
     * Obtains the file that will receive the compilation output of a given file. This method will
     * return a unique file in the output directory for each input file.
     *
     * <p>This method will also create any parent directories needed to hold the output file.
     *
     * @param request the compile resource request containing the input, output and folder name
     * @return the output file
     */
    File compileOutputFor(@NonNull CompileResourceRequest request);
}
