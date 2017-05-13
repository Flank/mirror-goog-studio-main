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
package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.io.File;
import java.util.Map;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

/**
 * A task that processes the manifest
 */
public abstract class ManifestProcessorTask extends IncrementalTask {

    private File manifestOutputDirectory;

    private File aaptFriendlyManifestOutputDirectory;

    private File instantRunManifestOutputDirectory;

    /** The processed Manifest. */
    @NonNull
    @Internal
    public abstract File getManifestOutputFile();

    @Nullable
    @Internal
    public abstract File getInstantRunManifestOutputFile();

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @Nullable
    @Internal
    public abstract File getAaptFriendlyManifestOutputFile();

    /** The processed Manifest. */
    @OutputDirectory
    public File getManifestOutputDirectory() {
        return manifestOutputDirectory;
    }

    public void setManifestOutputDirectory(File manifestOutputFolder) {
        this.manifestOutputDirectory = manifestOutputFolder;
    }

    @OutputDirectory
    @Optional
    public File getInstantRunManifestOutputDirectory() {
        return instantRunManifestOutputDirectory;
    }

    public void setInstantRunManifestOutputDirectory(File instantRunManifestOutputDirectory) {
        this.instantRunManifestOutputDirectory = instantRunManifestOutputDirectory;
    }

    /**
     * The aapt friendly processed Manifest. In case we are processing a library manifest, some
     * placeholders may not have been resolved (and will be when the library is merged into the
     * importing application). However, such placeholders keys are not friendly to aapt which flags
     * some illegal characters. Such characters are replaced/encoded in this version.
     */
    @OutputDirectory
    @Optional
    public File getAaptFriendlyManifestOutputDirectory() {
        return aaptFriendlyManifestOutputDirectory;
    }

    public void setAaptFriendlyManifestOutputDirectory(File aaptFriendlyManifestOutputDirectory) {
        this.aaptFriendlyManifestOutputDirectory = aaptFriendlyManifestOutputDirectory;
    }


    /**
     * Serialize a map key+value pairs into a comma separated list. Map elements are sorted to
     * ensure stability between instances.
     *
     * @param mapToSerialize the map to serialize.
     */
    protected static String serializeMap(Map<String, Object> mapToSerialize) {
        final Joiner keyValueJoiner = Joiner.on(":");
        // transform the map on a list of key:value items, sort it and concatenate it.
        return Joiner.on(",").join(
                Ordering.natural().sortedCopy(Iterables.transform(
                        mapToSerialize.entrySet(),
                        (input) -> keyValueJoiner.join(input.getKey(), input.getValue()))));
    }
}
