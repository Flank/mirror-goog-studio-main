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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AbstractAapt;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.tools.aapt2.Aapt2Jni;
import com.android.tools.aapt2.Aapt2RenamingConventions;
import com.android.tools.aapt2.Aapt2Result;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}.
 */
public class AaptV2Jni extends AbstractAapt {

    @NonNull private final File intermediateDir;
    @NonNull private final ProcessOutputHandler processOutputHandler;
    @NonNull private final WaitableExecutor executor;

    /** Creates a new entry point to {@code aapt2} using the jni bindings. */
    public AaptV2Jni(
            @NonNull File intermediateDir,
            @NonNull WaitableExecutor executor,
            @NonNull ProcessOutputHandler processOutputHandler) {
        this.intermediateDir = intermediateDir;
        this.executor = executor;
        this.processOutputHandler = processOutputHandler;

        // FIXME: Work-around to address http://b/38175665 (File nio copy sometimes fails on Windows
        // when using Aapt2jni): We need to perform a "dummy" file nio copy here to somehow
        // resolve file nio copy's low-level dependencies *before* Aapt2jni is used.
        // We will remove this code once there is a proper fix.
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            File sourceFile = new File(intermediateDir, "dummySource");
            File targetFile = new File(intermediateDir, "dummyTarget");
            try {
                FileUtils.deleteIfExists(sourceFile);
                FileUtils.deleteIfExists(targetFile);
                Files.createFile(sourceFile.toPath());
                Files.copy(sourceFile.toPath(), targetFile.toPath());
                FileUtils.delete(sourceFile);
                FileUtils.delete(targetFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @NonNull
    @Override
    protected ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {
        if (config.getResourceOutputApk() != null) {
            try {
                Files.deleteIfExists(config.getResourceOutputApk().toPath());
            } catch (IOException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
        List<String> args = AaptV2CommandBuilder.makeLink(config, intermediateDir);
        Aapt2Result aapt2Result = Aapt2Jni.link(args);
        writeMessages(processOutputHandler, aapt2Result.getMessages());

        if (aapt2Result.getReturnCode() == 0) {
            return Futures.immediateFuture(null);
        } else {
            return Futures.immediateFailedFuture(buildException("link", args, aapt2Result));
        }
    }

    @NonNull
    @Override
    public Future<File> compile(@NonNull CompileResourceRequest request) throws Exception {
        return executor.execute(
                () -> {
                    List<String> args = AaptV2CommandBuilder.makeCompile(request);
                    Aapt2Result aapt2Result = Aapt2Jni.compile(args);
                    writeMessages(processOutputHandler, aapt2Result.getMessages());
                    if (aapt2Result.getReturnCode() == 0) {
                        return new File(
                                request.getOutput(),
                                Aapt2RenamingConventions.compilationRename(request.getInput()));
                    } else {
                        throw buildException("compile", args, aapt2Result);
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

    private static AaptException buildException(
            String action, List<String> args, Aapt2Result aapt2Result) {
        StringBuilder builder = new StringBuilder();
        builder.append("AAPT2 ")
                .append(action)
                .append(" failed:\naapt2 ")
                .append(action)
                .append(" ")
                .append(Joiner.on(' ').join(args))
                .append("\n");
        if (aapt2Result.getMessages().isEmpty()) {
            builder.append("No issues were reported");
        } else {
            builder.append("Issues:\n - ")
                    .append(Joiner.on("\n - ").join(aapt2Result.getMessages()));
        }
        return new AaptException(builder.toString());
    }

    private static void writeMessages(
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull List<Aapt2Result.Message> messages)
            throws AaptException {
        if (messages.isEmpty()) {
            return;
        }
        ProcessOutput output;
        try (Closeable ignored = output = processOutputHandler.createOutput();
                PrintWriter err = new PrintWriter(output.getErrorOutput());
                PrintWriter out = new PrintWriter(output.getStandardOutput())) {
            for (Aapt2Result.Message message : messages) {
                switch (message.getLevel()) {
                    case NOTE:
                        out.println(message.toString());
                        break;
                    case WARN:
                    case ERROR:
                        err.println(message.toString());
                        break;
                }
            }
        } catch (IOException e) {
            throw new AaptException(e, "Unexpected error handling AAPT output");
        }
        try {
            processOutputHandler.handleOutput(output);
        } catch (ProcessException e) {
            throw new AaptException(e, "Unexpected error handling AAPT output");
        }
    }
}
