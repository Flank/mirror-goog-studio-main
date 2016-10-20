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

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_DEX;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_XML;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.manifmerger.ManifestMerger2.Invoker;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.files.NativeLibraryAbiPredicate;
import com.android.builder.files.RelativeFile;
import com.android.builder.files.RelativeFiles;
import com.android.builder.internal.SymbolLoader;
import com.android.builder.internal.SymbolWriter;
import com.android.builder.internal.TestManifestGenerator;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.compiler.AidlProcessor;
import com.android.builder.internal.compiler.LeafFolderGatherer;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.internal.compiler.RenderScriptProcessor;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.builder.internal.compiler.SourceSearcher;
import com.android.builder.internal.packaging.OldPackager;
import com.android.builder.model.AndroidBundle;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.packaging.NativeLibrariesPackagingMode;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SealedPackageException;
import com.android.builder.packaging.SigningException;
import com.android.builder.packaging.ZipAbortException;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.signing.SignedJarApkCreator;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.ide.common.process.CachedProcessOutputHandler;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.JavaProcessInfo;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfo;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.io.FileWrapper;
import com.android.io.StreamException;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.ManifestSystemProperty;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.PlaceholderHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.LineCollector;
import com.android.xml.AndroidManifest;
import com.google.common.base.Charsets;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This is the main builder class. It is given all the data to process the build (such as
 * {@link DefaultProductFlavor}s, {@link DefaultBuildType} and dependencies) and use them when doing specific
 * build steps.
 *
 * To use:
 * create a builder with {@link #AndroidBuilder(String, String, ProcessExecutor, JavaProcessExecutor, ErrorReporter, ILogger, boolean)}
 *
 * then build steps can be done with
 * {@link #mergeManifestsForApplication(File, List, List, String, int, String, String, String, Integer, String, String, String, ManifestMerger2.MergeType, Map, List, File)}
 * {@link #mergeManifestsForTestVariant(String, String, String, String, String, Boolean, Boolean, String, File, List, Map, File, File)}
 * {@link #processResources(Aapt, AaptPackageConfig.Builder, boolean)}
 * {@link #compileAllAidlFiles(List, File, File, Collection, List, DependencyFileProcessor, ProcessOutputHandler)}
 * {@link #getDexByteCodeConverter()}
 * {@link #oldPackageApk(String, Set, Collection, Collection, File, Set, boolean, SigningConfig, File, int, Predicate)}
 *
 * Java compilation is not handled but the builder provides the boot classpath with
 * {@link #getBootClasspath(boolean)}.
 */
public class AndroidBuilder {

    /**
     * Minimal supported version of build tools.
     */
    public static final Revision MIN_BUILD_TOOLS_REV = new Revision(24, 0, 2);

    /**
     * API level for split APKs.
     */
    private static final int API_LEVEL_SPLIT_APK = 21;

    @NonNull
    private final String mProjectId;
    @NonNull
    private final ILogger mLogger;

    @NonNull
    private final ProcessExecutor mProcessExecutor;
    @NonNull
    private final JavaProcessExecutor mJavaProcessExecutor;
    @NonNull
    private final ErrorReporter mErrorReporter;

    private final boolean mVerboseExec;

    @Nullable private String mCreatedBy;


    private SdkInfo mSdkInfo;
    private TargetInfo mTargetInfo;

    private List<File> mBootClasspathFiltered;
    private List<File> mBootClasspathAll;
    @NonNull
    private List<LibraryRequest> mLibraryRequests = ImmutableList.of();

    private DexByteCodeConverter mDexByteCodeConverter = null;

    /**
     * Creates an AndroidBuilder.
     * <p>
     * <var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param createdBy the createdBy String for the apk manifest.
     * @param logger the Logger
     * @param verboseExec whether external tools are launched in verbose mode
     */
    public AndroidBuilder(
            @NonNull String projectId,
            @Nullable String createdBy,
            @NonNull ProcessExecutor processExecutor,
            @NonNull JavaProcessExecutor javaProcessExecutor,
            @NonNull ErrorReporter errorReporter,
            @NonNull ILogger logger,
            boolean verboseExec) {
        mProjectId = checkNotNull(projectId);
        mCreatedBy = createdBy;
        mProcessExecutor = checkNotNull(processExecutor);
        mJavaProcessExecutor = checkNotNull(javaProcessExecutor);
        mErrorReporter = checkNotNull(errorReporter);
        mLogger = checkNotNull(logger);
        mVerboseExec = verboseExec;
    }

    /**
     * Sets the SdkInfo and the targetInfo on the builder. This is required to actually
     * build (some of the steps).
     *
     * @see com.android.builder.sdk.SdkLoader
     */
    public void setTargetInfo(@NonNull TargetInfo targetInfo) {
        mTargetInfo = targetInfo;
        mDexByteCodeConverter = new DexByteCodeConverter(
                getLogger(), mTargetInfo, mJavaProcessExecutor, mVerboseExec);

        if (mTargetInfo.getBuildTools().getRevision().compareTo(MIN_BUILD_TOOLS_REV) < 0) {
            throw new IllegalArgumentException(String.format(
                    "The SDK Build Tools revision (%1$s) is too low for project '%2$s'. "
                            + "Minimum required is %3$s",
                    mTargetInfo.getBuildTools().getRevision(), mProjectId, MIN_BUILD_TOOLS_REV));
        }
    }

    public void setSdkInfo(@NonNull SdkInfo sdkInfo) {
        mSdkInfo = sdkInfo;
    }

    public void setLibraryRequests(@NonNull Collection<LibraryRequest> libraryRequests) {
        mLibraryRequests = ImmutableList.copyOf(libraryRequests);
    }

    /**
     * Returns the SdkInfo, if set.
     */
    @Nullable
    public SdkInfo getSdkInfo() {
        return mSdkInfo;
    }

    /**
     * Returns the TargetInfo, if set.
     */
    @Nullable
    public TargetInfo getTargetInfo() {
        return mTargetInfo;
    }

    @NonNull
    public ILogger getLogger() {
        return mLogger;
    }

    @NonNull
    public ErrorReporter getErrorReporter() {
        return mErrorReporter;
    }

    /**
     * Returns the compilation target, if set.
     */
    @Nullable
    public IAndroidTarget getTarget() {
        checkState(mTargetInfo != null,
                "Cannot call getTarget() before setTargetInfo() is called.");
        return mTargetInfo.getTarget();
    }

    /**
     * Returns whether the compilation target is a preview.
     */
    public boolean isPreviewTarget() {
        checkState(mTargetInfo != null,
                "Cannot call isTargetAPreview() before setTargetInfo() is called.");
        return mTargetInfo.getTarget().getVersion().isPreview();
    }

    public String getTargetCodename() {
        checkState(mTargetInfo != null,
                "Cannot call getTargetCodename() before setTargetInfo() is called.");
        return mTargetInfo.getTarget().getVersion().getCodename();
    }

    /**
     * Helper method to get the boot classpath to be used during compilation.
     *
     * @param includeOptionalLibraries if true, optional libraries are included even if not
     *                                 required by the project setup.
     */
    @NonNull
    public List<File> getBootClasspath(boolean includeOptionalLibraries) {
        if (includeOptionalLibraries) {
            return computeFullBootClasspath();
        }

        return computeFilteredBootClasspath();
    }

    private List<File> computeFilteredBootClasspath() {
        // computes and caches the filtered boot classpath.
        // Changes here should be applied to #computeFullClasspath()

        if (mBootClasspathFiltered == null) {
            checkState(mTargetInfo != null,
                    "Cannot call getBootClasspath() before setTargetInfo() is called.");

            mBootClasspathFiltered = BootClasspathBuilder.computeFilteredClasspath(
                    mTargetInfo.getTarget(),
                    mLibraryRequests,
                    mErrorReporter,
                    mSdkInfo.getAnnotationsJar());
        }

        return mBootClasspathFiltered;
    }

    @NonNull
    private List<File> computeFullBootClasspath() {
        // computes and caches the full boot classpath.
        // Changes here should be applied to #computeFilteredClasspath()

        if (mBootClasspathAll == null) {
            checkState(mTargetInfo != null,
                    "Cannot call getBootClasspath() before setTargetInfo() is called.");

            mBootClasspathAll = BootClasspathBuilder.computeFullBootClasspath(
                    mTargetInfo.getTarget(),
                    mSdkInfo.getAnnotationsJar());
        }

        return mBootClasspathAll;
    }

    /**
     * Helper method to get the boot classpath to be used during compilation.
     *
     * @param includeOptionalLibraries if true, optional libraries are included even if not
     *                                 required by the project setup.
     */
    @NonNull
    public List<String> getBootClasspathAsStrings(boolean includeOptionalLibraries) {
        List<File> classpath = getBootClasspath(includeOptionalLibraries);

        // convert to Strings.
        List<String> results = Lists.newArrayListWithCapacity(classpath.size());
        for (File f : classpath) {
            results.add(f.getAbsolutePath());
        }

        return results;
    }

    /**
     * Returns the jar file for the renderscript mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the jar file, or null.
     *
     * @see #setTargetInfo(TargetInfo)
     */
    @Nullable
    public File getRenderScriptSupportJar() {
        if (mTargetInfo != null) {
            return RenderScriptProcessor.getSupportJar(
                    mTargetInfo.getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
    }

    /**
     * Returns the compile classpath for this config. If the config tests a library, this
     * will include the classpath of the tested config.
     *
     * If the SDK was loaded, this may include the renderscript support jar.
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    public Set<File> getCompileClasspath(@NonNull VariantConfiguration<?,?,?> variantConfiguration) {
        Set<File> compileClasspath = variantConfiguration.getCompileClasspath();

        if (variantConfiguration.getRenderscriptSupportModeEnabled()) {
            File renderScriptSupportJar = getRenderScriptSupportJar();

            Set<File> fullJars = Sets.newLinkedHashSetWithExpectedSize(compileClasspath.size() + 1);
            fullJars.addAll(compileClasspath);
            if (renderScriptSupportJar != null) {
                fullJars.add(renderScriptSupportJar);
            }
            compileClasspath = fullJars;
        }

        return compileClasspath;
    }

    /**
     * Returns the list of packaged jars for this config. If the config tests a library, this
     * will include the jars of the tested config
     *
     * If the SDK was loaded, this may include the renderscript support jar.
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getAllPackagedJars(@NonNull VariantConfiguration<?,?,?> variantConfiguration) {
        Set<File> packagedJars = Sets.newHashSet(variantConfiguration.getAllPackagedJars());

        if (variantConfiguration.getRenderscriptSupportModeEnabled()) {
            File renderScriptSupportJar = getRenderScriptSupportJar();

            if (renderScriptSupportJar != null) {
                packagedJars.add(renderScriptSupportJar);
            }
        }

        return packagedJars;
    }

    /**
     * Returns the list of packaged jars for this config. If the config tests a library, this
     * will include the jars of the tested config
     *
     * If the SDK was loaded, this may include the renderscript support jar.
     *
     * @return a non null, but possibly empty list.
     */
    @NonNull
    public Set<File> getAdditionalPackagedJars(@NonNull VariantConfiguration<?,?,?> variantConfiguration) {

        if (variantConfiguration.getRenderscriptSupportModeEnabled()) {
            File renderScriptSupportJar = getRenderScriptSupportJar();

            if (renderScriptSupportJar != null) {
                return ImmutableSet.of(renderScriptSupportJar);
            }
        }

        return ImmutableSet.of();
    }

    /**
     * Returns the native lib folder for the renderscript mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the folder, or null.
     *
     * @see #setTargetInfo(TargetInfo)
     */
    @Nullable
    public File getSupportNativeLibFolder() {
        if (mTargetInfo != null) {
            return RenderScriptProcessor.getSupportNativeLibFolder(
                    mTargetInfo.getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
    }

    /**
     * Returns the BLAS lib folder for renderscript support mode.
     *
     * This may return null if the SDK has not been loaded yet.
     *
     * @return the folder, or null.
     *
     * @see #setTargetInfo(TargetInfo)
     */
    @Nullable
    public File getSupportBlasLibFolder() {
        if (mTargetInfo != null) {
            return RenderScriptProcessor.getSupportBlasLibFolder(
                    mTargetInfo.getBuildTools().getLocation().getAbsolutePath());
        }

        return null;
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

    /**
     * Invoke the Manifest Merger version 2.
     */
    public void mergeManifestsForApplication(
            @NonNull File mainManifest,
            @NonNull List<File> manifestOverlays,
            @NonNull List<? extends AndroidBundle> bundles,
            String packageOverride,
            int versionCode,
            String versionName,
            @Nullable String minSdkVersion,
            @Nullable String targetSdkVersion,
            @Nullable Integer maxSdkVersion,
            @NonNull String outManifestLocation,
            @Nullable String outAaptSafeManifestLocation,
            @Nullable String outInstantRunManifestLocation,
            ManifestMerger2.MergeType mergeType,
            Map<String, Object> placeHolders,
            @NonNull List<Invoker.Feature> optionalFeatures,
            @Nullable File reportFile) {

        try {

            Invoker manifestMergerInvoker =
                    ManifestMerger2.newMerger(mainManifest, mLogger, mergeType)
                    .setPlaceHolderValues(placeHolders)
                    .addFlavorAndBuildTypeManifests(
                            manifestOverlays.toArray(new File[manifestOverlays.size()]))
                    .addAndroidBundleManifests(bundles)
                    .withFeatures(optionalFeatures.toArray(
                            new Invoker.Feature[optionalFeatures.size()]))
                    .setMergeReportFile(reportFile);

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
            mLogger.verbose("Merging result: %1$s", mergingReport.getResult());
            switch (mergingReport.getResult()) {
                case WARNING:
                    mergingReport.log(mLogger);
                    // fall through since these are just warnings.
                case SUCCESS:
                    String xmlDocument = mergingReport.getMergedDocument(
                            MergingReport.MergedManifestKind.MERGED);
                    String annotatedDocument = mergingReport.getMergedDocument(
                            MergingReport.MergedManifestKind.BLAME);
                    if (annotatedDocument != null) {
                        mLogger.verbose(annotatedDocument);
                    }
                    save(xmlDocument, new File(outManifestLocation));
                    mLogger.verbose("Merged manifest saved to " + outManifestLocation);

                    if (outAaptSafeManifestLocation != null) {
                        save(mergingReport.getMergedDocument(MergingReport.MergedManifestKind.AAPT_SAFE),
                                new File(outAaptSafeManifestLocation));
                    }

                    if (outInstantRunManifestLocation != null) {
                        String instantRunMergedManifest = mergingReport.getMergedDocument(
                                MergingReport.MergedManifestKind.INSTANT_RUN);
                        if (instantRunMergedManifest != null) {
                            save(instantRunMergedManifest, new File(outInstantRunManifestLocation));
                        }
                    }
                    break;
                case ERROR:
                    mergingReport.log(mLogger);
                    throw new RuntimeException(mergingReport.getReportString());
                default:
                    throw new RuntimeException("Unhandled result type : "
                            + mergingReport.getResult());
            }
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
            Files.write(xmlDocument, out, Charsets.UTF_8);
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
     * @param libraries the library dependency graph
     * @param manifestPlaceholders used placeholders in the manifest
     * @param outManifest the output location for the merged manifest
     * @param tmpDir temporary dir used for processing
     *
     * @see VariantConfiguration#getApplicationId()
     * @see VariantConfiguration#getTestedConfig()
     * @see VariantConfiguration#getMinSdkVersion()
     * @see VariantConfiguration#getTestedApplicationId()
     * @see VariantConfiguration#getInstrumentationRunner()
     * @see VariantConfiguration#getHandleProfiling()
     * @see VariantConfiguration#getFunctionalTest()
     * @see VariantConfiguration#getTestLabel()
     * @see VariantConfiguration#getFlatCompileAndroidLibraries() ()
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
            @NonNull List<? extends AndroidLibrary> libraries,
            @NonNull Map<String, Object> manifestPlaceholders,
            @NonNull File outManifest,
            @NonNull File tmpDir) throws IOException {
        checkNotNull(testApplicationId, "testApplicationId cannot be null.");
        checkNotNull(testedApplicationId, "testedApplicationId cannot be null.");
        checkNotNull(instrumentationRunner, "instrumentationRunner cannot be null.");
        checkNotNull(handleProfiling, "handleProfiling cannot be null.");
        checkNotNull(functionalTest, "functionalTest cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outManifest, "outManifestLocation cannot be null.");

        // These temp files are only need in the middle of processing manifests; delete
        // them when they're done. We're not relying on File#deleteOnExit for this
        // since in the Gradle daemon for example that would leave the files around much
        // longer than we want.
        File tempFile1 = null;
        File tempFile2 = null;
        try {
            FileUtils.mkdirs(tmpDir);
            File generatedTestManifest = libraries.isEmpty() && testManifestFile == null
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
                if (libraries.isEmpty()) {
                    handleMergingResult(mergingReport, outManifest);
                } else {
                    tempFile2 = File.createTempFile("manifestMerger", ".xml", tmpDir);
                    handleMergingResult(mergingReport, tempFile2);
                    generatedTestManifest = tempFile2;
                }
            }

            if (!libraries.isEmpty()) {
                MergingReport mergingReport = ManifestMerger2.newMerger(
                        generatedTestManifest, mLogger, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                        .setOverride(ManifestSystemProperty.PACKAGE, testApplicationId)
                        .addAndroidBundleManifests(libraries)
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
                    Files.write(finalMergedDocument, outFile, Charsets.UTF_8);
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

    /**
     * Process the resources and generate R.java and/or the packaged resources.
     *
     * @param aapt the interface to the {@code aapt} tool
     * @param aaptConfigBuilder aapt command invocation parameters; this will receive some additional
     * data (build tools, Android target and logger) and will be used to request package invocation
     * in {@code aapt} (see {@link Aapt#link(AaptPackageConfig)})
     * @param enforceUniquePackageName if {@code true} method will fail if some libraries share the
     * same package name
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws ProcessException failed
     */
    public void processResources(
            @NonNull Aapt aapt,
            @NonNull AaptPackageConfig.Builder aaptConfigBuilder,
            boolean enforceUniquePackageName)
            throws IOException, InterruptedException, ProcessException {

        checkState(mTargetInfo != null,
                "Cannot call processResources() before setTargetInfo() is called.");

        aaptConfigBuilder.setBuildToolInfo(mTargetInfo.getBuildTools());
        aaptConfigBuilder.setAndroidTarget(mTargetInfo.getTarget());
        aaptConfigBuilder.setLogger(mLogger);

        AaptPackageConfig aaptConfig = aaptConfigBuilder.build();

        try {
            aapt.link(aaptConfig).get();
        } catch (Exception e) {
            throw new ProcessException("Failed to execute aapt", e);
        }


        // If the project has libraries, R needs to be created for each library.
        if (aaptConfig.getSourceOutputDir() != null
                && !aaptConfig.getLibraries().isEmpty()) {
            SymbolLoader fullSymbolValues = null;

            // First pass processing the libraries, collecting them by packageName,
            // and ignoring the ones that have the same package name as the application
            // (since that R class was already created).
            String appPackageName = aaptConfig.getCustomPackageForR();
            if (appPackageName == null) {
                File manifestFile = aaptConfig.getManifestFile();
                if (manifestFile != null) {
                    try {
                        appPackageName = AndroidManifest.getPackage(new FileWrapper(manifestFile));
                    } catch (StreamException e) {
                        // we were not able to get the content of the file, keep the null value
                    }
                }
            }

            // list of all the symbol loaders per package names.
            Multimap<String, SymbolLoader> libMap = ArrayListMultimap.create();

            for (AndroidLibrary lib : aaptConfig.getLibraries()) {
                if (aaptConfig.getVariantType() != VariantType.INSTANTAPP && lib.isProvided()) {
                    continue;
                }

                if (Strings.isNullOrEmpty(appPackageName)) {
                    continue;
                }

                String packageName;
                try {
                    packageName = AndroidManifest.getPackage(new FileWrapper(lib.getManifest()));
                } catch (StreamException e) {
                    // we were not able to get the content of the file,
                    packageName = null;
                }

                if (appPackageName.equals(packageName)) {
                    if (enforceUniquePackageName) {
                        String msg = String.format(
                                "Error: A library uses the same package as this project: %s",
                                packageName);
                        throw new RuntimeException(msg);
                    }

                    // ignore libraries that have the same package name as the app
                    continue;
                }

                File rFile = lib.getSymbolFile();
                // if the library has no resource, this file won't exist.
                if (rFile.isFile()) {

                    // load the full values if that's not already been done.
                    // Doing it lazily allow us to support the case where there's no
                    // resources anywhere.
                    if (fullSymbolValues == null) {
                        fullSymbolValues = new SymbolLoader(
                                new File(aaptConfig.getSymbolOutputDir(), "R.txt"), mLogger);
                        fullSymbolValues.load();
                    }

                    SymbolLoader libSymbols = new SymbolLoader(rFile, mLogger);
                    libSymbols.load();


                    // store these symbols by associating them with the package name.
                    libMap.put(packageName, libSymbols);
                }
            }

            // now loop on all the package name, merge all the symbols to write, and write them
            for (String packageName : libMap.keySet()) {
                Collection<SymbolLoader> symbols = libMap.get(packageName);

                if (enforceUniquePackageName && symbols.size() > 1) {
                    String msg = String.format(
                            "Error: more than one library with package name '%s'", packageName);
                    throw new RuntimeException(msg);
                }

                boolean generateFinalIds = true;
                if (aaptConfig.getVariantType() == VariantType.LIBRARY) {
                    generateFinalIds = false;
                } else if (aaptConfig.getVariantType() == VariantType.ATOM
                        && aaptConfig.getBaseFeature() != null) {
                    generateFinalIds = false;
                }
                //noinspection ConstantConditions
                SymbolWriter writer =
                        new SymbolWriter(
                                aaptConfig.getSourceOutputDir().getAbsolutePath(),
                                packageName,
                                fullSymbolValues,
                                generateFinalIds);
                for (SymbolLoader symbolLoader : symbols) {
                    writer.addSymbolsToWrite(symbolLoader);
                }
                writer.write();
            }
        }
    }

    public void generateApkData(
            @NonNull File apkFile,
            @NonNull File outResFolder,
            @NonNull String mainPkgName,
            @NonNull String resName) throws ProcessException, IOException {

        // need to run aapt to get apk information
        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String aapt = buildToolInfo.getPath(BuildToolInfo.PathId.AAPT);
        if (aapt == null) {
            throw new IllegalStateException(
                    "Unable to get aapt location from Build Tools " + buildToolInfo.getRevision());
        }

        ApkInfoParser parser = new ApkInfoParser(new File(aapt), mProcessExecutor);
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

        Files.write(content,
                new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML),
                Charsets.UTF_8);
    }

    public void generateUnbundledWearApkData(
            @NonNull File outResFolder,
            @NonNull String mainPkgName) throws ProcessException, IOException {

        String content = String.format(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<wearableApp package=\"%1$s\">\n" +
                        "    <unbundled />\n" +
                        "</wearableApp>",
                mainPkgName);

        // xml folder
        File resXmlFile = new File(outResFolder, FD_RES_XML);
        FileUtils.mkdirs(resXmlFile);

        Files.write(
                content,
                new File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML),
                Charsets.UTF_8);
    }

    public static void generateApkDataEntryInManifest(
            int minSdkVersion,
            int targetSdkVersion,
            @NonNull File manifestFile)
            throws InterruptedException, LoggedErrorException, IOException {

        StringBuilder content = new StringBuilder();
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                .append("<manifest package=\"\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
                .append("            <uses-sdk android:minSdkVersion=\"")
                .append(minSdkVersion).append("\"");
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

        Files.write(content, manifestFile, Charsets.UTF_8);
    }

    /**
     * Compiles all the aidl files found in the given source folders.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param packagedOutputDir the output dir for the AIDL files that will be packaged in an aar
     * @param packageWhiteList a list of AIDL FQCNs that are not parcelable that should also be
     *                         packaged in an aar
     * @param importFolders import folders
     * @param dependencyFileProcessor the dependencyFileProcessor to record the dependencies
     *                                of the compilation.
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws LoggedErrorException failed
     */
    public void compileAllAidlFiles(@NonNull List<File> sourceFolders,
                                    @NonNull File sourceOutputDir,
                                    @Nullable File packagedOutputDir,
                                    @Nullable Collection<String> packageWhiteList,
                                    @NonNull List<File> importFolders,
                                    @Nullable DependencyFileProcessor dependencyFileProcessor,
                                    @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, LoggedErrorException, ProcessException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAllAidlFiles() before setTargetInfo() is called.");

        IAndroidTarget target = mTargetInfo.getTarget();
        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String aidl = buildToolInfo.getPath(BuildToolInfo.PathId.AIDL);
        if (aidl == null || !new File(aidl).isFile()) {
            throw new IllegalStateException("aidl is missing");
        }

        List<File> fullImportList = Lists.newArrayListWithCapacity(
                sourceFolders.size() + importFolders.size());
        fullImportList.addAll(sourceFolders);
        fullImportList.addAll(importFolders);

        AidlProcessor processor = new AidlProcessor(
                aidl,
                target.getPath(IAndroidTarget.ANDROID_AIDL),
                fullImportList,
                sourceOutputDir,
                packagedOutputDir,
                packageWhiteList,
                dependencyFileProcessor != null ?
                        dependencyFileProcessor : DependencyFileProcessor.NO_OP,
                mProcessExecutor,
                processOutputHandler);

        SourceSearcher searcher = new SourceSearcher(sourceFolders, "aidl");
        searcher.setUseExecutor(true);
        searcher.search(processor);
    }

    /**
     * Compiles the given aidl file.
     *
     * @param aidlFile the AIDL file to compile
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param importFolders all the import folders, including the source folders.
     * @param dependencyFileProcessor the dependencyFileProcessor to record the dependencies
     *                                of the compilation.
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws LoggedErrorException failed
     */
    public void compileAidlFile(@NonNull File sourceFolder,
                                @NonNull File aidlFile,
                                @NonNull File sourceOutputDir,
                                @Nullable File packagedOutputDir,
                                @Nullable Collection<String> packageWhitelist,
                                @NonNull List<File> importFolders,
                                @Nullable DependencyFileProcessor dependencyFileProcessor,
                                @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, LoggedErrorException, ProcessException {
        checkNotNull(aidlFile, "aidlFile cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAidlFile() before setTargetInfo() is called.");

        IAndroidTarget target = mTargetInfo.getTarget();
        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String aidl = buildToolInfo.getPath(BuildToolInfo.PathId.AIDL);
        if (aidl == null || !new File(aidl).isFile()) {
            throw new IllegalStateException("aidl is missing");
        }

        AidlProcessor processor = new AidlProcessor(
                aidl,
                target.getPath(IAndroidTarget.ANDROID_AIDL),
                importFolders,
                sourceOutputDir,
                packagedOutputDir,
                packageWhitelist,
                dependencyFileProcessor != null ?
                        dependencyFileProcessor : DependencyFileProcessor.NO_OP,
                mProcessExecutor,
                processOutputHandler);

        processor.processFile(sourceFolder, aidlFile);
    }

    /**
     * Compiles all the shader files found in the given source folders.
     *
     * @param sourceFolder the source folder with the merged shaders
     * @param outputDir the output dir in which to generate the output
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws LoggedErrorException failed
     */
    public void compileAllShaderFiles(
            @NonNull File sourceFolder,
            @NonNull File outputDir,
            @NonNull List<String> defaultArgs,
            @NonNull Map<String, List<String>> scopedArgs,
            @Nullable File nkdLocation,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, LoggedErrorException, ProcessException {
        checkNotNull(sourceFolder, "sourceFolder cannot be null.");
        checkNotNull(outputDir, "outputDir cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAllShaderFiles() before setTargetInfo() is called.");

        ShaderProcessor processor = new ShaderProcessor(
                nkdLocation,
                sourceFolder,
                outputDir,
                defaultArgs,
                scopedArgs,
                mProcessExecutor,
                processOutputHandler);

        SourceSearcher searcher = new SourceSearcher(
                sourceFolder,
                ShaderProcessor.EXT_VERT,
                ShaderProcessor.EXT_TESC,
                ShaderProcessor.EXT_TESE,
                ShaderProcessor.EXT_GEOM,
                ShaderProcessor.EXT_FRAG,
                ShaderProcessor.EXT_COMP);
        searcher.setUseExecutor(true);
        searcher.search(processor);
    }

    /**
     * Compiles the given aidl file.
     *
     * @param sourceFolder the source folder containing the file
     * @param shaderFile the shader file to compile
     * @param outputDir the output dir
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws LoggedErrorException failed
     */
    public void compileShaderFile(
            @NonNull File sourceFolder,
            @NonNull File shaderFile,
            @NonNull File outputDir,
            @NonNull List<String> defaultArgs,
            @NonNull Map<String, List<String>> scopedArgs,
            @Nullable File nkdLocation,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, LoggedErrorException, ProcessException {
        checkNotNull(sourceFolder, "sourceFolder cannot be null.");
        checkNotNull(shaderFile, "aidlFile cannot be null.");
        checkNotNull(outputDir, "outputDir cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAidlFile() before setTargetInfo() is called.");

        ShaderProcessor processor = new ShaderProcessor(
                nkdLocation,
                sourceFolder,
                outputDir,
                defaultArgs,
                scopedArgs,
                mProcessExecutor,
                processOutputHandler);
        processor.processFile(sourceFolder, shaderFile);
    }

    /**
     * Compiles all the renderscript files found in the given source folders.
     *
     * Right now this is the only way to compile them as the renderscript compiler requires all
     * renderscript files to be passed for all compilation.
     *
     * Therefore whenever a renderscript file or header changes, all must be recompiled.
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
     * @param abiFilters ABI filters in case of support mode
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws LoggedErrorException failed
     */
    public void compileAllRenderscriptFiles(
            @NonNull List<File> sourceFolders,
            @NonNull List<File> importFolders,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            int targetApi,
            boolean debugBuild,
            int optimLevel,
            boolean ndkMode,
            boolean supportMode,
            @Nullable Set<String> abiFilters,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws InterruptedException, ProcessException, LoggedErrorException, IOException {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(resOutputDir, "resOutputDir cannot be null.");
        checkState(mTargetInfo != null,
                "Cannot call compileAllRenderscriptFiles() before setTargetInfo() is called.");

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();

        String renderscript = buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException("llvm-rs-cc is missing");
        }

        RenderScriptProcessor processor = new RenderScriptProcessor(
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
                abiFilters,
                mLogger);
        processor.build(mProcessExecutor, processOutputHandler);
    }

    /**
     * Computes and returns the leaf folders based on a given file extension.
     *
     * This looks through all the given root import folders, and recursively search for leaf
     * folders containing files matching the given extensions. All the leaf folders are gathered
     * and returned in the list.
     *
     * @param extension the extension to search for.
     * @param importFolders an array of list of root folders.
     * @return a list of leaf folder, never null.
     */
    @NonNull
    @SafeVarargs
    public static List<File> getLeafFolders(@NonNull String extension, List<File>... importFolders) {
        List<File> results = Lists.newArrayList();

        if (importFolders != null) {
            for (List<File> folders : importFolders) {
                SourceSearcher searcher = new SourceSearcher(folders, extension);
                searcher.setUseExecutor(false);
                LeafFolderGatherer processor = new LeafFolderGatherer();
                try {
                    searcher.search(processor);
                } catch (InterruptedException
                        | IOException
                        | ProcessException
                        | LoggedErrorException e) {
                    // wont happen as we're not using the executor, and our processor
                    // doesn't throw those.
                }

                results.addAll(processor.getFolders());
            }
        }

        return results;
    }

    @NonNull
    public DexByteCodeConverter getDexByteCodeConverter() {
        checkState(mDexByteCodeConverter != null,
                "Cannot call getDexByteCodeConverter() before setTargetInfo() is called.");
        return mDexByteCodeConverter;
    }

    /**
     * Converts the bytecode to Dalvik format
     * @param inputs the input files
     * @param outDexFolder the location of the output folder
     * @param dexOptions dex options
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws ProcessException failed
     */
    public void convertByteCode(
            @NonNull Collection<File> inputs,
            @NonNull File outDexFolder,
            boolean multidex,
            @Nullable File mainDexList,
            @NonNull DexOptions dexOptions,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, ProcessException {
        getDexByteCodeConverter().convertByteCode(inputs,
                outDexFolder,
                multidex,
                mainDexList,
                dexOptions,
                processOutputHandler);
    }

    public enum MainDexListOption {
        DISABLE_ANNOTATION_RESOLUTION_WORKAROUND;
    }

    public Set<String> createMainDexList(
            @NonNull File allClassesJarFile,
            @NonNull File jarOfRoots,
            @NonNull EnumSet<MainDexListOption> options) throws ProcessException {

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        String dx = buildToolInfo.getPath(BuildToolInfo.PathId.DX_JAR);
        if (dx == null || !new File(dx).isFile()) {
            throw new IllegalStateException("dx.jar is missing");
        }

        builder.setClasspath(dx);
        builder.setMain("com.android.multidex.ClassReferenceListBuilder");

        if (options.contains(MainDexListOption.DISABLE_ANNOTATION_RESOLUTION_WORKAROUND)) {
            builder.addArgs("--disable-annotation-resolution-workaround");
        }

        builder.addArgs(jarOfRoots.getAbsolutePath());
        builder.addArgs(allClassesJarFile.getAbsolutePath());

        CachedProcessOutputHandler processOutputHandler = new CachedProcessOutputHandler();

        mJavaProcessExecutor.execute(builder.createJavaProcess(), processOutputHandler)
                .rethrowFailure()
                .assertNormalExitValue();

        LineCollector lineCollector = new LineCollector();
        processOutputHandler.getProcessOutput().processStandardOutputLines(lineCollector);
        return ImmutableSet.copyOf(lineCollector.getResult());
    }

    /**
     * Converts the bytecode to Dalvik format, using the {@link PreDexCache} layer.
     *
     * @param inputFile the input file
     * @param outFile the output file or folder if multi-dex is enabled
     * @param multiDex whether multidex is enabled
     * @param dexOptions dex options
     * @param processOutputHandler output handler to use
     * @throws IOException failed
     * @throws InterruptedException failed
     * @throws ProcessException failed
     */
    public void preDexLibrary(
            @NonNull File inputFile,
            @NonNull File outFile,
            boolean multiDex,
            @NonNull DexOptions dexOptions,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws IOException, InterruptedException, ProcessException {
        checkState(mTargetInfo != null,
                "Cannot call preDexLibrary() before setTargetInfo() is called.");

        getLogger().verbose("AndroidBuilder::preDexLibrary %1$s", inputFile.getAbsolutePath());
        if (inputFile.isFile()) {
            PreDexCache.getCache().preDexLibrary(
                    this,
                    inputFile,
                    outFile,
                    multiDex,
                    dexOptions,
                    processOutputHandler);
        } else {
            preDexLibraryNoCache(
                    inputFile, outFile, multiDex, dexOptions, processOutputHandler);
        }
    }

    /**
     * Converts the bytecode to Dalvik format, ignoring the {@link PreDexCache} layer.
     *
     * @param inputFile the input file
     * @param outFile the output file or folder if multi-dex is enabled.
     * @param multiDex whether multidex is enabled.
     * @param dexOptions the dex options
     * @return the list of generated files.
     * @throws ProcessException failed
     */
    @NonNull
    public ImmutableList<File> preDexLibraryNoCache(
            @NonNull File inputFile,
            @NonNull File outFile,
            boolean multiDex,
            @NonNull DexOptions dexOptions,
            @NonNull ProcessOutputHandler processOutputHandler)
            throws ProcessException, IOException, InterruptedException {
        checkNotNull(inputFile, "inputFile cannot be null.");
        checkNotNull(outFile, "outFile cannot be null.");
        checkNotNull(dexOptions, "dexOptions cannot be null.");
        getLogger().verbose("AndroidBuilder::preDexLibraryNoCache %1$s", inputFile.getAbsolutePath());

        try {
            if (!checkLibraryClassesJar(inputFile)) {
                return ImmutableList.of();
            }
        } catch(IOException e) {
            throw new RuntimeException("Exception while checking library jar", e);
        }
        DexProcessBuilder builder = new DexProcessBuilder(outFile);

        builder.setVerbose(mVerboseExec)
                .setMultiDex(multiDex)
                .addInput(inputFile);

        getDexByteCodeConverter().runDexer(builder, dexOptions, processOutputHandler);

        if (multiDex) {
            File[] files = outFile.listFiles((file, name) -> {
                return name.endsWith(DOT_DEX);
            });

            if (files == null || files.length == 0) {
                throw new RuntimeException("No dex files created at " + outFile.getAbsolutePath());
            }

            return ImmutableList.copyOf(files);
        } else {
            return ImmutableList.of(outFile);
        }
    }

    /**
     * Returns true if the library (jar or folder) contains class files, false otherwise.
     */
    private static boolean checkLibraryClassesJar(@NonNull File input) throws IOException {

        if (!input.exists()) {
            return false;
        }

        if (input.isDirectory()) {
            return checkFolder(input);
        }

        try (ZipFile zipFile = new ZipFile(input)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(DOT_CLASS) || name.endsWith(DOT_DEX)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns true if this folder or one of its subfolder contains a class file, false otherwise.
     */
    private static boolean checkFolder(@NonNull File folder) {
        File[] subFolders = folder.listFiles();
        if (subFolders != null) {
            for (File childFolder : subFolders) {
                if (childFolder.isFile()) {
                    String name = childFolder.getName();
                    if (name.endsWith(DOT_CLASS) || name.endsWith(DOT_DEX)) {
                        return true;
                    }
                }
                if (childFolder.isDirectory()) {
                    // if childFolder returns false, continue search otherwise return success.
                    if (checkFolder(childFolder)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void createJacocoReportWithJackReporter(
            @NonNull File coverageFile,
            @NonNull File reportDir,
            @NonNull List<File> sourceDirs,
            @NonNull String reportName,
            @NonNull File metadataFile) throws ProcessException {

        checkNotNull(coverageFile, "coverageFile cannot be null.");
        checkNotNull(reportDir, "reportDir cannot be null.");
        checkNotNull(sourceDirs, "sourceDir cannot be null.");
        checkNotNull(reportName, "reportName cannot be null.");
        checkNotNull(metadataFile, "metadataFile cannot be null.");

        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        BuildToolInfo buildToolInfo = mTargetInfo.getBuildTools();
        String jackLocation = System.getenv("USE_JACK_LOCATION");
        String reporter = jackLocation != null
                ? jackLocation + File.separator + SdkConstants.FN_JACK_JACOCO_REPORTER
                : buildToolInfo.getPath(BuildToolInfo.PathId.JACK_JACOCO_REPORTER);
        if (reporter == null || !new File(reporter).isFile()) {
            throw new IllegalStateException("jack-jacoco-reporter.jar is missing: " + reporter);
        }

        builder.setClasspath(reporter);
        builder.setMain("com.android.jack.tools.jacoco.Main");

        builder.addArgs("--coverage-file", coverageFile.getAbsolutePath());
        builder.addArgs("--metadata-file", metadataFile.getAbsolutePath());
        builder.addArgs("--report-dir", reportDir.getAbsolutePath());
        builder.addArgs("--report-name", reportName);
        for (File sourceDir : sourceDirs) {
            builder.addArgs("--source-dir", sourceDir.getAbsolutePath());
        }
        JavaProcessInfo javaProcessInfo = builder.createJavaProcess();
        ProcessResult result = mJavaProcessExecutor.execute(
                javaProcessInfo,
                new LoggedProcessOutputHandler(getLogger()));
        result.rethrowFailure().assertNormalExitValue();
    }

    /**
     * Packages the apk.
     *
     * <p>If in debug mode (when {@code jniDebugBuild} is {@code true}), when native
     * libraries are present, the packaging will also include one or more copies of
     * {@code gdbserver} in the final APK file. These are used for debugging native code, to ensure
     * that {@code gdbserver} is accessible to the application. There will be one version of
     * {@code gdbserver} for each ABI supported by the application. The {@code gbdserver} files are
     * placed in the {@code libs/abi/} folders automatically by the NDK.
     *
     * @param androidResPkgLocation the location of the packaged resource file
     * @param dexFolders the folder(s) with the dex file(s).
     * @param javaResourcesLocations the processed Java resource folders and/or jars
     * @param jniLibsLocations the folders containing jni shared libraries
     * @param assetsFolder the folder containing assets (null if no assets are to be packaged)
     * @param abiFilters optional ABI filter
     * @param jniDebugBuild whether the app should include jni debug data
     * @param signingConfig the signing configuration
     * @param outApkLocation location of the APK.
     * @param noCompressPredicate predicate for paths that should not be compressed
     * @throws FileNotFoundException if the store location was not found
     * @throws KeytoolException failed
     * @throws PackagerException failed
     */
    @SuppressWarnings("deprecation")
    public void oldPackageApk(
            @NonNull String androidResPkgLocation,
            @NonNull Set<File> dexFolders,
            @NonNull Collection<File> javaResourcesLocations,
            @NonNull Collection<File> jniLibsLocations,
            @Nullable File assetsFolder,
            @NonNull Set<String> abiFilters,
            boolean jniDebugBuild,
            @Nullable SigningConfig signingConfig,
            @NonNull File outApkLocation,
            int minSdkVersion,
            @NonNull Predicate<String> noCompressPredicate)
            throws KeytoolException, PackagerException, SigningException, IOException {
        checkNotNull(androidResPkgLocation, "androidResPkgLocation cannot be null.");
        checkNotNull(outApkLocation, "outApkLocation cannot be null.");

        /*
         * This is because this method is not supposed be be called in an incremental build. So, if
         * an out APK already exists, we delete it.
         */
        FileUtils.deleteIfExists(outApkLocation);

        Map<RelativeFile, FileStatus> javaResourceMods = Maps.newHashMap();
        Map<File, FileStatus> javaResourceArchiveMods = Maps.newHashMap();
        for (File resourceLocation : javaResourcesLocations) {
            if (resourceLocation.isFile()) {
                javaResourceArchiveMods.put(resourceLocation, FileStatus.NEW);
            } else {
                Set<RelativeFile> files =
                        RelativeFiles.fromDirectory(resourceLocation, rf -> rf.getFile().isFile());
                javaResourceMods.putAll(
                        Maps.asMap(files, Functions.constant(FileStatus.NEW)));
            }
        }

        NativeLibraryAbiPredicate nativeLibraryPredicate =
                new NativeLibraryAbiPredicate(abiFilters, jniDebugBuild);
        Map<RelativeFile, FileStatus> jniMods = Maps.newHashMap();
        Map<File, FileStatus> jniArchiveMods = Maps.newHashMap();
        for (File jniLoc : jniLibsLocations) {
            if (jniLoc.isFile()) {
                jniArchiveMods.put(jniLoc, FileStatus.NEW);
            } else {
                Set<RelativeFile> files =
                        RelativeFiles.fromDirectory(
                                jniLoc,
                                RelativeFiles.fromPathPredicate(nativeLibraryPredicate));
                jniMods.putAll(
                        Maps.asMap(files, Functions.constant(FileStatus.NEW)));
            }
        }

        Set<RelativeFile> assets = assetsFolder == null
                ? Collections.emptySet()
                : RelativeFiles.fromDirectory(assetsFolder, rf -> rf.getFile().isFile());

        PrivateKey key;
        X509Certificate certificate;
        boolean v1SigningEnabled;
        boolean v2SigningEnabled;

        if (signingConfig != null && signingConfig.isSigningReady()) {
            CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                    signingConfig.getStoreType(),
                    Preconditions.checkNotNull(signingConfig.getStoreFile()),
                    Preconditions.checkNotNull(signingConfig.getStorePassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyAlias()));
            key = certificateInfo.getKey();
            certificate = certificateInfo.getCertificate();
            v1SigningEnabled = signingConfig.isV1SigningEnabled();
            v2SigningEnabled = signingConfig.isV2SigningEnabled();
        } else {
            key = null;
            certificate = null;
            v1SigningEnabled = false;
            v2SigningEnabled = false;
        }

        ApkCreatorFactory.CreationData creationData =
                new ApkCreatorFactory.CreationData(
                        outApkLocation,
                        key,
                        certificate,
                        v1SigningEnabled,
                        v2SigningEnabled,
                        null, // BuiltBy
                        mCreatedBy,
                        minSdkVersion,
                        NativeLibrariesPackagingMode.COMPRESSED,
                        noCompressPredicate);
        try (OldPackager packager = new OldPackager(creationData, androidResPkgLocation, mLogger)) {
            // add dex folder to the apk root.
            if (!dexFolders.isEmpty()) {
                packager.addDexFiles(dexFolders);
            }

            // add the output of the java resource merger
            for (Map.Entry<RelativeFile, FileStatus> resourceUpdate :
                    javaResourceMods.entrySet()) {
                packager.updateResource(resourceUpdate.getKey(), resourceUpdate.getValue());
            }

            for (Map.Entry<File, FileStatus> resourceArchiveUpdate :
                    javaResourceArchiveMods.entrySet()) {
                packager.updateResourceArchive(resourceArchiveUpdate.getKey(),
                        resourceArchiveUpdate.getValue(), i -> false);
            }

            for (Map.Entry<RelativeFile, FileStatus> jniLibUpdates : jniMods.entrySet()) {
                packager.updateResource(jniLibUpdates.getKey(), jniLibUpdates.getValue());
            }

            for (Map.Entry<File, FileStatus> resourceArchiveUpdate :
                    jniArchiveMods.entrySet()) {
                packager.updateResourceArchive(resourceArchiveUpdate.getKey(),
                        resourceArchiveUpdate.getValue(), nativeLibraryPredicate.negate());
            }

            for (RelativeFile asset : assets) {
                packager.addFile(
                        asset.getFile(),
                        SdkConstants.FD_ASSETS + "/" + asset.getOsIndependentRelativePath());
            }
        }
    }

    /**
     * Creates a new split APK containing only code, this will only be functional on
     * MarshMallow and above devices.
     */
    public void packageCodeSplitApk(
            @NonNull String androidResPkgLocation,
            @NonNull File dexFile,
            @Nullable SigningConfig signingConfig,
            @NonNull File outApkLocation)
            throws KeytoolException, PackagerException, IOException {

        PrivateKey key;
        X509Certificate certificate;
        boolean v1SigningEnabled;
        boolean v2SigningEnabled;

        if (signingConfig != null && signingConfig.isSigningReady()) {
            CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                    signingConfig.getStoreType(),
                    Preconditions.checkNotNull(signingConfig.getStoreFile()),
                    Preconditions.checkNotNull(signingConfig.getStorePassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyAlias()));
            key = certificateInfo.getKey();
            certificate = certificateInfo.getCertificate();
            v1SigningEnabled = signingConfig.isV1SigningEnabled();
            v2SigningEnabled = signingConfig.isV2SigningEnabled();
        } else {
            key = null;
            certificate = null;
            v1SigningEnabled = false;
            v2SigningEnabled = false;
        }

        ApkCreatorFactory.CreationData creationData =
                new ApkCreatorFactory.CreationData(
                        outApkLocation,
                        key,
                        certificate,
                        v1SigningEnabled,
                        v2SigningEnabled,
                        null,
                        mCreatedBy,
                        API_LEVEL_SPLIT_APK,
                        NativeLibrariesPackagingMode.COMPRESSED,
                        s -> false);

        try (OldPackager packager = new OldPackager(creationData, androidResPkgLocation, mLogger)) {
            packager.addFile(dexFile, "classes.dex");
        } catch (SealedPackageException e) {
            // shouldn't happen since we control the package from start to end.
            throw new RuntimeException(e);
        }
    }

    /**
     * Signs a single jar file using the passed {@link SigningConfig}.
     *
     * @param in the jar file to sign.
     * @param signingConfig the signing configuration
     * @param out the file path for the signed jar.
     * @throws IOException failed
     * @throws KeytoolException failed
     * @throws SigningException failed
     * @throws NoSuchAlgorithmException failed
     * @throws ZipAbortException failed
     * @throws com.android.builder.signing.SigningException failed
     */
    public static void signApk(
            @NonNull File in,
            @Nullable SigningConfig signingConfig,
            @NonNull File out)
            throws KeytoolException, SigningException, NoSuchAlgorithmException, ZipAbortException,
            com.android.builder.signing.SigningException, IOException {

        PrivateKey key;
        X509Certificate certificate;
        boolean v1SigningEnabled;
        boolean v2SigningEnabled;

        if (signingConfig != null && signingConfig.isSigningReady()) {
            CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                    signingConfig.getStoreType(),
                    Preconditions.checkNotNull(signingConfig.getStoreFile()),
                    Preconditions.checkNotNull(signingConfig.getStorePassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyAlias()));
            key = certificateInfo.getKey();
            certificate = certificateInfo.getCertificate();
            v1SigningEnabled = signingConfig.isV1SigningEnabled();
            v2SigningEnabled = signingConfig.isV2SigningEnabled();
        } else {
            key = null;
            certificate = null;
            v1SigningEnabled = false;
            v2SigningEnabled = false;
        }

        ApkCreatorFactory.CreationData creationData =
                new ApkCreatorFactory.CreationData(
                        out,
                        key,
                        certificate,
                        v1SigningEnabled,
                        v2SigningEnabled,
                        null,
                        null,
                        1,
                        NativeLibrariesPackagingMode.COMPRESSED,
                        s -> false);

        try (SignedJarApkCreator signedJarBuilder = new SignedJarApkCreator(creationData)) {
            signedJarBuilder.writeZip(in);
        }
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
