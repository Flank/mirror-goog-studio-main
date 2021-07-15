/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.bazel.repolinker;

import com.google.common.collect.Maps;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;

/** Creates a Maven repository using symlinks. */
public class RepoLinker {
    /** Resolves Models for building effective POM models. */
    private final RepoResolver modelResolver = new RepoResolver();

    /** Builds effective POM models. */
    private final ModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();

    /**
     * Creates a Maven repository using symlinks.
     *
     * @param destination The destination directory for the Maven repository.
     * @param artifacts The list of artifacts that need to be resolved. The artifacts should be
     *     given in order of POM file, followed by artifacts corresponding to the POM file. If the
     *     artifact has a classifier, the classifier should be included at the end of the path,
     *     delimited by a comma.
     */
    public void link(Path destination, List<String> artifacts) throws Exception {
        for (Map.Entry<Path, Path> entry : resolve(artifacts).entrySet()) {
            Path src = entry.getKey();
            Path dest = destination.resolve(entry.getValue());

            Files.createDirectories(dest.getParent());
            if (Files.exists(dest)) {
                Files.delete(dest);
            }
            Files.createSymbolicLink(dest, src);
        }
    }

    /**
     * Resolves artifact paths to relative Maven paths.
     *
     * @param artifacts A list of artifacts that should be linked.
     * @return A Map of absolute source paths to relative Maven paths.
     */
    public Map<Path, Path> resolve(List<String> artifacts) throws Exception {
        Map<Path, Path> resolved = Maps.newHashMap();
        Model model = null;

        for (String artifact : artifacts) {
            // Extract the classifier from the file name.
            String classifier = null;
            if (artifact.contains(",")) {
                String[] classified = artifact.split(",");
                artifact = classified[0];
                classifier = classified[1];
            }

            Path artifactPath = Paths.get(artifact);
            Path artifactDest;

            if (artifact.endsWith(".pom")) {
                // If the artifact is a POM file, update the model.
                model = getModel(artifactPath);

                artifactDest = getPath(model, null, "pom");
                modelResolver.putResolved(model.getId(), artifactPath.toFile());
            } else {
                // Otherwise, use the model to determine the artifact path.
                assert model != null;
                String extension = artifact.substring(artifact.lastIndexOf(".") + 1);
                artifactDest = getPath(model, classifier, extension);
            }

            artifactPath = artifactPath.toAbsolutePath();
            resolved.put(artifactPath, artifactDest);
        }

        return resolved;
    }

    /** Loads a model from a given POM file. */
    private Model getModel(Path pomPath) throws ModelBuildingException {
        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setModelSource(new FileModelSource(pomPath.toFile()));
        request.setModelResolver(modelResolver);
        request.setSystemProperties(System.getProperties());
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return modelBuilder.build(request).getEffectiveModel();
    }

    /**
     * Returns the repo path of an artifact with the given attributes.
     *
     * <p>The repo path is constructing using the Maven 2/3 repository layout.
     */
    private static Path getPath(
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String extension) {
        Path path = Paths.get("");

        for (String section : groupId.split("\\.")) {
            path = path.resolve(section);
        }
        path = path.resolve(artifactId);
        path = path.resolve(version);

        String filename = artifactId + "-" + version;
        if (classifier != null) {
            filename += "-" + classifier;
        }
        filename += "." + extension;
        path = path.resolve(filename);

        return path;
    }

    private static Path getPath(Model model, String classifier, String extension) {
        return getPath(
                model.getGroupId(),
                model.getArtifactId(),
                model.getVersion(),
                classifier,
                extension);
    }

    /** Returns the unique ID of an artifact as groupId:artifactId:version. */
    private static String getId(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }

    private static class RepoResolver implements ModelResolver {

        /** Stores a map of resolved model IDs to their source files. */
        private final Map<String, File> resolvedModels = Maps.newHashMap();

        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version)
                throws UnresolvableModelException {
            try {
                return new FileModelSource(resolvedModels.get(getId(groupId, artifactId, version)));
            } catch (RuntimeException e) {
                throw new UnresolvableModelException(e, groupId, artifactId, version);
            }
        }

        @Override
        public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            try {
                return new FileModelSource(resolvedModels.get(parent.getId()));
            } catch (RuntimeException e) {
                throw new UnresolvableModelException(
                        e, parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
            }
        }

        // AGP tests use a version of model-builder that does not include this method, but Studio
        // tests do. This method is included but without @Override for compatibility between both
        // versions.
        public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
            return resolveModel(
                    dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        }

        @Override
        public void addRepository(Repository repository) throws InvalidRepositoryException {}

        @Override
        public void addRepository(Repository repository, boolean replace)
                throws InvalidRepositoryException {}

        @Override
        public ModelResolver newCopy() {
            return new RepoResolver();
        }

        /** Stores the path to a resolved model ID. */
        public void putResolved(String id, File path) {
            resolvedModels.put(id, path);
        }
    }
}
