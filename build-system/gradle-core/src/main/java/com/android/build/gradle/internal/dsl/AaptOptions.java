/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.build.gradle.internal.LoggerWrapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * DSL object for configuring aapt options.
 */
public class AaptOptions implements com.android.builder.model.AaptOptions {

    @Nullable
    private String ignoreAssetsPattern;

    @Nullable
    private List<String> noCompressList;

    private boolean cruncherEnabled = true;

    private boolean failOnMissingConfigEntry = false;

    @Nullable
    private List<String> additionalParameters;

    private int cruncherProcesses = 0;

    public void setIgnoreAssetsPattern(@Nullable String ignoreAssetsPattern) {
        this.ignoreAssetsPattern = ignoreAssetsPattern;
    }

    public void setIgnoreAssets(@Nullable String ignoreAssetsPattern) {
        setIgnoreAssetsPattern(ignoreAssetsPattern);
    }

    /**
     * Pattern describing assets to be ignore.
     *
     * <p>See <code>aapt --help</code>
     */
    @Override
    @Optional
    @Input
    public String getIgnoreAssets() {
        return ignoreAssetsPattern;
    }

    /**
     * Pattern describing assets to be ignore.
     *
     * <p>See <code>aapt --help</code>
     */
    public String getIgnoreAssetsPattern() {
        return ignoreAssetsPattern;
    }

    public void setNoCompress(String noCompress) {
        setNoCompress(new String[] { noCompress });
    }

    public void setNoCompress(String... noCompress) {
        for (String p : noCompress) {
            if (p.equals("\"\"")) {
                LoggerWrapper.getLogger(AaptOptions.class).warning("noCompress pattern '\"\"' "
                        + "no longer matches every file. It now matches exactly two double quote "
                        + "characters. Please use '' instead.");
            }
        }

        noCompressList = Arrays.asList(noCompress);
    }

    /**
     * Extensions of files that will not be stored compressed in the APK. Adding an empty
     * extension, <i>i.e.</i>, setting {@code noCompress ''} will trivially disable compression
     * for all files.
     *
     * <p>Equivalent of the -0 flag. See <code>aapt --help</code>
     */
    @Override
    @Optional
    @Input
    public Collection<String> getNoCompress() {
        return noCompressList;
    }

    public void useNewCruncher(boolean value) {
        LoggerWrapper.getLogger(AaptOptions.class).warning("useNewCruncher has been deprecated. "
                + "It will be removed in a future version of the gradle plugin. New cruncher is "
                + "now always enabled.");
    }

    public void setUseNewCruncher(boolean value) {
        LoggerWrapper.getLogger(AaptOptions.class).warning("useNewCruncher has been deprecated. "
                + "It will be removed in a future version of the gradle plugin. New cruncher is "
                + "now always enabled.");
    }

    /**
     * Enables or disables PNG crunching.
     */
    public void setCruncherEnabled(boolean value) {
        cruncherEnabled = value;
    }

    /**
     * Returns true if the PNGs should be crunched, false otherwise.
     */
    @Input
    public boolean getCruncherEnabled() {
        return cruncherEnabled;
    }
    
    /**
     * Whether to use the new cruncher.
     */
    @Input
    public boolean getUseNewCruncher() {
        return true;
    }

    public void failOnMissingConfigEntry(boolean value) {
        failOnMissingConfigEntry = value;
    }

    public void setFailOnMissingConfigEntry(boolean value) {
        failOnMissingConfigEntry = value;
    }

    /**
     * Forces aapt to return an error if it fails to find an entry for a configuration.
     *
     * <p>See <code>aapt --help</code>
     */
    @Override
    @Input
    public boolean getFailOnMissingConfigEntry() {
        return failOnMissingConfigEntry;
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /**
     * Sets extensions of files that will not be stored compressed in the APK.
     *
     * <p>Equivalent of the -0 flag. See <code>aapt --help</code>
     */
    public void noCompress(String noCompress) {
        noCompressList = Collections.singletonList(noCompress);
    }

    /**
     * Sets extensions of files that will not be stored compressed in the APK.
     *
     * <p>Equivalent of the -0 flag. See <code>aapt --help</code>
     */
    public void noCompress(String... noCompress) {
        noCompressList = Arrays.asList(noCompress);
    }

    /**
     * Adds additional parameters to be passed to {@code aapt}.
     */
    public void additionalParameters(@NonNull String param) {
        additionalParameters = Collections.singletonList(param);
    }

    /**
     * Adds additional parameters to be passed to {@code aapt}.
     */
    public void additionalParameters(String... params) {
        additionalParameters = Arrays.asList(params);
    }

    public void setAdditionalParameters(@Nullable List<String> parameters) {
        additionalParameters = parameters;
    }

    /**
     * Returns the list of additional parameters to pass to {@code appt}.
     */
    @Nullable
    @Override
    @Optional
    @Input
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setCruncherProcesses(int cruncherProcesses) {
        this.cruncherProcesses = cruncherProcesses;
    }

    /**
     * Obtains the number of cruncher processes to use. More cruncher processes will crunch files
     * faster, but will require more memory and CPU.
     *
     * @return the number of cruncher processes, {@code 0} to use the default
     */
    public int getCruncherProcesses() {
        return cruncherProcesses;
    }
}
