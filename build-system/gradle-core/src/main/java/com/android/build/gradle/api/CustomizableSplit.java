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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import java.util.List;

/**
 * Interface for customizing output splits. Registers and {@see Action} on {@see
 * BaseVariant#registerSplitCustomizer} to get notified by the build system of a split creation.
 */
public interface CustomizableSplit {

    /**
     * Split name
     *
     * @return the split name
     */
    @NonNull
    String getName();

    /**
     * Split type
     *
     * @return the split type
     */
    @NonNull
    OutputFile.OutputType getType();

    /**
     * List of filters associated with this split, empty if no filters were used.
     *
     * @return the list of filters for this split.
     */
    @NonNull
    List<FilterData> getFilters();

    /**
     * Returns the filter value for a particular filter type.
     *
     * @param filterType the filter type as a {@link String} representation of {@link
     *     OutputFile.FilterType}
     * @return the filter value if there is such a filter on this split, null otherwise.
     */
    @Nullable
    String getFilter(String filterType);

    /**
     * Sets the version code to use for this split. Each split can have a distinct version code to
     * be disambiguated by the Play Store.
     *
     * @param version the new split version.
     */
    void setVersionCode(int version);

    /**
     * Sets the version name to use for this split.
     *
     * @param versionName the new split version name.
     */
    void setVersionName(String versionName);

    /**
     * Sets the output file name to use for this split
     *
     * @param outputFileName
     */
    void setOutputFileName(String outputFileName);
}
