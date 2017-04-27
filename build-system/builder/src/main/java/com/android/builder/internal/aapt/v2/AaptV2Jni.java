/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AbstractAapt;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.tools.aapt2.Aapt2Jni;
import com.android.tools.aapt2.Aapt2RenamingConventions;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}.
 */
public class AaptV2Jni extends AbstractAapt {

    @NonNull private final File intermediateDir;
    @NonNull private final WaitableExecutor executor;

    /** Creates a new entry point to {@code aapt2} using the jni bindings. */
    public AaptV2Jni(@NonNull File intermediateDir, @NonNull WaitableExecutor executor) {
        this.intermediateDir = intermediateDir;
        this.executor = executor;
    }

    @Override
    protected ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {

        if (config.getResourceOutputApk() != null) {
            try {
                Files.deleteIfExists(config.getResourceOutputApk().toPath());
            } catch (IOException e) {
                throw new AaptException(e.getMessage(), e);
            }
        }
        List<String> args = AaptV2CommandBuilder.makeLink(config, intermediateDir);
        int returnCode = Aapt2Jni.link(args);
        if (returnCode != 0) {
            throw new AaptException(
                    "Aapt2 link failed: returned error code "
                            + returnCode
                            + "\n"
                            + "aapt2 link "
                            + Joiner.on(' ').join(args));
        }
        return Futures.immediateFuture(null);
    }

    @NonNull
    @Override
    public Future<File> compile(@NonNull CompileResourceRequest request) throws Exception {
        return executor.execute(
                () -> {
                    List<String> args = AaptV2CommandBuilder.makeCompile(request);
                    int returnCode = Aapt2Jni.compile(args);
                    if (returnCode == 0) {
                        return new File(
                                request.getOutput(),
                                Aapt2RenamingConventions.compilationRename(request.getInput()));
                    } else {
                        throw new AaptException(
                                "Aapt2 compile failed: returned error code "
                                        + returnCode
                                        + "\n"
                                        + "aapt2 compile "
                                        + Joiner.on(' ').join(args));
                    }
                });
    }

    @Override
    public void start() {
        // we don't batch.
    }

    @Override
    public void end() throws InterruptedException {
        // since we don't batch, we are done.
    }

    @Override
    @NonNull
    public File compileOutputFor(@NonNull CompileResourceRequest request) {
        return new File(
                request.getOutput(),
                Aapt2RenamingConventions.compilationRename(request.getInput()));
    }
}
