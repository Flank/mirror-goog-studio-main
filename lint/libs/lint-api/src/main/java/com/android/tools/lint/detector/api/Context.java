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

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.SUPPRESS_ALL;
import static com.android.tools.lint.detector.api.CharSequences.indexOf;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.SdkInfo;
import com.google.common.annotations.Beta;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Node;

/**
 * Context passed to the detectors during an analysis run. It provides
 * information about the file being analyzed, it allows shared properties (so
 * the detectors can share results), etc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class Context {
    /**
     * The file being checked. Note that this may not always be to a concrete
     * file. For example, in the {@link Detector#beforeCheckProject(Context)}
     * method, the context file is the directory of the project.
     */
    public final File file;

    /** The driver running through the checks */
    protected final LintDriver driver;

    /** The project containing the file being checked */
    @NonNull
    private final Project project;

    /**
     * The "main" project. For normal projects, this is the same as {@link #project},
     * but for library projects, it's the root project that includes (possibly indirectly)
     * the various library projects and their library projects.
     * <p>
     * Note that this is a property on the {@link Context}, not the
     * {@link Project}, since a library project can be included from multiple
     * different top level projects, so there isn't <b>one</b> main project,
     * just one per main project being analyzed with its library projects.
     */
    private final Project mainProject;

    /** The current configuration controlling which checks are enabled etc */
    private final Configuration configuration;

    /** The contents of the file */
    private CharSequence contents;

    /** Map of properties to share results between detectors */
    private Map<String, Object> properties;

    /** Whether this file contains any suppress markers (null means not yet determined) */
    private Boolean containsCommentSuppress;

    /**
     * Construct a new {@link Context}
     *
     * @param driver the driver running through the checks
     * @param project the project containing the file being checked
     * @param main the main project if this project is a library project, or
     *            null if this is not a library project. The main project is
     *            the root project of all library projects, not necessarily the
     *            directly including project.
     * @param file the file being checked
     */
    public Context(
            @NonNull LintDriver driver,
            @NonNull Project project,
            @Nullable Project main,
            @NonNull File file) {
        this.file = file;

        this.driver = driver;
        this.project = project;
        mainProject = main;
        configuration = project.getConfiguration(driver);
    }

    /**
     * Returns the scope for the lint job
     *
     * @return the scope, never null
     */
    @NonNull
    public EnumSet<Scope> getScope() {
        return driver.getScope();
    }

    /**
     * Returns the configuration for this project.
     *
     * @return the configuration, never null
     */
    @NonNull
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Returns the project containing the file being checked
     *
     * @return the project, never null
     */
    @NonNull
    public Project getProject() {
        return project;
    }

    /**
     * Returns the main project if this project is a library project, or self
     * if this is not a library project. The main project is the root project
     * of all library projects, not necessarily the directly including project.
     *
     * @return the main project, never null
     */
    @NonNull
    public Project getMainProject() {
        return mainProject != null ? mainProject : project;
    }

    /**
     * Returns the lint client requesting the lint check
     *
     * @return the client, never null
     */
    @NonNull
    public LintClient getClient() {
        return driver.getClient();
    }

    /**
     * Returns the driver running through the lint checks
     *
     * @return the driver
     */
    @NonNull
    public LintDriver getDriver() {
        return driver;
    }

    /**
     * Returns the contents of the file. This may not be the contents of the
     * file on disk, since it delegates to the {@link LintClient}, which in turn
     * may decide to return the current edited contents of the file open in an
     * editor.
     *
     * @return the contents of the given file, or null if an error occurs.
     */
    @Nullable
    public CharSequence getContents() {
        if (contents == null) {
            contents = driver.getClient().readFile(file);
        }

        return contents;
    }

    /**
     * Returns the value of the given named property, or null.
     *
     * @param name the name of the property
     * @return the corresponding value, or null
     */
    @SuppressWarnings("UnusedDeclaration") // Used in ADT
    @Nullable
    public Object getProperty(String name) {
        if (properties == null) {
            return null;
        }

        return properties.get(name);
    }

    /**
     * Sets the value of the given named property.
     *
     * @param name the name of the property
     * @param value the corresponding value
     */
    @SuppressWarnings("UnusedDeclaration") // Used in ADT
    public void setProperty(@NonNull String name, @Nullable Object value) {
        if (value == null) {
            if (properties != null) {
                properties.remove(name);
            }
        } else {
            if (properties == null) {
                properties = new HashMap<>();
            }
            properties.put(name, value);
        }
    }

    /**
     * Gets the SDK info for the current project.
     *
     * @return the SDK info for the current project, never null
     */
    @NonNull
    public SdkInfo getSdkInfo() {
        return project.getSdkInfo();
    }

    // ---- Convenience wrappers  ---- (makes the detector code a bit leaner)

    /**
     * Returns false if the given issue has been disabled. Convenience wrapper
     * around {@link Configuration#getSeverity(Issue)}.
     *
     * @param issue the issue to check
     * @return false if the issue has been disabled
     */
    public boolean isEnabled(@NonNull Issue issue) {
        return configuration.isEnabled(issue);
    }

    /**
     * Reports an issue. Convenience wrapper around {@link LintClient#report}
     *
     * @param issue the issue to report
     * @param location the location of the issue
     * @param message the message for this warning
     */
    public void report(
            @NonNull Issue issue,
            @NonNull Location location,
            @NonNull String message) {
        report(issue, location, message, null);
    }

    /**
     * Reports an issue. Convenience wrapper around {@link LintClient#report}
     *
     * @param issue        the issue to report
     * @param location     the location of the issue
     * @param message      the message for this warning
     * @param quickfixData parameterized data for IDE quickfixes. If you're passing in
     *                     multiple parameters, consider using {@link QuickfixData} instead
     *                     of using for example {@link com.android.utils.Pair} or {@link Map}
     */
    public void report(
            @NonNull Issue issue,
            @NonNull Location location,
            @NonNull String message,
            @Nullable Object quickfixData) {
        // See if we actually have an associated source for this location, and if so
        // check to see if the warning might be suppressed.
        Object source = location.getSource();
        if (source instanceof Node) {
            Node node = (Node) source;
            // Also see if we have the context for this location (e.g. code could
            // have directly called XmlContext/JavaContext report methods instead); this
            // is better because the context also checks for issues suppressed via comment
            if (this instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) this;
                if (node.getOwnerDocument() == xmlContext.document) {
                    xmlContext.report(issue, node, location, message, quickfixData);
                    return;
                }
            }
            if (driver.isSuppressed(null, issue, node)) {
                return;
            }
        } else if (source instanceof PsiElement) {
            // Check for suppressed issue via location node
            PsiElement element = (PsiElement) source;
            if (this instanceof JavaContext) {
                JavaContext javaContext = (JavaContext) this;
                if (Objects.equals(element.getContainingFile(), javaContext.getPsiFile())) {
                    javaContext.report(issue, element, location, message, quickfixData);
                    return;
                }
            }
            if (driver.isSuppressed(null, issue, element)) {
                return;
            }
        } else if (source instanceof UElement) {
            PsiElement element = ((UElement) source).getPsi();
            if (element != null && this instanceof JavaContext) {
                JavaContext javaContext = (JavaContext) this;
                if (Objects.equals(element.getContainingFile(), javaContext.getPsiFile())) {
                    javaContext.report(issue, element, location, message, quickfixData);
                    return;
                }
            }
            if (driver.isSuppressed(null, issue, element)) {
                return;
            }
        }

        doReport(issue, location, message, quickfixData);
    }

    // Method not callable outside of the lint infrastructure: perform the actual reporting.
    // This is a separate method instead of just having Context#report() do this work,
    // since Context#report() will possibly redirect to the XmlContext or JavaContext reporting
    // mechanisms if it discovers that it's been called on the wrong node.
    void doReport(
            @NonNull Issue issue,
            @NonNull Location location,
            @NonNull String message,
            @Nullable Object quickfixData) {
        //noinspection ConstantConditions
        if (location == null) {
            // Misbehaving third-party lint detectors
            assert false : issue;
            return;
        }

        if (location == Location.NONE) {
            // Detector reported error for issue in a non-applicable location etc
            return;
        }

        Configuration configuration = this.configuration;

        // If this error was computed for a context where the context corresponds to
        // a project instead of a file, the actual error may be in a different project (e.g.
        // a library project), so adjust the configuration as necessary.
        Project project = driver.findProjectFor(location.getFile());
        if (project != null) {
            configuration = project.getConfiguration(driver);
        }

        // If an error occurs in a library project, but you've disabled that check in the
        // main project, disable it in the library project too. (In some cases you don't
        // control the lint.xml of a library project, and besides, if you're not interested in
        // a check for your main project you probably don't care about it in the library either.)
        if (configuration != this.configuration
                && this.configuration.getSeverity(issue) == Severity.IGNORE) {
            return;
        }

        Severity severity = configuration.getSeverity(issue);
        if (severity == Severity.IGNORE) {
            return;
        }

        driver.getClient().report(this, issue, severity, location, message, TextFormat.RAW,
                quickfixData);
    }

    /**
     * Send an exception to the log. Convenience wrapper around {@link LintClient#log}.
     *
     * @param exception the exception, possibly null
     * @param format the error message using {@link String#format} syntax, possibly null
     * @param args any arguments for the format string
     */
    public void log(
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args) {
        driver.getClient().log(exception, format, args);
    }

    /**
     * Returns the current phase number. The first pass is numbered 1. Only one pass
     * will be performed, unless a {@link Detector} calls {@link #requestRepeat}.
     *
     * @return the current phase, usually 1
     */
    public int getPhase() {
        return driver.getPhase();
    }

    /**
     * Requests another pass through the data for the given detector. This is
     * typically done when a detector needs to do more expensive computation,
     * but it only wants to do this once it <b>knows</b> that an error is
     * present, or once it knows more specifically what to check for.
     *
     * @param detector the detector that should be included in the next pass.
     *            Note that the lint runner may refuse to run more than a couple
     *            of runs.
     * @param scope the scope to be revisited. This must be a subset of the
     *       current scope ({@link #getScope()}, and it is just a performance hint;
     *       in particular, the detector should be prepared to be called on other
     *       scopes as well (since they may have been requested by other detectors).
     *       You can pall null to indicate "all".
     */
    public void requestRepeat(@NonNull Detector detector, @Nullable EnumSet<Scope> scope) {
        driver.requestRepeat(detector, scope);
    }

    /** Returns the comment marker used in Studio to suppress statements for language, if any */
    @Nullable
    protected String getSuppressCommentPrefix() {
        // Java and XML files are handled in sub classes (XmlContext, JavaContext)

        String path = file.getPath();
        if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_GRADLE)) {
            return JavaContext.SUPPRESS_COMMENT_PREFIX;
        } else if (path.endsWith(DOT_XML)) {
            return XmlContext.SUPPRESS_COMMENT_PREFIX;
        } else if (path.endsWith(".cfg") || path.endsWith(".pro")) {
            return "#suppress ";
        }

        return null;
    }

    /** Returns whether this file contains any suppress comment markers */
    public boolean containsCommentSuppress() {
        if (containsCommentSuppress == null) {
            containsCommentSuppress = false;
            String prefix = getSuppressCommentPrefix();
            if (prefix != null) {
                CharSequence contents = getContents();
                if (contents != null) {
                    containsCommentSuppress = indexOf(contents, prefix) != -1;
                }
            }
        }

        return containsCommentSuppress;
    }

    /**
     * Returns true if the given issue is suppressed at the given character offset
     * in the file's contents
     */
    public boolean isSuppressedWithComment(int startOffset, @NonNull Issue issue) {
        String prefix = getSuppressCommentPrefix();
        if (prefix == null) {
            return false;
        }

        if (startOffset <= 0) {
            return false;
        }

        // Check whether there is a comment marker
        CharSequence contents = getContents();
        assert contents != null; // otherwise we wouldn't be here
        if (startOffset >= contents.length()) {
            return false;
        }

        // Scan backwards to the previous line and see if it contains the marker
        int lineStart = CharSequences.lastIndexOf(contents, '\n', startOffset) + 1;
        if (lineStart <= 1) {
            return false;
        }
        int index = findPrefixOnPreviousLine(contents, lineStart, prefix);
        if (index != -1 &&index+prefix.length() < lineStart) {
            String line = contents.subSequence(index + prefix.length(), lineStart).toString();
            return line.contains(issue.getId())
                    || line.contains(SUPPRESS_ALL) && line.trim().startsWith(SUPPRESS_ALL);
        }

        return false;
    }

    private static int findPrefixOnPreviousLine(CharSequence contents, int lineStart,
            String prefix) {
        // Search backwards on the previous line until you find the prefix start (also look
        // back on previous lines if the previous line(s) contain just whitespace
        char first = prefix.charAt(0);
        int offset = lineStart - 2; // 0: first char on this line, -1: \n on previous line, -2 last
        boolean seenNonWhitespace = false;
        for (; offset >= 0; offset--) {
            char c = contents.charAt(offset);
            if (seenNonWhitespace && c == '\n') {
                return -1;
            }

            if (!seenNonWhitespace && !Character.isWhitespace(c)) {
                seenNonWhitespace = true;
            }

            if (c == first && CharSequences.regionMatches(contents, offset, prefix, 0,
                    prefix.length())) {
                return offset;
            }
        }

        return -1;
    }
}
