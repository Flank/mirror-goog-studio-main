/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.coverage;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.builder.model.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiReportVisitor;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;

/**
 * Simple Jacoco report task that calls the Ant version.
 */
public class JacocoReportTask extends DefaultTask {

    private FileCollection jacocoClasspath;

    private Supplier<File> coverageDirectory;
    private FileCollection classFileCollection;
    private Supplier<Collection<File>> sourceFolders;

    private File coverageFile;
    private File reportDir;
    private String reportName;


    private int tabWidth = 4;

    @InputFile
    @Optional
    @Nullable
    public File getCoverageFile() {
        return coverageFile;
    }

    public void setCoverageFile(File coverageFile) {
        this.coverageFile = coverageFile;
    }

    @InputDirectory
    @Optional
    @Nullable
    public File getCoverageDirectory() {
        return coverageDirectory.get();
    }

    @OutputDirectory
    public File getReportDir() {
        return reportDir;
    }

    public void setReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    @InputFiles
    public FileCollection getClassFileCollection() {
        return classFileCollection;
    }

    @InputFiles
    public Collection<File> getSourceFolders() {
        return sourceFolders.get();
    }

    public String getReportName() {
        return reportName;
    }

    @Input
    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    @InputFiles
    public FileCollection getJacocoClasspath() {
        return jacocoClasspath;
    }

    public void setJacocoClasspath(FileCollection jacocoClasspath) {
        this.jacocoClasspath = jacocoClasspath;
    }

    @Input
    public int getTabWidth() {
        return tabWidth;
    }

    public void setTabWidth(int tabWidth) {
        this.tabWidth = tabWidth;
    }

    @TaskAction
    public void generateReport() throws IOException {
        File coverageFile = getCoverageFile();
        File coverageDir = coverageDirectory.get();


        List<File> coverageFiles = Lists.newArrayList();
        if (coverageFile != null) {
            coverageFiles.add(coverageFile);
        }
        if (coverageDir != null) {
            Files.fileTreeTraverser().breadthFirstTraversal(coverageDir)
                    .filter(File::isFile).copyInto(coverageFiles);
        }

        if (coverageFiles.isEmpty()) {
            if (coverageDir == null) {
                throw new IOException("No input file or directory specified.");
            } else {
                throw new IOException(String.format(
                        "No coverage data to process in directory '%1$s'", coverageDir));
            }
        }

        generateReport(
                coverageFiles,
                getReportDir(),
                getClassFileCollection().getFiles(),
                getSourceFolders(),
                getTabWidth(),
                getReportName(),
                getLogger());
    }

    @VisibleForTesting
    static void generateReport(
            @NonNull List<File> coverageFiles,
            @NonNull File reportDir,
            @NonNull Collection<File> classFolders,
            @NonNull Collection<File> sourceFolders,
            int tabWidth,
            @NonNull String reportName,
            @NonNull Logger logger)
            throws IOException {
        // Load data
        final ExecFileLoader loader = new ExecFileLoader();
        for (File coverageFile: coverageFiles) {
            loader.load(coverageFile);
        }

        SessionInfoStore sessionInfoStore = loader.getSessionInfoStore();
        ExecutionDataStore executionDataStore = loader.getExecutionDataStore();

        // Initialize report generator.
        HTMLFormatter htmlFormatter = new HTMLFormatter();
        htmlFormatter.setOutputEncoding("UTF-8");
        htmlFormatter.setLocale(Locale.US);
        htmlFormatter.setFooterText("Generated by the Android Gradle plugin " +
                Version.ANDROID_GRADLE_PLUGIN_VERSION);

        FileMultiReportOutput output = new FileMultiReportOutput(reportDir);
        IReportVisitor htmlReport = htmlFormatter.createVisitor(output);

        XMLFormatter xmlFormatter = new XMLFormatter();
        xmlFormatter.setOutputEncoding("UTF-8");
        OutputStream xmlReportOutput = output.createFile("report.xml");
        try {
            IReportVisitor xmlReport = xmlFormatter.createVisitor(xmlReportOutput);

            final IReportVisitor visitor =
                    new MultiReportVisitor(ImmutableList.of(htmlReport, xmlReport));

            // Generate report
            visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());

            final CoverageBuilder builder = new CoverageBuilder();
            final Analyzer analyzer = new Analyzer(executionDataStore, builder);

            analyzeAll(analyzer, classFolders);

            MultiSourceFileLocator locator = new MultiSourceFileLocator(0);
            for (File folder : sourceFolders) {
                locator.add(new DirectorySourceFileLocator(folder, "UTF-8", tabWidth));
            }

            final IBundleCoverage bundle = builder.getBundle(reportName);
            visitor.visitBundle(bundle, locator);
            visitor.visitEnd();
        } finally {
            try {
                xmlReportOutput.close();
            } catch (IOException e) {
                logger.error("Could not close xml report file", e);
            }
        }
    }

    private static void analyzeAll(
            @NonNull Analyzer analyzer, @NonNull Collection<File> classFolders) throws IOException {
        for (final File folder : classFolders) {
            analyze(analyzer, folder, classFolders);
        }
    }

    /**
     * Analyzes code coverage on file if it's a class file, or recursively analyzes descendants if
     * file is a folder.
     *
     * @param analyzer Jacoco Analyzer
     * @param file a file or folder
     * @param originalClassFolders the original collection of class folders to be analyzed; e.g.,
     *     this.classFileCollection.getFiles(). This parameter is included to avoid redundant
     *     computation in the case when one of the original class folders is a descendant of
     *     another.
     */
    private static void analyze(
            @NonNull Analyzer analyzer,
            @NonNull File file,
            @NonNull Collection<File> originalClassFolders)
            throws IOException {
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                for (final File f : files) {
                    // check that f is not in originalClassFolders to avoid redundant computation
                    if (!originalClassFolders.contains(f)) {
                        analyze(analyzer, f, originalClassFolders);
                    }
                }
            }
        } else {
            String name = file.getName();
            if (!name.endsWith(".class") ||
                    name.equals("R.class") ||
                    name.startsWith("R$") ||
                    name.equals("Manifest.class") ||
                    name.startsWith("Manifest$") ||
                    name.equals("BuildConfig.class")) {
                return;
            }

            InputStream in = new FileInputStream(file);
            try {
                analyzer.analyzeClass(in, file.getAbsolutePath());
            } finally {
                Closeables.closeQuietly(in);
            }
        }
    }

    public static class ConfigAction implements TaskConfigAction<JacocoReportTask> {
        private VariantScope scope;

        public ConfigAction (VariantScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("create", "CoverageReport");
        }

        @NonNull
        @Override
        public Class<JacocoReportTask> getType() {
            return JacocoReportTask.class;
        }

        @Override
        public void execute(@NonNull JacocoReportTask task) {

            task.setDescription("Creates JaCoCo test coverage report from data gathered on the "
                    + "device.");

            task.setReportName(scope.getVariantConfiguration().getFullName());
            final Project project = scope.getGlobalScope().getProject();

            checkNotNull(scope.getTestedVariantData());
            final VariantScope testedScope = scope.getTestedVariantData().getScope();

            task.jacocoClasspath =
                    project.getConfigurations().getAt(JacocoPlugin.ANT_CONFIGURATION_NAME);

            task.coverageDirectory = TaskInputHelper.memoize(
                    () -> ((TestVariantData) scope.getVariantData()).connectedTestTask
                                    .getCoverageDir());

            task.classFileCollection =
                    testedScope.getOutput(TaskOutputHolder.AnchorOutputType.ALL_CLASSES);

            task.sourceFolders =
                    TaskInputHelper.bypassFileSupplier(
                            () -> testedScope.getVariantData().getJavaSourceFoldersForCoverage());

            task.setReportDir(testedScope.getCoverageReportDir());
        }
    }
}
