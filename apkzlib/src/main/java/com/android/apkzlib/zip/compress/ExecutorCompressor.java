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

package com.android.apkzlib.zip.compress;

import com.android.apkzlib.zip.CompressionResult;
import com.android.apkzlib.zip.Compressor;
import com.android.apkzlib.zip.utils.CloseableByteSource;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

/**
 * A synchronous compressor is a compressor that computes the result of compression immediately
 * and never returns an uncomputed future object.
 */
public abstract class ExecutorCompressor implements Compressor {

    /**
     * The executor that does the work.
     */
    @Nonnull
    private final Executor mExecutor;

    /**
     * Compressor that delegates execution into the given executor.
     * @param executor the executor that will do the compress
     */
    public ExecutorCompressor(@Nonnull Executor executor) {
        mExecutor = executor;
    }

    @Nonnull
    @Override
    public ListenableFuture<CompressionResult> compress(
            @Nonnull final CloseableByteSource source) {
        final SettableFuture<CompressionResult> future = SettableFuture.create();
        mExecutor.execute(() -> {
            try {
                future.set(immediateCompress(source));
            } catch (Exception e) {
                future.setException(e);
            }
        });

        return future;
    }

    /**
     * Immediately compresses a source.
     * @param source the source to compress
     * @return the result of compression
     * @throws Exception failed to compress
     */
    @Nonnull
    protected abstract CompressionResult immediateCompress(@Nonnull CloseableByteSource source)
            throws Exception;
}
