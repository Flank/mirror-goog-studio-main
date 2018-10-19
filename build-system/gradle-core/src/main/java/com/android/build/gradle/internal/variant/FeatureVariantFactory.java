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

package com.android.build.gradle.internal.variant;

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantModel;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.FeatureVariantImpl;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.VariantType;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.profile.Recorder;
import com.android.ide.common.build.ApkData;
import com.android.ide.common.build.SplitOutputMatcher;
import com.android.resources.Density;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.NamedDomainObjectContainer;

public class FeatureVariantFactory extends BaseVariantFactory {

    @NonNull private final VariantType variantType;

    public FeatureVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig extension,
            @NonNull VariantType variantType) {
        super(globalScope, extension);
        this.variantType = variantType;
    }

    @NonNull
    @Override
    public BaseVariantData createVariantData(
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull TaskManager taskManager,
            @NonNull Recorder recorder) {
        FeatureVariantData variant =
                new FeatureVariantData(
                        globalScope, extension, taskManager, variantConfiguration, recorder);
        computeOutputs(variantConfiguration, variant);

        return variant;
    }

    private void computeOutputs(
            @NonNull GradleVariantConfiguration variantConfiguration,
            ApplicationVariantData variant) {
        variant.calculateFilters(extension.getSplits());

        Set<String> densities = variant.getFilters(OutputFile.FilterType.DENSITY);
        Set<String> abis = variant.getFilters(OutputFile.FilterType.ABI);
        Set<String> languages = variant.getFilters(OutputFile.FilterType.LANGUAGE);

        checkSplitsConflicts(variant, abis);

        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(extension.getSplits().getDensity().getCompatibleScreens());
        }

        OutputFactory outputFactory = variant.getOutputFactory();

        // create its output
        if (variant.getMultiOutputPolicy() == MultiOutputPolicy.MULTI_APK) {
            populateMultiApkOutputs(abis, densities, outputFactory);
        } else {
            populatePureSplitsOutputs(
                    abis, densities, languages, outputFactory, variantConfiguration);
        }

        restrictEnabledOutputs(variantConfiguration, variant.getOutputScope().getApkDatas());
    }

    private void populateMultiApkOutputs(
            Set<String> abis, Set<String> densities, OutputFactory outputFactory) {
        if (densities.isEmpty() && abis.isEmpty()) {
            // If both are empty, we will have only the main Apk, which was already added
            // in the base module.
            return;
        }

        boolean universalApkForAbi =
                extension.getSplits().getAbi().isEnable()
                        && extension.getSplits().getAbi().isUniversalApk();
        if (abis.isEmpty() || universalApkForAbi) {
            outputFactory.addUniversalApk();
        }

        if (!abis.isEmpty()) {
            // TODO(buss): Check if this is still needed/used, as BundleTool don't do this.
            // for each ABI, create a specific split that will contain all densities.
            abis.forEach(
                    abi ->
                            outputFactory.addFullSplit(
                                    ImmutableList.of(Pair.of(OutputFile.FilterType.ABI, abi))));
        }

        // create its outputs
        for (String density : densities) {
            if (!abis.isEmpty()) {
                for (String abi : abis) {
                    outputFactory.addFullSplit(
                            ImmutableList.of(
                                    Pair.of(OutputFile.FilterType.ABI, abi),
                                    Pair.of(OutputFile.FilterType.DENSITY, density)));
                }
            } else {
                outputFactory.addFullSplit(
                        ImmutableList.of(Pair.of(OutputFile.FilterType.DENSITY, density)));
            }
        }
    }

    private void populatePureSplitsOutputs(
            Set<String> abis,
            Set<String> densities,
            Set<String> languages,
            OutputFactory outputFactory,
            GradleVariantConfiguration variantConfiguration) {
        Iterable<ApkData> producedApks =
                Iterables.concat(
                        generateApkDataFor(VariantOutput.FilterType.ABI, abis, outputFactory),
                        generateApkDataFor(
                                VariantOutput.FilterType.DENSITY, densities, outputFactory),
                        generateApkDataFor(
                                VariantOutput.FilterType.LANGUAGE, languages, outputFactory));

        producedApks.forEach(
                apk -> {
                    apk.setVersionCode(variantConfiguration.getVersionCodeSerializableSupplier());
                    apk.setVersionName(variantConfiguration.getVersionNameSerializableSupplier());
                });
    }

    private ImmutableList<ApkData> generateApkDataFor(
            VariantOutput.FilterType filterType, Set<String> filters, OutputFactory outputFactory) {
        return filters.stream()
                .map(f -> outputFactory.addConfigurationSplit(filterType, f))
                .collect(ImmutableList.toImmutableList());
    }

    private void checkSplitsConflicts(
            @NonNull ApplicationVariantData variantData, @NonNull Set<String> abiFilters) {

        // if we don't have any ABI splits, nothing is conflicting.
        if (abiFilters.isEmpty()) {
            return;
        }

        // if universalAPK is requested, abiFilter will control what goes into the universal APK.
        if (extension.getSplits().getAbi().isUniversalApk()) {
            return;
        }

        // check supportedAbis in Ndk configuration versus ABI splits.
        Set<String> ndkConfigAbiFilters =
                variantData.getVariantConfiguration().getNdkConfig().getAbiFilters();
        if (ndkConfigAbiFilters == null || ndkConfigAbiFilters.isEmpty()) {
            return;
        }

        // if we have any ABI splits, whether it's a full or pure ABI splits, it's an error.
        EvalIssueReporter issueReporter = globalScope.getAndroidBuilder().getIssueReporter();
        issueReporter.reportError(
                EvalIssueReporter.Type.GENERIC,
                new EvalIssueException(
                        String.format(
                                "Conflicting configuration : '%1$s' in ndk abiFilters "
                                        + "cannot be present when splits abi filters are set : %2$s",
                                Joiner.on(",").join(ndkConfigAbiFilters),
                                Joiner.on(",").join(abiFilters))));
    }

    private void restrictEnabledOutputs(
            GradleVariantConfiguration configuration, List<ApkData> apkDataList) {

        Set<String> supportedAbis = configuration.getSupportedAbis();
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        String buildTargetAbi =
                projectOptions.get(BooleanOption.BUILD_ONLY_TARGET_ABI)
                                || globalScope.getExtension().getSplits().getAbi().isEnable()
                        ? projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI)
                        : null;
        if (buildTargetAbi == null) {
            return;
        }

        String buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY);
        Density density = Density.getEnum(buildTargetDensity);

        List<ApkData> apksToGenerate =
                SplitOutputMatcher.computeBestOutput(
                        apkDataList,
                        supportedAbis,
                        density == null ? -1 : density.getDpiValue(),
                        Arrays.asList(Strings.nullToEmpty(buildTargetAbi).split(",")));

        if (apksToGenerate.isEmpty()) {
            List<String> splits =
                    apkDataList
                            .stream()
                            .map(ApkData::getFilterName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            globalScope
                    .getAndroidBuilder()
                    .getIssueReporter()
                    .reportWarning(
                            EvalIssueReporter.Type.GENERIC,
                            String.format(
                                    "Cannot build selected target ABI: %1$s, "
                                            + (splits.isEmpty()
                                                    ? "no suitable splits configured: %2$s;"
                                                    : "supported ABIs are: %2$s"),
                                    buildTargetAbi,
                                    supportedAbis == null
                                            ? Joiner.on(", ").join(splits)
                                            : Joiner.on(", ").join(supportedAbis)));

            // do not disable anything, build all and let the apk install figure it out.
            return;
        }

        apkDataList.forEach(
                apkData -> {
                    if (!apksToGenerate.contains(apkData)) {
                        apkData.disable();
                    }
                });
    }

    @Override
    @NonNull
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return FeatureVariantImpl.class;
    }

    @NonNull
    @Override
    public Collection<VariantType> getVariantConfigurationTypes() {
        return ImmutableList.of(variantType);
    }

    @Override
    public boolean hasTestScope() {
        return false;
    }

    @Override
    public void validateModel(@NonNull VariantModel model) {}

    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    }
}
