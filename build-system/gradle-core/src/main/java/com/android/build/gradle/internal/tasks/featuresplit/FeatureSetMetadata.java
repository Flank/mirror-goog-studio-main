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

package com.android.build.gradle.internal.tasks.featuresplit;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_SET_METADATA;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;

/** Container for all the feature split metadata. */
public class FeatureSetMetadata {

    public interface SupplierProvider {
        @NonNull
        Supplier<String> getFeatureNameSupplierForTask(
                @NonNull VariantScope variantScope, @NonNull Task task);

        @NonNull
        Supplier<Integer> getResOffsetSupplierForTask(
                @NonNull VariantScope variantScope, @NonNull Task task);
    }

    @VisibleForTesting static final String OUTPUT_FILE_NAME = "feature-metadata.json";
    @VisibleForTesting public static final int BASE_ID = 0x80;

    private final Set<FeatureInfo> featureSplits;

    public FeatureSetMetadata() {
        featureSplits = new HashSet<>();
    }

    private FeatureSetMetadata(Set<FeatureInfo> featureSplits) {
        this.featureSplits = ImmutableSet.copyOf(featureSplits);
    }

    public void addFeatureSplit(@NonNull String modulePath, @NonNull String featureName) {
        featureSplits.add(new FeatureInfo(modulePath, featureName, BASE_ID + featureSplits.size()));
    }

    @Nullable
    public Integer getResOffsetFor(@NonNull String modulePath) {
        Optional<FeatureInfo> featureInfo =
                featureSplits
                        .stream()
                        .filter(metadata -> metadata.modulePath.equals(modulePath))
                        .findFirst();
        return featureInfo.isPresent() ? featureInfo.get().resOffset : null;
    }

    @Nullable
    public String getFeatureNameFor(@NonNull String modulePath) {
        Optional<FeatureInfo> featureInfo =
                featureSplits
                        .stream()
                        .filter(metadata -> metadata.modulePath.equals(modulePath))
                        .findFirst();
        return featureInfo.isPresent() ? featureInfo.get().featureName : null;
    }

    public void save(@NonNull File outputFile) throws IOException {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        Files.write(gson.toJson(featureSplits), outputFile, Charsets.UTF_8);
    }

    /**
     * Loads the feature set metadata file
     *
     * @param input the location of the file, or the folder that contains it.
     * @return the FeatureSetMetadata instance that contains all the data from the file
     * @throws IOException if the loading failed.
     */
    @NonNull
    public static FeatureSetMetadata load(@NonNull File input) throws IOException {
        if (input.isDirectory()) {
            input = new File(input, OUTPUT_FILE_NAME);
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        Type typeToken = new TypeToken<HashSet<FeatureInfo>>() {}.getType();
        try (FileReader fileReader = new FileReader(input)) {
            Set<FeatureInfo> featureIds = gson.fromJson(fileReader, typeToken);
            return new FeatureSetMetadata(featureIds);
        }
    }

    public static SupplierProvider getInstance() {
        return INSTANCE;
    }

    private static final SupplierProvider INSTANCE = new SupplierProviderImpl();

    private static class SupplierProviderImpl implements SupplierProvider {
        @NonNull
        @Override
        public Supplier<String> getFeatureNameSupplierForTask(
                @NonNull VariantScope variantScope, @NonNull Task task) {
            final FileCollection fc =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            MODULE,
                            FEATURE_SET_METADATA);

            // make the task depends on the file collection so that it runs after the file we need
            // as been created.
            // The file collection however is not an input so that we only re-run the task if the
            // one feature name we care about has changed.
            task.dependsOn(fc);

            final String gradlePath = task.getProject().getPath();

            return TaskInputHelper.memoize(
                    () -> {
                        try {
                            FeatureSetMetadata featureSetMetadata =
                                    FeatureSetMetadata.load(fc.getSingleFile());
                            return featureSetMetadata.getFeatureNameFor(gradlePath);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        @NonNull
        @Override
        public Supplier<Integer> getResOffsetSupplierForTask(
                @NonNull VariantScope variantScope, @NonNull Task task) {
            final FileCollection fc =
                    variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            MODULE,
                            FEATURE_SET_METADATA);

            // make the task depends on the file collection so that it runs after the file we need
            // as been created.
            // The file collection however is not an input so that we only re-run the task if the
            // one feature name we care about has changed.
            task.dependsOn(fc);

            final String gradlePath = task.getProject().getPath();

            return TaskInputHelper.memoize(
                    () -> {
                        try {
                            FeatureSetMetadata featureSetMetadata =
                                    FeatureSetMetadata.load(fc.getSingleFile());
                            return featureSetMetadata.getResOffsetFor(gradlePath);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static class FeatureInfo {
        final String modulePath;
        final String featureName;
        final int resOffset;

        FeatureInfo(String modulePath, String featureName, int resOffset) {
            this.modulePath = modulePath;
            this.featureName = featureName;
            this.resOffset = resOffset;
        }
    }
}
