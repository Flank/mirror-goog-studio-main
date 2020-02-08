/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.variant

import com.android.build.VariantOutput
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.variant.DependenciesInfo
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.VariantApiScope
import com.android.build.gradle.internal.scope.VariantPropertiesApiScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.ide.common.build.SplitOutputMatcher
import com.android.resources.Density
import com.android.utils.Pair
import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import java.util.Arrays
import java.util.Objects
import java.util.function.Consumer
import java.util.stream.Collectors

class ApplicationVariantFactory(
    variantApiScope: VariantApiScope,
    variantApiPropertiesScope: VariantPropertiesApiScope,
    globalScope: GlobalScope
) : AbstractAppVariantFactory<ApplicationVariantImpl, ApplicationVariantPropertiesImpl>(
    variantApiScope,
    variantApiPropertiesScope,
    globalScope
) {

    override fun createVariantObject(
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo
    ): ApplicationVariantImpl {
        val extension = globalScope.extension as BaseAppModuleExtension

        return globalScope
            .dslScope
            .objectFactory
            .newInstance(
                ApplicationVariantImpl::class.java,
                variantDslInfo,
                extension.dependenciesInfo,
                componentIdentity,
                variantApiScope
            )
    }

    override fun createVariantPropertiesObject(
        variant: ApplicationVariantImpl,
        componentIdentity: ComponentIdentity,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: BuildArtifactsHolder,
        variantScope: VariantScope,
        variantData: BaseVariantData,
        transformManager: TransformManager
    ): ApplicationVariantPropertiesImpl {
        val variantProperties = globalScope
            .dslScope
            .objectFactory
            .newInstance(
                ApplicationVariantPropertiesImpl::class.java,
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                variantScope,
                variantData,
                variant.dependenciesInfo as DependenciesInfo,
                transformManager,
                variantPropertiesApiScope,
                globalScope
            )

        computeOutputs(variantProperties, (variantData as ApplicationVariantData), true)

        return variantProperties
    }

    override fun getVariantType(): VariantType {
        return VariantTypeImpl.BASE_APK
    }

    private fun computeOutputs(
        variantProperties: ApplicationVariantPropertiesImpl,
        variant: ApplicationVariantData,
        includeMainApk: Boolean
    ) {
        val extension = globalScope.extension
        variant.calculateFilters(extension.splits)
        val densities =
            variant.getFilters(VariantOutput.FilterType.DENSITY)
        val abis =
            variant.getFilters(VariantOutput.FilterType.ABI)
        checkSplitsConflicts(variantProperties.variantDslInfo, variant, abis)
        if (!densities.isEmpty()) {
            variant.setCompatibleScreens(
                extension.splits.density
                    .compatibleScreens
            )
        }
        val outputFactory = variant.outputFactory
        populateMultiApkOutputs(abis, densities, outputFactory, includeMainApk)
        outputFactory.finalizeApkDataList()
            .forEach(Consumer { apkData: ApkData? ->
                variantProperties.addVariantOutput(apkData!!)
            })
        restrictEnabledOutputs(
            variantProperties.variantDslInfo, variantProperties.outputs
        )
    }

    private fun populateMultiApkOutputs(
        abis: Set<String?>,
        densities: Set<String>,
        outputFactory: OutputFactory,
        includeMainApk: Boolean
    ) {
        if (densities.isEmpty() && abis.isEmpty()) { // If both are empty, we will have only the main Apk.
            if (includeMainApk) {
                outputFactory.addMainApk()
            }
            return
        }
        val universalApkForAbi =
            (globalScope.extension.splits.abi.isEnable
                    && globalScope.extension.splits.abi.isUniversalApk)
        if (universalApkForAbi) {
            outputFactory.addUniversalApk()
        } else {
            if (abis.isEmpty() && includeMainApk) {
                outputFactory.addUniversalApk()
            }
        }
        if (!abis.isEmpty()) { // TODO(b/117973371): Check if this is still needed/used, as BundleTool don't do this.
            // for each ABI, create a specific split that will contain all densities.
            abis.forEach(
                Consumer { abi: String? ->
                    outputFactory.addFullSplit(
                        ImmutableList.of(
                            Pair.of(
                                VariantOutput.FilterType.ABI,
                                abi
                            )
                        )
                    )
                }
            )
        }
        // create its outputs
        for (density in densities) {
            if (!abis.isEmpty()) {
                for (abi in abis) {
                    outputFactory.addFullSplit(
                        ImmutableList.of(
                            Pair.of(
                                VariantOutput.FilterType.ABI,
                                abi
                            ),
                            Pair.of(
                                VariantOutput.FilterType.DENSITY,
                                density
                            )
                        )
                    )
                }
            } else {
                outputFactory.addFullSplit(
                    ImmutableList.of(
                        Pair.of(
                            VariantOutput.FilterType.DENSITY,
                            density
                        )
                    )
                )
            }
        }
    }

    private fun checkSplitsConflicts(
        variantDslInfo: VariantDslInfo,
        variantData: ApplicationVariantData,
        abiFilters: Set<String?>
    ) { // if we don't have any ABI splits, nothing is conflicting.
        if (abiFilters.isEmpty()) {
            return
        }
        // if universalAPK is requested, abiFilter will control what goes into the universal APK.
        if (globalScope.extension.splits.abi.isUniversalApk) {
            return
        }
        // check supportedAbis in Ndk configuration versus ABI splits.
        val ndkConfigAbiFilters =
            variantDslInfo.ndkConfig.abiFilters
        if (ndkConfigAbiFilters == null || ndkConfigAbiFilters.isEmpty()) {
            return
        }
        // if we have any ABI splits, whether it's a full or pure ABI splits, it's an error.
        val issueReporter = globalScope.dslScope.issueReporter
        issueReporter.reportError(
            IssueReporter.Type.GENERIC, String.format(
                "Conflicting configuration : '%1\$s' in ndk abiFilters "
                        + "cannot be present when splits abi filters are set : %2\$s",
                Joiner.on(",").join(ndkConfigAbiFilters),
                Joiner.on(",").join(abiFilters)
            )
        )
    }

    private fun restrictEnabledOutputs(
        variantDslInfo: VariantDslInfo, variantOutputs: VariantOutputList
    ) {
        val supportedAbis: Set<String?>? = variantDslInfo.supportedAbis
        val projectOptions = globalScope.projectOptions
        val buildTargetAbi =
            (if (projectOptions[BooleanOption.BUILD_ONLY_TARGET_ABI]
                || globalScope.extension.splits.abi.isEnable
            ) projectOptions[StringOption.IDE_BUILD_TARGET_ABI] else null)
                ?: return
        val buildTargetDensity =
            projectOptions[StringOption.IDE_BUILD_TARGET_DENSITY]
        val density = Density.getEnum(buildTargetDensity)
        val apkDataList = variantOutputs
            .stream()
            .map(VariantOutputImpl::apkData)
            .collect(Collectors.toList())
        val apksToGenerate = SplitOutputMatcher.computeBestOutput(
            apkDataList,
            supportedAbis,
            density?.dpiValue ?: -1,
            Arrays.asList(
                *Strings.nullToEmpty(
                    buildTargetAbi
                ).split(",".toRegex()).toTypedArray()
            )
        )
        if (apksToGenerate.isEmpty()) {
            val splits = apkDataList
                .stream()
                .map { obj: ApkData -> obj.filterName }
                .filter { obj: String? ->
                    Objects.nonNull(
                        obj
                    )
                }
                .collect(Collectors.toList())
            globalScope
                .dslScope
                .issueReporter
                .reportWarning(
                    IssueReporter.Type.GENERIC, String.format(
                        "Cannot build selected target ABI: %1\$s, "
                                + if (splits.isEmpty()) "no suitable splits configured: %2\$s;" else "supported ABIs are: %2\$s",
                        buildTargetAbi,
                        if (supportedAbis == null) Joiner.on(", ").join(
                            splits
                        ) else Joiner.on(", ").join(supportedAbis)
                    )
                )
            // do not disable anything, build all and let the apk install figure it out.
            return
        }
        variantOutputs.forEach {
            if (!apksToGenerate.contains(it.apkData)) {
                it.isEnabled.set(false)
            }
        }
    }
}