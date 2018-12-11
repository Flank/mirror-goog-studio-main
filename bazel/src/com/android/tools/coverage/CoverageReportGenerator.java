/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.coverage;

import com.android.tools.utils.WorkspaceUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.InputStreamSourceFileLocator;
import org.jacoco.report.MultiReportVisitor;
import org.jacoco.report.xml.XMLFormatter;

/**
 * Generates a Jacoco coverage report for tests run with bazel.
 *
 * <p>Usage CoverageReportGenerator report_name //tools/base:test_target1 //tools/base:test_target2
 *
 * <p>The list of test targets is listed by {@code bazel query "kind(test, rdeps(//tools/base/...,
 * deps(//tools/base:coverage_report)))"}
 */
public class CoverageReportGenerator {

    private final String reportName;
    private final Path productionTargets;
    private final String testLogsPath;

    public static void main(String[] argsArray) throws IOException {
        List<String> args = ImmutableList.copyOf(argsArray);
        if (args.size() != 3) {
            throw new IllegalArgumentException(
                    "Must pass at the report name, file of targets and a (relative ok) test log path");
        }
        new CoverageReportGenerator(args.get(0), Paths.get(args.get(1)), args.get(2))
                .generateReport();
    }

    public CoverageReportGenerator(String reportName, Path productionTargets, String testLogsPath) {
        this.reportName = reportName;
        this.productionTargets = productionTargets;
        this.testLogsPath = testLogsPath;
    }

    public void generateReport() throws IOException {
        Path tempDir = Files.createTempDirectory("CoverageReportGenerator");

        Path classesJar = tempDir.resolve("classes.jar");
        extractJar("classes.jar", classesJar);
        Path srcJar = tempDir.resolve("sources.jar");
        extractJar("sources.jar", srcJar);

        Set<String> sourcePackages = getSourcePackages(srcJar);
        Path workspace = WorkspaceUtils.findWorkspace();
        Path bazelTestLogs = workspace.resolve(testLogsPath).toRealPath();

        ExecFileLoader loader = new ExecFileLoader();
        loadCoverageFiles(bazelTestLogs, loader);

        Path reportDir = workspace.resolve("out/agent-coverage/").resolve(reportName);
        generateReport(classesJar, srcJar, loader, reportDir, sourcePackages);
        System.out.format("XML report generated at file://%1$s/report.xml%n", reportDir);
    }

    private static Set<String> getSourcePackages(Path srcJar) throws IOException {
        Set<String> packages = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(srcJar))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String packageName = getPackageName(entry.getName());
                if (packageName == null) {
                    continue;
                }

                packages.add(packageName);
            }
        }
        return packages;
    }

    private void loadCoverageFiles(Path bazelTestLogs, ExecFileLoader loader) throws IOException {
        Set<String> prefixes;
        try (Stream<String> lines = Files.lines(productionTargets)) {
            prefixes =
                    lines.filter(it -> !it.isEmpty())
                            .map(it -> it.substring(2).replace(':', '/'))
                            .collect(Collectors.toSet());
        }
        int count = 0;
        for (String target : prefixes) {
            Path dir = bazelTestLogs.resolve(target);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> outputZips;
            try (Stream<Path> paths = Files.walk(dir)) {
                outputZips =
                        paths.filter((it) -> it.getFileName().toString().equals("outputs.zip"))
                                .collect(Collectors.toList());
            }

            for (Path file : outputZips) {
                try (ZipInputStream zipInputStream =
                        new ZipInputStream(Files.newInputStream(file))) {
                    ZipEntry entry;
                    while ((entry = zipInputStream.getNextEntry()) != null) {
                        if (entry.getName().startsWith("coverage/") && !entry.isDirectory()) {
                            try {
                                count++;
                                System.out.print('.');
                                loader.load(zipInputStream);
                            } catch (Exception e) {
                                throw new IOException(
                                        "error loading bazel-testlogs/"
                                                + bazelTestLogs.relativize(file)
                                                + ":"
                                                + entry.getName(),
                                        e);
                            }
                        }
                    }
                }
            }
        }
        if (count == 0) {
            throw new IOException("No coverage files to load from targets");
        }
    }

    private void generateReport(
            Path classesJar,
            Path srcJar,
            ExecFileLoader loader,
            Path reportDir,
            Set<String> sourcePackages)
            throws IOException {
        SessionInfoStore sessionInfoStore = loader.getSessionInfoStore();
        ExecutionDataStore executionDataStore = loader.getExecutionDataStore();

        FileMultiReportOutput output = new FileMultiReportOutput(reportDir.toFile());

        XMLFormatter xmlFormatter = new XMLFormatter();
        xmlFormatter.setOutputEncoding(Charsets.UTF_8.name());

        System.out.format("Generating coverage report '%1$s'%n", reportDir);

        try (OutputStream xmlReportOutput = output.createFile("report.xml"); ) {
            IReportVisitor xmlReport = xmlFormatter.createVisitor(xmlReportOutput);

            final IReportVisitor visitor =
                    new MultiReportVisitor(ImmutableList.of(xmlReport));

            visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());

            final CoverageBuilder builder = new CoverageBuilder();
            final Analyzer analyzer = new Analyzer(executionDataStore, builder);

            System.out.print("Analyzing class files ");
            try (ZipInputStream zipInputStream =
                    new ZipInputStream(Files.newInputStream(classesJar))) {
                Stopwatch stopwatch = Stopwatch.createStarted();
                ZipEntry entry;
                int count = 0;

                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!analyze(entry.getName(), sourcePackages)) {
                        continue;
                    }
                    try {
                        analyzer.analyzeClass(
                                zipInputStream,
                                entry.getName()
                                        .substring(
                                                0, entry.getName().length() - ".class".length()));
                        count++;
                        if (count % 500 == 0) {
                            System.out.print('.');
                        }
                    } catch (IOException e) {
                        throw new IOException(
                                "Error analyzing " + classesJar + " " + entry.getName(), e);
                    }
                }
                if (count == 0) {
                    throw new IOException("No classes to analyze");
                }
                System.out.format("%nanalzyed %1$d classes in %2$s%n", count, stopwatch.toString());
            }

            System.out.print("Generating report.. ");
            Stopwatch stopwatch = Stopwatch.createStarted();
            try (JarSourceFileLocator locator = new JarSourceFileLocator(srcJar)) {
                final IBundleCoverage bundle = builder.getBundle(reportName);
                visitor.visitBundle(bundle, locator);
                visitor.visitEnd();
            }
            System.out.println("... done in " + stopwatch.toString());
        }
    }

    private static boolean analyze(String entryName, Set<String> sourcePackages) {
        if (!entryName.endsWith(".class")) {
            return false;
        }
        String packageName = getPackageName(entryName);
        if (packageName == null) {
            return false;
        }
        boolean include =
                sourcePackages.contains(packageName)
                        //TODO: once we have source attachments, we should get this automatically.
                        | packageName.startsWith("com.android")
                        | packageName.startsWith("org.jetbrains.android")
                        | packageName.startsWith("org.jetbrains.kotlin.android");

        if (!include) {
            return false;
        }

        boolean exclude =
                packageName.contains("proto")
                        || packageName.startsWith("com.android.tools.r8")
                        || packageName.equals("com.google.wireless.android.sdk.stats")
                        || packageName.startsWith("com.android.internal")
                        || packageName.startsWith("com.android.bundle")
                        || packageName.startsWith("com.android.aapt")
                        || packageName.startsWith("com.android.i18n")
                        || packageName.contains("unimi.dsi.fastutil");

        return !exclude;
    }

    private static String getPackageName(String name) {
        int endIndex = name.lastIndexOf('/');
        if (endIndex < 0) {
            return null;
        }
        return name.substring(0, endIndex).replace('/', '.');
    }

    private static void extractJar(String jarName, Path location) throws IOException {
        URL jar = CoverageReportGenerator.class.getResource("/" + jarName);
        Preconditions.checkNotNull(jar, jar + " not found");
        try (OutputStream classesJarOut =
                new BufferedOutputStream(Files.newOutputStream(location))) {
            Resources.copy(jar, classesJarOut);
        }
    }

    private static class JarSourceFileLocator extends InputStreamSourceFileLocator
            implements Closeable {

        private ZipFile sourcesJar;

        public JarSourceFileLocator(Path jar) throws IOException {
            super(Charsets.UTF_8.name(), 4);
            sourcesJar = new ZipFile(jar.toFile());
        }

        @Override
        protected InputStream getSourceStream(String path) throws IOException {
            ZipEntry entry = sourcesJar.getEntry(path);
            if (entry == null) {
                return null;
            }
            System.out.print('.');
            return sourcesJar.getInputStream(entry);
        }

        @Override
        public void close() throws IOException {
            sourcesJar.close();
        }
    }
}
