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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.resources.Density;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class to help with installation of multi-output variants.
 */
public class SplitOutputMatcher {


    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * <p>if there are pure splits, use the split-select tool otherwise revert to store logic.
     *
     * @param processExecutor an executor to execute native processes.
     * @param splitSelectExe the split select tool optionally.
     * @param deviceConfigProvider the device configuration.
     * @param outputs the tested variant outpts.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     *     of the outputs, if there is a single output with no abi filters. If the list is null or
     *     empty, then the variant does not restrict ABI packaging.
     * @return the list of APK files to install.
     * @throws ProcessException when the splitSelect executable failed to execute.
     */
    @NonNull
    public static List<File> computeBestOutput(
            @NonNull ProcessExecutor processExecutor,
            @Nullable File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            @NonNull Collection<OutputFile> outputs,
            @Nullable Collection<String> variantAbiFilters)
            throws ProcessException {

        // build the list of pure split APKs.
        List<String> splitApksPath =
                outputs.stream()
                        .filter(outputFile -> outputFile.getOutputType().equals(OutputFile.SPLIT))
                        .map(outputFile -> outputFile.getOutputFile().getAbsolutePath())
                        .collect(Collectors.toList());

        if (splitApksPath.isEmpty()) {
            List<File> apkFiles = new ArrayList<>();

            // now look for a matching output file
            List<OutputFile> outputFiles = SplitOutputMatcher.computeBestOutput(
                    outputs,
                    variantAbiFilters,
                    deviceConfigProvider.getDensity(),
                    deviceConfigProvider.getAbis());
            for (OutputFile outputFile : outputFiles) {
                apkFiles.add(outputFile.getOutputFile());
            }
            return apkFiles;
        } else {
            Optional<? extends OutputFile> mainApk =
                    outputs.stream()
                            .filter(
                                    outputFile ->
                                            !outputFile.getOutputType().equals(OutputFile.SPLIT))
                            .findFirst();
            if (!mainApk.isPresent()) {
                throw new RuntimeException("Cannot retrieve the main APK from variant outputs");
            }
            if (splitSelectExe == null) {
                throw new RuntimeException(
                        "Pure splits installation requires build tools 22 or above");
            }
            return computeBestOutput(
                    processExecutor,
                    splitSelectExe,
                    deviceConfigProvider,
                    mainApk.get().getOutputFile(),
                    splitApksPath);
        }
    }

    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * <p>if there are pure splits, use the split-select tool otherwise revert to store logic.
     *
     * @param processExecutor an executor to execute native processes.
     * @param splitSelectExe the split select tool optionally.
     * @param deviceConfigProvider the device configuration.
     * @param mainApk the main apk file.
     * @param splitApksPath the list of split apks path.
     * @return the list of APK files to install.
     * @throws ProcessException when the splitSelect executable failed to execute.
     */
    @NonNull
    public static List<File> computeBestOutput(
            @NonNull ProcessExecutor processExecutor,
            @NonNull File splitSelectExe,
            @NonNull DeviceConfigProvider deviceConfigProvider,
            @NonNull File mainApk,
            @NonNull Collection<String> splitApksPath)
            throws ProcessException {

        // build the list of APKs.
        if (splitApksPath.isEmpty()) {
            return ImmutableList.of(mainApk);
        } else {
            List<File> apkFiles = new ArrayList<>();

            Set<String> resultApksPath = new HashSet<>();
            for (String abi : deviceConfigProvider.getAbis()) {
                String deviceConfiguration =
                        prepareConfigFormatMccMnc(deviceConfigProvider.getConfigFor(abi));
                resultApksPath.addAll(SplitSelectTool.splitSelect(
                        processExecutor,
                        splitSelectExe,
                        deviceConfiguration,
                        mainApk.getAbsolutePath(),
                        splitApksPath));
            }
            for (String resultApkPath : resultApksPath) {
                apkFiles.add(new File(resultApkPath));
            }
            // and add back the main APK.
            apkFiles.add(mainApk);
            return apkFiles;
        }
    }

    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * <p>This uses the same logic as the store, using two passes: First, find all the compatible
     * outputs. Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     *     of the outputs, if there is a single output with no abi filters. If the list is null,
     *     then the variant does not restrict ABI packaging.
     * @param deviceDensity the density of the device.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    @NonNull
    public static <T extends VariantOutput> List<T> computeBestOutput(
            @NonNull Collection<? extends T> outputs,
            @Nullable Collection<String> variantAbiFilters,
            int deviceDensity,
            @NonNull List<String> deviceAbis) {
        Density densityEnum = Density.getEnum(deviceDensity);

        String densityValue;
        if (densityEnum == null) {
            densityValue = null;
        } else {
            densityValue = densityEnum.getResourceValue();
        }

        // gather all compatible matches.
        List<T> matches = Lists.newArrayList();

        // find a matching output.
        for (T variantOutput : outputs) {
            String densityFilter = getFilter(variantOutput, OutputFile.DENSITY);
            String abiFilter = getFilter(variantOutput, OutputFile.ABI);

            if (densityFilter != null && !densityFilter.equals(densityValue)) {
                continue;
            }

            if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                continue;
            }
            matches.add(variantOutput);
        }

        if (matches.isEmpty()) {
            return ImmutableList.of();
        }

        T match =
                Collections.max(
                        matches,
                        (splitOutput, splitOutput2) -> {
                            int rc = splitOutput.getVersionCode() - splitOutput2.getVersionCode();
                            if (rc != 0) {
                                return rc;
                            }
                            int abiOrder1 = getAbiPreferenceOrder(splitOutput, deviceAbis);
                            int abiOrder2 = getAbiPreferenceOrder(splitOutput2, deviceAbis);
                            return abiOrder1 - abiOrder2;
                        });

        return isMainApkCompatibleWithDevice(match, variantAbiFilters, deviceAbis)
                ? ImmutableList.of(match)
                : ImmutableList.of();
    }

    /**
     * Return the preference score of a VariantOutput for the deviceAbi list.
     *
     * Higher score means a better match.  Scores returned by different call are only comparable if
     * the specified deviceAbi is the same.
     */
    private static int getAbiPreferenceOrder(VariantOutput variantOutput, List<String> deviceAbi) {
        String abiFilter = getFilter(variantOutput, OutputFile.ABI);
        if (Strings.isNullOrEmpty(abiFilter)) {
            // Null or empty imply a universal APK, which would return the second highest score.
            return deviceAbi.size() - 1;
        }
        int match = deviceAbi.indexOf(abiFilter);
        if (match == 0) {
            // We want to select the output that matches the first deviceAbi.  The filtered output
            // is preferred over universal APK if it matches the first deviceAbi as they are likely
            // to take a shorter time to build.
            match = deviceAbi.size();  // highest possible score for the specified deviceAbi.
        } else if (match > 0) {
            // Universal APK may contain the best match even though it is not guaranteed, that's
            // why it is preferred over a filtered output that does not match the best ABI.
            match = deviceAbi.size() - match - 1;
        }
        return match;
    }

    private static boolean isMainApkCompatibleWithDevice(
            VariantOutput mainOutputFile,
            Collection<String> variantAbiFilters,
            Collection<String> deviceAbis) {
        // so far, we are not dealing with the pure split files...
        if (getFilter(mainOutputFile, OutputFile.ABI) == null
                && variantAbiFilters != null
                && !variantAbiFilters.isEmpty()) {
            // if we have a match that has no abi filter, and we have variant-level filters, then
            // we need to make sure that the variant filters are compatible with the device abis.
            for (String abi : deviceAbis) {
                if (variantAbiFilters.contains(abi)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Nullable
    private static String getFilter(
            @NonNull VariantOutput variantOutput, @NonNull String filterType) {
        Collection<FilterData> filters;
        try {
            filters = variantOutput.getFilters();
        } catch (UnsupportedOperationException ignored) {
            // Before plugin 3.0, only OutputFilter::getFilters exists, but not VariantOutput::getFilters.
            filters =
                    variantOutput
                            .getOutputs()
                            .stream()
                            .map(OutputFile::getFilters)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList());
        }
        for (FilterData filterData : filters) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }

    /**
     * Preparing the configuration string according to the format expected by the split-select
     * tool. This should not make sure that the configuration string is valid, but just fix
     * this know issue.
     *
     * <p>Devices having api level 21 have a config format that is not compatible with split-select.
     * The problem is that the split-select tool expects mcc310-mnc260, while the am get-config
     * command will output 310mcc-260mnc-... (mcc is always before mnc, and both are optional). That
     * is why the string is processed to match the expected format for these two dimensions.
     * The rest of the config string is not changed.
     *
     * @param deviceConfig device configuration to process
     * @return device configuration in format expected by the split-select tool
     */
    @VisibleForTesting
    public static String prepareConfigFormatMccMnc(@NonNull String deviceConfig) {
        Iterable<String> configParts = Splitter.on("-").split(deviceConfig);
        List<String> outputParts = Lists.newArrayList();

        // we should fix only the first 2 parts of the config, as mcc and mnc
        // are found only there (they can also be omitted from the config)
        int processed = 0;
        for (String part: configParts) {
            // both mcc and mnc parts are 6 characters long, also start with 3 digits
            boolean matchingFormat =
                    part.length() == 6
                            && Character.isDigit(part.charAt(0))
                            && Character.isDigit(part.charAt(1))
                            && Character.isDigit(part.charAt(2));

            if (processed == 0 && matchingFormat && part.endsWith("mcc")) {
                processed = 1;
                outputParts.add(fixSingleConfigDimension(part, "mcc"));
            } else if (processed < 2 && matchingFormat && part.endsWith("mnc")) {
                processed = 2;
                outputParts.add(fixSingleConfigDimension(part, "mnc"));
            } else {
                outputParts.add(part);
            }
        }

        return Joiner.on("-").join(outputParts);
    }

    private static String fixSingleConfigDimension(
            @NonNull String configDimension, @NonNull String dimensionName) {
        int nameStartIndex = configDimension.lastIndexOf(dimensionName);
        if (nameStartIndex > 0) {
            // put the dimension name first, than the rest
            return dimensionName + configDimension.substring(0, nameStartIndex);
        } else {
            // no fix needed
            return configDimension;
        }
    }
}
