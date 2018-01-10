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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Encapsulates settings for <a
 * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split">
 * building per-language (or locale) APKs</a>.
 *
 * <p><b>Note:</b> Building per-language APKs is supported only when <a
 * href="https://developer.android.com/topic/instant-apps/guides/config-splits.html">building
 * configuration APKs</a> for <a
 * href="https://developer.android.com/topic/instant-apps/index.html">Android Instant Apps</a>.
 */
public class LanguageSplitOptions {

    private boolean enable = false;
    private boolean auto = false;
    private Set<String> include;
    private final DeprecationReporter deprecationReporter;

    @Inject
    public LanguageSplitOptions(@NonNull DeprecationReporter deprecationReporter) {
        this.deprecationReporter = deprecationReporter;
    }

    /**
     * Collection of include patterns.
     */
    public Set<String> getInclude() {
        return include;
    }

    public void setInclude(@NonNull List<String> list) {
        include = Sets.newHashSet(list);
    }

    /**
     * Adds an include pattern.
     */
    public void include(@NonNull String... includes) {
        if (include == null) {
            include = Sets.newHashSet(includes);
            return;
        }

        include.addAll(Arrays.asList(includes));
    }

    @NonNull
    public Set<String> getApplicationFilters() {
        return include == null || !enable ? new HashSet<String>() : include;
    }

    /**
     * enables or disables splits for language
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * Returns true if splits should be generated for languages.
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * Sets whether the build system should determine the splits based on the "values-*" folders in
     * the resources. If the auto mode is set to true, the include list will be ignored.
     *
     * <p>Additionally, if AAPT2 is enabled and resources are included targeting multiple regional
     * variants of the same language, a single APK will be generated for each such language,
     * containing all of the resources for all of the targeted regions. For example: if resources
     * are included in "values-fr/," "values-fr-rCA/", and "values-fr-rBE/", a single configuration
     * APK will be built containing all of those resources.
     *
     * @param auto true to automatically set the splits list based on the folders presence, false to
     *     use the include list.
     */
    public void setAuto(boolean auto) {
        deprecationReporter.reportObsoleteUsage(
                "LanguageSplitOptions.auto",
                DeprecationReporter.DeprecationTarget.AUTO_SPLITS_OR_RES_CONFIG);
        this.auto = auto;
    }

    /**
     * Returns whether to use the automatic discovery mechanism for supported languages (true) or
     * the manual include list (false).
     * @return true for automatic, false for manual mode.
     */
    public boolean isAuto() {
        return auto;
    }
}
