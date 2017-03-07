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
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.SyncIssue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DSL object for configuring Jack options.
 *
 * <p>See <a href="https://developer.android.com/studio/build/jack.html">Jack and Jill</a>
 */
@SuppressWarnings("UnnecessaryInheritDoc")
public class JackOptions implements CoreJackOptions {

    static final String DEPRECATION_WARNING =
            "Jack toolchain has been deprecated, and will not run. "
                    + "Please delete the 'jackOptions { ... }' block from your build file, as "
                    + "it will be incompatible with next version of the Android plugin for Gradle.";

    @Nullable
    private Boolean isEnabledFlag;
    @Nullable
    private Boolean isJackInProcessFlag;
    @NonNull
    private Map<String, String> additionalParameters = Maps.newHashMap();
    @NonNull
    private List<String> pluginNames = Lists.newArrayList();

    @NonNull private final ErrorReporter errorReporter;

    public JackOptions(@NonNull ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
    }

    void _initWith(CoreJackOptions that) {
        isEnabledFlag = that.isEnabled();
        isJackInProcessFlag = that.isJackInProcess();
        additionalParameters = Maps.newHashMap(that.getAdditionalParameters());
        pluginNames = Lists.newArrayList(that.getPluginNames());
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    @Deprecated
    public Boolean isEnabled() {
        // Jack toolchain has been deprecated
        return null;
    }

    @Deprecated
    public void setEnabled(@Nullable Boolean enabled) {
        errorReporter.handleSyncWarning(null, SyncIssue.TYPE_GENERIC, DEPRECATION_WARNING);
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public Boolean isJackInProcess() {
        return isJackInProcessFlag;
    }

    public void setJackInProcess(@Nullable Boolean jackInProcess) {
        isJackInProcessFlag = jackInProcess;
    }

    /** {@inheritDoc} */
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
    @Override
    @NonNull
    public List<String> getPluginNames() {
        return pluginNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JackOptions that = (JackOptions) o;
        return Objects.equal(isEnabledFlag, that.isEnabledFlag)
                && Objects.equal(isJackInProcessFlag, that.isJackInProcessFlag)
                && Objects.equal(additionalParameters, that.additionalParameters)
                && Objects.equal(pluginNames, that.pluginNames);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                isEnabledFlag, isJackInProcessFlag, additionalParameters, pluginNames);
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
