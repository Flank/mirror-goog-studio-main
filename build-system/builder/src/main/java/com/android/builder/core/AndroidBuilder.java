/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_XML;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.manifmerger.ManifestMerger2.Invoker;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.internal.TestManifestGenerator;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.v2.Aapt2;
import com.android.builder.internal.aapt.v2.Aapt2Exception;
import com.android.builder.internal.aapt.v2.Aapt2InternalException;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.builder.internal.compiler.RenderScriptProcessor;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.builder.symbols.BytecodeRClassWriterKt;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.ide.common.symbols.RGeneration;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.symbols.SymbolUtils;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestProvider;
import com.android.manifmerger.ManifestSystemProperty;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.PlaceholderHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This is the main builder class. It is given all the data to process the build (such as {@link
 * DefaultProductFlavor}s, {@link DefaultBuildType} and dependencies) and use them when doing
 * specific build steps.
 *
 * <p>To use: create a builder with {@link #AndroidBuilder(String, ProcessExecutor,
 * JavaProcessExecutor, EvalIssueReporter, MessageReceiver, ILogger)}
 *
 * <p>then build steps can be done with:
 *
 * <ol>
 *   <li>{@link #mergeManifestsForApplication }
 *   <li>{@link #mergeManifestsForTestVariant }
 *   <li>{@link #processResources }
 * </ol>
 */
public class AndroidBuilder {

    /**
     * Minimal supported version of build tools.
     *
     * <p>ATTENTION: When changing this value, make sure to update the release notes
     * (https://developer.android.com/studio/releases/gradle-plugin).
     */
    public static final Revision MIN_BUILD_TOOLS_REV =
            Revision.parseRevision(SdkConstants.CURRENT_BUILD_TOOLS_VERSION);

    /**
     * Default version of build tools that will be used if the user does not specify.
     *
     * <p>ATTENTION: This is usually the same as the minimum build tools version, as documented in
     * {@code com.android.build.gradle.AndroidConfig#getBuildToolsVersion()} and {@code
     * com.android.build.api.dsl.extension.BuildProperties#getBuildToolsVersion()}, and in the
     * release notes (https://developer.android.com/studio/releases/gradle-plugin). If this version
     * is higher than the minimum version, make sure to update those places to document the new
     * behavior.
     */
    public static final Revision DEFAULT_BUILD_TOOLS_REVISION = MIN_BUILD_TOOLS_REV;

    @NonNull
    private final ILogger mLogger;

    @NonNull
    private final ProcessExecutor mProcessExecutor;
    @NonNull
    private final JavaProcessExecutor mJavaProcessExecutor;
    @NonNull private final EvalIssueReporter issueReporter;
    @NonNull private final MessageReceiver messageReceiver;

    @Nullable private String mCreatedBy;

    /**
     * Creates an AndroidBuilder.
     *
     * <p><var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param createdBy the createdBy String for the apk manifest.
     * @param logger the Logger
     */
    public AndroidBuilder(
            @Nullable String createdBy,
            @NonNull ProcessExecutor processExecutor,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull MessageReceiver messageReceiver,
            @NonNull ILogger logger) {
        mCreatedBy = createdBy;
        mProcessExecutor = checkNotNull(processExecutor);
        mJavaProcessExecutor = checkNotNull(javaProcessExecutor);
        this.issueReporter = checkNotNull(issueReporter);
        this.messageReceiver = messageReceiver;
        mLogger = checkNotNull(logger);
    }

    @NonNull
    public ILogger getLogger() {
        return mLogger;
    }

    @NonNull
    public EvalIssueReporter getIssueReporter() {
        return issueReporter;
    }

    @NonNull
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    @NonNull
    public ProcessExecutor getProcessExecutor() {
        return mProcessExecutor;
    }

    @NonNull
    public JavaProcessExecutor getJavaProcessExecutor() {
        return mJavaProcessExecutor;
    }

    @NonNull
    public ProcessResult executeProcess(@NonNull ProcessInfo processInfo,
            @NonNull ProcessOutputHandler handler) {
        return mProcessExecutor.execute(processInfo, handler);
    }

    /** Invoke the Manifest Merger version 2. */
    public static MergingReport mergeManifestsForApplication(
            @NonNull File mainManifest,
            @NonNull List<File> manifestOverlays,
            @NonNull List<? extends ManifestProvider> dependencies,
            @NonNull List<File> navigationFiles,
            @Nullable String featureName,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion,
            @NonNull String outManifestLocation,
            @Nullable String outAaptSafeManifestLocation,
            @Nullable String outMetadataFeatureManifestLocation,
            @Nullable String outBundleManifestLocation,
            @Nullable String outInstantAppManifestLocation,
            @NonNull ManifestMerger2.MergeType mergeType,
            Map<String, Object> placeHolders,
            @NonNull Collection<Invoker.Feature> optionalFeatures,
            @Nullable File reportFile,
            @NonNull ILogger logger) {

        try {

            Invoker manifestMergerInvoker =
                    ManifestMerger2.newMerger(mainManifest, logger, mergeType)
                            .setPlaceHolderValues(placeHolders)
                            .addFlavorAndBuildTypeManifests(manifestOverlays.toArray(new File[0]))
                            .addManifestProviders(dependencies)
                            .addNavigationFiles(navigationFiles)
                            .withFeatures(optionalFeatures.toArray(new Invoker.Feature[0]))
                            .setMergeReportFile(reportFile)
                            .setFeatureName(featureName);

            if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
            }

            //noinspection VariableNotUsedInsideIf
            if (outAaptSafeManifestLocation != null) {
                manifestMergerInvoker.withFeatures(Invoker.Feature.MAKE_AAPT_SAFE);
            }

            setInjectableValues(manifestMergerInvoker,
                    packageOverride, versionCode, versionName,
                    minSdkVersion, targetSdkVersion, maxSdkVersion);

            MergingReport mergingReport = manifestMergerInvoker.merge();
            logger.verbose("Merging result: %1$s", mergingReport.getResult());
            switch (mergingReport.getResult()) {
                case WARNING:
                    mergingReport.log(logger);
                    // fall through since these are just warnings.
                case SUCCESS:
                    String xmlDocument =
                            mergingReport.getMergedDocument(
                                    MergingReport.MergedManifestKind.MERGED);
                    String annotatedDocument =
                            mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME);
                    if (annotatedDocument != null) {
                        logger.verbose(annotatedDocument);
                    }
                    save(xmlDocument, new File(outManifestLocation));
                    logger.verbose("Merged manifest saved to " + outManifestLocation);

                    if (outAaptSafeManifestLocation != null) {
                        save(
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.AAPT_SAFE),
                                new File(outAaptSafeManifestLocation));
                    }

                    if (outMetadataFeatureManifestLocation != null) {
                        // This is the manifest used for merging back to the base. This is created
                        // by both dynamic-features and normal features.
                        String featureManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.METADATA_FEATURE);
                        if (featureManifest != null) {
                            save(featureManifest, new File(outMetadataFeatureManifestLocation));
                        }
                    }

                    if (outBundleManifestLocation != null) {
                        String bundleMergedManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.BUNDLE);
                        if (bundleMergedManifest != null) {
                            save(bundleMergedManifest, new File(outBundleManifestLocation));
                        }
                    }

                    if (outInstantAppManifestLocation != null) {
                        String instantAppManifest =
                                mergingReport.getMergedDocument(
                                        MergingReport.MergedManifestKind.INSTANT_APP);
                        if (instantAppManifest != null) {
                            save(instantAppManifest, new File(outInstantAppManifestLocation));
                        }
                    }
                    break;
                case ERROR:
                    mergingReport.log(logger);
                    throw new RuntimeException(mergingReport.getReportString());
                default:
                    throw new RuntimeException("Unhandled result type : "
                            + mergingReport.getResult());
            }
            return mergingReport;
        } catch (ManifestMerger2.MergeFailureException e) {
            // TODO: unacceptable.
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the {@link ManifestSystemProperty} that can be injected
     * in the manifest file.
     */
    private static void setInjectableValues(
            ManifestMerger2.Invoker<?> invoker,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion) {

        if (!Strings.isNullOrEmpty(packageOverride)) {
            invoker.setOverride(ManifestSystemProperty.PACKAGE, packageOverride);
        }
        if (versionCode > 0) {
            invoker.setOverride(ManifestSystemProperty.VERSION_CODE,
                    String.valueOf(versionCode));
        }
        if (!Strings.isNullOrEmpty(versionName)) {
            invoker.setOverride(ManifestSystemProperty.VERSION_NAME, versionName);
        }
        if (!Strings.isNullOrEmpty(minSdkVersion)) {
            invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion);
        }
        if (!Strings.isNullOrEmpty(targetSdkVersion)) {
            invoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
        }
        if (maxSdkVersion != null) {
            invoker.setOverride(ManifestSystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
        }
    }

    /**
     * Saves the {@link com.android.manifmerger.XmlDocument} to a file in UTF-8 encoding.
     * @param xmlDocument xml document to save.
     * @param out file to save to.
     */
    private static void save(String xmlDocument, File out) {
        try {
            Files.createParentDirs(out);
            Files.asCharSink(out, Charsets.UTF_8).write(xmlDocument);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testApplicationId the application id of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param targetSdkVersion the targetSdkVersion of the test application
     * @param testedApplicationId the application id of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param handleProfiling whether or not the Instrumentation object will turn profiling on and off
     * @param functionalTest whether or not the Instrumentation class should run as a functional test
     * @param testLabel the label for the tests
     * @param testManifestFile optionally user provided AndroidManifest.xml for testing application
     * @param manifestProviders the manifest providers
     * @param manifestPlaceholders used placeholders in the manifest
     * @param outManifest the output location for the merged manifest
     * @param tmpDir temporary dir used for processing
     */
    public void mergeManifestsForTestVariant(
            @NonNull String testApplicationId,
            @NonNull String minSdkVersion,
            @NonNull String targetSdkVersion,
            @NonNull String testedApplicationId,
            @NonNull String instrumentationRunner,
            @NonNull Boolean handleProfiling,
            @NonNull Boolean functionalTest,
            @Nullable String testLabel,
            @Nullable File testManifestFile,
            @NonNull List<? extends ManifestProvider> manifestProviders,
            @NonNull Map<String, Object> manifestPlaceholders,
            @NonNull File outManifest,
            @NonNull File tmpDir) {
        checkNotNull(testApplicationId, "testApplicationId cannot be null.");
        checkNotNull(testedApplicationId, "testedApplicationId cannot be null.");
        checkNotNull(instrumentationRunner, "instrumentationRunner cannot be null.");
        checkNotNull(handleProfiling, "handleProfiling cannot be null.");
        checkNotNull(functionalTest, "functionalTest cannot be null.");
        checkNotNull(manifestProviders, "manifestProviders cannot be null.");
        checkNotNull(outManifest, "outManifestLocation cannot be null.");

        // These temp files are only need in the middle of processing manifests; delete
        // them when they're done. We're not relying on File#deleteOnExit for this
        // since in the Gradle daemon for example that would leave the files around much
        // longer than we want.
        File tempFile1 = null;
        File tempFile2 = null;
        try {
            FileUtils.mkdirs(tmpDir);
            File generatedTestManifest = manifestProviders.isEmpty() && testManifestFile == null
                    ? outManifest
                    : (tempFile1 = File.createTempFile("manifestMerger", ".xml", tmpDir));

            // we are generating the manifest and if there is an existing one,
            // it will be overlaid with the generated one
            mLogger.verbose("Generating in %1$s", generatedTestManifest.getAbsolutePath());
            generateTestManifest(
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersion.equals("-1") ? null : targetSdkVersion,
                    testedApplicationId,
                    instrumentationRunner,
                    handleProfiling,
                    functionalTest,
                    generatedTestManifest);

            if (testManifestFile != null && testManifestFile.exists()) {
                Invoker invoker = ManifestMerger2.newMerger(
                        testManifestFile, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .setPlaceHolderValues(manifestPlaceholders)
                        .setPlaceHolderValue(PlaceholderHandler.INSTRUMENTATION_RUNNER,
                                instrumentationRunner)
                        .addLibraryManifest(generatedTestManifest);

                // we override these properties
                invoker.setOverride(ManifestSystemProperty.PACKAGE, testApplicationId);
                invoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion);
                invoker.setOverride(ManifestSystemProperty.NAME, instrumentationRunner);
                invoker.setOverride(ManifestSystemProperty.TARGET_PACKAGE, testedApplicationId);
                invoker.setOverride(ManifestSystemProperty.FUNCTIONAL_TEST, functionalTest.toString());
                invoker.setOverride(ManifestSystemProperty.HANDLE_PROFILING, handleProfiling.toString());
                if (testLabel != null) {
                    invoker.setOverride(ManifestSystemProperty.LABEL, testLabel);
                }

                if (!targetSdkVersion.equals("-1")) {
                    invoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion);
                }

                MergingReport mergingReport = invoker.merge();
                if (manifestProviders.isEmpty()) {
                    handleMergingResult(mergingReport, outManifest);
                } else {
                    tempFile2 = File.createTempFile("manifestMerger", ".xml", tmpDir);
                    handleMergingResult(mergingReport, tempFile2);
                    generatedTestManifest = tempFile2;
                }
            }

            if (!manifestProviders.isEmpty()) {
                MergingReport mergingReport = ManifestMerger2.newMerger(
                        generatedTestManifest, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                        .setOverride(ManifestSystemProperty.PACKAGE, testApplicationId)
                        .addManifestProviders(manifestProviders)
                        .setPlaceHolderValues(manifestPlaceholders)
                        .merge();

                handleMergingResult(mergingReport, outManifest);
            }
        } catch(IOException e) {
            throw new RuntimeException("Unable to create the temporary file", e);
        } catch (ManifestMerger2.MergeFailureException e) {
            throw new RuntimeException("Manifest merging exception", e);
        } finally {
            try {
                if (tempFile1 != null) {
                    FileUtils.delete(tempFile1);
                }
                if (tempFile2 != null) {
                    FileUtils.delete(tempFile2);
                }
            } catch (IOException e){
                // just log this, so we do not mask the initial exception if there is any
                mLogger.error(e, "Unable to clean up the temporary files.");
            }
        }
    }

    private void handleMergingResult(@NonNull MergingReport mergingReport, @NonNull File outFile) {
        switch (mergingReport.getResult()) {
            case WARNING:
                mergingReport.log(mLogger);
                // fall through since these are just warnings.
            case SUCCESS:
                try {
                    String annotatedDocument = mergingReport.getMergedDocument(
                            MergingReport.MergedManifestKind.BLAME);
                    if (annotatedDocument != null) {
                        mLogger.verbose(annotatedDocument);
                    } else {
                        mLogger.verbose("No blaming records from manifest merger");
                    }
                } catch (Exception e) {
                    mLogger.error(e, "cannot print resulting xml");
                }
                String finalMergedDocument = mergingReport
                        .getMergedDocument(MergingReport.MergedManifestKind.MERGED);
                if (finalMergedDocument == null) {
                    throw new RuntimeException("No result from manifest merger");
                }
                try {
                    Files.asCharSink(outFile, Charsets.UTF_8).write(finalMergedDocument);
                } catch (IOException e) {
                    mLogger.error(e, "Cannot write resulting xml");
                    throw new RuntimeException(e);
                }
                mLogger.verbose("Merged manifest saved to " + outFile);
                break;
            case ERROR:
                mergingReport.log(mLogger);
                throw new RuntimeException(mergingReport.getReportString());
            default:
                throw new RuntimeException("Unhandled result type : "
                        + mergingReport.getResult());
        }
    }

    private static void generateTestManifest(
            @NonNull String testApplicationId,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @NonNull String testedApplicationId,
            @NonNull String instrumentationRunner,
            @NonNull Boolean handleProfiling,
            @NonNull Boolean functionalTest,
            @NonNull File outManifestLocation) {
        TestManifestGenerator generator = new TestManifestGenerator(
                outManifestLocation,
                testApplicationId,
                minSdkVersion,
                targetSdkVersion,
                testedApplicationId,
                instrumentationRunner,
                handleProfiling,
                functionalTest);
        try {
            generator.generate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void processResources(
            @NonNull Aapt2 aapt,
            @NonNull AaptPackageConfig aaptConfig,
            @Nullable File rJar,
            @NonNull ILogger logger)
            throws IOException, ProcessException {

        try {
            aapt.link(aaptConfig, logger);
        } catch (Aapt2Exception | Aapt2InternalException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessException("Failed to execute aapt", e);
        }

        File sourceOut = aaptConfig.getSourceOutputDir();
        if (sourceOut != null || rJar != null) {
            // Figure out what the main symbol file's package is.
            String mainPackageName = aaptConfig.getCustomPackageForR();
            if (mainPackageName == null) {
                mainPackageName =
                        SymbolUtils.getPackageNameFromManifest(aaptConfig.getManifestFile());
            }

            // Load the main symbol file.
            File mainRTxt = new File(aaptConfig.getSymbolOutputDir(), "R.txt");
            SymbolTable mainSymbols =
                    mainRTxt.isFile()
                            ? SymbolIo.readFromAapt(mainRTxt, mainPackageName)
                            : SymbolTable.builder().tablePackage(mainPackageName).build();

            // For each dependency, load its symbol file.
            Set<SymbolTable> depSymbolTables =
                    SymbolUtils.loadDependenciesSymbolTables(
                            aaptConfig.getLibrarySymbolTableFiles());

            boolean finalIds = aaptConfig.getUseFinalIds();
            if (rJar != null) { // not yet used, will be used in non-namespaced case
                // replace the default values from the dependency table with the allocated values
                // from the main table
                depSymbolTables =
                        depSymbolTables
                                .stream()
                                .map(t -> t.withValuesFrom(mainSymbols))
                                .collect(Collectors.toSet());
                BytecodeRClassWriterKt.exportToCompiledJava(
                        Iterables.concat(Collections.singleton(mainSymbols), depSymbolTables),
                        rJar.toPath(),
                        finalIds);
            } else { // namespaced case, TODO: use exportToCompiledJava instead b/130110629
                RGeneration.generateRForLibraries(
                        mainSymbols, depSymbolTables, sourceOut, finalIds);
            }
        }
    }

    public void generateApkData(
            @NonNull File apkFile,
            @NonNull File outResFolder,
            @NonNull String mainPkgName,
            @NonNull String resName,
            @NonNull File aapt2Executable)
            throws ProcessException, IOException {

        ApkInfoParser parser = new ApkInfoParser(aapt2Executable, mProcessExecutor);
        ApkInfoParser.ApkInfo apkInfo = parser.parseApk(apkFile);

        if (!apkInfo.getPackageName().equals(mainPkgName)) {
            throw new RuntimeException("The main and the micro apps do not have the same package name.");
        }

        String content = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<wearableApp package=\"%1$s\">\n" +
                        "    <versionCode>%2$s</versionCode>\n" +
                        "    <versionName>%3$s</versionName>\n" +
                        "    <rawPathResId>%4$s</rawPathResId>\n" +
                        "</wearableApp>",
                apkInfo.getPackageName(),
                apkInfo.getVersionCode(),
                apkInfo.getVersionName(),
                resName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.asCharSink(new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
                .write(content);
    }

    public static void generateUnbundledWearApkData(
            @NonNull File outResFolder, @NonNull String mainPkgName) throws IOException {

        String content = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<wearableApp package=\"%1$s\">\n" +
                        "    <unbundled />\n" +
                        "</wearableApp>",
                mainPkgName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.asCharSink(new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
                .write(content);
    }

    public static void generateApkDataEntryInManifest(
            int minSdkVersion, int targetSdkVersion, @NonNull File manifestFile)
            throws IOException {

        StringBuilder content = new StringBuilder();
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
                .append("    package=\"${packageName}\">\n")
                .append("    <uses-sdk android:minSdkVersion=\"")
                .append(minSdkVersion)
                .append("\"");
        if (targetSdkVersion != -1) {
            content.append(" android:targetSdkVersion=\"").append(targetSdkVersion).append("\"");
        }
        content.append("/>\n");
        content.append("    <application>\n")
                .append("        <meta-data android:name=\"" + ANDROID_WEAR + "\"\n")
                .append("                   android:resource=\"@xml/" + ANDROID_WEAR_MICRO_APK)
                .append("\" />\n")
                .append("   </application>\n")
                .append("</manifest>\n");

        Files.asCharSink(manifestFile, Charsets.UTF_8).write(content);
    }

    /**
     * Compiles all the shader files found in the given source folders.
     *
     * @param sourceFolder the source folder with the merged shaders
     * @param outputDir the output dir in which to generate the output
     * @throws IOException failed
     */
    public void compileAllShaderFiles(
            @NonNull File sourceFolder,
            @NonNull File outputDir,
            @NonNull List<String> defaultArgs,
            @NonNull Map<String, List<String>> scopedArgs,
            @Nullable Supplier<File> ndkLocation,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull WorkerExecutorFacade workers)
            throws IOException {
        checkNotNull(sourceFolder, "sourceFolder cannot be null.");
        checkNotNull(outputDir, "outputDir cannot be null.");

        Supplier<ShaderProcessor> processor =
                () ->
                        new ShaderProcessor(
                                ndkLocation,
                                sourceFolder,
                                outputDir,
                                defaultArgs,
                                scopedArgs,
                                mProcessExecutor,
                                processOutputHandler,
                                workers);

        DirectoryWalker.builder()
                .root(sourceFolder.toPath())
                .extensions(
                        ShaderProcessor.EXT_VERT,
                        ShaderProcessor.EXT_TESC,
                        ShaderProcessor.EXT_TESE,
                        ShaderProcessor.EXT_GEOM,
                        ShaderProcessor.EXT_FRAG,
                        ShaderProcessor.EXT_COMP)
                .action(processor)
                .build()
                .walk();
    }

    /**
     * Compiles all the renderscript files found in the given source folders.
     *
     * <p>Right now this is the only way to compile them as the renderscript compiler requires all
     * renderscript files to be passed for all compilation.
     *
     * <p>Therefore whenever a renderscript file or header changes, all must be recompiled.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param importFolders all the import folders.
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param resOutputDir the output dir in which to generate the bitcode file
     * @param targetApi the target api
     * @param debugBuild whether the build is debug
     * @param optimLevel the optimization level
     * @param ndkMode whether the renderscript code should be compiled to generate C/C++ bindings
     * @param supportMode support mode flag to generate .so files.
     * @param useAndroidX whether to use AndroidX dependencies
     * @param abiFilters ABI filters in case of support mode
     * @throws IOException failed
     * @throws InterruptedException failed
     */
    public void compileAllRenderscriptFiles(
            @NonNull Collection<File> sourceFolders,
            @NonNull Collection<File> importFolders,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            int targetApi,
            boolean debugBuild,
            int optimLevel,
            boolean ndkMode,
            boolean supportMode,
            boolean useAndroidX,
            @Nullable Set<String> abiFilters,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull BuildToolInfo buildToolInfo)
            throws InterruptedException, ProcessException, IOException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(resOutputDir, "resOutputDir cannot be null.");

        String renderscript = buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException("llvm-rs-cc is missing");
        }

        RenderScriptProcessor processor =
                new RenderScriptProcessor(
                        sourceFolders,
                        importFolders,
                        sourceOutputDir,
                        resOutputDir,
                        objOutputDir,
                        libOutputDir,
                        buildToolInfo,
                        targetApi,
                        debugBuild,
                        optimLevel,
                        ndkMode,
                        supportMode,
                        useAndroidX,
                        abiFilters,
                        mLogger);
        processor.build(mProcessExecutor, processOutputHandler);
    }

    /**
     * Obtains the "created by" tag for the packaged manifest.
     *
     * @return the "created by" tag or {@code null} if no tag was defined
     */
    @Nullable
    public String getCreatedBy() {
        return mCreatedBy;
    }
}
