/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.gradle;

import static com.android.SdkConstants.DOT_XML;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.tools.lint.Reporter.STDERR;
import static com.android.tools.lint.Reporter.STDOUT;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.LintOptions;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Strings;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

// TODO: Move into LintCliFlags?
public class SyncOptions {

    public static void syncTo(
            @NonNull LintOptions options,
            @NonNull LintCliClient client,
            @NonNull LintCliFlags flags,
            @Nullable String variantName,
            @Nullable Project project,
            @Nullable File reportsDir,
            boolean report) {
        flags.getSuppressedIds().addAll(options.getDisable());
        flags.getEnabledIds().addAll(options.getEnable());
        Set<String> check = options.getCheck();
        if (check != null && !check.isEmpty()) {
            flags.setExactCheckedIds(check);
        }
        flags.setSetExitCode(options.isAbortOnError());
        flags.setFullPath(options.isAbsolutePaths());
        flags.setShowSourceLines(!options.isNoLines());
        flags.setQuiet(options.isQuiet());
        flags.setCheckAllWarnings(options.isCheckAllWarnings());
        flags.setIgnoreWarnings(options.isIgnoreWarnings());
        flags.setWarningsAsErrors(options.isWarningsAsErrors());
        flags.setCheckTestSources(options.isCheckTestSources());
        flags.setCheckGeneratedSources(options.isCheckGeneratedSources());
        flags.setCheckDependencies(options.isCheckDependencies());
        flags.setShowEverything(options.isShowAll());
        flags.setDefaultConfiguration(options.getLintConfig());
        flags.setExplainIssues(options.isExplainIssues());
        flags.setBaselineFile(options.getBaselineFile());

        Map<String, Integer> severityOverrides = options.getSeverityOverrides();
        if (severityOverrides != null) {
            Map<String, Severity> map = new HashMap<>();
            BuiltinIssueRegistry registry = new BuiltinIssueRegistry();
            for (Map.Entry<String, Integer> entry : severityOverrides.entrySet()) {
                String id = entry.getKey();
                Integer severityInt = entry.getValue();
                Issue issue = registry.getIssue(id);
                Severity severity =
                        issue != null ? getSeverity(issue, severityInt) : Severity.WARNING;
                map.put(id, severity);
            }
            flags.setSeverityOverrides(map);
        } else {
            flags.setSeverityOverrides(Collections.emptyMap());
        }

        if (report || flags.isFatalOnly() && options.isAbortOnError()) {
            if (options.getTextReport() || flags.isFatalOnly()) {
                File output = options.getTextOutput();
                if (output == null) {
                    output = new File(flags.isFatalOnly() ? STDERR : STDOUT);
                } else if (!output.isAbsolute()
                        && !isStdOut(output)
                        && !isStdErr(output)
                        && project != null) {
                    output = project.file(output.getPath());
                }
                output = validateOutputFile(output);

                Writer writer;
                File file = null;
                boolean closeWriter;
                if (isStdOut(output)) {
                    //noinspection IOResourceOpenedButNotSafelyClosed,resource
                    writer = new PrintWriter(System.out, true);
                    closeWriter = false;
                } else if (isStdErr(output)) {
                    //noinspection IOResourceOpenedButNotSafelyClosed,resource
                    writer = new PrintWriter(System.err, true);
                    closeWriter = false;
                } else {
                    file = output;
                    try {
                        //noinspection IOResourceOpenedButNotSafelyClosed,resource
                        writer = new BufferedWriter(new FileWriter(output));
                    } catch (IOException e) {
                        throw new org.gradle.api.GradleException("Text invalid argument.", e);
                    }
                    closeWriter = true;
                }
                flags.getReporters()
                        .add(Reporter.createTextReporter(client, flags, file, writer, closeWriter));
            }
            if (options.getHtmlReport()) {
                File output = options.getHtmlOutput();
                if (output == null || flags.isFatalOnly()) {
                    output =
                            createOutputPath(
                                    project, variantName, ".html", reportsDir, flags.isFatalOnly());
                } else if (!output.isAbsolute() && project != null) {
                    output = project.file(output.getPath());
                }
                output = validateOutputFile(output);
                try {
                    flags.getReporters().add(Reporter.createHtmlReporter(client, output, flags));
                } catch (IOException e) {
                    throw new GradleException("HTML invalid argument.", e);
                }
            }
            if (options.getXmlReport()) {
                File output = options.getXmlOutput();
                if (output == null || flags.isFatalOnly()) {
                    output =
                            createOutputPath(
                                    project, variantName, DOT_XML, reportsDir, flags.isFatalOnly());
                } else if (!output.isAbsolute() && project != null) {
                    output = project.file(output.getPath());
                }
                output = validateOutputFile(output);
                try {
                    flags.getReporters().add(Reporter.createXmlReporter(client, output, false));
                } catch (IOException e) {
                    throw new org.gradle.api.GradleException("XML invalid argument.", e);
                }
            }
        }
    }

    @NonNull
    static Severity getSeverity(@NonNull Issue issue, @NonNull Integer severityInt) {
        if (severityInt == LintOptions.SEVERITY_DEFAULT_ENABLED) {
            return issue.getDefaultSeverity();
        } else {
            return Severity.fromLintOptionSeverity(severityInt);
        }
    }

    private static boolean isStdOut(@NonNull File output) {
        return STDOUT.equals(output.getPath());
    }

    private static boolean isStdErr(@NonNull File output) {
        return STDERR.equals(output.getPath());
    }

    @NonNull
    public static File validateOutputFile(@NonNull File output) {
        if (isStdOut(output) || isStdErr(output)) {
            return output;
        }

        File parent = output.getParentFile();
        if (!parent.exists()) {
            boolean ok = parent.mkdirs();
            if (!ok) {
                throw new org.gradle.api.GradleException("Could not create directory " + parent);
            }
        }

        output = output.getAbsoluteFile();
        if (output.exists()) {
            boolean delete = output.delete();
            if (!delete) {
                throw new org.gradle.api.GradleException("Could not delete old " + output);
            }
        }
        if (output.getParentFile() != null && !output.getParentFile().canWrite()) {
            throw new org.gradle.api.GradleException("Cannot write output file " + output);
        }

        return output;
    }

    public static File createOutputPath(
            @Nullable Project project,
            @Nullable String variantName,
            @NonNull String extension,
            @Nullable File reportsDir,
            boolean fatalOnly) {
        StringBuilder base = new StringBuilder();
        base.append("lint-results");
        if (!Strings.isNullOrEmpty(variantName)) {
            base.append("-");
            base.append(variantName);
        }
        if (fatalOnly) {
            base.append("-fatal");
        }
        base.append(extension);

        if (reportsDir != null) {
            return new File(reportsDir, base.toString());
        } else if (project == null) {
            return new File(base.toString());
        } else {
            File buildDir = project.getBuildDir();
            return new File(buildDir, FD_REPORTS + separator + base.toString());
        }
    }
}
