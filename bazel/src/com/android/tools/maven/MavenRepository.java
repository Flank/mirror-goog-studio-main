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

package com.android.tools.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.resolution.ModelResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * A maven repository.
 *
 * <p>Provides a friendly API to accessing a local maven repository, by encapsulating all the aether
 * requests.
 */
public class MavenRepository {

    private static final Map<String, String> EXTENSIONS_MAP =
            ImmutableMap.of("bundle", "jar", "maven-plugin", "jar", "eclipse-plugin", "jar");

    public static String getArtifactExtension(Model model) {
        return EXTENSIONS_MAP.getOrDefault(model.getPackaging(), model.getPackaging());
    }

    private final Path mRepoDirectory;
    private final ModelBuilder mModelBuilder;
    private final ModelResolver mModelResolver;
    private final RepositorySystem mRepositorySystem;
    private final DefaultRepositorySystemSession mRepositorySystemSession;
    private final LocalRepositoryManager mLocalRepositoryManager;

    public MavenRepository(Path repoDirectory) {
        this(repoDirectory, new LocalModelResolver(repoDirectory));
    }

    public MavenRepository(Path repoDirectory, ModelResolver resolver) {
        mRepoDirectory = checkNotNull(repoDirectory);
        mModelBuilder = new DefaultModelBuilderFactory().newInstance();
        mModelResolver = resolver;
        mRepositorySystem = AetherUtils.getRepositorySystem();
        mRepositorySystemSession =
                AetherUtils.getRepositorySystemSession(mRepositorySystem, repoDirectory);
        mLocalRepositoryManager = mRepositorySystemSession.getLocalRepositoryManager();
    }

    public Path getDirectory() {
        return mRepoDirectory;
    }

    public DefaultRepositorySystemSession getRepositorySystemSession() {
        return mRepositorySystemSession;
    }


    public Model getPomEffectiveModel(Path pomFile) {
        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setModelSource(new FileModelSource(pomFile.toFile()));
        request.setModelResolver(mModelResolver);
        request.setSystemProperties(System.getProperties());
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        try {
            return mModelBuilder.build(request).getEffectiveModel();
        } catch (ModelBuildingException e) {
            System.err.println(e.toString());
            return null;
        }
    }

    public Path getRelativePath(Artifact artifact) {
        return Paths.get(mLocalRepositoryManager.getPathForLocalArtifact(artifact));
    }

    private Path getPath(Artifact artifact) {
        return mRepoDirectory.resolve(getRelativePath(artifact));
    }

    private Path getPath(String group, String artifact, String extension, String version) {
        return getPath(new DefaultArtifact(group, artifact, extension, version));
    }

    private Path getPath(String group, String artifact, String classifier, String extension, String version) {
        return getPath(new DefaultArtifact(group, artifact, classifier, extension, version));
    }

    public Path getArtifactPath(Model model, String classifier) {
        return getPath(
                model.getGroupId(),
                model.getArtifactId(),
                classifier,
                getArtifactExtension(model),
                model.getVersion());
    }

    public Path getArtifactPath(Model model) {
        return getPath(
                model.getGroupId(),
                model.getArtifactId(),
                getArtifactExtension(model),
                model.getVersion());
    }

    public Path getSourceArtifactPath(Model model) {
        return getPath(new DefaultArtifact(
                model.getGroupId(),
                model.getArtifactId(),
                "sources",
                getArtifactExtension(model),
                model.getVersion()));
    }

    public Map<String, Path> getExecutables(Model model) {
        Map<String, Path> exes = Maps.newHashMap();
        String[] classifiers = {"linux-x86_64", "osx-x86_64", "windows-x86_64"};
        for (String classifier : classifiers) {
            exes.put(classifier, getPath(new DefaultArtifact(
                    model.getGroupId(),
                    model.getArtifactId(),
                    classifier,
                    "exe",
                    model.getVersion())));
        }
        return exes;
    }

    public Path getParentPomPath(Model model) {
        return getPath(model.getParent().getGroupId(),
                model.getParent().getArtifactId(),
                "pom",
                model.getParent().getVersion());
    }

    public Path getPomPath(Artifact artifact) {
        return getPath(artifact.getGroupId(),
                artifact.getArtifactId(),
                "pom",
                artifact.getVersion());
    }

    public Path getPomPath(Model model) {
        return getPath(model.getGroupId(),
                model.getArtifactId(),
                "pom",
                model.getVersion());
    }

    public Path relativize(Path path) {
        return mRepoDirectory.relativize(path);
    }

    public DependencyResult resolveDependencies(DependencyRequest request)
            throws DependencyResolutionException {
        return mRepositorySystem.resolveDependencies(mRepositorySystemSession, request);
    }

    public ArtifactDescriptorResult readArtifactDescriptor(Artifact artifact)
            throws ArtifactDescriptorException {
        return mRepositorySystem.readArtifactDescriptor(
                mRepositorySystemSession,
                new ArtifactDescriptorRequest(artifact, null, null));

    }
}
