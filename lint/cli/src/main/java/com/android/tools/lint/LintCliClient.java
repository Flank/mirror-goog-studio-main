/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint;

import static com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE;
import static com.android.tools.lint.LintCliFlags.ERRNO_ERRORS;
import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;
import static com.android.tools.lint.client.api.IssueRegistry.BASELINE;
import static com.android.tools.lint.client.api.IssueRegistry.LINT_ERROR;
import static com.android.tools.lint.client.api.IssueRegistry.PARSER_ERROR;
import static com.android.tools.lint.detector.api.CharSequences.indexOf;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.Reporter.Stats;
import com.android.tools.lint.checks.HardcodedValuesDetector;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Lint client for command line usage. Supports the flags in {@link LintCliFlags},
 * and offers text, HTML and XML reporting, etc.
 * <p>
 * Minimal example:
 * <pre>
 * // files is a list of java.io.Files, typically a directory containing
 * // lint projects or direct references to project root directories
 * IssueRegistry registry = new BuiltinIssueRegistry();
 * LintCliFlags flags = new LintCliFlags();
 * LintCliClient client = new LintCliClient(flags);
 * int exitCode = client.run(registry, files);
 * </pre>
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
public class LintCliClient extends LintClient {
    protected final List<Warning> warnings = new ArrayList<>();
    protected boolean hasErrors;
    protected int errorCount;
    protected int warningCount;
    protected IssueRegistry registry;
    protected LintDriver driver;
    protected final LintCliFlags flags;
    private Configuration configuration;
    private boolean validatedIds;

    /** Creates a CLI driver */
    public LintCliClient() {
        super(CLIENT_CLI);
        flags = new LintCliFlags();
        TextReporter reporter = new TextReporter(this, flags, new PrintWriter(System.out, true),
                false);
        flags.getReporters().add(reporter);
    }

    /** Creates a CLI driver */
    public LintCliClient(@NonNull LintCliFlags flags, @NonNull String clientName) {
        super(clientName);
        this.flags = flags;
    }

    /**
     * Runs the static analysis command line driver. You need to add at least one error reporter
     * to the command line flags.
     */
    public int run(@NonNull IssueRegistry registry, @NonNull List<File> files) throws IOException {
        assert !flags.getReporters().isEmpty();
        this.registry = registry;
        driver = new LintDriver(registry, this);

        driver.setAbbreviating(!flags.isShowEverything());

        File baselineFile = flags.getBaselineFile();
        LintBaseline baseline = null;
        if (baselineFile != null) {
            baseline = new LintBaseline(this, baselineFile);
            driver.setBaseline(baseline);
            if (flags.isRemoveFixedBaselineIssues()) {
                baseline.setWriteOnClose(true);
                baseline.setRemoveFixed(true);
            }
        }

        addProgressPrinter();
        driver.addLintListener((driver, type, context) -> {
            if (type == LintListener.EventType.SCANNING_PROJECT && !validatedIds) {
                // Make sure all the id's are valid once the driver is all set up and
                // ready to run (such that custom rules are available in the registry etc)
                validateIssueIds(context != null ? context.getProject() : null);
            }
        });

        driver.analyze(createLintRequest(files));

        Collections.sort(warnings);

        int baselineErrorCount = 0;
        int baselineWarningCount = 0;
        int fixedCount = 0;
        if (baseline != null) {
            baselineErrorCount = baseline.getFoundErrorCount();
            baselineWarningCount = baseline.getFoundWarningCount();
            fixedCount = baseline.getFixedCount();
        }

        Stats stats = new Stats(errorCount, warningCount,
                baselineErrorCount, baselineWarningCount, fixedCount);

        boolean hasConsoleOutput = false;
        for (Reporter reporter : flags.getReporters()) {
            reporter.write(stats, warnings);
            if (reporter instanceof TextReporter && ((TextReporter)reporter).isWriteToConsole()) {
                hasConsoleOutput = true;
            }
        }

        if (!flags.isQuiet() && !hasConsoleOutput) {
            System.out.print(String.format("Lint found %1$s",
                    LintUtils.describeCounts(errorCount, warningCount, true)));
            if (baselineErrorCount > 0 || baselineWarningCount > 0) {
                System.out.print(String.format(" (%1$s filtered by baseline %2$s)",
                        LintUtils.describeCounts(stats.baselineErrorCount,
                                stats.baselineWarningCount, true),
                        flags.getBaselineFile().getName()));
            }
            System.out.println();
        }

        if (baselineFile != null && !baselineFile.exists() && flags.isWriteBaselineIfMissing()) {
            File dir = baselineFile.getParentFile();
            boolean ok = true;
            if (dir != null && !dir.isDirectory()) {
                ok = dir.mkdirs();
            }
            if (!ok) {
                System.err.println("Couldn't create baseline folder " + dir);
            } else {
                Reporter reporter = Reporter.createXmlReporter(this, baselineFile, true);
                reporter.write(stats, warnings);
                String message = ""
                        + "Created baseline file " + baselineFile + "\n"
                        + "\n"
                        + "Also breaking the build in case this was not intentional. If you\n"
                        + "deliberately created the baseline file, re-run the build and this\n"
                        + "time it should succeed without warnings.\n"
                        + "\n"
                        + "If not, investigate the baseline path in the lintOptions config\n"
                        + "or verify that the baseline file has been checked into version\n"
                        + "control.\n";
                System.err.println(message);
                return ERRNO_CREATED_BASELINE;
            }
        }

        return flags.isSetExitCode() ? (hasErrors ? ERRNO_ERRORS : ERRNO_SUCCESS) : ERRNO_SUCCESS;
    }

    protected void addProgressPrinter() {
        if (!flags.isQuiet()) {
            driver.addLintListener(new ProgressPrinter());
        }
    }

    /** Creates a lint request */
    @NonNull
    protected LintRequest createLintRequest(@NonNull List<File> files) {
        return new LintRequest(this, files);
    }

    @Override
    public void log(
            @NonNull Severity severity,
            @Nullable Throwable exception,
            @Nullable String format,
            @Nullable Object... args) {
        System.out.flush();
        if (!flags.isQuiet()) {
            // Place the error message on a line of its own since we're printing '.' etc
            // with newlines during analysis
            System.err.println();
        }
        if (format != null) {
            System.err.println(String.format(format, args));
        }
        if (exception != null) {
            exception.printStackTrace();
        }
    }

    @Override
    public XmlParser getXmlParser() {
        return new LintCliXmlParser();
    }

    @NonNull
    @Override
    public Configuration getConfiguration(@NonNull Project project, @Nullable LintDriver driver) {
        return new CliConfiguration(getConfiguration(), project, flags.isFatalOnly());
    }

    /** File content cache */
    private final Map<File, CharSequence> mFileContents = new HashMap<>(100);

    /** Read the contents of the given file, possibly cached */
    private CharSequence getContents(File file) {
        return mFileContents.computeIfAbsent(file, k -> readFile(file));
    }

    @Override
    public JavaParser getJavaParser(@Nullable Project project) {
        return new EcjParser(this, project);
    }

    @Override
    public void report(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message,
            @NonNull TextFormat format) {
        assert context.isEnabled(issue) || issue == LINT_ERROR;

        if (severity.isError()) {
            hasErrors = true;
            errorCount++;
        } else {
            warningCount++;
        }

        // Store the message in the raw format internally such that we can
        // convert it to text for the text reporter, HTML for the HTML reporter
        // and so on.
        message = format.convertTo(message, TextFormat.RAW);
        Warning warning = new Warning(issue, message, severity, context.getProject());
        warnings.add(warning);

        //noinspection ConstantConditions
        if (location == null) {
            // Misbehaving third party lint rules
            log(Severity.ERROR, null, "No location provided for issue " + issue);
            return;
        }

        warning.location = location;
        File file = location.getFile();
        warning.file = file;
        warning.path = getDisplayPath(context.getProject(), file);

        Position startPosition = location.getStart();
        if (startPosition != null) {
            int line = startPosition.getLine();
            warning.line = line;
            warning.offset = startPosition.getOffset();
            Position endPosition = location.getEnd();
            if (endPosition != null) {
                warning.endOffset = endPosition.getOffset();
            }
            if (line >= 0) {
                if (context.file == location.getFile()) {
                    warning.fileContents = context.getContents();
                }
                if (warning.fileContents == null) {
                    warning.fileContents = getContents(location.getFile());
                }

                if (flags.isShowSourceLines()) {
                    // Compute error line contents
                    warning.errorLine = getLine(warning.fileContents, line);
                    if (warning.errorLine != null) {
                        // Replace tabs with spaces such that the column
                        // marker (^) lines up properly:
                        warning.errorLine = warning.errorLine.replace('\t', ' ');
                        int column = startPosition.getColumn();
                        if (column < 0) {
                            column = 0;
                            for (int i = 0; i < warning.errorLine.length(); i++, column++) {
                                if (!Character.isWhitespace(warning.errorLine.charAt(i))) {
                                    break;
                                }
                            }
                        }
                        StringBuilder sb = new StringBuilder(100);
                        sb.append(warning.errorLine);
                        sb.append('\n');
                        for (int i = 0; i < column; i++) {
                            sb.append(' ');
                        }

                        boolean displayCaret = true;
                        if (endPosition != null) {
                            int endLine = endPosition.getLine();
                            int endColumn = endPosition.getColumn();
                            if (endLine == line && endColumn > column) {
                                for (int i = column; i < endColumn; i++) {
                                    sb.append('~');
                                }
                                displayCaret = false;
                            }
                        }

                        if (displayCaret) {
                            sb.append('^');
                        }
                        sb.append('\n');
                        warning.errorLine = sb.toString();
                    }
                }
            }
        }
    }

    /** Look up the contents of the given line */
    static String getLine(CharSequence contents, int line) {
        int index = getLineOffset(contents, line);
        if (index != -1) {
            return getLineOfOffset(contents, index);
        } else {
            return null;
        }
    }

    static String getLineOfOffset(CharSequence contents, int offset) {
        int end = indexOf(contents, '\n', offset);
        if (end == -1) {
            end = indexOf(contents, '\r', offset);
        }
        return contents.subSequence(offset, end != -1 ? end : contents.length()).toString();
    }


    /** Look up the contents of the given line */
    static int getLineOffset(CharSequence contents, int line) {
        int index = 0;
        for (int i = 0; i < line; i++) {
            index = indexOf(contents, '\n', index);
            if (index == -1) {
                return -1;
            }
            index++;
        }

        return index;
    }

    @NonNull
    @Override
    public CharSequence readFile(@NonNull File file) {
        try {
            return LintUtils.getEncodedString(this, file, false);
        } catch (IOException e) {
            return "";
        }
    }

    boolean isCheckingSpecificIssues() {
        return flags.getExactCheckedIds() != null;
    }

    private Map<Project, ClassPathInfo> mProjectInfo;

    @Override
    @NonNull
    protected ClassPathInfo getClassPath(@NonNull Project project) {
        ClassPathInfo classPath = super.getClassPath(project);

        List<File> sources = flags.getSourcesOverride();
        List<File> classes = flags.getClassesOverride();
        List<File> libraries = flags.getLibrariesOverride();
        if (classes == null && sources == null && libraries == null) {
            return classPath;
        }

        ClassPathInfo info;
        if (mProjectInfo == null) {
            mProjectInfo = Maps.newHashMap();
            info = null;
        } else {
            info = mProjectInfo.get(project);
        }

        if (info == null) {
            if (sources == null) {
                sources = classPath.getSourceFolders();
            }
            if (classes == null) {
                classes = classPath.getClassFolders();
            }
            if (libraries == null) {
                libraries = classPath.getLibraries(true);
            }

            info = new ClassPathInfo(sources, classes, libraries, classPath.getLibraries(false),
                    classPath.getTestSourceFolders(), classPath.getTestLibraries());
            mProjectInfo.put(project, info);
        }

        return info;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders(@NonNull Project project) {
        List<File> resources = flags.getResourcesOverride();
        if (resources == null) {
            return super.getResourceFolders(project);
        }

        return resources;
    }

    /**
     * Consult the lint.xml file, but override with the --enable and --disable
     * flags supplied on the command line
     */
    protected class CliConfiguration extends DefaultConfiguration {
        private final boolean mFatalOnly;

        protected CliConfiguration(@NonNull Configuration parent, @NonNull Project project,
                boolean fatalOnly) {
            super(LintCliClient.this, project, parent);
            mFatalOnly = fatalOnly;
        }

        protected CliConfiguration(File lintFile, boolean fatalOnly) {
            super(LintCliClient.this, null, null, lintFile);
            mFatalOnly = fatalOnly;
        }

        protected CliConfiguration(
                @NonNull File lintFile,
                @Nullable Configuration parent,
                @Nullable Project project,
                boolean fatalOnly) {
            super(LintCliClient.this, project, parent, lintFile);
            mFatalOnly = fatalOnly;
        }

        @NonNull
        @Override
        public Severity getSeverity(@NonNull Issue issue) {
            Severity severity = computeSeverity(issue);

            if (mFatalOnly && severity != Severity.FATAL) {
                return Severity.IGNORE;
            }

            if (flags.isWarningsAsErrors() && severity == Severity.WARNING) {
                if (issue == IssueRegistry.BASELINE) {
                    // Don't promote the baseline informational issue
                    // (number of issues promoted) to error
                    return severity;
                }
                severity = Severity.ERROR;
            }

            if (flags.isIgnoreWarnings() && severity == Severity.WARNING) {
                severity = Severity.IGNORE;
            }

            return severity;
        }

        @NonNull
        @Override
        protected Severity getDefaultSeverity(@NonNull Issue issue) {
            if (flags.isCheckAllWarnings()) {
                return issue.getDefaultSeverity();
            }

            return super.getDefaultSeverity(issue);
        }

        private Severity computeSeverity(@NonNull Issue issue) {
            Severity severity = super.getSeverity(issue);

            String id = issue.getId();
            Set<String> suppress = flags.getSuppressedIds();
            if (suppress.contains(id)) {
                return Severity.IGNORE;
            }

            Severity manual = flags.getSeverityOverrides().get(id);
            if (manual != null) {
                if (this.severity != null && (this.severity.containsKey(id)
                        || this.severity.containsKey(VALUE_ALL))) {
                    // Ambiguity! We have a specific severity override provided
                    // via lint options for the main app module, but a local lint.xml
                    // file in the library (not a lintOptions definition) which also
                    // specifies severity for the same issue.
                    //
                    // Who should win? Should the intent from the main app module
                    // win, such that you have a global way to say "this is the severity
                    // I want during this lint run?". Or should the library-local definition
                    // win, to say "there's a local problem in this library; I need to
                    // change things here?".
                    //
                    // Both are plausible, so for now I'm going with a middle ground: local
                    // definitions should be used to turn of issues that don't work right.
                    // Therefore, we'll take the minimum of the two severities!
                    return Severity.min(severity, manual);
                }
                return manual;
            }

            Set<String> enabled = flags.getEnabledIds();
            Set<String> check = flags.getExactCheckedIds();
            if (enabled.contains(id) || (check != null && check.contains(id))) {
                // Overriding default
                // Detectors shouldn't be returning ignore as a default severity,
                // but in case they do, force it up to warning here to ensure that
                // it's run
                if (severity == Severity.IGNORE) {
                    severity = issue.getDefaultSeverity();
                    if (severity == Severity.IGNORE) {
                        severity = Severity.WARNING;
                    }
                }

                return severity;
            }

            if (check != null && issue != LINT_ERROR && issue != PARSER_ERROR &&
                    issue != BASELINE) {
                return Severity.IGNORE;
            }

            return severity;
        }
    }

    /**
     * Checks that any id's specified by id refer to valid, known, issues. This
     * typically can't be done right away (in for example the Gradle code which
     * handles DSL references to strings, or in the command line parser for the
     * lint command) because the full set of valid id's is not known until lint
     * actually starts running and for example gathers custom rules from all
     * AAR dependencies reachable from libraries, etc.
     */
    private void validateIssueIds(@Nullable Project project) {
        if (driver != null) {
            IssueRegistry registry = driver.getRegistry();
            if (!registry.isIssueId(HardcodedValuesDetector.ISSUE.getId())) {
                // This should not be necessary, but there have been some strange
                // reports where lint has reported some well known builtin issues
                // to not exist:
                //
                //   Error: Unknown issue id "DuplicateDefinition" [LintError]
                //   Error: Unknown issue id "GradleIdeError" [LintError]
                //   Error: Unknown issue id "InvalidPackage" [LintError]
                //   Error: Unknown issue id "JavascriptInterface" [LintError]
                //   ...
                //
                // It's not clear how this can happen, though it's probably related
                // to using 3rd party lint rules (where lint will create new composite
                // issue registries to wrap the various additional issues) - but
                // we definitely don't want to validate issue id's if we can't find
                // well known issues.
                return;
            }
            validatedIds = true;
            validateIssueIds(project, registry, flags.getExactCheckedIds());
            validateIssueIds(project, registry, flags.getEnabledIds());
            validateIssueIds(project, registry, flags.getSuppressedIds());
            validateIssueIds(project, registry, flags.getSeverityOverrides().keySet());
        }
    }

    private void validateIssueIds(@Nullable Project project, @NonNull IssueRegistry registry,
            @Nullable Collection<String> ids) {
        if (ids != null) {
            for (String id : ids) {
                if (registry.getIssue(id) == null) {
                    reportNonExistingIssueId(project, id);
                }
            }
        }
    }

    protected void reportNonExistingIssueId(@Nullable Project project, @NonNull String id) {
        String message = String.format("Unknown issue id \"%1$s\"", id);

        if (driver != null && project != null) {
            Location location = Location.create(project.getDir());
            if (!isSuppressed(IssueRegistry.LINT_ERROR)) {
                report(new Context(driver, project, project, project.getDir()),
                        IssueRegistry.LINT_ERROR,
                        project.getConfiguration(driver).getSeverity(IssueRegistry.LINT_ERROR),
                        location, message, TextFormat.RAW);
            }
        } else {
            log(Severity.ERROR, null, "Lint: %1$s", message);
        }
    }

    private static class ProgressPrinter implements LintListener {
        @Override
        public void update(
                @NonNull LintDriver lint,
                @NonNull EventType type,
                @Nullable Context context) {
            switch (type) {
                case SCANNING_PROJECT: {
                    String name = context != null ? context.getProject().getName() : "?";
                    if (lint.getPhase() > 1) {
                        System.out.print(String.format(
                                "\nScanning %1$s (Phase %2$d): ",
                                name,
                                lint.getPhase()));
                    } else {
                        System.out.print(String.format(
                                "\nScanning %1$s: ",
                                name));
                    }
                    break;
                }
                case SCANNING_LIBRARY_PROJECT: {
                    String name = context != null ? context.getProject().getName() : "?";
                    System.out.print(String.format(
                            "\n         - %1$s: ",
                            name));
                    break;
                }
                case SCANNING_FILE:
                    System.out.print('.');
                    break;
                case NEW_PHASE:
                    // Ignored for now: printing status as part of next project's status
                    break;
                case CANCELED:
                case COMPLETED:
                    System.out.println();
                    break;
                case STARTING:
                    // Ignored for now
                    break;
            }
        }
    }

    /**
     * Given a file, it produces a cleaned up path from the file.
     * This will clean up the path such that
     * <ul>
     *   <li>  {@code foo/./bar} becomes {@code foo/bar}
     *   <li>  {@code foo/bar/../baz} becomes {@code foo/baz}
     * </ul>
     *
     * Unlike {@link java.io.File#getCanonicalPath()} however, it will <b>not</b> attempt
     * to make the file canonical, such as expanding symlinks and network mounts.
     *
     * @param file the file to compute a clean path for
     * @return the cleaned up path
     */
    @VisibleForTesting
    @NonNull
    static String getCleanPath(@NonNull File file) {
        String path = file.getPath();
        StringBuilder sb = new StringBuilder(path.length());

        if (path.startsWith(File.separator)) {
            sb.append(File.separator);
        }
        elementLoop:
        for (String element : Splitter.on(File.separatorChar).omitEmptyStrings().split(path)) {
            if (element.equals(".")) {
                continue;
            } else if (element.equals("..")) {
                if (sb.length() > 0) {
                    for (int i = sb.length() - 1; i >= 0; i--) {
                        char c = sb.charAt(i);
                        if (c == File.separatorChar) {
                            sb.setLength(i == 0 ? 1 : i);
                            continue elementLoop;
                        }
                    }
                    sb.setLength(0);
                    continue;
                }
            }

            if (sb.length() > 1) {
                sb.append(File.separatorChar);
            } else if (sb.length() > 0 && sb.charAt(0) != File.separatorChar) {
                sb.append(File.separatorChar);
            }
            sb.append(element);
        }
        if (path.endsWith(File.separator) && sb.length() > 0
                && sb.charAt(sb.length() - 1) != File.separatorChar) {
            sb.append(File.separator);
        }

        return sb.toString();
    }

    @NonNull
    String getDisplayPath(@NonNull Project project, @NonNull File file) {
        return getDisplayPath(project, file, flags.isFullPath());
    }

    static String getDisplayPath(@NonNull Project project, @NonNull File file, boolean fullPath) {
        String path = file.getPath();
        if (!fullPath && path.startsWith(project.getReferenceDir().getPath())) {
            int chop = project.getReferenceDir().getPath().length();
            if (path.length() > chop && path.charAt(chop) == File.separatorChar) {
                chop++;
            }
            path = path.substring(chop);
            if (path.isEmpty()) {
                path = file.getName();
            }
        } else if (fullPath) {
            path = getCleanPath(file.getAbsoluteFile());
        }

        return path;
    }

    /** Returns whether all warnings are enabled, including those disabled by default */
    boolean isAllEnabled() {
        return flags.isCheckAllWarnings();
    }

    /** Returns the issue registry used by this client */
    IssueRegistry getRegistry() {
        return registry;
    }

    /** Returns the driver running the lint checks */
    LintDriver getDriver() {
        return driver;
    }

    private static Set<File> sAlreadyWarned;

    /** Returns the configuration used by this client */
    protected Configuration getConfiguration() {
        if (configuration == null) {
            File configFile = flags.getDefaultConfiguration();
            if (configFile != null) {
                if (!configFile.exists()) {
                    if (sAlreadyWarned == null || !sAlreadyWarned.contains(configFile)) {
                        log(Severity.ERROR, null,
                                "Warning: Configuration file %1$s does not exist", configFile);
                    }
                    if (sAlreadyWarned == null) {
                        sAlreadyWarned = Sets.newHashSet();
                    }
                    sAlreadyWarned.add(configFile);
                }
                configuration = createConfigurationFromFile(configFile);
            }
        }

        return configuration;
    }

    /** Returns true if the given issue has been explicitly disabled */
    boolean isSuppressed(Issue issue) {
        return flags.getSuppressedIds().contains(issue.getId());
    }

    public Configuration createConfigurationFromFile(File file) {
        return new CliConfiguration(file, flags.isFatalOnly());
    }

    @Override
    @Nullable
    public String getClientRevision() {
        File file = findResource("tools" + File.separator +
                "source.properties");
        if (file != null && file.exists()) {
            FileInputStream input = null;
            try {
                input = new FileInputStream(file);
                Properties properties = new Properties();
                properties.load(input);

                String revision = properties.getProperty("Pkg.Revision");
                if (revision != null && !revision.isEmpty()) {
                    return revision;
                }
            } catch (IOException e) {
                // Couldn't find or read the version info: just print out unknown below
            } finally {
                try {
                    Closeables.close(input, true /* swallowIOException */);
                } catch (IOException e) {
                    // cannot happen
                }
            }
        }

        return null;
    }

    @NonNull
    public LintCliFlags getFlags() {
        return flags;
    }

    public boolean haveErrors() {
        return errorCount > 0;
    }

    @VisibleForTesting
    public void reset() {
        warnings.clear();
        errorCount = 0;
        warningCount = 0;

        projectDirs = Sets.newHashSet();
        dirToProject = null;
    }
}
