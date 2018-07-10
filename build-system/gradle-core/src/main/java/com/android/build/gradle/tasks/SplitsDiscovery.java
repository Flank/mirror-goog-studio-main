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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.FN_SPLIT_LIST;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.SplitList;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Variant scoped task.
 *
 * <p>Task that will populate the {@link SplitList} variant scoped object keeping the list of pure
 * splits that should be built for this variant.
 *
 * <p>The task will also persist the list of splits in a gson file for consumption on subsequent
 * builds when there is no input changes to avoid rerunning it.
 *
 * TODO: Remove this and make users of InternalArtifactType.SPLIT_LIST read from VariantData.
 */
@Deprecated
@CacheableTask
public class SplitsDiscovery extends AndroidBuilderTask {

    Set<String> densityFilters;
    Set<String> languageFilters;
    Set<String> abiFilters;
    Collection<String> resourceConfigs;

    @Input
    @Optional
    public Set<String> getDensityFilters() {
        return densityFilters;
    }

    @Input
    @Optional
    public Set<String> getLanguageFilters() {
        return languageFilters;
    }

    @Input
    @Optional
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @Input
    public Collection<String> getResourceConfigs() {
        return resourceConfigs;
    }

    File persistedList;

    @OutputFile
    public File getPersistedList() {
        return persistedList;
    }

    @TaskAction
    void taskAction() throws IOException {
        SplitList.save(
                getPersistedList(),
                getFilters(getDensityFilters()),
                getFilters(getLanguageFilters()),
                getFilters(getAbiFilters()),
                getFilters(getResourceConfigs()));
    }

    /**
     * Gets the list of filter values for a filter type either from the user specified build.gradle
     * settings or through a discovery mechanism using folders names.
     *
     * @param filters the raw filter collection.
     * @return a possibly empty list of filter value for this filter type.
     */
    @NonNull
    private static List<SplitList.Filter> getFilters(Collection<String> filters) {

        Set<String> filtersList = new HashSet<>();
        if (filters != null) {
            filtersList.addAll(filters);
        }
        return filtersList.stream().map(SplitList.Filter::new).collect(Collectors.toList());
    }

    @Deprecated
    public static final class ConfigAction extends TaskConfigAction<SplitsDiscovery> {

        private final VariantScope variantScope;

        public ConfigAction(VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("splitsDiscoveryTask");
        }

        @NonNull
        @Override
        public Class<SplitsDiscovery> getType() {
            return SplitsDiscovery.class;
        }

        @Override
        public void execute(@NonNull SplitsDiscovery task) {
            task.setVariantName(variantScope.getFullVariantName());
            Splits splits = variantScope.getGlobalScope().getExtension().getSplits();

            // TODO: Check if this is still needed.
            task.persistedList =
                    variantScope
                            .getArtifacts()
                            .appendArtifact(InternalArtifactType.SPLIT_LIST, task, FN_SPLIT_LIST);

            if (splits.getDensity().isEnable()) {
                task.densityFilters = splits.getDensityFilters();
            }
            if (splits.getLanguage().isEnable()) {
                task.languageFilters = splits.getLanguageFilters();
            }
            if (splits.getAbi().isEnable()) {
                task.abiFilters = splits.getAbiFilters();
            }

            task.resourceConfigs =
                    variantScope
                            .getVariantConfiguration()
                            .getMergedFlavor()
                            .getResourceConfigurations();
        }
    }
}
