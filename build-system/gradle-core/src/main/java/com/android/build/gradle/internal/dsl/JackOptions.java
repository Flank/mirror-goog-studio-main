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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Jack toolchain is <em>deprecated</em>.
 *
 * <p>If you want to use Java 8 language features, use the improved support included in the default
 * toolchain. To learn more, read <a
 * href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
 * features</a>.
 *
 * @deprecated For more information, read <a
 *     href="https://developer.android.com/studio/write/java8-support.html">Use Java 8 language
 *     features</a>.
 */
@Deprecated
@SuppressWarnings("UnnecessaryInheritDoc")
public class JackOptions implements CoreJackOptions {

    static final String DEPRECATION_URL =
            "https://d.android.com/r/tools/java-8-support-message.html";

    @Nullable
    private Boolean isEnabledFlag;
    @Nullable
    private Boolean isJackInProcessFlag;
    @NonNull
    private Map<String, String> additionalParameters = Maps.newHashMap();
    @NonNull
    private List<String> pluginNames = Lists.newArrayList();

    @NonNull private final DeprecationReporter deprecationReporter;

    public JackOptions(@NonNull DeprecationReporter deprecationReporter) {
        this.deprecationReporter = deprecationReporter;
    }

    void _initWith(CoreJackOptions that) {
        isEnabledFlag = that.isEnabled();
        isJackInProcessFlag = that.isJackInProcess();
        additionalParameters = Maps.newHashMap(that.getAdditionalParameters());
        pluginNames = Lists.newArrayList(that.getPluginNames());
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    @Nullable
    public Boolean isEnabled() {
        deprecationReporter.reportObsoleteUsage(
                "JackOptions.enabled", DEPRECATION_URL, DeprecationTarget.EOY2018);
        // Jack toolchain has been deprecated
        return null;
    }

    public void setEnabled(@Nullable Boolean enabled) {
        deprecationReporter.reportObsoleteUsage(
                "JackOptions.enabled", DEPRECATION_URL, DeprecationTarget.EOY2018);
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    @Nullable
    public Boolean isJackInProcess() {
        deprecationReporter.reportObsoleteUsage(
                "JackOptions.jackInProcess", DEPRECATION_URL, DeprecationTarget.EOY2018);
        return isJackInProcessFlag;
    }

    public void setJackInProcess(@Nullable Boolean jackInProcess) {
        deprecationReporter.reportObsoleteUsage(
                "JackOptions.jackInProcess", DEPRECATION_URL, DeprecationTarget.EOY2018);
        isJackInProcessFlag = jackInProcess;
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    @NonNull
    public Map<String, String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(@NonNull Map<String, String> additionalParameters) {
        this.additionalParameters.clear();
        this.additionalParameters.putAll(additionalParameters);
    }

    public void additionalParameters(@NonNull Map<String, String> additionalParameters) {
        this.additionalParameters.putAll(additionalParameters);
    }

    /** Sets the plugin names list to the specified one. */
    public void setPluginNames(@NonNull List<String> pluginNames) {
        this.pluginNames = Lists.newArrayList(pluginNames);
    }

    /** Adds the specified plugin names to the existing list of plugin names. */
    public void pluginNames(@NonNull String... pluginNames) {
        Collections.addAll(this.pluginNames, pluginNames);
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    @NonNull
    public List<String> getPluginNames() {
        return pluginNames;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("isEnabled", isEnabledFlag)
                .add("isJackInProcess", isJackInProcessFlag)
                .add("additionalParameters", isJackInProcessFlag)
                .add("pluginNames", pluginNames)
                .toString();
    }
}
