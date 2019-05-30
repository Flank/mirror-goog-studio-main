/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.checker.agent;

import static com.android.tools.checker.util.StacktraceParser.formattedCallstack;
import static com.android.tools.checker.util.StacktraceParser.stackTraceToString;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The baseline file is a list of whitelisted callstacks that should be ignored when there is a
 * matching aspect. That helps to enable rules currently currently disrespected in a few places to
 * make sure we don't introduce more occurrences in the future.
 *
 * <p>The baseline files are made by one whitelisted callstack per line, in the format defined by
 * {@link com.android.tools.checker.util.StacktraceParser}.
 */
public class Baseline {
    private static final Logger LOGGER = Logger.getLogger(Baseline.class.getName());

    /**
     * Guards the {@link #activeStackTracesLog} to make sure we don't log duplicate stack traces.
     */
    private static final Object ACTIVE_STACK_TRACES_LOCK = new Object();

    private static Baseline instance;

    /**
     * Accessed through {@link #isGeneratingBaseline()}. When null, the getter should calculate its
     * value from system properties.
     */
    @Nullable private Boolean isGeneratingBaseline;

    // TODO: allow whitelisting stack traces for specific rules.
    @NonNull private Set<String> whitelist = new HashSet<>();

    /**
     * Keeps track of the active stack traces, i.e. the ones that are actually being called in the
     * tests. This is helpful in case we want to remove stale stack traces from the baseline.
     */
    @NonNull private Set<String> activeWhitelistEntries = new HashSet<>();

    private File activeStackTracesLog;

    @VisibleForTesting
    Baseline(File activeStackTracesLog) {
        this.activeStackTracesLog = activeStackTracesLog;
    }

    /** Parses the baseline content into a {@link Set<String>}. */
    public void parse(@Nullable InputStream input) {
        if (input == null) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            Set<String> whitelist = new HashSet<>();
            String line;

            while ((line = reader.readLine()) != null) {
                whitelist.add(line);
            }
            whitelist.forEach(
                    (callstack) ->
                            LOGGER.fine(
                                    String.format(
                                            "Ignoring whitelisted callstack:\n%s",
                                            formattedCallstack(callstack))));
            this.whitelist = whitelist;
        } catch (IOException e) {
            LOGGER.warning(String.format("Error while parsing the baseline.\n%s", e.getMessage()));
        }
    }

    @VisibleForTesting
    public static Baseline getInstance(boolean createNewInstance) {
        if (instance == null || createNewInstance) {
            instance = new Baseline(null);
        }
        return instance;
    }

    public static Baseline getInstance() {
        return getInstance(false);
    }

    /**
     * Checks whether the given {@link StackTraceElement} corresponds to a whitelisted callstack.
     * TODO: allow whitelisting callstacks for specific annotations/rules.
     */
    public boolean isWhitelisted(@NonNull StackTraceElement[] stackTrace) {
        String parsedStackTrace = stackTraceToString(stackTrace);
        boolean isWhitelisted = whitelist.contains(parsedStackTrace);
        if (isWhitelisted) {
            logActiveStackTrace(parsedStackTrace);
        }
        return isWhitelisted;
    }

    /**
     * Writes the stack trace to {@link #activeStackTracesLog} in case it was not written yet. This
     * method's content is synchronized in case we try to write the same stack trace simultaneously
     * from different threads.
     */
    private void logActiveStackTrace(String parsedStackTrace) {
        synchronized (ACTIVE_STACK_TRACES_LOCK) {
            if (activeWhitelistEntries.contains(parsedStackTrace)) {
                return;
            }
            File activeStackTraceFile = getBaselineActiveStackTraceFile();
            if (activeStackTraceFile != null) {
                try {
                    Files.write(
                            activeStackTraceFile.toPath(),
                            Collections.singleton(parsedStackTrace),
                            StandardOpenOption.APPEND);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            activeWhitelistEntries.add(parsedStackTrace);
        }
    }

    public void whitelistStackTrace(StackTraceElement[] stackTrace) {
        String parsedStackTrace = stackTraceToString(stackTrace);
        whitelist.add(parsedStackTrace);
    }

    public boolean isGeneratingBaseline() {
        // Lazily determine if we're in generating baseline mode
        if (isGeneratingBaseline == null) {
            isGeneratingBaseline =
                    System.getProperty("aspects.baseline.export.path") != null
                            && !System.getProperty("aspects.baseline.export.path").isEmpty();
            if (isGeneratingBaseline) {
                // If we are generating the baseline, add the shutdown hook to export it to a file.
                exportBaselineOnShutdown();
            }
        }
        return isGeneratingBaseline;
    }

    private void exportBaselineOnShutdown() {
        Runnable writeBaselineToFile =
                () -> {
                    String outputPath = System.getProperty("aspects.baseline.export.path");
                    LOGGER.info(
                            String.format(
                                    Locale.getDefault(),
                                    "Exporting %d elements to %s",
                                    whitelist.size(),
                                    outputPath));
                    List<String> baseline = new ArrayList<>(whitelist);
                    Collections.sort(baseline);
                    try {
                        Path output = Paths.get(outputPath);
                        Files.createDirectories(output.getParent());

                        Files.write(output, baseline);
                    } catch (IOException e) {
                        LOGGER.severe(
                                String.format(
                                        "Error while exporting baseline:\n%s", e.getMessage()));
                    }
                };
        Runtime.getRuntime().addShutdownHook(new Thread(writeBaselineToFile));
    }

    @Nullable
    private File getBaselineActiveStackTraceFile() {
        if (activeStackTracesLog != null) {
            return activeStackTracesLog;
        }
        String logPath = System.getenv("ASPECTS_ACTIVE_BASELINE_STACKTRACES");
        if (logPath == null) {
            return null; // Variable not set.
        }
        activeStackTracesLog = new File(logPath);
        if (!activeStackTracesLog.exists()) {
            // The file path provided in $ASPECTS_ACTIVE_BASELINE_STACKTRACES does not exist
            activeStackTracesLog = null;
        }
        return activeStackTracesLog;
    }
}
