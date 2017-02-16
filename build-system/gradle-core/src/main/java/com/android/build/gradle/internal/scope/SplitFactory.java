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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.ide.common.build.Split;
import com.android.utils.Pair;
import com.android.utils.StringHelper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Factory for {@link Split} instances. Cannot be stored in any model related objects. */
public class SplitFactory {

    static final String UNIVERSAL = "universal";

    private final GradleVariantConfiguration variantConfiguration;
    private final GlobalScope globalScope;
    private final SplitScope splitScope;

    public SplitFactory(
            GlobalScope globalScope,
            GradleVariantConfiguration variantConfiguration,
            SplitScope splitScope) {
        this.globalScope = globalScope;
        this.variantConfiguration = variantConfiguration;
        this.splitScope = splitScope;
    }

    public Split addMainApk() {
        Split mainApk =
                // the main output basename comes from the variant configuration.
                // the main output should not have a dirName set as all the getXXXOutputDirectory
                // in variant scope already include the variant name.
                // TODO : we probably should clean this up, having the getXXXOutputDirectory APIs
                // return the top level folder and have all users use the getDirName() as part of
                // the task output folder configuration.
                new Main(
                        variantConfiguration.getBaseName(), variantConfiguration.getFullName(), "");
        checkNoDuplicate(mainApk);
        splitScope.addSplit(mainApk);
        return mainApk;
    }

    public Split addUniversalApk() {
        Split mainApk =
                new Universal(
                        variantConfiguration.computeBaseNameWithSplits(UNIVERSAL),
                        variantConfiguration.computeFullNameWithSplits(UNIVERSAL));
        checkNoDuplicate(mainApk);
        splitScope.addSplit(mainApk);
        return mainApk;
    }

    private void checkNoDuplicate(Split newSplit) {
        List<Split> splitsByType = splitScope.getSplitsByType(VariantOutput.OutputType.MAIN);
        if (!splitsByType.isEmpty()) {
            throw new RuntimeException(
                    "Cannot add "
                            + newSplit
                            + " in a scope that already"
                            + " has "
                            + Joiner.on(",").join(splitsByType));
        }
    }

    public Split addFullSplit(ImmutableList<Pair<OutputFile.FilterType, String>> filters) {
        ImmutableList<FilterData> filtersList =
                ImmutableList.copyOf(
                        filters.stream()
                                .map(
                                        filter ->
                                                new FilterDataImpl(
                                                        filter.getFirst(), filter.getSecond()))
                                .collect(Collectors.toList()));
        String filterName = FullSplit._getFilterName(filtersList);
        Split split =
                new FullSplit(
                        filterName,
                        variantConfiguration.computeBaseNameWithSplits(filterName),
                        variantConfiguration.computeFullNameWithSplits(filterName),
                        filtersList);
        splitScope.addSplit(split);
        return split;
    }

    public Split addConfigurationSplit(OutputFile.FilterType filterType, String s) {
        ImmutableList<FilterData> filtersList = ImmutableList.of(new FilterDataImpl(filterType, s));
        return addConfigurationSplit(filtersList);
    }

    public static String getFilterNameForSplits(Collection<FilterData> filters) {
        return Joiner.on("-")
                .join(filters.stream().map(FilterData::getIdentifier).collect(Collectors.toList()));
    }

    public Split addConfigurationSplit(ImmutableList<FilterData> filtersList) {
        String filterName = getFilterNameForSplits(filtersList);
        Split split =
                new DefaultSplit(
                        VariantOutput.OutputType.SPLIT,
                        filterName,
                        variantConfiguration.computeBaseNameWithSplits(filterName),
                        variantConfiguration.getFullName(),
                        variantConfiguration.getDirName(),
                        filtersList);
        splitScope.addSplit(split);
        return split;
    }

    private static final class Main extends Split {

        private final String baseName, fullName, dirName;

        private Main(String baseName, String fullName, String dirName) {
            this.baseName = baseName;
            this.fullName = fullName;
            this.dirName = dirName;
        }

        @Override
        public OutputFile.OutputType getType() {
            return OutputFile.OutputType.MAIN;
        }

        @Override
        public String getFilterName() {
            return null;
        }

        @Override
        public String getBaseName() {
            return baseName;
        }

        @Override
        public String getFullName() {
            return fullName;
        }

        @NonNull
        @Override
        public String getDirName() {
            return dirName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), baseName, fullName, dirName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            Main that = (Main) o;
            return Objects.equals(baseName, that.baseName)
                    && Objects.equals(fullName, that.fullName)
                    && Objects.equals(dirName, that.dirName);
        }
    }

    private static class Universal extends Split {
        private final String baseName, fullName;

        private Universal(String baseName, String fullName) {
            this.baseName = baseName;
            this.fullName = fullName;
        }

        @Override
        public OutputFile.OutputType getType() {
            return OutputType.FULL_SPLIT;
        }

        @Override
        public String getFilterName() {
            return UNIVERSAL;
        }

        @Override
        public String getBaseName() {
            return baseName;
        }

        @Override
        public String getFullName() {
            return fullName;
        }

        @NonNull
        @Override
        public String getDirName() {
            if (getFilters().isEmpty()) {
                return UNIVERSAL;
            }
            StringBuilder sb = new StringBuilder();
            for (FilterData filter : getFilters()) {
                sb.append(filter.getIdentifier()).append('/');
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            Universal that = (Universal) o;
            return Objects.equals(baseName, that.baseName)
                    && Objects.equals(fullName, that.fullName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), baseName, fullName);
        }
    }

    static class FullSplit extends Universal {
        private final ImmutableList<FilterData> filters;
        private final String filterName;

        private FullSplit(
                String filterName,
                String baseName,
                String fullName,
                ImmutableList<FilterData> filters) {
            super(baseName, fullName);
            this.filterName = filterName;
            this.filters = filters;
        }

        private static String _getFilterName(ImmutableList<FilterData> filters) {
            StringBuilder sb = new StringBuilder();
            String densityFilter = Split.getFilter(filters, OutputFile.FilterType.DENSITY);
            if (densityFilter != null) {
                sb.append(densityFilter);
            }
            String abiFilter = getFilter(filters, OutputFile.FilterType.ABI);
            if (abiFilter != null) {
                if (sb.length() > 0) {
                    sb.append(StringHelper.capitalize(abiFilter));
                } else {
                    sb.append(abiFilter);
                }
            }
            return sb.toString();
        }

        @Override
        public OutputFile.OutputType getType() {
            return OutputFile.OutputType.FULL_SPLIT;
        }

        @NonNull
        @Override
        public ImmutableList<FilterData> getFilters() {
            return filters;
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @NonNull
        @Override
        public String getDirName() {
            StringBuilder sb = new StringBuilder();
            for (FilterData filter : getFilters()) {
                sb.append(filter.getIdentifier()).append('/');
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            FullSplit that = (FullSplit) o;
            return Objects.equals(filterName, that.filterName)
                    && Objects.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), filterName, filters);
        }
    }

    public static class DefaultSplit extends Split {

        private final String filterName, baseName, fullName, dirName;
        private final ImmutableList<FilterData> filters;
        private final OutputType outputType;

        public DefaultSplit(
                OutputType outputType,
                String filterName,
                String baseName,
                String fullName,
                String dirName,
                ImmutableList<FilterData> filters) {
            this.outputType = outputType;
            this.filters = filters;
            this.filterName =
                    Joiner.on("-")
                            .join(
                                    filters.stream()
                                            .map(FilterData::getIdentifier)
                                            .collect(Collectors.toList()));
            this.baseName = baseName;
            this.fullName = fullName;
            this.dirName = dirName;
        }

        @Override
        public OutputFile.OutputType getType() {
            return outputType;
        }

        @Override
        public boolean requiresAapt() {
            return false;
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @NonNull
        @Override
        public ImmutableList<FilterData> getFilters() {
            return filters;
        }

        @Override
        public String getBaseName() {
            return baseName;
        }

        @Override
        public String getFullName() {
            return fullName;
        }

        @NonNull
        @Override
        public String getDirName() {
            return dirName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            DefaultSplit that = (DefaultSplit) o;
            return outputType == that.outputType
                    && Objects.equals(baseName, that.baseName)
                    && Objects.equals(fullName, that.fullName)
                    && Objects.equals(dirName, that.dirName)
                    // faster to compare the filterName than filters.
                    && Objects.equals(filterName, that.filterName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    super.hashCode(), outputType, baseName, fullName, dirName, filterName);
        }
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    static String _getFilter(Collection<FilterData> filters, OutputFile.FilterType filterType) {
        for (FilterData filter : filters) {
            if (FilterDataImpl.getType(filter) == filterType) {
                return filter.getIdentifier();
            }
        }
        return null;
    }
}
