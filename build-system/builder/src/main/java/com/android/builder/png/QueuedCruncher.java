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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.QueueThreadContext;
import com.android.builder.tasks.Task;
import com.android.builder.tasks.WorkQueue;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.internal.PngException;
import com.android.utils.ILogger;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * implementation of {@link com.android.ide.common.internal.PngCruncher} that queues request and
 * use a pool or aapt server processes to serve those.
 */
public class QueuedCruncher implements PngCruncher {

    /**
     * Number of concurrent cruncher processes to launch.
     */
    private static final int DEFAULT_NUMBER_CRUNCHER_PROCESSES = 5;

    /**
     * Delay to shutdown the work queue after the last {@link Terminator#shutdown()}.
     */
    private static final Duration TERMINATE_DELAY = Duration.ofSeconds(5);

    // use an enum to ensure singleton.
    public enum Builder {
        INSTANCE;

        private final Map<String, QueuedCruncher> sInstances = new ConcurrentHashMap<>();
        private final Object sLock = new Object();

        /**
         * Creates a new {@link com.android.builder.png.QueuedCruncher} or return an existing one
         * based on the underlying AAPT executable location.
         *
         * @param aaptLocation the AAPT executable location.
         * @param logger the logger to use
         * @param cruncherProcesses number of cruncher processes to use; {@code 0} to use the
         * default number
         * @return a new of existing instance of the {@link com.android.builder.png.QueuedCruncher}
         */
        public QueuedCruncher newCruncher(
                @NonNull String aaptLocation,
                @NonNull ILogger logger,
                int cruncherProcesses) {
            synchronized (sLock) {
                logger.info("QueuedCruncher is using %1$s", aaptLocation);
                if (!sInstances.containsKey(aaptLocation)) {
                    QueuedCruncher queuedCruncher =
                            new QueuedCruncher(aaptLocation, logger, cruncherProcesses);
                    sInstances.put(aaptLocation, queuedCruncher);
                }
                return sInstances.get(aaptLocation);
            }
        }
    }


    @NonNull private final String mAaptLocation;
    @NonNull private final ILogger mLogger;
    // Queue responsible for handling all passed jobs with a pool of worker threads.
    @NonNull private final WorkQueue<AaptProcess> mCrunchingRequests;
    // list of outstanding jobs.
    @NonNull private final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> mOutstandingJobs =
            new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>>();
    // list of finished jobs.
    @NonNull private final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> mDoneJobs =
            new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>>();
    // ref count of active users, if it drops to zero, that means there are no more active users
    // and the queue should be shutdown.
    @NonNull private final AtomicInteger refCount = new AtomicInteger(0);

    // per process unique key provider to remember which users enlisted which requests.
    @NonNull private final AtomicInteger keyProvider = new AtomicInteger(0);

    /**
     * Terminator thread used to finish the cruncher some time after all PNGs have been crunched.
     * The deferred termination is used to allow the cruncher to reuse aapt processes. This will
     * be {@code null} while there are no crunchers running and no need to terminate them.
     */
    @Nullable
    @GuardedBy("this")
    private Terminator mTerminator;

    private QueuedCruncher(
            @NonNull final String aaptLocation,
            @NonNull ILogger iLogger,
            int cruncherProcesses) {
        mAaptLocation = aaptLocation;
        mLogger = iLogger;
        QueueThreadContext<AaptProcess> queueThreadContext = new QueueThreadContext<AaptProcess>() {

            // move this to a TLS, but do not store instances of AaptProcess in it.
            @NonNull private final Map<String, AaptProcess> mAaptProcesses =
                    new ConcurrentHashMap<>();

            @Override
            public void creation(@NonNull Thread t) throws IOException {
                try {
                    AaptProcess aaptProcess = new AaptProcess.Builder(mAaptLocation, mLogger).start();
                    assert aaptProcess != null;
                    mLogger.verbose("Thread(%1$s): created aapt slave, Process(%2$s)",
                            Thread.currentThread().getName(), aaptProcess.hashCode());
                    aaptProcess.waitForReady();
                    mAaptProcesses.put(t.getName(), aaptProcess);
                } catch (InterruptedException e) {
                    mLogger.error(e, "Cannot start slave process");
                    e.printStackTrace();
                }
            }

            @Override
            public void runTask(@NonNull Job<AaptProcess> job) throws Exception {
                job.runTask(
                        new JobContext<AaptProcess>(
                                mAaptProcesses.get(Thread.currentThread().getName())));
                mOutstandingJobs.get(((QueuedJob) job).key).remove(job);
                mDoneJobs.get(((QueuedJob) job).key).add(job);
            }

            @Override
            public void destruction(@NonNull Thread t) throws IOException, InterruptedException {

                AaptProcess aaptProcess = mAaptProcesses.get(Thread.currentThread().getName());
                if (aaptProcess != null) {
                    mLogger.verbose("Thread(%1$s): notify aapt slave shutdown, Process(%2$s)",
                            Thread.currentThread().getName(), aaptProcess.hashCode());
                    aaptProcess.shutdown();
                    mAaptProcesses.remove(t.getName());
                    mLogger.verbose("Thread(%1$s): Process(%2$d), after shutdown queue_size=%3$d",
                            Thread.currentThread().getName(),
                            aaptProcess.hashCode(),
                            mAaptProcesses.size());
                }
            }

            @Override
            public void shutdown() {
                if (!mAaptProcesses.isEmpty()) {
                    mLogger.warning("Process list not empty");
                    for (Map.Entry<String, AaptProcess> aaptProcessEntry : mAaptProcesses
                            .entrySet()) {
                        mLogger.warning("Thread(%1$s): queue not cleaned", aaptProcessEntry.getKey());
                        try {
                            aaptProcessEntry.getValue().shutdown();
                        } catch (Exception e) {
                            mLogger.error(e, "while shutting down" + aaptProcessEntry.getKey());
                        }
                    }
                }
                mAaptProcesses.clear();
            }
        };

        int cruncherProcessToUse;
        if (cruncherProcesses > 0) {
            cruncherProcessToUse = cruncherProcesses;
        } else {
            cruncherProcessToUse = DEFAULT_NUMBER_CRUNCHER_PROCESSES;
        }

        mCrunchingRequests =
                new WorkQueue<>(
                        mLogger,
                        queueThreadContext,
                        "png-cruncher",
                        cruncherProcessToUse,
                        0);
    }

    private static final class QueuedJob extends Job<AaptProcess> {

        private final int key;
        public QueuedJob(int key, String jobTile, Task<AaptProcess> task) {
            super(jobTile, task);
            this.key = key;
        }
    }

    @Override
    public void crunchPng(int key, @NonNull final File from, @NonNull final File to)
            throws PngException {

        if (from.getAbsolutePath().length() > 240
                && SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            throw new PngException("File path too long on Windows, keep below 240 characters : "
                    + from.getAbsolutePath());
        }
        if (to.getAbsolutePath().length() > 240
                && SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            throw new PngException("File path too long on Windows, keep below 240 characters : "
                    + to.getAbsolutePath());
        }

        try {
            final Job<AaptProcess> aaptProcessJob = new QueuedJob(
                    key,
                    "Cruncher " + from.getName(),
                    new Task<AaptProcess>() {
                        @Override
                        public void run(@NonNull Job<AaptProcess> job,
                                @NonNull JobContext<AaptProcess> context) throws IOException {
                            AaptProcess aapt = context.getPayload();
                            if (aapt == null) {
                                mLogger.error(null /* throwable */,
                                        "Thread(%1$s) has a null payload",
                                        Thread.currentThread().getName());
                                return;
                            }
                            mLogger.verbose("Thread(%1$s): submitting job %2$s to %3$d",
                                    Thread.currentThread().getName(),
                                    job.getJobTitle(),
                                    aapt.hashCode());
                            aapt.crunch(from, to, job);
                            mLogger.verbose("Thread(%1$s): submitted job %2$s",
                                    Thread.currentThread().getName(), job.getJobTitle());
                        }

                        @Override
                        public String toString() {
                            return Objects.toStringHelper(this)
                                    .add("from", from.getName())
                                    .add("to", to.getAbsolutePath())
                                    .toString();
                        }
                    });
            mOutstandingJobs.get(key).add(aaptProcessJob);
            mCrunchingRequests.push(aaptProcessJob);
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            throw new PngException(e);
        }
    }

    private void waitForAll(int key) throws InterruptedException {
        mLogger.verbose("Thread(%1$s): begin waitForAll", Thread.currentThread().getName());
        ConcurrentLinkedQueue<Job<AaptProcess>> jobs = mOutstandingJobs.get(key);
        Job<AaptProcess> aaptProcessJob = jobs.poll();
        boolean hasExceptions = false;
        while (aaptProcessJob != null) {
            mLogger.verbose("Thread(%1$s) : wait for {%2$s)", Thread.currentThread().getName(),
                    aaptProcessJob.toString());
            if (!aaptProcessJob.await()) {
                throw new RuntimeException(
                        "Crunching " + aaptProcessJob.getJobTitle() + " failed, see logs");
            }
            if (aaptProcessJob.getFailureReason() != null) {
                mLogger.verbose("Exception while crunching png : " + aaptProcessJob.toString()
                        + " : " + aaptProcessJob.getFailureReason());
                hasExceptions = true;
            }
            aaptProcessJob = jobs.poll();
        }
        // process done jobs to retrieve potential issues.
        jobs = mDoneJobs.get(key);
        aaptProcessJob = jobs.poll();
        while(aaptProcessJob != null) {
            if (aaptProcessJob.getFailureReason() != null) {
                mLogger.verbose("Exception while crunching png : " + aaptProcessJob.toString()
                        + " : " + aaptProcessJob.getFailureReason());
                hasExceptions = true;
            }
            aaptProcessJob = jobs.poll();
        }
        if (hasExceptions) {
            throw new RuntimeException("Some file crunching failed, see logs for details");
        }
        mLogger.verbose("Thread(%1$s): end waitForAll", Thread.currentThread().getName());
    }

    @Override
    public synchronized int start() {
        // increment our reference count.
        if (refCount.incrementAndGet() == 1) {

            /*
             * First key starting. If there is no terminator running, start one. IF there is one
             * running, it is being reused from a previous run.
             */
            if (mTerminator == null) {
                mTerminator = new Terminator();
                mTerminator.start();
            }
        }

        // get a unique key for the lifetime of this process.
        int key = keyProvider.incrementAndGet();
        mOutstandingJobs.put(key, new ConcurrentLinkedQueue<Job<AaptProcess>>());
        mDoneJobs.put(key, new ConcurrentLinkedQueue<Job<AaptProcess>>());
        mTerminator.reset();
        return key;
    }

    @Override
    public synchronized void end(int key) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            waitForAll(key);
            mOutstandingJobs.get(key).clear();
            mLogger.verbose("Job finished in %1$d", System.currentTimeMillis() - startTime);
        } finally {
            // even if we have failures, we need to shutdown property the sub processes.
            if (refCount.decrementAndGet() == 0) {
                Preconditions.checkState(mTerminator != null, "mTerminator == null");
                mTerminator.shutdown();
                mLogger.verbose("Shutdown finished in %1$d",
                        System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * Thread that terminates all threads in the {@link WorkQueue} at {@link #mCrunchingRequests}
     * some time after {@link #shutdown()} is invoked. If {@link #shutdown()} is called multiple
     * times, the time will count from the last invocation.
     *
     * <p>If at any point {@link #reset()} is called, termination is suspended until
     * {@link #shutdown()} has been invoked and the timeout has passed.
     */
    private class Terminator extends Thread {

        /**
         * When to terminate. {@code null} if {@link #shutdown()} was not invoked.
         */
        @Nullable
        private Instant mTerminateAt;

        /**
         * Creates a new terminator thread.
         */
        private Terminator() {
            super("Cruncher terminator");

            mTerminateAt = null;
        }

        /**
         * Resets the terminator. The work queue will not be invoke unless {@link #shutdown()} is
         * called <i>after</i> this invocation (it is irrelevant if {@link #shutdown()} has been
         * called before this invocation.
         */
        private void reset() {
            synchronized (QueuedCruncher.this) {
                mTerminateAt = null;
                QueuedCruncher.this.notifyAll();
            }
        }

        /**
         * Shuts down the terminator in {@link #TERMINATE_DELAY}, overriding any
         * previously defined termination time.
         */
        private void shutdown() {
            synchronized (QueuedCruncher.this) {
                mTerminateAt = Instant.now().plus(TERMINATE_DELAY);
                QueuedCruncher.this.notifyAll();
            }
        }

        @Override
        public void run() {
            synchronized (QueuedCruncher.this) {
                while (true) {
                    try {
                        if (mTerminateAt == null) {
                            QueuedCruncher.this.wait();
                        } else {
                            long waitTime = ChronoUnit.MILLIS.between(Instant.now(), mTerminateAt);
                            if (waitTime > 0) {
                                QueuedCruncher.this.wait(waitTime);
                            }

                            if (mTerminateAt != null && !mTerminateAt.isAfter(Instant.now())) {
                                mCrunchingRequests.shutdown();
                                mTerminator = null;
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        /*
                         * We don't care.
                         */
                    }
                }
            }
        }
    }
}
