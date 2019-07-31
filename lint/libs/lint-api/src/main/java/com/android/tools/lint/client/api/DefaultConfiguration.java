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

package com.android.tools.lint.client.api;

import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.SdkConstants.SUPPRESS_ALL;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.utils.SdkUtils.globToRegexp;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * Default implementation of a {@link Configuration} which reads and writes configuration data into
 * {@code lint.xml} in the project directory.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
public class DefaultConfiguration extends Configuration {

    private final LintClient client;
    /** Default name of the configuration file */
    public static final String CONFIG_FILE_NAME = "lint.xml";

    // Lint XML File

    /** The root tag in a configuration file */
    public static final String TAG_LINT = "lint";

    private static final String TAG_ISSUE = "issue";
    private static final String ATTR_ID = "id";
    private static final String ATTR_SEVERITY = "severity";
    private static final String ATTR_PATH = "path";
    private static final String ATTR_REGEXP = "regexp";
    private static final String TAG_IGNORE = "ignore";
    public static final String VALUE_ALL = "all";
    private static final String ATTR_BASELINE = "baseline";

    private static final String RES_PATH_START = "res" + File.separatorChar;
    private static final int RES_PATH_START_LEN = RES_PATH_START.length();

    private final Configuration parent;
    private final Project project;
    private final File configFile;
    private boolean bulkEditing;
    private File baselineFile;

    /**
     * Returns whether lint should check all warnings, including those off by default, or null if
     * not configured in this configuration
     */
    private Boolean checkAllWarnings;

    /**
     * Returns whether lint will only check for errors (ignoring warnings), or null if not
     * configured in this configuration
     */
    private Boolean ignoreWarnings;

    /**
     * Returns whether lint should treat all warnings as errors, or null if not configured in this
     * configuration
     */
    private Boolean warningsAsErrors;

    private Boolean fatalOnly;
    private Boolean checkTestSources;
    private Boolean ignoreTestSources;
    private Boolean checkGeneratedSources;
    private Boolean checkDependencies;
    private Boolean explainIssues;
    private Boolean applySuggestions;
    private Boolean removeFixedBaselineIssues;
    private Boolean abortOnError;

    /** Map from id to list of project-relative paths for suppressed warnings */
    private Map<String, List<String>> suppressed;

    /** Map from id to regular expressions. */
    @Nullable private Map<String, List<Pattern>> regexps;

    /** Map from id to custom {@link Severity} override */
    protected Map<String, Severity> severity;

    protected DefaultConfiguration(
            @NonNull LintClient client,
            @Nullable Project project,
            @Nullable Configuration parent,
            @NonNull File configFile) {
        this.client = client;
        this.project = project;
        this.parent = parent;
        this.configFile = configFile;
    }

    protected DefaultConfiguration(
            @NonNull LintClient client, @NonNull Project project, @Nullable Configuration parent) {
        this(client, project, parent, new File(project.getDir(), CONFIG_FILE_NAME));
    }

    /**
     * Creates a new {@link DefaultConfiguration}
     *
     * @param client the client to report errors to etc
     * @param project the associated project
     * @param parent the parent/fallback configuration or null
     * @return a new configuration
     */
    @NonNull
    public static DefaultConfiguration create(
            @NonNull LintClient client, @NonNull Project project, @Nullable Configuration parent) {
        return new DefaultConfiguration(client, project, parent);
    }

    /**
     * Creates a new {@link DefaultConfiguration} for the given lint config file, not affiliated
     * with a project. This is used for global configurations.
     *
     * @param client the client to report errors to etc
     * @param lintFile the lint file containing the configuration
     * @return a new configuration
     */
    @NonNull
    public static DefaultConfiguration create(@NonNull LintClient client, @NonNull File lintFile) {
        return new DefaultConfiguration(client, null, null, lintFile);
    }

    @Override
    public boolean isIgnored(
            @NonNull Context context,
            @NonNull Issue issue,
            @Nullable Location location,
            @NonNull String message) {
        ensureInitialized();

        String id = issue.getId();
        List<String> paths = suppressed.get(id);
        List<String> all = suppressed.get(VALUE_ALL);
        if (paths == null) {
            paths = all;
        } else if (all != null) {
            paths.addAll(all);
        }
        if (paths != null && location != null) {
            File file = location.getFile();
            String relativePath = context.getProject().getRelativePath(file);
            for (String suppressedPath : paths) {
                if (suppressedPath.equals(relativePath)) {
                    return true;
                }
                // Also allow a prefix
                if (relativePath.startsWith(suppressedPath)) {
                    return true;
                }
            }
            // A project can have multiple resources folders. The code before this
            // only checks for paths relative to project root (which doesn't work for paths such as
            // res/layout/foo.xml defined in lint.xml - when using gradle where the
            // resource directory points to src/main/res)
            // Here we check if any of the suppressed paths are relative to the resource folders
            // of a project.
            Set<Path> suppressedPathSet = new HashSet<>();
            for (String p : paths) {
                if (p.startsWith(RES_PATH_START)) {
                    Path path = Paths.get(p.substring(RES_PATH_START_LEN));
                    suppressedPathSet.add(path);
                }
            }

            if (!suppressedPathSet.isEmpty()) {
                Path toCheck = file.toPath();
                // Is it relative to any of the resource folders?
                for (File resDir : context.getProject().getResourceFolders()) {
                    Path path = resDir.toPath();
                    Path relative = path.relativize(toCheck);
                    if (suppressedPathSet.contains(relative)) {
                        return true;
                    }
                    // Allow suppress the relativePath if it is a prefix
                    if (suppressedPathSet.stream().anyMatch(relative::startsWith)) {
                        return true;
                    }
                }
            }
        }

        if (regexps != null) {
            List<Pattern> regexps = this.regexps.get(id);
            List<Pattern> allRegexps = this.regexps.get(VALUE_ALL);
            if (regexps == null) {
                regexps = allRegexps;
            } else if (allRegexps != null) {
                regexps.addAll(allRegexps);
            }
            if (regexps != null && location != null) {
                // Check message
                for (Pattern regexp : regexps) {
                    Matcher matcher = regexp.matcher(message);
                    if (matcher.find()) {
                        return true;
                    }
                }

                // Check location
                File file = location.getFile();
                String relativePath = context.getProject().getRelativePath(file);
                boolean checkUnixPath = false;
                for (Pattern regexp : regexps) {
                    Matcher matcher = regexp.matcher(relativePath);
                    if (matcher.find()) {
                        return true;
                    } else if (regexp.pattern().indexOf('/') != -1) {
                        checkUnixPath = true;
                    }
                }

                if (checkUnixPath && CURRENT_PLATFORM == PLATFORM_WINDOWS) {
                    relativePath = relativePath.replace('\\', '/');
                    for (Pattern regexp : regexps) {
                        Matcher matcher = regexp.matcher(relativePath);
                        if (matcher.find()) {
                            return true;
                        }
                    }
                }
            }
        }

        return parent != null && parent.isIgnored(context, issue, location, message);
    }

    @NonNull
    protected Severity getDefaultSeverity(@NonNull Issue issue) {
        if (!issue.isEnabledByDefault()) {
            return Severity.IGNORE;
        }

        return issue.getDefaultSeverity();
    }

    @Override
    @NonNull
    public Severity getSeverity(@NonNull Issue issue) {
        ensureInitialized();

        if (issue.getSuppressNames() != null) {
            // Not allowed to suppress this issue via lint.xml.
            // Consider reporting this as well (not easy here since we don't have
            // a context.)
            // Ideally we'd report this to the user too, but we can't really do
            // that here because we can't access the flag which lets you opt out
            // of the restrictions, where we'd unconditionally continue to
            // report this warning:
            //    if (this.severity.get(issue.getId()) != null) {
            //        LintClient.Companion.report(client, IssueRegistry.LINT_ERROR,
            //                "Issue `" + issue.getId() + "` is not allowed to be suppressed",
            //                configFile, project);
            //    }
            return getDefaultSeverity(issue);
        }

        Severity severity = this.severity.get(issue.getId());
        if (severity == null) {
            // id's can also refer to categories
            Category category = issue.getCategory();
            severity = this.severity.get(category.getName());
            if (severity != null) {
                return severity;
            }

            category = category.getParent();
            if (category != null) {
                severity = this.severity.get(category.getName());
                if (severity != null) {
                    return severity;
                }
            }

            severity = this.severity.get(VALUE_ALL);
        }

        if (severity != null) {
            return severity;
        }

        if (parent != null) {
            return parent.getSeverity(issue);
        }

        return getDefaultSeverity(issue);
    }

    private void ensureInitialized() {
        if (suppressed == null) {
            readConfig();
        }
    }

    private void formatError(String message, Object... args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        message = "Failed to parse `lint.xml` configuration file: " + message;
        LintClient.Companion.report(client, IssueRegistry.LINT_ERROR, message, configFile, project);
    }

    private void readConfig() {
        suppressed = new HashMap<>();
        severity = new HashMap<>();

        if (!configFile.exists()) {
            return;
        }

        try {
            // TODO: Switch to a pull parser!
            Document document = XmlUtils.parseUtfXmlFile(configFile, false);

            Element root = document.getDocumentElement();
            readFlags(root);

            String baseline = root.getAttribute(ATTR_BASELINE);
            if (!baseline.isEmpty()) {
                baselineFile = new File(baseline.replace('/', File.separatorChar));
                if (project != null && !baselineFile.isAbsolute()) {
                    baselineFile = new File(project.getDir(), baselineFile.getPath());
                }
            }
            NodeList issues = document.getElementsByTagName(TAG_ISSUE);
            Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
            for (int i = 0, count = issues.getLength(); i < count; i++) {
                Node node = issues.item(i);
                Element element = (Element) node;
                String idList = element.getAttribute(ATTR_ID);
                if (idList.isEmpty()) {
                    formatError("Invalid lint config file: Missing required issue id attribute");
                    continue;
                }
                Iterable<String> ids = splitter.split(idList);

                NamedNodeMap attributes = node.getAttributes();
                for (int j = 0, n = attributes.getLength(); j < n; j++) {
                    Node attribute = attributes.item(j);
                    String name = attribute.getNodeName();
                    String value = attribute.getNodeValue();
                    if (ATTR_ID.equals(name)) {
                        // already handled
                    } else if (ATTR_SEVERITY.equals(name)) {
                        for (Severity severity : Severity.values()) {
                            if (value.equalsIgnoreCase(severity.name())) {
                                for (String id : ids) {
                                    this.severity.put(id, severity);
                                }
                                break;
                            }
                        }
                    } else {
                        formatError("Unexpected attribute \"%1$s\"", name);
                    }
                }

                // Look up ignored errors
                NodeList childNodes = element.getChildNodes();
                if (childNodes.getLength() > 0) {
                    for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                        Node child = childNodes.item(j);
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            Element ignore = (Element) child;
                            String path = ignore.getAttribute(ATTR_PATH);
                            if (path.isEmpty()) {
                                String regexp = ignore.getAttribute(ATTR_REGEXP);
                                if (regexp.isEmpty()) {
                                    formatError(
                                            "Missing required attribute %1$s or %2$s under %3$s",
                                            ATTR_PATH, ATTR_REGEXP, idList);
                                } else {
                                    addRegexp(idList, ids, n, regexp, false);
                                }
                            } else {
                                // Normalize path format to File.separator. Also
                                // handle the file format containing / or \.
                                if (File.separatorChar == '/') {
                                    path = path.replace('\\', '/');
                                } else {
                                    path = path.replace('/', File.separatorChar);
                                }

                                if (path.indexOf('*') != -1) {
                                    String regexp = globToRegexp(path);
                                    addRegexp(idList, ids, n, regexp, false);
                                } else {
                                    for (String id : ids) {
                                        List<String> paths = suppressed.get(id);
                                        if (paths == null) {
                                            paths = new ArrayList<>(n / 2 + 1);
                                            suppressed.put(id, paths);
                                        }
                                        paths.add(path);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SAXParseException e) {
            formatError(e.getMessage());
        } catch (Exception e) {
            client.log(e, null);
        }
    }

    private void readFlags(@NonNull Element root) {
        if (root.getAttributes().getLength() > 0) {
            checkAllWarnings = readBooleanFlag(root, "checkAllWarnings");
            ignoreWarnings = readBooleanFlag(root, "ignoreWarnings");
            warningsAsErrors = readBooleanFlag(root, "warningsAsErrors");
            fatalOnly = readBooleanFlag(root, "fatalOnly");
            checkTestSources = readBooleanFlag(root, "checkTestSources");
            ignoreTestSources = readBooleanFlag(root, "ignoreTestSources");
            checkGeneratedSources = readBooleanFlag(root, "checkGeneratedSources");
            checkDependencies = readBooleanFlag(root, "checkDependencies");
            explainIssues = readBooleanFlag(root, "explainIssues");
            applySuggestions = readBooleanFlag(root, "applySuggestions");
            removeFixedBaselineIssues = readBooleanFlag(root, "removeFixedBaselineIssues");
            abortOnError = readBooleanFlag(root, "abortOnError");
            // Note that we don't let you configure the allowSuppress flag by lint.xml
        }
    }

    @Nullable
    private static Boolean readBooleanFlag(@NonNull Element root, @NonNull String attribute) {
        if (root.hasAttribute(attribute)) {
            String value = root.getAttribute(attribute);
            if (value.equals(VALUE_TRUE)) {
                return true;
            } else if (value.equals(VALUE_FALSE)) {
                return false;
            }
        }

        return null;
    }

    @Nullable
    private static File readFileFlag(@NonNull Element root, @NonNull String attribute) {
        if (root.hasAttribute(attribute)) {
            return new File(root.getAttribute(attribute));
        }

        return null;
    }

    @Nullable
    public Boolean getCheckAllWarnings() {
        ensureInitialized();
        return checkAllWarnings;
    }

    @Nullable
    public Boolean getIgnoreWarnings() {
        ensureInitialized();
        return ignoreWarnings;
    }

    @Nullable
    public Boolean getWarningsAsErrors() {
        ensureInitialized();
        return warningsAsErrors;
    }

    @Nullable
    public Boolean getFatalOnly() {
        ensureInitialized();
        return fatalOnly;
    }

    @Nullable
    public Boolean getCheckTestSources() {
        ensureInitialized();
        return checkTestSources;
    }

    @Nullable
    public Boolean getIgnoreTestSources() {
        ensureInitialized();
        return ignoreTestSources;
    }

    @Nullable
    public Boolean getCheckGeneratedSources() {
        ensureInitialized();
        return checkGeneratedSources;
    }

    @Nullable
    public Boolean getCheckDependencies() {
        ensureInitialized();
        return checkDependencies;
    }

    @Nullable
    public Boolean getExplainIssues() {
        ensureInitialized();
        return explainIssues;
    }

    public Boolean getApplySuggestions() {
        ensureInitialized();
        return applySuggestions;
    }

    @Nullable
    public Boolean getRemoveFixedBaselineIssues() {
        ensureInitialized();
        return removeFixedBaselineIssues;
    }

    @Nullable
    public Boolean getAbortOnError() {
        ensureInitialized();
        return abortOnError;
    }

    private void addRegexp(
            @NonNull String idList,
            @NonNull Iterable<String> ids,
            int n,
            @NonNull String regexp,
            boolean silent) {
        try {
            if (regexps == null) {
                regexps = new HashMap<>();
            }
            Pattern pattern = Pattern.compile(regexp);
            for (String id : ids) {
                List<Pattern> paths = regexps.get(id);
                if (paths == null) {
                    paths = new ArrayList<>(n / 2 + 1);
                    regexps.put(id, paths);
                }
                paths.add(pattern);
            }
        } catch (PatternSyntaxException e) {
            if (!silent) {
                formatError(
                        "Invalid pattern %1$s under %2$s: %3$s",
                        regexp, idList, e.getDescription());
            }
        }
    }

    private void writeConfig() {
        try {
            // Write the contents to a new file first such that we don't clobber the
            // existing file if some I/O error occurs.
            File file = new File(configFile.getParentFile(), configFile.getName() + ".new");

            Writer writer = new BufferedWriter(new FileWriter(file));
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<");
            writer.write(TAG_LINT);

            if (baselineFile != null) {
                writer.write(" baseline=\"");
                String path =
                        project != null
                                ? project.getRelativePath(baselineFile)
                                : baselineFile.getPath();
                writeAttribute(writer, ATTR_BASELINE, path.replace('\\', '/'));
            }
            writer.write(">\n");

            if (!suppressed.isEmpty() || !severity.isEmpty()) {
                // Process the maps in a stable sorted order such that if the
                // files are checked into version control with the project,
                // there are no random diffs just because hashing algorithms
                // differ:
                Set<String> idSet = new HashSet<>();
                for (String id : suppressed.keySet()) {
                    idSet.add(id);
                }
                for (String id : severity.keySet()) {
                    idSet.add(id);
                }
                List<String> ids = new ArrayList<>(idSet);
                Collections.sort(ids);

                for (String id : ids) {
                    writer.write("    <");
                    writer.write(TAG_ISSUE);
                    writeAttribute(writer, ATTR_ID, id);
                    Severity severity = this.severity.get(id);
                    if (severity != null) {
                        writeAttribute(
                                writer, ATTR_SEVERITY, severity.name().toLowerCase(Locale.US));
                    }

                    List<Pattern> regexps = this.regexps != null ? this.regexps.get(id) : null;
                    List<String> paths = suppressed.get(id);
                    if (paths != null && !paths.isEmpty()
                            || regexps != null && !regexps.isEmpty()) {
                        writer.write('>');
                        writer.write('\n');
                        // The paths are already kept in sorted order when they are modified
                        // by ignore(...)
                        if (paths != null) {
                            for (String path : paths) {
                                writer.write("        <");
                                writer.write(TAG_IGNORE);
                                writeAttribute(writer, ATTR_PATH, path.replace('\\', '/'));
                                writer.write(" />\n");
                            }
                        }
                        if (regexps != null) {
                            for (Pattern regexp : regexps) {
                                writer.write("        <");
                                writer.write(TAG_IGNORE);
                                writeAttribute(writer, ATTR_REGEXP, regexp.pattern());
                                writer.write(" />\n");
                            }
                        }
                        writer.write("    </");
                        writer.write(TAG_ISSUE);
                        writer.write('>');
                        writer.write('\n');
                    } else {
                        writer.write(" />\n");
                    }
                }
            }

            writer.write("</lint>\n");
            writer.close();

            // Move file into place: move current version to lint.xml~ (removing the old ~ file
            // if it exists), then move the new version to lint.xml.
            File oldFile = new File(configFile.getParentFile(), configFile.getName() + '~');
            if (oldFile.exists()) {
                oldFile.delete();
            }
            if (configFile.exists()) {
                configFile.renameTo(oldFile);
            }
            boolean ok = file.renameTo(configFile);
            if (ok && oldFile.exists()) {
                oldFile.delete();
            }
        } catch (Exception e) {
            client.log(e, null);
        }
    }

    private static void writeAttribute(
            @NonNull Writer writer, @NonNull String name, @NonNull String value)
            throws IOException {
        writer.write(' ');
        writer.write(name);
        writer.write('=');
        writer.write('"');
        writer.write(value);
        writer.write('"');
    }

    @Override
    public void ignore(
            @NonNull Context context,
            @NonNull Issue issue,
            @Nullable Location location,
            @NonNull String message) {
        // This configuration only supports suppressing warnings on a per-file basis
        if (location != null) {
            ignore(issue, location.getFile());
        }
    }

    @Override
    public void ignore(@NonNull Issue issue, @NonNull File file) {
        ignore(issue.getId(), file);
    }

    @Override
    public void ignore(@NonNull String id, @NonNull File file) {
        ensureInitialized();

        String path = project != null ? project.getRelativePath(file) : file.getPath();

        List<String> paths = suppressed.get(id);
        if (paths == null) {
            paths = new ArrayList<>();
            suppressed.put(id, paths);
        }
        paths.add(path);

        // Keep paths sorted alphabetically; makes XML output stable
        Collections.sort(paths);

        if (!bulkEditing) {
            writeConfig();
        }
    }

    @Override
    public void setSeverity(@NonNull Issue issue, @Nullable Severity severity) {
        ensureInitialized();

        String id = issue.getId();
        if (severity == null) {
            this.severity.remove(id);
        } else {
            this.severity.put(id, severity);
        }

        if (!bulkEditing) {
            writeConfig();
        }
    }

    @Override
    public void startBulkEditing() {
        bulkEditing = true;
    }

    @Override
    public void finishBulkEditing() {
        bulkEditing = false;
        writeConfig();
    }

    @VisibleForTesting
    File getConfigFile() {
        return configFile;
    }

    @Override
    @Nullable
    public File getBaselineFile() {
        if (baselineFile != null) {
            if (project != null && !baselineFile.isAbsolute()) {
                return new File(project.getDir(), baselineFile.getPath());
            }
        }
        return baselineFile;
    }

    @Override
    public void setBaselineFile(@Nullable File baselineFile) {
        this.baselineFile = baselineFile;
    }

    @Override
    public void validateIssueIds(
            @NonNull LintClient client,
            @Nullable LintDriver driver,
            @NonNull Project project,
            @NonNull IssueRegistry registry) {
        super.validateIssueIds(client, driver, project, registry);

        ensureInitialized();

        validateIssueIds(client, driver, project, registry, severity.keySet());
        validateIssueIds(client, driver, project, registry, suppressed.keySet());
        if (regexps != null) {
            validateIssueIds(client, driver, project, registry, regexps.keySet());
        }
    }

    public void validateIssueIds(
            @NonNull LintClient client,
            @Nullable LintDriver driver,
            @NonNull Project project,
            @NonNull IssueRegistry registry,
            Collection<String> ids) {
        for (String id : ids) {
            if (id.equals(SUPPRESS_ALL)) {
                // builtin special "id" which means all id's
                continue;
            }
            if (id.equals("IconLauncherFormat")) {
                // Deleted issue, no longer flagged
                continue;
            }
            if (registry.getIssue(id) == null) {
                reportNonExistingIssueId(client, driver, project, id);
            }
        }
    }

    private void reportNonExistingIssueId(
            @NonNull LintClient client,
            @Nullable LintDriver driver,
            @NonNull Project project,
            @NonNull String id) {
        String message = String.format("Unknown issue id \"%1$s\"", id);
        if (configFile != null) {
            message += String.format(", found in %1$s", configFile.getPath().replace("\\", "\\\\"));
        }

        if (driver != null) {
            Location location = Location.create(project.getDir());
            if (getSeverity(IssueRegistry.LINT_ERROR) != Severity.IGNORE) {
                client.report(
                        new Context(driver, project, project, project.getDir(), null),
                        IssueRegistry.LINT_ERROR,
                        project.getConfiguration(driver).getSeverity(IssueRegistry.LINT_ERROR),
                        location,
                        message,
                        TextFormat.RAW,
                        null);
            } else {
                client.log(Severity.ERROR, null, "Lint: %1$s", message);
            }
        }
    }
}
