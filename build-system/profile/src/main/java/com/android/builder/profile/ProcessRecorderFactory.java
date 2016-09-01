/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.profile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.Anonymizer;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configures and creates instances of {@link ProcessRecorder}.
 *
 * There can be only one instance of {@link ProcessRecorder} per process (well class loader
 * to be exact). This instance can be configured initially before any calls to
 * {@link ThreadRecorder#get()} is made. An exception will be thrown if an attempt is made to
 * configure the instance of {@link ProcessRecorder} past this initialization window.
 *
 */
public class ProcessRecorderFactory {

    public static void shutdown() throws InterruptedException {
        synchronized (LOCK) {

            if (sINSTANCE.isInitialized()) {
                sINSTANCE.processRecorder.finish();
            }
            sINSTANCE.processRecorder = null;
        }
    }


    @NonNull
    private ScheduledExecutorService mScheduledExecutorService = Executors.newScheduledThreadPool(1);

    @VisibleForTesting
    ProcessRecorderFactory() {}
    @Nullable
    private ILogger mLogger = null;

    /**
     * Set up the the ProcessRecorder. Idempotent for multi-project builds.
     *
     */
    public static void initialize(
            @NonNull File projectPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger,
            @Nullable File out) {

        synchronized (LOCK) {
            if (sINSTANCE.isInitialized()) {
                return;
            }
            sINSTANCE.setLogger(logger);
            sINSTANCE.setProfileOutputFile(out != null ? out.toPath() : null);

            ProcessRecorder recorder = sINSTANCE.get(); // Initialize the ProcessRecorder instance

            setGlobalProperties(recorder, projectPath, gradleVersion, logger);
        }
    }

    private static void setGlobalProperties(
            @NonNull ProcessRecorder recorder,
            @NonNull File projectPath,
            @NonNull String gradleVersion,
            @NonNull ILogger logger) {
        recorder.getProperties()
                .setOsName(Strings.nullToEmpty(System.getProperty("os.name")))
                .setOsVersion(Strings.nullToEmpty(System.getProperty("os.version")))
                .setJavaVersion(Strings.nullToEmpty(System.getProperty("java.version")))
                .setJavaVmVersion(Strings.nullToEmpty(System.getProperty("java.vm.version")))
                .setMaxMemory(Runtime.getRuntime().maxMemory())
                .setGradleVersion(Strings.nullToEmpty(gradleVersion));

        try {
            recorder.getProperties().setProjectId(
                    Anonymizer.anonymizeUtf8(logger, projectPath.getAbsolutePath()));
        } catch (IOException e) {
            logger.error(e, "Could not anonymize project id.");
        }
    }

    public synchronized void setLogger(@NonNull ILogger iLogger) {
        assertRecorderNotCreated();
        this.mLogger = iLogger;
    }

    public static ProcessRecorderFactory getFactory() {
        return sINSTANCE;
    }

    boolean isInitialized() {
        return processRecorder != null;
    }

    @SuppressWarnings("VariableNotUsedInsideIf")
    private void assertRecorderNotCreated() {
        if (isInitialized()) {
            throw new RuntimeException("ProcessRecorder already created.");
        }
    }

    private static final Object LOCK = new Object();
    static ProcessRecorderFactory sINSTANCE = new ProcessRecorderFactory();

    @Nullable
    private ProcessRecorder processRecorder = null;

    @VisibleForTesting
    public static void initializeForTests(@NonNull Path profileOutputFile) {
        sINSTANCE = new ProcessRecorderFactory();
        sINSTANCE.setProfileOutputFile(profileOutputFile);
        ProcessRecorder.resetForTests();
        ProcessRecorder recorder = sINSTANCE.get(); // Initialize the ProcessRecorder instance
        setGlobalProperties(recorder,
                new File("fake/path/to/test_project/"),
                "2.10",
                new StdLogger(StdLogger.Level.VERBOSE));
    }

    private static void initializeAnalytics(@NonNull ILogger logger,
            @NonNull ScheduledExecutorService eventLoop) {
        AnalyticsSettings settings = AnalyticsSettings.getInstance(logger);
        UsageTracker.initialize(settings, eventLoop);
        UsageTracker tracker = UsageTracker.getInstance();
        tracker.setMaxJournalTime(10, TimeUnit.MINUTES);
        tracker.setMaxJournalSize(1000);
    }

    private Path profileOutputFile = null;

    private void setProfileOutputFile(Path outputFile) {
        this.profileOutputFile = outputFile;
    }

    synchronized ProcessRecorder get() {
        if (processRecorder == null) {
            if (mLogger == null) {
                mLogger = new StdLogger(StdLogger.Level.INFO);
            }
            initializeAnalytics(mLogger, mScheduledExecutorService);
            processRecorder = new ProcessRecorder(profileOutputFile);
        }

        return processRecorder;
    }

}
