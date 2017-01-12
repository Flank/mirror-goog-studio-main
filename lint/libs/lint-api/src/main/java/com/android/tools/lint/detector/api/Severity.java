/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.LintOptions;
import com.google.common.annotations.Beta;

/**
 * Severity of an issue found by lint
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public enum Severity {
    /**
     * Fatal: Use sparingly because a warning marked as fatal will be
     * considered critical and will abort Export APK etc in ADT
     */
    @NonNull
    FATAL("Fatal"),

    /**
     * Errors: The issue is known to be a real error that must be addressed.
     */
    @NonNull
    ERROR("Error"),

    /**
     * Warning: Probably a problem.
     */
    @NonNull
    WARNING("Warning"),

    /**
     * Information only: Might not be a problem, but the check has found
     * something interesting to say about the code.
     */
    @NonNull
    INFORMATIONAL("Information"),

    /**
     * Ignore: The user doesn't want to see this issue
     */
    @NonNull
    IGNORE("Ignore");

    @NonNull
    private final String display;

    Severity(@NonNull String display) {
        this.display = display;
    }

    /**
     * Returns a description of this severity suitable for display to the user
     *
     * @return a description of the severity
     */
    @NonNull
    public String getDescription() {
        return display;
    }

    /** Returns the name of this severity */
    @NonNull
    public String getName() {
        return name();
    }

    /**
     * Looks up the severity corresponding to a given named severity. The severity
     * string should be one returned by {@link #toString()}
     *
     * @param name the name to look up
     * @return the corresponding severity, or null if it is not a valid severity name
     */
    @Nullable
    public static Severity fromName(@NonNull String name) {
        for (Severity severity : values()) {
            if (severity.name().equalsIgnoreCase(name)) {
                return severity;
            }
        }

        return null;
    }

    /**
     * Returns a severity corresponding to the various {@link LintOptions} severity
     * constants
     *
     * @param severity the {@link LintOptions} severity constant, such as {@link
     *                 LintOptions#SEVERITY_FATAL}
     * @return the corresponding lint {@linkplain Severity}
     */
    @NonNull
    public static Severity fromLintOptionSeverity(int severity) {
        switch (severity) {
            case LintOptions.SEVERITY_FATAL:
                return Severity.FATAL;
            case LintOptions.SEVERITY_ERROR:
                return Severity.ERROR;
            case LintOptions.SEVERITY_WARNING:
                return Severity.WARNING;
            case LintOptions.SEVERITY_INFORMATIONAL:
                return Severity.INFORMATIONAL;
            case LintOptions.SEVERITY_IGNORE:
            default:
                return Severity.IGNORE;
        }
    }

    /**
     * Returns the smallest / least severe of the two given severities
     *
     * @param severity1 the first severity to compare
     * @param severity2 the second severity to compare
     * @return the least severe of the given severities
     */
    @NonNull
    public static Severity min(@NonNull Severity severity1, @NonNull Severity severity2) {
        // Using ">" instead of "<" here because compareTo is inherited from
        // enum and the severity constants are in descending order of severity
        return severity1.compareTo(severity2) > 0 ? severity1 : severity2;
    }

    /**
     * Returns the largest / most severe of the two given severities
     *
     * @param severity1 the first severity to compare
     * @param severity2 the second severity to compare
     * @return the most severe of the given severities
     */
    @NonNull
    public static Severity max(@NonNull Severity severity1, @NonNull Severity severity2) {
        // Using "<" instead of ">" here because compareTo is inherited from
        // enum and the severity constants are in descending order of severity
        return severity1.compareTo(severity2) < 0 ? severity1 : severity2;
    }

    /** Returns true if this severity is at least an error */
    public boolean isError() {
        return this == ERROR || this == FATAL;
    }
}