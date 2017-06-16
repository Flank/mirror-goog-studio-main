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

package com.android.ide.common.builder.model;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of a {@link BaseArtifact}. */
public abstract class IdeBaseArtifactImpl extends IdeModel implements IdeBaseArtifact {
    private static final String[] TEST_ARTIFACT_NAMES = {ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST};

    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myName;
    @NonNull private final String myCompileTaskName;
    @NonNull private final String myAssembleTaskName;
    @NonNull private final File myClassesFolder;
    @NonNull private final IdeDependencies myDependencies;
    @NonNull private final Set<String> myIdeSetupTaskNames;
    @NonNull private final Collection<File> myGeneratedSourceFolders;
    @NonNull private final Set<File> myAdditionalClassFolders;
    @NonNull private final IdeLevel2Dependencies myLevel2Dependencies;
    @Nullable private final IdeDependencies myCompileDependencies;
    @Nullable private final File myJavaResourcesFolder;
    @Nullable private final DependencyGraphs myDependencyGraphs;
    @Nullable private final IdeSourceProvider myVariantSourceProvider;
    @Nullable private final IdeSourceProvider myMultiFlavorSourceProvider;
    private final int myHashCode;

    protected IdeBaseArtifactImpl(
            @NonNull BaseArtifact artifact,
            @NonNull ModelCache modelCache,
            @NonNull IdeLevel2DependenciesFactory dependenciesFactory,
            @Nullable GradleVersion modelVersion) {
        super(artifact, modelCache);
        myName = artifact.getName();
        myCompileTaskName = artifact.getCompileTaskName();
        myAssembleTaskName = artifact.getAssembleTaskName();
        myClassesFolder = artifact.getClassesFolder();
        myJavaResourcesFolder = copyNewProperty(artifact::getJavaResourcesFolder, null);
        myDependencies = copy(artifact.getDependencies(), modelCache, modelVersion);
        myCompileDependencies =
                copyNewProperty(
                        modelCache,
                        artifact::getCompileDependencies,
                        dependencies ->
                                new IdeDependenciesImpl(dependencies, modelCache, modelVersion),
                        null);

        if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
            myDependencyGraphs =
                    modelCache.computeIfAbsent(
                            artifact.getDependencyGraphs(),
                            graphs -> new IdeDependencyGraphs(graphs, modelCache));
        } else {
            myDependencyGraphs = null;
        }

        myIdeSetupTaskNames = ImmutableSet.copyOf(getIdeSetupTaskNames(artifact));
        myGeneratedSourceFolders = ImmutableList.copyOf(getGeneratedSourceFolders(artifact));
        myVariantSourceProvider =
                createSourceProvider(modelCache, artifact.getVariantSourceProvider());
        myMultiFlavorSourceProvider =
                createSourceProvider(modelCache, artifact.getMultiFlavorSourceProvider());
        myAdditionalClassFolders =
                copyNewProperty(artifact::getAdditionalClassesFolders, Collections.emptySet());
        myLevel2Dependencies = dependenciesFactory.create(artifact, modelVersion);
        myHashCode = calculateHashCode();
    }

    @NonNull
    private static IdeDependencies copy(
            @NonNull Dependencies original,
            @NonNull ModelCache modelCache,
            @Nullable GradleVersion modelVersion) {
        return modelCache.computeIfAbsent(
                original,
                dependencies -> new IdeDependenciesImpl(dependencies, modelCache, modelVersion));
    }

    @NonNull
    private static Set<String> getIdeSetupTaskNames(@NonNull BaseArtifact artifact) {
        try {
            // This method was added in 1.1 - we have to handle the case when it's missing on the Gradle side.
            return ImmutableSet.copyOf(artifact.getIdeSetupTaskNames());
        } catch (NoSuchMethodError | UnsupportedMethodException e) {
            if (artifact instanceof AndroidArtifact) {
                return Collections.singleton(((AndroidArtifact) artifact).getSourceGenTaskName());
            }
        }
        return Collections.emptySet();
    }

    @NonNull
    private static Collection<File> getGeneratedSourceFolders(@NonNull BaseArtifact artifact) {
        try {
            Collection<File> folders = artifact.getGeneratedSourceFolders();
            // JavaArtifactImpl#getGeneratedSourceFolders returns null even though BaseArtifact#getGeneratedSourceFolders is marked as @NonNull.
            // See https://code.google.com/p/android/issues/detail?id=216236
            //noinspection ConstantConditions
            return folders != null ? folders : Collections.emptyList();
        } catch (UnsupportedMethodException e) {
            // Model older than 1.2.
        }
        return Collections.emptyList();
    }

    @Nullable
    private static IdeSourceProvider createSourceProvider(
            @NonNull ModelCache modelCache, @Nullable SourceProvider original) {
        return original != null
                ? modelCache.computeIfAbsent(
                        original, provider -> new IdeSourceProvider(provider, modelCache))
                : null;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getCompileTaskName() {
        return myCompileTaskName;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        return myAssembleTaskName;
    }

    @Override
    @NonNull
    public File getClassesFolder() {
        return myClassesFolder;
    }

    @Override
    @NonNull
    public File getJavaResourcesFolder() {
        if (myJavaResourcesFolder != null) {
            return myJavaResourcesFolder;
        }
        throw new UnsupportedMethodException(
                "Unsupported method: BaseArtifact.getJavaResourcesFolder");
    }

    @Override
    @NonNull
    public IdeDependencies getDependencies() {
        return myDependencies;
    }

    @Override
    @NonNull
    public Dependencies getCompileDependencies() {
        if (myCompileDependencies != null) {
            return myCompileDependencies;
        }
        throw new UnsupportedMethodException(
                "Unsupported method: BaseArtifact.getCompileDependencies()");
    }

    @Override
    @NonNull
    public DependencyGraphs getDependencyGraphs() {
        if (myDependencyGraphs != null) {
            return myDependencyGraphs;
        }
        // Since this method is marked as @NonNull, it is not defined what to do when invoked while using older models. For now, we
        // keep the default behavior and throw an exception.
        throw new UnsupportedMethodException(
                "Unsupported method: BaseArtifact.getDependencyGraphs");
    }

    @Override
    @NonNull
    public Set<String> getIdeSetupTaskNames() {
        return myIdeSetupTaskNames;
    }

    @Override
    @NonNull
    public IdeLevel2Dependencies getLevel2Dependencies() {
        return myLevel2Dependencies;
    }

    @Override
    @NonNull
    public Collection<File> getGeneratedSourceFolders() {
        return myGeneratedSourceFolders;
    }

    @Override
    @Nullable
    public IdeSourceProvider getVariantSourceProvider() {
        return myVariantSourceProvider;
    }

    @Override
    @Nullable
    public IdeSourceProvider getMultiFlavorSourceProvider() {
        return myMultiFlavorSourceProvider;
    }

    @Override
    public Set<File> getAdditionalClassesFolders() {
        return myAdditionalClassFolders;
    }

    @Override
    public boolean isTestArtifact() {
        return Arrays.asList(TEST_ARTIFACT_NAMES).contains(myName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeBaseArtifactImpl)) {
            return false;
        }
        IdeBaseArtifactImpl artifact = (IdeBaseArtifactImpl) o;
        return artifact.canEquals(this)
                && Objects.equals(myName, artifact.myName)
                && Objects.equals(myCompileTaskName, artifact.myCompileTaskName)
                && Objects.equals(myAssembleTaskName, artifact.myAssembleTaskName)
                && Objects.equals(myClassesFolder, artifact.myClassesFolder)
                && Objects.equals(myAdditionalClassFolders, artifact.myAdditionalClassFolders)
                && Objects.equals(myJavaResourcesFolder, artifact.myJavaResourcesFolder)
                && Objects.equals(myDependencies, artifact.myDependencies)
                && Objects.equals(myLevel2Dependencies, artifact.myLevel2Dependencies)
                && Objects.equals(myCompileDependencies, artifact.myCompileDependencies)
                && Objects.equals(myDependencyGraphs, artifact.myDependencyGraphs)
                && Objects.equals(myIdeSetupTaskNames, artifact.myIdeSetupTaskNames)
                && Objects.equals(myGeneratedSourceFolders, artifact.myGeneratedSourceFolders)
                && Objects.equals(myVariantSourceProvider, artifact.myVariantSourceProvider)
                && Objects.equals(
                        myMultiFlavorSourceProvider, artifact.myMultiFlavorSourceProvider);
    }

    protected boolean canEquals(Object other) {
        return other instanceof IdeBaseArtifactImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myName,
                myCompileTaskName,
                myAssembleTaskName,
                myClassesFolder,
                myJavaResourcesFolder,
                myDependencies,
                myLevel2Dependencies,
                myCompileDependencies,
                myDependencyGraphs,
                myIdeSetupTaskNames,
                myGeneratedSourceFolders,
                myVariantSourceProvider,
                myMultiFlavorSourceProvider,
                myAdditionalClassFolders);
    }

    @Override
    public String toString() {
        return "myName='"
                + myName
                + '\''
                + ", myCompileTaskName='"
                + myCompileTaskName
                + '\''
                + ", myAssembleTaskName='"
                + myAssembleTaskName
                + '\''
                + ", myClassesFolder="
                + myClassesFolder
                + ", myJavaResourcesFolder="
                + myJavaResourcesFolder
                + ", myDependencies="
                + myDependencies
                + ", myLevel2Dependencies"
                + myLevel2Dependencies
                + ", myCompileDependencies="
                + myCompileDependencies
                + ", myDependencyGraphs="
                + myDependencyGraphs
                + ", myIdeSetupTaskNames="
                + myIdeSetupTaskNames
                + ", myGeneratedSourceFolders="
                + myGeneratedSourceFolders
                + ", myVariantSourceProvider="
                + myVariantSourceProvider
                + ", myMultiFlavorSourceProvider="
                + myMultiFlavorSourceProvider;
    }
}
