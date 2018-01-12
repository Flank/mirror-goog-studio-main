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

package com.android.builder.internal.aapt;

import com.android.annotations.NonNull;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Abstract implementation of an {@code aapt} command. It provides a common framework for
 * rule validation.
 */
public abstract class AbstractAapt implements Aapt {

    @Override
    public final void link(@NonNull AaptPackageConfig config)
            throws AaptException, ExecutionException, InterruptedException {
        validatePackageConfig(config);
        makeValidatedPackage(config).get();
    }

    /**
     * Same as {@link #link(AaptPackageConfig)} but invoked only after validation has
     * been performed.
     *
     * @param config same as in {@link #link(AaptPackageConfig)}
     * @return same as in {@link #link(AaptPackageConfig)}
     * @throws AaptException same as in {@link #link(AaptPackageConfig)}
     */
    @NonNull
    protected abstract ListenableFuture<Void> makeValidatedPackage(
            @NonNull AaptPackageConfig config) throws AaptException;

    /**
     * Checks whether a package config is valid. Subclasses may override this method to change the
     * validation performed. The default validation is described below.
     *
     * <p>Config defines multiple fields, not all mandatory. The following rules apply when defining
     * a package configuration (failure to comply will result in {@link AaptException} being thrown:
     *
     * <ul>
     *   <li>The following fields must be defined:
     *       <ul>
     *         <li>Android target (see {@link AaptPackageConfig#getAndroidTarget()});
     *         <li>Build tools information (see {@link AaptPackageConfig#getBuildToolInfo()}};
     *         <li>Logger (see {@link AaptPackageConfig#getLogger()});
     *         <li>Manifest file (see {@link AaptPackageConfig#getManifestFile()});
     *         <li>{@code aapt} options (see {@link AaptPackageConfig#getOptions()});
     *         <li>Variant type (see {@link AaptPackageConfig#getVariantType()}};
     *       </ul>
     *   <li>The following fields are optional and some have defaults (these fields do <i>not</i>
     *       need to be defined):
     *       <ul>
     *         <li>Custom package for {@code R} (see {@link
     *             AaptPackageConfig#getCustomPackageForR()});
     *         <li>Libraries (see {@link AaptPackageConfig#getLibrarySymbolTableFiles()}); if none
     *             is set, an empty list is assumed;
     *         <li>Preferred density (see {@link AaptPackageConfig#getPreferredDensity()});
     *         <li>Proguard output file (see {@link AaptPackageConfig#getProguardOutputFile()});
     *         <li>Resource configs (see {@link AaptPackageConfig#getResourceConfigs()}); if none is
     *             set, an empty collection is assumed;
     *         <li>Resource dir (see {@link AaptPackageConfig#getResourceDirs()});
     *         <li>Resource output APK (see {@link AaptPackageConfig#getResourceOutputApk()});
     *         <li>Source output directory (see {@link AaptPackageConfig#getSourceOutputDir()});
     *         <li>Splits (see {@link AaptPackageConfig#getSplits()});
     *         <li>Symbol output directory (see {@link AaptPackageConfig#getSymbolOutputDir()});
     *         <li>Debuggable (see {@link AaptPackageConfig#isDebuggable()}), {@code false} by
     *             default;
     *         <li>Pseudo localize (see {@link AaptPackageConfig#isPseudoLocalize()}), {@code false}
     *             by default;
     *         <li>Verbose (see {@link AaptPackageConfig#isVerbose()}), {@code false} by default;
     *       </ul>
     *   <li>Either the source output directory ({@link AaptPackageConfig#getSourceOutputDir()}) or
     *       the resource output APK ({@link AaptPackageConfig#getResourceOutputApk()}) must be
     *       defined;
     *   <li>If there are no libraries defined (see {@link
     *       AaptPackageConfig#getLibrarySymbolTableFiles()}) then the symbol output directory
     *       ({@link AaptPackageConfig#getSymbolOutputDir()}) and the source output directory
     *       ({@link AaptPackageConfig#getSourceOutputDir()}) must <i>not</i> be defined; (*)
     *   <li>If the build tools' version is {@code < 21}, then pseudo-localization is not allowed
     *       ({@link AaptPackageConfig#isPseudoLocalize()});
     *   <li>If the build tools' version is {@code < 21}, then fail on missing config entry ( {@code
     *       getOptions().getFailOnMissingConfigEntry()} must be {@code false};
     *   <li>If there are splits ({@link AaptPackageConfig#getSplits()}) configured and resource
     *       configs ({@link AaptPackageConfig#getResourceConfigs()}) configured, then all splits
     *       must be preset in the resource configs and all densities in resource configs must match
     *       splits;
     *   <li>If the build tools' version is {@code < 21}, then preferred density should not be set
     *       (breaking this rule will issue a warning, but won't throw {@link AaptException});
     *   <li>If at least one dependent feature ({@link AaptPackageConfig#getDependentFeatures()}) is
     *       defined, a package ID ({@link AaptPackageConfig#getPackageId()}) value must also be
     *       defined.
     * </ul>
     *
     * <p>(*) This rule is currently disabled as it is sometimes broken for unknown reasons.
     *
     * @param packageConfig the package config to validate
     * @throws AaptException the package config is not valid
     */
    protected void validatePackageConfig(@NonNull AaptPackageConfig packageConfig)
            throws AaptException {
        if (packageConfig.getManifestFile() == null) {
            throw new AaptException("Manifest file not set.");
        }

        if (packageConfig.getOptions() == null) {
            throw new AaptException("aapt options not set.");
        }

        if (packageConfig.getAndroidTarget() == null) {
            throw new AaptException("Android target not set.");
        }

        ILogger logger = packageConfig.getLogger();
        if (logger == null) {
            throw new AaptException("Logger not set.");
        }

        BuildToolInfo buildToolInfo = packageConfig.getBuildToolInfo();
        if (buildToolInfo == null) {
            throw new AaptException("Build tools not set.");
        }

        if (packageConfig.getVariantType() == null) {
            throw new AaptException("Variant type not set.");
        }

        if ((packageConfig.getSymbolOutputDir() != null
                        || packageConfig.getSourceOutputDir() != null)
                && packageConfig.getLibrarySymbolTableFiles().isEmpty()) {
            /*
             * This rule seems to be broken some times. Not exactly why, but the original code
             * (from where this was refactored) allowed libraries to be set to empty and defining
             * either symbol output dir or source output dir.
             */
            // throw new AaptException("Symbol output directory and source output directory can "
            //        + "only be defined if there are libraries.");
        }

        if (!packageConfig.getDependentFeatures().isEmpty()
                && packageConfig.getPackageId() == null) {
            throw new AaptException(
                    "A dependent feature was defined but no package ID was set. "
                            + "You are probably missing a feature dependency in the base feature.");
        }
    }
}
