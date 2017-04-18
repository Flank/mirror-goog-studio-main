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

package com.android.builder.internal.aapt.v2;

import com.android.annotations.NonNull;
import com.android.builder.core.VariantType;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AaptUtils;
import com.android.builder.model.AaptOptions;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds the command lines for use with {@code aapt2}.
 */
public final class AaptV2CommandBuilder {

    /**
     * Utility class, no constructor.
     */
    private AaptV2CommandBuilder() {}

    /**
     * Creates the command line used to compile a resource.
     * See {@link com.android.builder.internal.aapt.Aapt#compile(File, File)}.
     *
     * @param file see above
     * @param output see above
     * @return the command line arguments
     */
    public static ImmutableList<String> makeCompile(@NonNull File file, @NonNull File output) {
        return ImmutableList.of(
                "-o",
                output.getAbsolutePath(),
                file.getAbsolutePath());
    }

    /**
     * Creates the command line used to link the package.
     *
     * <p>See {@link com.android.builder.internal.aapt.Aapt#link(AaptPackageConfig)}.
     *
     * @param config see above
     * @param intermediateDir a directory for intermediate files
     * @return the command line arguments
     * @throws AaptException failed to build the command line
     */
    public static ImmutableList<String> makeLink(
            @NonNull AaptPackageConfig config, @NonNull File intermediateDir) throws AaptException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();

        if (config.isVerbose()) {
            builder.add("-v");
        }

        File stableResourceIdsFile = new File(intermediateDir, "stable-resource-ids.txt");
        // TODO: For now, we ignore this file, but as soon as aapt2 supports it, we'll use it.

        // inputs
        IAndroidTarget target = config.getAndroidTarget();
        Preconditions.checkNotNull(target);
        builder.add("-I", target.getPath(IAndroidTarget.ANDROID_JAR));

        File manifestFile = config.getManifestFile();
        Preconditions.checkNotNull(manifestFile);
        builder.add("--manifest", manifestFile.getAbsolutePath());

        if (config.getResourceDir() != null) {
            // TODO: Fix when aapt 2 supports -R directories (http://b.android.com/209331)
            // builder.addArgs("-R", config.getResourceDir().getAbsolutePath());
            try {
                Files.walk(config.getResourceDir().toPath())
                        .filter(Files::isRegularFile)
                        .forEach((p) -> builder.add("-R", p.toString()));
            } catch (IOException e) {
                throw new AaptException("Failed to walk path " + config.getResourceDir());
            }
        }

        builder.add("--auto-add-overlay");

        // outputs
        if (config.getSourceOutputDir() != null) {
            builder.add("--java", config.getSourceOutputDir().getAbsolutePath());
        }

        if (config.getResourceOutputApk() != null) {
            builder.add("-o", config.getResourceOutputApk().getAbsolutePath());
        } else {
            // FIXME: Fix when aapt 2 support not providing -o (http://b.android.com/210026)
            try {
                File tmpOutput = File.createTempFile("aapt-", "-out");
                tmpOutput.deleteOnExit();
                builder.add("-o", tmpOutput.getAbsolutePath());
            } catch (IOException e) {
                throw new AaptException("No output apk defined and failed to create tmp file", e);
            }
        }

        if (config.getProguardOutputFile()!= null) {
            builder.add("--proguard", config.getProguardOutputFile().getAbsolutePath());
        }

        if (config.getSplits() != null) {
            for (String split : config.getSplits()) {
                // FIXME: Fix when --split is supported (http://b.android.com/212372)
//                builder.addArgs("--split", split);
            }
        }

        // options controlled by build variants

        if (config.isDebuggable()) {
//            builder.addArgs("--debug-mode");
        }

        ILogger logger = config.getLogger();
        Preconditions.checkNotNull(logger);
        if (config.getVariantType() != VariantType.ANDROID_TEST
                && config.getCustomPackageForR() != null) {
            builder.add("--custom-package", config.getCustomPackageForR());
        }

        // bundle specific options
        boolean generateFinalIds = true;
        if (config.getVariantType() == VariantType.LIBRARY) {
            generateFinalIds = false;
        }
        if (!generateFinalIds) {
            builder.add("--non-final-ids");
        }

        // AAPT options
        AaptOptions options = config.getOptions();
        Preconditions.checkNotNull(options);
        String ignoreAssets = options.getIgnoreAssets();
        if (ignoreAssets != null) {
//            builder.addArgs("--ignore-assets", ignoreAssets);
        }

        if (config.getOptions().getFailOnMissingConfigEntry()) {
//            builder.addArgs("--error-on-missing-config-entry");
        }

        /*
         * Never compress apks.
         */
//        builder.addArgs("-0", "apk");

        /*
         * Add custom no-compress extensions.
         */
        Collection<String> noCompressList = config.getOptions().getNoCompress();
        if (noCompressList != null) {
            for (String noCompress : noCompressList) {
                builder.add("-0", noCompress);
            }
        }
        List<String> additionalParameters = config.getOptions().getAdditionalParameters();
        if (additionalParameters != null) {
            builder.addAll(additionalParameters);
        }

        List<String> resourceConfigs = new ArrayList<String>();
        resourceConfigs.addAll(config.getResourceConfigs());

        /*
         * Split the density and language resource configs, since starting in 21, the
         * density resource configs should be passed with --preferred-density to ensure packaging
         * of scalable resources when no resource for the preferred density is present.
         */
        Collection<String> otherResourceConfigs;
        String preferredDensity = null;
        Collection<String> densityResourceConfigs = Lists.newArrayList(
                AaptUtils.getDensityResConfigs(resourceConfigs));
        otherResourceConfigs = Lists.newArrayList(AaptUtils.getNonDensityResConfigs(
                resourceConfigs));
        preferredDensity = config.getPreferredDensity();

        if (preferredDensity != null && !densityResourceConfigs.isEmpty()) {
            throw new AaptException(
                    String.format("When using splits in tools 21 and above, "
                                    + "resConfigs should not contain any densities. Right now, it "
                                    + "contains \"%1$s\"\nSuggestion: remove these from resConfigs "
                                    + "from build.gradle",
                            Joiner.on("\",\"").join(densityResourceConfigs)));
        }

        if (densityResourceConfigs.size() > 1) {
            throw new AaptException("Cannot filter assets for multiple densities using "
                    + "SDK build tools 21 or later. Consider using apk splits instead.");
        }

        if (preferredDensity == null && densityResourceConfigs.size() == 1) {
            preferredDensity = Iterables.getOnlyElement(densityResourceConfigs);
        }

        if (!otherResourceConfigs.isEmpty()) {
            Joiner joiner = Joiner.on(',');
            builder.add("-c", joiner.join(otherResourceConfigs));
        }

        if (preferredDensity != null) {
            builder.add("--preferred-density", preferredDensity);
        }

        if (config.getSymbolOutputDir() != null && (config.getVariantType() == VariantType.LIBRARY
                || !config.getLibraries().isEmpty())) {
            File rDotTxt = new File(config.getSymbolOutputDir(), "R.txt");
            builder.add("--output-text-symbols", rDotTxt.getAbsolutePath());
        }

        if (config.getPackageId() != null) {
            builder.add("--package-id", "0x" + Integer.toHexString(config.getPackageId()));
            for (File dependentFeature : config.getDependentFeatures()) {
                builder.add("-I", dependentFeature.getAbsolutePath());
            }
        } else if (!config.getDependentFeatures().isEmpty()) {
            throw new AaptException("Dependent features configured but no package ID was set.");
        }

        builder.add("--no-version-vectors");

        if (config.getBaseFeature() != null) {
            throw new AaptException(
                    "Feature-splits are not supported in AAPT2 yet. "
                            + "Use -Pandroid.enableAapt2=false flag to use AAPT1 instead.");
        }

        return builder.build();
    }
}
