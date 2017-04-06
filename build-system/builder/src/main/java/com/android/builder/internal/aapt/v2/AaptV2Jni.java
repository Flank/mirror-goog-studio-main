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
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.tools.aapt2.Aapt2Jni;
import com.android.tools.aapt2.Aapt2RenamingConventions;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}.
 */
public class AaptV2Jni extends AbstractAapt {

    @NonNull private final File intermediateDir;
    @NonNull private final ProcessOutputHandler processOutputHandler;
    @NonNull private final WaitableExecutor<Void> executor;

    /** Creates a new entry point to {@code aapt2} using the jni bindings. */
    public AaptV2Jni(
            @NonNull File intermediateDir,
            @NonNull WaitableExecutor<Void> executor,
            @NonNull ProcessOutputHandler processOutputHandler) {
        this.intermediateDir = intermediateDir;
        this.executor = executor;
        this.processOutputHandler = processOutputHandler;
    }

    @NonNull
    @Override
    protected ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {

        SettableFuture<Void> result = SettableFuture.create();
        executor.execute(
                () -> {
                    if (config.getResourceOutputApk() != null) {
                        Files.deleteIfExists(config.getResourceOutputApk().toPath());
                    }
                    try {
                        List<String> args = AaptV2CommandBuilder.makeLink(config, intermediateDir);
                        int returnCode = Aapt2Jni.link(args);
                        if (returnCode == 0) {
                            result.set(null);
                        } else {
                            result.setException(
                                    new AaptException(
                                            "Aapt2 link failed: returned error code "
                                                    + returnCode
                                                    + "\n"
                                                    + "aapt2 link "
                                                    + Joiner.on(' ').join(args)));
                        }
                    } catch (Exception e) {
                        result.setException(e);
                        throw e;
                    }
                    return null;
                });
        return result;
    }

    @NonNull
    @Override
    public ListenableFuture<File> compile(@NonNull File file, @NonNull File output)
            throws Exception {
        SettableFuture<File> result = SettableFuture.create();
        executor.execute(
                () -> {
                    try {
                        List<String> args = AaptV2CommandBuilder.makeCompile(file, output);
                        int returnCode = Aapt2Jni.compile(args);
                        if (returnCode == 0) {
                            result.set(
                                    new File(
                                            output,
                                            Aapt2RenamingConventions.compilationRename(file)));
                        } else {
                            result.setException(
                                    new AaptException(
                                            "Aapt2 compile failed: returned error code "
                                                    + returnCode
                                                    + "\n"
                                                    + "aapt2 compile "
                                                    + Joiner.on(' ').join(args)));
                        }
                    } catch (Exception e) {
                        result.setException(e);
                        throw e;
                    }
                    return null;
                });
        return result;
    }

    @Override
    public void start() {
        // we don't batch.
    }

    @Override
    public void end() throws InterruptedException {
        // since we don't batch, we are done.
    }
}
