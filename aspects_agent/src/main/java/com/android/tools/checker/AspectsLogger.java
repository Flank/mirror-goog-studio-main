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

package com.android.tools.checker;

import static com.android.tools.checker.util.StacktraceParser.stackTraceToString;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.checker.agent.Baseline;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

@SuppressWarnings("unused") // Called via reflection
public class AspectsLogger {
    private static final Logger LOGGER = Logger.getLogger(AspectsLogger.class.getName());

    /**
     * Guard for {@link #aspectsAgentLog}. As it's lazily initiated by instrumented code and
     * different threads might call {@link #getAspectsAgentLog()} simultaneously, we need to
     * synchronize its definition.
     */
    private static final Object ASPECTS_LOG_LOCK = new Object();

    @VisibleForTesting @Nullable static File aspectsAgentLog;

    private AspectsLogger() {}

    public static void logIfEdt() {
        log(() -> !SwingUtilities.isEventDispatchThread(), Thread.currentThread().getStackTrace());
    }

    public static void logIfNotEdt() {
        log(SwingUtilities::isEventDispatchThread, Thread.currentThread().getStackTrace());
    }

    private static void log(Supplier<Boolean> shouldReturnEarly, StackTraceElement[] stackTrace) {
        if (shouldReturnEarly.get() || Baseline.getInstance().isWhitelisted(stackTrace)) {
            return;
        }
        if (Baseline.getInstance().isGeneratingBaseline()) {
            Baseline.getInstance().whitelistStackTrace(stackTrace);
            return;
        }

        if (getAspectsAgentLog() == null) {
            LOGGER.info("Aspects agent log does not exist.");
            return;
        }

        try {
            assert aspectsAgentLog != null;
            // Synchronizing this non-final field should be safe here because when it's not null, it
            // means we've initialized it in getAspectsAgentLog(). When that's the case, its value
            // shouldn't change since this method will return the already stored value.
            //noinspection SynchronizeOnNonFinalField
            synchronized (aspectsAgentLog) {
                Files.write(
                        getAspectsAgentLog().toPath(),
                        Collections.singleton(stackTraceToString(stackTrace)),
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            LOGGER.info("Failed to write violation to the aspects agent log");
        }
    }

    @Nullable
    private static File getAspectsAgentLog() {
        synchronized (ASPECTS_LOG_LOCK) {
            if (aspectsAgentLog != null) {
                return aspectsAgentLog;
            }
            String logPath = System.getenv("ASPECTS_AGENT_LOG");
            if (logPath != null) {
                aspectsAgentLog = new File(logPath);
                if (!aspectsAgentLog.exists()) {
                    // The file path provided in the $ASPECTS_AGENT_LOG environment does not exist
                    aspectsAgentLog = null;
                }
            }
        }
        return aspectsAgentLog;
    }
}
