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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;

/**
 * Compiler that will process individual resource files. A compiler will produce an optional
 * output file for an input file.
 */
@FunctionalInterface
public interface ResourceCompiler {

    /** Resource compiler that doesn't do anything. */
    ResourceCompiler NONE = (r) -> Futures.immediateFuture(null);

    /**
     * Produces an optional output file for an input file. Not all files are compilable. An
     * individual resource compiler will know if a file is compilable or not.
     *
     * @return a future for the output file, which may be produced asynchronously; if the future is
     *     computed as {@code null}, then the file is not compilable; this future may hol an
     *     exception if compilation fails
     * @throws Exception failed to process the compilation request
     */
    @NonNull
    ListenableFuture<File> compile(@NonNull CompileResourceRequest request) throws Exception;
}
