/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.packaging;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.CapturingChangesApkCreator;
import com.android.build.gradle.internal.incremental.FolderBasedApkCreator;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.model.SigningConfig;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.PackagingUtils;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.gradle.api.Project;

/**
 * Factory class to create instances of {@link IncrementalPackager}. Since there are many options
 * for {@link IncrementalPackager} and not all are always required, this makes building the packager
 * easier.
 *
 * <p>While some parameters have sensible defaults, some parameters must be defined. See the
 * {@link #build()} method for information on which parameters are mandatory.
 */
public class IncrementalPackagerBuilder {

    /** Enums for all the supported output format. */
    public enum ApkFormat {
        /** Usual APK format. */
        FILE {
            @Override
            ApkCreatorFactory factory(boolean keepTimestampsInApk, boolean debuggableBuild) {
                return ApkCreatorFactories.fromProjectProperties(
                        keepTimestampsInApk, debuggableBuild);
            }
        },

        FILE_WITH_LIST_OF_CHANGES {
            @SuppressWarnings({"OResourceOpenedButNotSafelyClosed", "resource"})
            @Override
            ApkCreatorFactory factory(boolean keepTimestampsInApk, boolean debuggableBuild) {
                ApkCreatorFactory apk =
                        ApkCreatorFactories.fromProjectProperties(
                                keepTimestampsInApk, debuggableBuild);
                return creationData ->
                        new CapturingChangesApkCreator(creationData, apk.make(creationData));
            }
        },

        /** Directory with a structure mimicking the APK format. */
        DIRECTORY {
            @SuppressWarnings({"OResourceOpenedButNotSafelyClosed", "resource"})
            @Override
            ApkCreatorFactory factory(boolean keepTimestampsInApk, boolean debuggableBuild) {
                return creationData ->
                        new CapturingChangesApkCreator(
                                creationData, new FolderBasedApkCreator(creationData));
            }
        };

        abstract ApkCreatorFactory factory(boolean keepTimestampsInApk, boolean debuggableBuild);
    }

    /**
     * Type of build that invokes the instance of IncrementalPackagerBuilder
     *
     * <p>This information is provided as a hint for possible performance optimizations
     */
    public enum BuildType {
        UNKNOWN,
        CLEAN,
        INCREMENTAL
    }

    /** Builder for the data to create APK file. */
    @NonNull
    private ApkCreatorFactory.CreationData.Builder creationDataBuilder =
            ApkCreatorFactory.CreationData.builder();


    /** Desired format of the output. */
    @NonNull private ApkFormat apkFormat;

    /**
     * How should native libraries be packaged. If not defined, it can be inferred if
     * {@link #manifest} is defined.
     */
    @Nullable
    private NativeLibrariesPackagingMode nativeLibrariesPackagingMode;

    /**
     * The no-compress predicate: returns {@code true} for paths that should not be compressed. If
     * not defined, but {@link #aaptOptionsNoCompress} and {@link #manifest} are both defined, it
     * can be inferred.
     */
    @Nullable private Predicate<String> noCompressPredicate;

    /** Whether the timestamps should be kept in the apk. */
    @Nullable private Boolean keepTimestampsInApk;

    /**
     * Directory for intermediate contents.
     */
    @Nullable
    private File intermediateDir;

    /**
     * Is the build debuggable?
     */
    private boolean debuggableBuild;

    /**
     * Is the build JNI-debuggable?
     */
    private boolean jniDebuggableBuild;

    /**
     * ABI filters. Empty if none.
     */
    @NonNull
    private Set<String> abiFilters;

    /**
     * Manifest.
     */
    @Nullable
    private File manifest;

    /** aapt options no compress config. */
    @Nullable private Collection<String> aaptOptionsNoCompress;

    @Nullable private EvalIssueReporter issueReporter;
    @Nullable private BooleanSupplier canParseManifest;
    @NonNull private BuildType buildType;

    /** Creates a new builder. */
    public IncrementalPackagerBuilder(@NonNull ApkFormat apkFormat, @NonNull BuildType buildType) {
        abiFilters = new HashSet<>();
        this.apkFormat = apkFormat;
        this.buildType = buildType;
        creationDataBuilder.setIncremental(buildType == BuildType.INCREMENTAL);
    }

    /** Creates a new builder. */
    public IncrementalPackagerBuilder(@NonNull ApkFormat apkFormat) {
        this(apkFormat, BuildType.UNKNOWN);
    }

    /**
     * Sets the signing configuration information for the incremental packager.
     *
     * @param signingConfig the signing config; if {@code null} then the APK will not be signed
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withSigning(@Nullable SigningConfig signingConfig) {
        return withSigning(signingConfig, 1);
    }

    /**
     * Sets the signing configuration information for the incremental packager.
     *
     * @param signingConfig the signing config; if {@code null} then the APK will not be signed
     * @param minSdk the minimum SDK
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withSigning(
            @Nullable SigningConfig signingConfig, int minSdk) {
        if (signingConfig == null) {
            return this;
        }
        try {
            String error =
                    "SigningConfig \""
                            + signingConfig.getName()
                            + "\" is missing required property \"%s\".";
            CertificateInfo certificateInfo =
                    KeystoreHelper.getCertificateInfo(
                            signingConfig.getStoreType(),
                            Preconditions.checkNotNull(
                                    signingConfig.getStoreFile(), error, "storeFile"),
                            Preconditions.checkNotNull(
                                    signingConfig.getStorePassword(), error, "storePassword"),
                            Preconditions.checkNotNull(
                                    signingConfig.getKeyPassword(), error, "keyPassword"),
                            Preconditions.checkNotNull(
                                    signingConfig.getKeyAlias(), error, "keyAlias"));
            // V1 signature is useless if minSdk is 24+
            boolean enableV1Signing = minSdk < 24 && signingConfig.isV1SigningEnabled();
            creationDataBuilder.setSigningOptions(
                    SigningOptions.builder()
                            .setKey(certificateInfo.getKey())
                            .setCertificates(certificateInfo.getCertificate())
                            .setV1SigningEnabled(enableV1Signing)
                            .setV2SigningEnabled(signingConfig.isV2SigningEnabled())
                            .setMinSdkVersion(minSdk)
                            .setValidation(computeValidation())
                            .build());
        } catch (KeytoolException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    private SigningOptions.Validation computeValidation() {
        switch (buildType) {
            case INCREMENTAL:
                return SigningOptions.Validation.ASSUME_VALID;
            case CLEAN:
                return SigningOptions.Validation.ASSUME_INVALID;
            case UNKNOWN:
                return SigningOptions.Validation.ALWAYS_VALIDATE;
            default:
                throw new RuntimeException(
                        "Unknown IncrementalPackagerBuilder build type " + buildType);
        }
    }

    /**
     * Sets the output file for the APK.
     *
     * @param f the output file
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withOutputFile(@NonNull File f) {
        creationDataBuilder.setApkPath(f);
        return this;
    }

    /**
     * Sets the packaging mode for native libraries.
     *
     * @param packagingMode the packging mode
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withNativeLibraryPackagingMode(
            @NonNull NativeLibrariesPackagingMode packagingMode) {
        nativeLibrariesPackagingMode = packagingMode;
        return this;
    }

    /**
     * Sets the manifest. While the manifest itself is not used for packaging, information on
     * the native libraries packaging mode can be inferred from the manifest.
     *
     * @param manifest the manifest
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withManifest(@NonNull File manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * Sets the no-compress predicate. This predicate returns {@code true} for files that should
     * not be compressed
     *
     * @param noCompressPredicate the predicate
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withNoCompressPredicate(
            @NonNull Predicate<String> noCompressPredicate) {
        this.noCompressPredicate = noCompressPredicate;
        return this;
    }

    /**
     * Sets the {@code aapt} options no compress predicate.
     *
     * <p>The no-compress predicate can be computed if this and the manifest (see {@link
     * #withManifest(File)}) are both defined.
     */
    @NonNull
    public IncrementalPackagerBuilder withAaptOptionsNoCompress(
            @Nullable Collection<String> aaptOptionsNoCompress) {
        this.aaptOptionsNoCompress = aaptOptionsNoCompress;
        return this;
    }

    /**
     * Sets whether the timestamps should be kept in the apk.
     *
     * @param keepTimestampsInApk whether the timestamps should be kept in the apk
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withKeepTimestampsInApk(boolean keepTimestampsInApk) {
        this.keepTimestampsInApk = keepTimestampsInApk;
        return this;
    }

    /**
     * Sets the intermediate directory used to store information for incremental builds.
     *
     * @param intermediateDir the intermediate directory
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withIntermediateDir(@NonNull File intermediateDir) {
        this.intermediateDir = intermediateDir;
        return this;
    }

    /**
     * Sets the created-by parameter.
     *
     * @param createdBy the optional value for created-by
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withCreatedBy(@Nullable String createdBy) {
        creationDataBuilder.setCreatedBy(createdBy);
        return this;
    }

    /**
     * Sets whether the build is debuggable or not.
     *
     * @param debuggableBuild is the build debuggable?
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withDebuggableBuild(boolean debuggableBuild) {
        this.debuggableBuild = debuggableBuild;
        return this;
    }

    /**
     * Sets whether the build is JNI-debuggable or not.
     *
     * @param jniDebuggableBuild is the build JNI-debuggable?
     * @return {@code this} for use with fluent-style notation
     */
    @NonNull
    public IncrementalPackagerBuilder withJniDebuggableBuild(boolean jniDebuggableBuild) {
        this.jniDebuggableBuild = jniDebuggableBuild;
        return this;
    }

    /**
     * Sets the set of accepted ABIs.
     *
     * @param acceptedAbis the accepted ABIs; if empty then all ABIs are accepted
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withAcceptedAbis(@NonNull Set<String> acceptedAbis) {
        this.abiFilters = ImmutableSet.copyOf(acceptedAbis);
        return this;
    }

    /**
     * Sets the issueReporter to report errors/warnings.
     *
     * @param issueReporter the EvalIssueReporter to use.
     * @return {@code this} for use with fluent-style notation
     */
    public IncrementalPackagerBuilder withIssueReporter(@NonNull EvalIssueReporter issueReporter) {
        this.issueReporter = issueReporter;
        return this;
    }

    /**
     * Creates the packager, verifying that all the minimum data has been provided. The required
     * information are:
     *
     * <ul>
     *    <li>{@link #withOutputFile(File)}
     *    <li>{@link #withIntermediateDir(File)}
     * </ul>
     *
     * @return the incremental packager
     */
    @NonNull
    public IncrementalPackager build() {
        Preconditions.checkState(keepTimestampsInApk != null, "keepTimestampsInApk == null");
        Preconditions.checkState(intermediateDir != null, "intermediateDir == null");

        if (noCompressPredicate == null) {
            if (manifest != null) {
                noCompressPredicate =
                        PackagingUtils.getNoCompressPredicate(
                                aaptOptionsNoCompress, manifest, () -> true, issueReporter);
            } else {
                noCompressPredicate = path -> false;
            }
        }

        if (nativeLibrariesPackagingMode == null) {
            if (manifest != null) {
                nativeLibrariesPackagingMode =
                        PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                manifest, canParseManifest, issueReporter);
            } else {
                nativeLibrariesPackagingMode = NativeLibrariesPackagingMode.COMPRESSED;
            }
        }

        creationDataBuilder
                .setNativeLibrariesPackagingMode(nativeLibrariesPackagingMode)
                .setNoCompressPredicate(noCompressPredicate::test);

        try {
            return new IncrementalPackager(
                    creationDataBuilder.build(),
                    intermediateDir,
                    apkFormat.factory(keepTimestampsInApk, debuggableBuild),
                    abiFilters,
                    jniDebuggableBuild);
        } catch (PackagerException|IOException e) {
            throw new RuntimeException(e);
        }
    }
}
