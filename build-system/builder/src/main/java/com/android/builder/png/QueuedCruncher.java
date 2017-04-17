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
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.QueueThreadContext;
import com.android.builder.tasks.Task;
import com.android.builder.tasks.WorkQueue;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.internal.PngException;
import com.android.utils.ILogger;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link com.android.ide.common.internal.PngCruncher} that queues request and use
 * a pool of aapt server processes to serve those.
 */
public class QueuedCruncher implements PngCruncher {

    private static final boolean VERBOSE_LOGGING = false;
    /**
     * Number of concurrent cruncher processes to launch.
     */
    private static final int DEFAULT_NUMBER_CRUNCHER_PROCESSES =
            Integer.max(5, Runtime.getRuntime().availableProcessors());

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
                logger.verbose("QueuedCruncher is using %1$s%n", aaptLocation);
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
            new ConcurrentHashMap<>();
    // list of finished jobs.
    @NonNull private final Map<Integer, ConcurrentLinkedQueue<Job<AaptProcess>>> mDoneJobs =
            new ConcurrentHashMap<>();
    // ref count of active users, if it drops to zero, that means there are no more active users
    // and the queue should be shutdown.
    @NonNull private final AtomicInteger refCount = new AtomicInteger(0);

    // per process unique key provider to remember which users enlisted which requests.
    @NonNull private final AtomicInteger keyProvider = new AtomicInteger(0);

    private QueuedCruncher(
            @NonNull final String aaptLocation,
            @NonNull ILogger iLogger,
            int cruncherProcesses) {
        mAaptLocation = aaptLocation;
        mLogger = iLogger;
        QueueThreadContext<AaptProcess> queueThreadContext =
                new QueueThreadContext<AaptProcess>() {

                    // move this to a TLS, but do not store instances of AaptProcess in it.
                    @NonNull
                    private final Map<String, AaptProcess> mAaptProcesses =
                            new ConcurrentHashMap<>();

                    @Override
                    public void creation(@NonNull Thread t) throws IOException {
                        try {
                            AaptProcess aaptProcess =
                                    new AaptProcess.Builder(mAaptLocation, mLogger).start();
                            assert aaptProcess != null;
                            if (VERBOSE_LOGGING) {
                                mLogger.verbose(
                                        "Thread(%1$s): created aapt slave, Process(%2$s)",
                                        Thread.currentThread().getName(), aaptProcess.hashCode());
                            }
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
                                new JobContext<>(
                                        mAaptProcesses.get(Thread.currentThread().getName())));
                        mOutstandingJobs.get(((QueuedJob) job).key).remove(job);
                        ConcurrentLinkedQueue<Job<AaptProcess>> jobs =
                                mDoneJobs.get(((QueuedJob) job).key);
                        synchronized (mDoneJobs) {
                            if (jobs == null) {
                                jobs = new ConcurrentLinkedQueue<>();
                                mDoneJobs.put(((QueuedJob) job).key, jobs);
                            }
                        }
                        jobs.add(job);
                    }

                    @Override
                    public void destruction(@NonNull Thread t)
                            throws IOException, InterruptedException {

                        AaptProcess aaptProcess =
                                mAaptProcesses.get(Thread.currentThread().getName());
                        if (aaptProcess != null) {
                            if (VERBOSE_LOGGING) {
                                mLogger.verbose(
                                        "Thread(%1$s): notify aapt slave shutdown, Process(%2$s)",
                                        Thread.currentThread().getName(), aaptProcess.hashCode());
                            }
                            aaptProcess.shutdown();
                            mAaptProcesses.remove(t.getName());
                            if (VERBOSE_LOGGING) {
                                mLogger.verbose(
                                        "Thread(%1$s): Process(%2$d), after shutdown queue_size=%3$d",
                                        Thread.currentThread().getName(),
                                        aaptProcess.hashCode(),
                                        mAaptProcesses.size());
                            }
                        }
                    }

                    @Override
                    public void shutdown() {
                        if (!mAaptProcesses.isEmpty()) {
                            mLogger.warning("Process list not empty");
                            for (Map.Entry<String, AaptProcess> aaptProcessEntry :
                                    mAaptProcesses.entrySet()) {
                                mLogger.warning(
                                        "Thread(%1$s): queue not cleaned",
                                        aaptProcessEntry.getKey());
                                try {
                                    aaptProcessEntry.getValue().shutdown();
                                } catch (Exception e) {
                                    mLogger.error(
                                            e, "while shutting down" + aaptProcessEntry.getKey());
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
        public QueuedJob(int key, String jobTile, Task<AaptProcess> task, ListenableFuture<File> resultFuture) {
            super(jobTile, task, resultFuture);
            this.key = key;
        }
    }

    @Override
    public ListenableFuture<File> crunchPng(int key, @NonNull final File from, @NonNull final File to)
            throws PngException {
        SettableFuture<File> result = SettableFuture.create();
        try {
            final Job<AaptProcess> aaptProcessJob =
                    new QueuedJob(
                            key,
                            "Cruncher " + from.getName(),
                            new Task<AaptProcess>() {
                                @Override
                                public void run(
                                        @NonNull Job<AaptProcess> job,
                                        @NonNull JobContext<AaptProcess> context)
                                        throws IOException {
                                    AaptProcess aapt = context.getPayload();
                                    if (aapt == null) {
                                        mLogger.error(
                                                null /* throwable */,
                                                "Thread(%1$s) has a null payload",
                                                Thread.currentThread().getName());
                                        return;
                                    }
                                    if (VERBOSE_LOGGING) {
                                        mLogger.verbose(
                                                "Thread(%1$s): submitting job %2$s to %3$d",
                                                Thread.currentThread().getName(),
                                                job.getJobTitle(),
                                                aapt.hashCode());
                                    }
                                    aapt.crunch(from, to, job);
                                    if (VERBOSE_LOGGING) {
                                        mLogger.verbose(
                                                "Thread(%1$s): submitted job %2$s",
                                                Thread.currentThread().getName(),
                                                job.getJobTitle());
                                    }
                                }

                                @Override
                                public void finished() {
                                    result.set(to);
                                }

                                @Override
                                public void error(Exception e) {
                                    result.setException(e);
                                }

                                @Override
                                public String toString() {
                                    return MoreObjects.toStringHelper(this)
                                            .add("from", from.getAbsolutePath())
                                            .add("to", to.getAbsolutePath())
                                            .toString();
                                }
                            },
                            result);
            ConcurrentLinkedQueue<Job<AaptProcess>> jobs = mOutstandingJobs.get(key);
            synchronized (mOutstandingJobs) {
                if (jobs == null) {
                    jobs = new ConcurrentLinkedQueue<>();
                    mOutstandingJobs.put(key, jobs);
                }
            }
            mCrunchingRequests.push(aaptProcessJob);
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            throw new PngException(e);
        }
        return result;
    }

    private void waitForAll(int key) throws InterruptedException {
        if (VERBOSE_LOGGING) {
            mLogger.verbose("Thread(%1$s): begin waitForAll", Thread.currentThread().getName());
        }
        ConcurrentLinkedQueue<Job<AaptProcess>> jobs = mOutstandingJobs.get(key);
        Job<AaptProcess> aaptProcessJob = jobs.poll();
        boolean hasExceptions = false;
        while (aaptProcessJob != null) {
            mLogger.verbose("Thread(%1$s) : wait for {%2$s)", Thread.currentThread().getName(),
                    aaptProcessJob.toString());
            try {
                aaptProcessJob.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                mLogger.verbose("Exception while crunching png : " + aaptProcessJob.toString()
                        + " : " + e.getCause());
                hasExceptions = true;
            }
            aaptProcessJob = jobs.poll();
        }
        // process done jobs to retrieve potential issues.
        jobs = mDoneJobs.get(key);
        while((aaptProcessJob = jobs.poll()) != null) {
            try {
                aaptProcessJob.awaitRethrowExceptions();
            } catch (ExecutionException e) {
                mLogger.verbose("Exception while crunching png : " + aaptProcessJob.toString()
                        + " : " + e.getCause());
                hasExceptions = true;
            }
        }
        if (hasExceptions) {
            throw new RuntimeException("Some file crunching failed, see logs for details");
        }
        if (VERBOSE_LOGGING) {
            mLogger.verbose("Thread(%1$s): end waitForAll", Thread.currentThread().getName());
        }
    }

    @Override
    public synchronized int start() {
        // increment our reference count.
        refCount.incrementAndGet();

        // get a unique key for the lifetime of this process.
        int key = keyProvider.incrementAndGet();
        mOutstandingJobs.put(key, new ConcurrentLinkedQueue<>());
        mDoneJobs.put(key, new ConcurrentLinkedQueue<>());
        return key;
    }

    @Override
    public synchronized void end(int key) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        try {
            waitForAll(key);
            mOutstandingJobs.get(key).clear();
            if (VERBOSE_LOGGING) {
                mLogger.verbose("Job finished in %1$d", System.currentTimeMillis() - startTime);
            }
        } finally {
            // even if we have failures, we need to shutdown property the sub processes.
            if (refCount.decrementAndGet() == 0) {
                try {
                    mCrunchingRequests.shutdown();
                } catch(InterruptedException e) {
                    Thread.interrupted();
                    mLogger.warning("Error while shutting down crunching queue : %s",
                            e.getMessage());
                }
                mLogger.verbose(
                        "Shutdown finished in %1$dms", System.currentTimeMillis() - startTime);
            }
        }
    }
}
