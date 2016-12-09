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
 * A specialization of the {@link ResourceCompiler} that can queue compile request and execute
 * them all using slave threads or processes.
 */
public interface QueueableResourceCompiler extends ResourceCompiler {

    QueueableResourceCompiler NONE = new QueueableResourceCompiler() {

        @NonNull
        @Override
        public ListenableFuture<File> compile(@NonNull File file, @NonNull File output)
                throws Exception {
            return Futures.immediateFuture(null);
        }

        @Override
        public void start() {
        }

        @Override
        public void end() throws InterruptedException {
        }
    };

    /**
     * Start a new queueing request for compile activities. All calls made to
     * {@link ResourceCompiler#compile(File, File)} will be part of the same batch of requests.
     */
    void start();

    /**
     * End the current batch of request. This will wait until requested compilation requested issued
     * with {@link ResourceCompiler#compile(File, File)} have finished before returning.
     *
     * Each compile request result will be available through the
     * {@link com.google.common.util.concurrent.ListenableFuture} returned by
     * {@link ResourceCompiler}.
     *
     * @throws InterruptedException
     */
    void end() throws InterruptedException;
}
