/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.ide.common.blame.parser.JsonEncodedGradleMessageParser.STDOUT_ERROR_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.dependency.ConfigurationDependencyGraphs;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.ide.ArtifactMetaDataImpl;
import com.android.build.gradle.internal.ide.JavaArtifactImpl;
import com.android.build.gradle.internal.ide.SyncIssueImpl;
import com.android.build.gradle.internal.variant.DefaultSourceProviderContainer;
import com.android.builder.core.ErrorReporter;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageJsonSerializer;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * For storing additional model information.
 */
public class ExtraModelInfo extends ErrorReporter {

    @NonNull
    private final Project project;

    @NonNull
    private final ErrorFormatMode errorFormatMode;

    private final Map<SyncIssueKey, SyncIssue> syncIssues = Maps.newHashMap();

    private final Map<String, ArtifactMetaData> extraArtifactMap = Maps.newHashMap();
    private final ListMultimap<String, AndroidArtifact> extraAndroidArtifacts = ArrayListMultimap.create();
    private final ListMultimap<String, JavaArtifact> extraJavaArtifacts = ArrayListMultimap.create();

    private final ListMultimap<String, SourceProviderContainer> extraBuildTypeSourceProviders = ArrayListMultimap.create();
    private final ListMultimap<String, SourceProviderContainer> extraProductFlavorSourceProviders = ArrayListMultimap.create();
    private final ListMultimap<String, SourceProviderContainer> extraMultiFlavorSourceProviders = ArrayListMultimap.create();

    @Nullable
    private final Gson mGson;

    public ExtraModelInfo(@NonNull Project project) {
        super(computeModelQueryMode(project));
        this.project = project;
        errorFormatMode = computeErrorFormatMode(project);
        if (errorFormatMode == ErrorFormatMode.MACHINE_PARSABLE) {
            GsonBuilder gsonBuilder = new GsonBuilder();
            MessageJsonSerializer.registerTypeAdapters(gsonBuilder);
            mGson = gsonBuilder.create();
        } else {
            mGson = null;
        }
    }

    public Map<SyncIssueKey, SyncIssue> getSyncIssues() {
        return syncIssues;
    }

    @Override
    @NonNull
    public SyncIssue handleIssue(
            @Nullable String data,
            int type,
            int severity,
            @NonNull String msg) {
        SyncIssue issue;
        switch (getMode()) {
            case STANDARD:
                if (severity != SyncIssue.SEVERITY_WARNING && !isDependencyIssue(type)) {
                    throw new GradleException(msg);
                }
                // if it's a dependency issue we don't throw right away. we'll
                // throw during build instead.
                // but we do log.
                project.getLogger().warn("WARNING: " + msg);
                issue = new SyncIssueImpl(type, severity, data, msg);
                break;
            case IDE_LEGACY:
                // compat mode for the only issue supported before the addition of SyncIssue
                // in the model.
                if (severity != SyncIssue.SEVERITY_WARNING
                        && type != SyncIssue.TYPE_UNRESOLVED_DEPENDENCY) {
                    throw new GradleException(msg);
                }
                // intended fall-through
            case IDE:
                // new IDE, able to support SyncIssue.
                issue = new SyncIssueImpl(type, severity, data, msg);
                syncIssues.put(SyncIssueKey.from(issue), issue);
                break;
            default:
                throw new RuntimeException("Unknown SyncIssue type");
        }

        return issue;
    }

    private static boolean isDependencyIssue(int type) {
        switch (type) {
            case SyncIssue.TYPE_UNRESOLVED_DEPENDENCY:
            case SyncIssue.TYPE_DEPENDENCY_IS_APK:
            case SyncIssue.TYPE_DEPENDENCY_IS_APKLIB:
            case SyncIssue.TYPE_NON_JAR_LOCAL_DEP:
            case SyncIssue.TYPE_NON_JAR_PACKAGE_DEP:
            case SyncIssue.TYPE_NON_JAR_PROVIDED_DEP:
            case SyncIssue.TYPE_JAR_DEPEND_ON_AAR:
            case SyncIssue.TYPE_MISMATCH_DEP:
                return true;
        }

        return false;

    }

    @Override
    public void receiveMessage(@NonNull Message message) {
        switch (message.getKind()) {
            case ERROR:
                if (errorFormatMode == ErrorFormatMode.MACHINE_PARSABLE) {
                    project.getLogger().error(machineReadableMessage(message));
                } else {
                    project.getLogger().error(humanReadableMessage(message));
                }
                break;
            case WARNING:
                if (errorFormatMode == ErrorFormatMode.MACHINE_PARSABLE) {
                    project.getLogger().warn(machineReadableMessage(message));
                } else {
                    project.getLogger().warn(humanReadableMessage(message));
                }
                break;
            case INFO:
                project.getLogger().info(humanReadableMessage(message));
                break;
            case STATISTICS:
                project.getLogger().trace(humanReadableMessage(message));
                break;
            case UNKNOWN:
                project.getLogger().debug(humanReadableMessage(message));
                break;
            case SIMPLE:
                project.getLogger().debug(humanReadableMessage(message));
                break;
        }
    }

    private static String humanReadableMessage(@NonNull Message message) {
        StringBuilder errorStringBuilder = new StringBuilder();
        List<SourceFilePosition> positions = message.getSourceFilePositions();
        if (positions.size() != 1 ||
                !SourceFilePosition.UNKNOWN.equals(Iterables.getOnlyElement(positions))) {
            errorStringBuilder.append(Joiner.on(' ').join(positions));
        }
        if (errorStringBuilder.length() > 0) {
            errorStringBuilder.append(": ");
        }
        if (message.getToolName().isPresent()) {
            errorStringBuilder.append(message.getToolName().get()).append(": ");
        }
        errorStringBuilder.append(message.getText());

        String rawMessage = message.getRawMessage();
        if (!message.getText().equals(message.getRawMessage())) {
            String separator = SdkUtils.getLineSeparator();
            errorStringBuilder.append("\n    ")
                    .append(rawMessage.replace(separator, separator + "    "));
        }
        return errorStringBuilder.toString();
    }

    /**
     * Only call if errorFormatMode == {@link ErrorFormatMode#MACHINE_PARSABLE}
     */
    private String machineReadableMessage(@NonNull Message message) {
        Preconditions.checkNotNull(mGson);
        return STDOUT_ERROR_TAG + mGson.toJson(message);
    }

    public Collection<ArtifactMetaData> getExtraArtifacts() {
        return extraArtifactMap.values();
    }

    public Collection<AndroidArtifact> getExtraAndroidArtifacts(@NonNull String variantName) {
        return extraAndroidArtifacts.get(variantName);
    }

    public Collection<JavaArtifact> getExtraJavaArtifacts(@NonNull String variantName) {
        return extraJavaArtifacts.get(variantName);
    }

    public Collection<SourceProviderContainer> getExtraFlavorSourceProviders(
            @NonNull String flavorName) {
        return extraProductFlavorSourceProviders.get(flavorName);
    }

    public Collection<SourceProviderContainer> getExtraBuildTypeSourceProviders(
            @NonNull String buildTypeName) {
        return extraBuildTypeSourceProviders.get(buildTypeName);
    }

    public void registerArtifactType(@NonNull String name,
            boolean isTest,
            int artifactType) {

        if (extraArtifactMap.get(name) != null) {
            throw new IllegalArgumentException(
                    String.format("Artifact with name %1$s already registered.", name));
        }

        extraArtifactMap.put(name, new ArtifactMetaDataImpl(name, isTest, artifactType));
    }

    public void registerBuildTypeSourceProvider(@NonNull String name,
            @NonNull CoreBuildType buildType,
            @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }

        extraBuildTypeSourceProviders.put(buildType.getName(),
                new DefaultSourceProviderContainer(name, sourceProvider));

    }

    public void registerProductFlavorSourceProvider(@NonNull String name,
            @NonNull CoreProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }

        extraProductFlavorSourceProviders.put(productFlavor.getName(),
                new DefaultSourceProviderContainer(name, sourceProvider));

    }

    public void registerMultiFlavorSourceProvider(@NonNull String name,
            @NonNull String flavorName,
            @NonNull SourceProvider sourceProvider) {
        if (extraArtifactMap.get(name) == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }

        extraMultiFlavorSourceProviders.put(flavorName,
                new DefaultSourceProviderContainer(name, sourceProvider));
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Collection<File> generatedSourceFolders,
            @NonNull Iterable<String> ideSetupTaskNames,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @NonNull File javaResourcesFolder,
            @Nullable SourceProvider sourceProvider) {
        ArtifactMetaData artifactMetaData = extraArtifactMap.get(name);
        if (artifactMetaData == null) {
            throw new IllegalArgumentException(String.format(
                    "Artifact with name %1$s is not yet registered. Use registerArtifactType()",
                    name));
        }
        if (artifactMetaData.getType() != ArtifactMetaData.TYPE_JAVA) {
            throw new IllegalArgumentException(
                    String.format("Artifact with name %1$s is not of type JAVA", name));
        }

        JavaArtifact artifact = new JavaArtifactImpl(
                name, assembleTaskName, javaCompileTaskName,
                ideSetupTaskNames, generatedSourceFolders, classesFolder, javaResourcesFolder, null,
                new ConfigurationDependencies(configuration),
                new ConfigurationDependencyGraphs(configuration),
                sourceProvider, null);

        extraJavaArtifacts.put(variant.getName(), artifact);
    }

    /**
     * Returns whether we are just trying to build a model for the IDE instead of building. This
     * means we will attempt to resolve dependencies even if some are broken/unsupported to avoid
     * failing the import in the IDE.
     */
    private static EvaluationMode computeModelQueryMode(@NonNull Project project) {
        if (AndroidGradleOptions.buildModelOnlyAdvanced(project)) {
            return EvaluationMode.IDE;
        }

        if (AndroidGradleOptions.buildModelOnly(project)) {
            return EvaluationMode.IDE_LEGACY;
        }

        return EvaluationMode.STANDARD;
    }

    private static ErrorFormatMode computeErrorFormatMode(@NonNull Project project) {
        if (AndroidGradleOptions.invokedFromIde(project)) {
            return ErrorFormatMode.MACHINE_PARSABLE;
        } else {
            return ErrorFormatMode.HUMAN_READABLE;
        }
    }

    @NonNull
    public ErrorFormatMode getErrorFormatMode() {
        return errorFormatMode;
    }

    public enum ErrorFormatMode {
        MACHINE_PARSABLE, HUMAN_READABLE
    }

    /**
     * Creates a key from a SyncIssue to use in a map.
     */
    @Immutable
    static final class SyncIssueKey {

        private final int type;

        @Nullable
        private final String data;

        static SyncIssueKey from(@NonNull SyncIssue syncIssue) {
            return new SyncIssueKey(syncIssue.getType(), syncIssue.getData());
        }

        private SyncIssueKey(int type, @Nullable String data) {
            this.type = type;
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SyncIssueKey that = (SyncIssueKey) o;
            return type == that.type &&
                    Objects.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, data);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("type", type)
                    .add("data", data)
                    .toString();
        }
    }
}
