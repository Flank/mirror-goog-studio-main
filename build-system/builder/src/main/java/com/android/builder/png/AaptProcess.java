/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.png;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.v2.Aapt2QueuedResourceProcessor;
import com.android.builder.internal.aapt.v2.AaptV2CommandBuilder;
import com.android.builder.tasks.BooleanLatch;
import com.android.builder.tasks.Job;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.sdklib.BuildToolInfo;
import com.android.tools.aapt2.Aapt2Exception;
import com.android.utils.FileUtils;
import com.android.utils.GrabProcessOutput;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * interface to the aapt long running process.
 */
public class AaptProcess {
    private static final int DEFAULT_SLAVE_AAPT_TIMEOUT_IN_SECONDS = 5;
    private static final int SLAVE_AAPT_TIMEOUT_IN_SECONDS =
            System.getenv("SLAVE_AAPT_TIMEOUT") == null
                    ? DEFAULT_SLAVE_AAPT_TIMEOUT_IN_SECONDS
                    : Integer.parseInt(System.getenv("SLAVE_AAPT_TIMEOUT"));
    private static final Joiner joiner = Joiner.on('\n');

    private final String mAaptLocation;
    private final Process mProcess;
    private final ILogger mLogger;

    private final ProcessOutputFacade mProcessOutputFacade = new ProcessOutputFacade();
    private int processCount = 0;
    private final AtomicBoolean mReady = new AtomicBoolean(false);
    private final BooleanLatch mReadyLatch = new BooleanLatch();
    private final OutputStreamWriter mWriter;

    private AaptProcess(
            @NonNull String aaptLocation, @NonNull Process process, @NonNull ILogger iLogger)
            throws InterruptedException {
        mAaptLocation = aaptLocation;
        mProcess = process;
        mLogger = iLogger;
        GrabProcessOutput.grabProcessOutput(process, GrabProcessOutput.Wait.ASYNC,
                        mProcessOutputFacade);
        mWriter = new OutputStreamWriter(mProcess.getOutputStream());
    }

    /**
     * Notifies the slave process of a new crunching request, does not block on completion, the
     * notification will be issued through the job parameter's {@link
     * com.android.builder.tasks.Job#finished()} or {@link Job#error(Throwable)} ()} functions.
     *
     * @param in the source file to crunch
     * @param out where to place the crunched file
     * @param job the job to notify when the crunching is finished successfully or not.
     */
    public void crunch(@NonNull File in, @NonNull File out, @NonNull Job<AaptProcess> job)
            throws IOException {
        if (!mReady.get()) {
            throw new RuntimeException("AAPT process not ready to receive commands");
        }
        NotifierProcessOutput notifier =
                new NotifierProcessOutput(job, mProcessOutputFacade, mLogger, null);

        mProcessOutputFacade.setNotifier(notifier);
        mWriter.write("s\n");
        mWriter.write(FileUtils.toExportableSystemDependentPath(in));
        mWriter.write('\n');
        mWriter.write(FileUtils.toExportableSystemDependentPath(out));
        mWriter.write('\n');
        mWriter.flush();
        processCount++;
        mLogger.verbose(
                "AAPT1 processed(%1$d) %2$s job:%3$s", hashCode(), in.getName(), job.toString());
    }

    /**
     * Notifies the slave process of a new AAPT2 compile request, does not block on completion, the
     * notification will be issued through the job parameter's {@link
     * com.android.builder.tasks.Job#finished()} or {@link Job#error(Throwable)} ()} functions.
     *
     * @param request the compile request containing the the input, output and compilation flags
     * @param job the job to notify when the compiling is finished successfully or not.
     */
    public void compile(
            @NonNull CompileResourceRequest request,
            @NonNull Job<AaptProcess> job,
            @Nullable ProcessOutputHandler processOutputHandler)
            throws IOException {

        if (!mReady.get()) {
            throw new RuntimeException(
                    String.format(
                            "AAPT2 process not ready to receive commands. Please make sure the "
                                    + "build tools (located at %s) are not corrupted. Check the "
                                    + "logs for details.",
                            mAaptLocation));
        }
        NotifierProcessOutput notifier =
                new NotifierProcessOutput(job, mProcessOutputFacade, mLogger, processOutputHandler);

        mProcessOutputFacade.setNotifier(notifier);
        mWriter.write('c');
        mWriter.write('\n');
        mWriter.write(joiner.join(AaptV2CommandBuilder.makeCompile(request)));
        // Finish the request
        mWriter.write('\n');
        mWriter.write('\n');
        mWriter.flush();
        processCount++;
        mLogger.verbose(
                "AAPT2 processed(%1$d) %2$s job:%3$s",
                hashCode(), request.getInput().getName(), job.toString());
    }

    /**
     * Notifies the slave process of a new AAPT2 link request, does not block on completion, the
     * notification will be issued through the job parameter's {@link
     * com.android.builder.tasks.Job#finished()} or {@link Job#error(Throwable)} ()} functions.
     *
     * @param config the configuration of the link request
     * @param intermediateDir the directory for intermediate files
     * @param job the job to notify when the linking is finished successfully or not.
     */
    public void link(
            @NonNull AaptPackageConfig config,
            @NonNull File intermediateDir,
            @NonNull Job<AaptProcess> job,
            @Nullable ProcessOutputHandler processOutputHandler)
            throws IOException {
        if (!mReady.get()) {
            throw new RuntimeException(
                    String.format(
                            "AAPT2 process not ready to receive commands. Please make sure the "
                                    + "build tools (located at %s) are not corrupted. Check the "
                                    + "logs for details.",
                            mAaptLocation));
        }
        NotifierProcessOutput notifier =
                new NotifierProcessOutput(job, mProcessOutputFacade, mLogger, processOutputHandler);

        mProcessOutputFacade.setNotifier(notifier);
        try {
            mWriter.write('l');
            mWriter.write('\n');
            mWriter.write(joiner.join(AaptV2CommandBuilder.makeLink(config, intermediateDir)));
            // Finish the request
            mWriter.write('\n');
            mWriter.write('\n');
            mWriter.flush();
        } catch (AaptException e) {
            throw new IOException(e);
        }
        processCount++;
        mLogger.verbose("AAPT2 processed(%1$d) linking job:%2$s", hashCode(), job.toString());
    }

    /*
     * @return true if process started successfully, false if it failed to start.
     */
    public boolean waitForReadyOrFail() throws InterruptedException {
        if (!mReadyLatch.await(TimeUnit.NANOSECONDS.convert(
                SLAVE_AAPT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS))) {
            throw new RuntimeException(String.format(
                    "Timed out while waiting for slave aapt process, make sure "
                        + "the aapt execute at %1$s can run successfully (some anti-virus may "
                        + "block it) or try setting environment variable SLAVE_AAPT_TIMEOUT to a "
                        + "value bigger than %2$d seconds",
                    mAaptLocation, SLAVE_AAPT_TIMEOUT_IN_SECONDS));
        }

        if (mReady.get()) {
            mLogger.verbose("Slave %1$s is ready", hashCode());
            return true;
        }

        mLogger.error(
                new RuntimeException(
                        String.format(
                                "AAPT slave failed to start. Please make sure the current build "
                                        + "tools (located at %s) are not corrupted.",
                                mAaptLocation)),
                String.format("Slave %1$s failed to start", hashCode()));
        Aapt2QueuedResourceProcessor.invalidateProcess(mAaptLocation);
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("hashcode", hashCode())
                .add("\nlocation", mAaptLocation)
                .add("\nready", mReady.get())
                .add("\nprocess", mProcess.hashCode())
                .toString();
    }

    /**
     * Shutdowns the slave process and release all resources.
     *
     */
    public void shutdown() throws IOException, InterruptedException {
        if (!mReady.get()) {
            // Process already shutdown or has never started properly in the first place.
            mLogger.verbose("Process (%1$s) already shutdown", hashCode());
            return;
        }
        mReady.set(false);
        mWriter.write("quit\n");
        mWriter.write('\n');
        mWriter.flush();
        mProcess.waitFor();
        mLogger.verbose("Process (%1$s) processed %2$s files", hashCode(), processCount);
    }

    public static class Builder {
        private final String mAaptLocation;
        private final ILogger mLogger;

        public Builder(@NonNull String aaptPath, @NonNull ILogger iLogger) {
            mAaptLocation = aaptPath;
            mLogger = iLogger;
        }

        @NonNull
        public AaptProcess start() throws IOException, InterruptedException {
            String[] command = new String[] {
                    mAaptLocation,
                    "m",
            };

            mLogger.verbose("Trying to start %1$s", command[0]);
            Process process = new ProcessBuilder(command).start();
            AaptProcess aaptProcess = new AaptProcess(mAaptLocation, process, mLogger);
            mLogger.verbose("Started %1$d", aaptProcess.hashCode());
            return aaptProcess;
        }
    }

    private class ProcessOutputFacade implements GrabProcessOutput.IProcessOutput {
        @Nullable NotifierProcessOutput notifier = null;

        synchronized void setNotifier(@NonNull NotifierProcessOutput notifierProcessOutput) {
            //noinspection VariableNotUsedInsideIf
            if (notifier != null) {
                throw new RuntimeException("Notifier already set, threading issue");
            }
            notifier = notifierProcessOutput;
        }

        @Override
        public String toString() {
            return "Facade for " + String.valueOf(AaptProcess.this.hashCode());
        }

        synchronized void reset() {
            notifier = null;
        }

        @Nullable
        synchronized NotifierProcessOutput getNotifier() {
            return notifier;
        }

        @Override
        public synchronized void out(@Nullable String line) {

            // an empty message or aapt startup message are ignored.
            if (Strings.isNullOrEmpty(line)) {
                return;
            }
            if (line.equals("Ready")) {
                AaptProcess.this.mReady.set(true);
                AaptProcess.this.mReadyLatch.signal();
                return;
            }
            if (line.equals("Exiting daemon")) {
                // shutdown() finished closing the daemon
                return;
            }
            NotifierProcessOutput delegate = getNotifier();
            if (delegate != null) {
                delegate.out(line);
            } else {
                mLogger.error(null, "AAPT out(%1$s) : No Delegate set : lost message:%2$s",
                        toString(), line);
            }
        }

        @Override
        public synchronized void err(@Nullable String line) {

            if (Strings.isNullOrEmpty(line)) {
                return;
            }
            NotifierProcessOutput delegate = getNotifier();
            if (delegate != null) {
                mLogger.verbose("AAPT1 err(%1$s): %2$s -> %3$s", toString(), line,
                        delegate.mJob);
                delegate.err(line);
            } else {
                if (!mReady.get()) {
                    if (line.equals("ERROR: Unknown command 'm'")) {
                        throw new RuntimeException(
                                "Invalid AAPT version.\n"
                                        + "For AAPT1 version 21 or above is required.\n"
                                        + "For AAPT2 version "
                                        + BuildToolInfo.PathId.DAEMON_AAPT2
                                                .getMinRevision()
                                                .toString()
                                        + " or above is required.");
                    }
                    mLogger.verbose("AAPT err(%1$s): %2$s", toString(), line);
                    mLogger.error(null, "AAPT err(%1$s): %2$s", toString(), line);
                } else {
                    mLogger.error(null, "AAPT err(%1$s) : No Delegate set : lost message:%2$s",
                            toString(), line);
                }
            }
            // Even after the aapt error, we should notify the main thread that we are ready.
            // The error state will be handled there
            if (!mReadyLatch.isSignalled()) {
                AaptProcess.this.mReady.set(false);
                mReadyLatch.signal();
            }
        }

        Process getProcess() {
            return mProcess;
        }
    }

    private static class NotifierProcessOutput implements GrabProcessOutput.IProcessOutput {

        @NonNull private final Job<AaptProcess> mJob;
        @NonNull private final ProcessOutputFacade mOwner;
        @NonNull private final ILogger mLogger;
        @NonNull private final AtomicBoolean mInError = new AtomicBoolean(false);
        @NonNull private final ArrayList<String> errors = new ArrayList<>();
        @Nullable private final ProcessOutputHandler processOutputHandler;

        NotifierProcessOutput(
                @NonNull Job<AaptProcess> job,
                @NonNull ProcessOutputFacade owner,
                @NonNull ILogger iLogger,
                @Nullable ProcessOutputHandler processOutputHandler) {
            mOwner = owner;
            mJob = job;
            mLogger = iLogger;
            this.processOutputHandler = processOutputHandler;
        }

        @Override
        public void out(@Nullable String line) {
            if (line != null) {
                //AAPT1 outputs "Done" and "Error" to stdout.
                if (line.equalsIgnoreCase("Done")) {
                    mOwner.reset();
                    if (mInError.get()) {
                        mJob.error(new AaptException(AaptProcess.joiner.join(errors)));
                    } else {
                        mJob.finished();
                    }
                } else if (line.equalsIgnoreCase("Error")) {
                    mInError.set(true);
                } else {
                    mLogger.verbose("AAPT(%1$s) discarded: %2$s", mJob, line);
                }
            }
        }

        @Override
        public void err(@Nullable String line) {
            if (line != null) {
                //AAPT2 outputs "Done" and "Error" to stderr for better error handling.
                if (line.equalsIgnoreCase("Done")) {
                    mOwner.reset();
                    if (mInError.get()) {
                        if (!handleOutput()) {
                            // If processing the output failed, just print the errors.
                            mJob.error(new Aapt2Exception(AaptProcess.joiner.join(errors)));
                        }
                    } else {
                        mJob.finished();
                    }
                } else if (line.equalsIgnoreCase("Error")) {
                    // Mark that the error actually happened.
                    mInError.set(true);
                } else if (mInError.get() || line.contains("error:")) {
                    // Grab all the possible errors.
                    errors.add(line);
                }
                mLogger.verbose(
                        "AAPT warning(%1$s), Job(%2$s): %3$s",
                        mOwner.getProcess().hashCode(), mJob, line);
            }
        }

        private boolean handleOutput() {
            if (processOutputHandler == null) {
                return false;
            }

            ProcessOutput output;
            try (Closeable ignored = output = processOutputHandler.createOutput();
                    PrintWriter err = new PrintWriter(output.getErrorOutput())) {
                for (String error : errors) {
                    err.println(error);
                }
            } catch (IOException e) {
                mJob.error(new Aapt2Exception("Unexpected error parsing AAPT2 error output"));
                return false;
            }

            try {
                processOutputHandler.handleOutput(output);
            } catch (ProcessException e) {
                mJob.error(new Aapt2Exception("Unexpected error parsing AAPT2 error output"));
                return false;
            }

            mJob.error(new Aapt2Exception("AAPT2 error: check logs for details"));
            return true;
        }
    }
}
