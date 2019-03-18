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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The baseline file is a list of whitelisted callstacks that should be ignored when there is a
 * matching aspect. That helps to enable rules currently currently disrespected in a few places to
 * make sure we don't introduce more occurrences in the future.
 *
 * <p>The baseline files are made by one whitelisted callstack per line, in the format:
 * method1|method2|...|methodN, where N is defined by {@link #SUBSTACK_SIZE}
 *
 * <p>For N = 2, we could have the following lines as example:
 * com.example.LaunchCompatibilityCheckerImpl.validate|com.other.pkg.Device.<init>
 * sun.configurations.Configuration.computeBestDevice|com.tools.Configuration.getDevice
 * com.example.Configuration.getDevice|com.nele.NlPreviewForm.lambda$initNeleModelWhenSmart$8
 *
 * <p>TODO: add utility methods to generate baseline files for given rules.
 */
public class Baseline {
    private static final Logger LOGGER = Logger.getLogger(Baseline.class.getName());

    private static final String SEPARATOR = "|";

    private static final String SEPARATOR_REGEX = "\\|";

    /**
     * Size of the subcallstack relevant to the whitelist. Generally, we don't need to whitelist the
     * full callstack as it would be a waste of resources. The top couple/few calls should be enough
     * to make each whitelisted flow unique.
     */
    private static final int SUBSTACK_SIZE = 2;

    /**
     * Index of a {@link StackTraceElement} array representing the current thread's callstack. This
     * index should skip the first element (Thread.currentThread().getStackTrace()) and the
     * following methods irrelevant to the callstack (e.g. the method called when intercepting an
     * instrumented call).
     */
    private static final int STACKTRACE_START_INDEX = 2;

    @NonNull private static Set<String> whitelist = Collections.emptySet();

    private Baseline() {}

    /** Parses the baseline content into a {@link Set<String>}. */
    public static void parse(@Nullable InputStream input) {
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
                            LOGGER.info(
                                    String.format(
                                            "Ignoring whitelisted callstack:\n%s",
                                            formattedCallstack(callstack))));
            Baseline.whitelist = whitelist;
        } catch (IOException e) {
            LOGGER.warning(String.format("Error while parsing the baseline.\n%s", e.getMessage()));
        }
    }

    @NonNull
    private static String formattedCallstack(@NonNull String callstack) {
        String[] toFormat = callstack.split(SEPARATOR_REGEX);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < toFormat.length; i++) {
            for (int j = 0; j < i; j++) {
                // Indentation
                output.append("  ");
            }
            output.append(toFormat[i]).append("\n");
        }
        return output.toString();
    }

    /**
     * Checks whether the given {@link StackTraceElement} corresponds to a whitelisted callstack.
     * TODO: allow whitelisting callstacks for specific annotations/rules.
     */
    public static boolean isWhitelisted(@NonNull StackTraceElement[] stackTrace) {
        return whitelist.contains(stackTraceToString(stackTrace));
    }

    private static String stackTraceToString(@NonNull StackTraceElement[] stackTrace) {
        StringBuilder callstack = new StringBuilder();
        for (int i = 0; i < SUBSTACK_SIZE; i++) {
            if (i > 0) {
                callstack.append(SEPARATOR);
            }
            callstack.append(
                    String.format(
                            "%s.%s",
                            stackTrace[STACKTRACE_START_INDEX + i].getClassName(),
                            stackTrace[STACKTRACE_START_INDEX + i].getMethodName()));
        }
        return callstack.toString();
    }
}
