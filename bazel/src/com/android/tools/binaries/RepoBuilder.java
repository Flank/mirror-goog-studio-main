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

package com.android.tools.binaries;

import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
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
public class RepoBuilder {
    /** Resolves Models for building effective POM models. */
    private final RepoResolver modelResolver = new RepoResolver();

    /** Builds effective POM models. */
    private final ModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();

    /** The root directory to resolve relative paths from */
    private final File root;

    public RepoBuilder(File root) {
        this.root = root;
    }

    public RepoBuilder() {
        // Use the current directory
        this.root = new File(".");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println(
                    "Usage: RepoBuilder <artifacts_file> <output.manifest> <output.short_manifest>");
            System.exit(1);
        }

        Path artifacts = Paths.get(args[0]);
        Path manifest = Paths.get(args[1]);
        Path shortManifest = Paths.get(args[2]);

        build(artifacts, manifest, shortManifest);
    }

    /**
     * Given a file with a list of artifacts in the form: _path_, _short_path_[, _classifier_],
     * it writes two manifests files. One mapping the desired path in the maven repo to the artifact's
     * path and the other mapping it to its short path.
     */
    public static void build(Path artifactsFile, Path manifest, Path shortManifest)
            throws Exception {
        List<String> artifacts = Files.readAllLines(artifactsFile);
        Map<String, String> paths = new HashMap<>();
        Map<String, String> shortPaths = new HashMap<>();
        new RepoBuilder().resolve(artifacts, paths, shortPaths);

        try (FileWriter fw = new FileWriter(manifest.toFile())) {
            for (Map.Entry<String, String> entry : paths.entrySet()) {
                fw.write(String.format("%s=%s\n", entry.getKey(), entry.getValue()));
            }
        }

        try (FileWriter fw = new FileWriter(shortManifest.toFile())) {
            for (Map.Entry<String, String> entry : shortPaths.entrySet()) {
                fw.write(String.format("%s=%s\n", entry.getKey(), entry.getValue()));
            }
        }
    }

    /**
     * Resolves artifact paths to relative Maven paths.
     *
     * @param artifacts A list of artifacts that should be linked. Each artifact is described
     * as a line of the form: _path_, _short_path_[, _classifier_]
     # @param paths A map from the path on the repo to the artifact file's path
     # @param paths A map from the path on the repo to the artifact file's short path
     */
    public void resolve(
            List<String> artifacts, Map<String, String> paths, Map<String, String> shortPaths)
            throws Exception {
        Model model = null;

        for (String spec : artifacts) {
            // Extract the classifier from the file name.
            String[] parts = spec.split(",");
            String path = parts[0];
            String shortPath = parts[1];
            String classifier = null;
            if (2 < parts.length) {
                classifier = parts[2];
            }

            Path artifactPath = root.toPath().resolve(path);
            Path artifactDest;

            if (path.endsWith(".pom")) {
                // If the artifact is a POM file, update the model.
                model = getModel(artifactPath);

                artifactDest = getPath(model, null, "pom");
                modelResolver.putResolved(model.getId(), artifactPath.toFile());
            } else {
                // Otherwise, use the model to determine the artifact path.
                assert model != null;
                String extension = path.substring(path.lastIndexOf(".") + 1);
                artifactDest = getPath(model, classifier, extension);
            }

            // Replace Windows-style path separators (backslash) with forward slashes according
            // to the zip specification.
            String dest = artifactDest.toString().replace('\\', '/');

            paths.put(dest, path.toString().replace('\\', '/'));
            shortPaths.put(dest, shortPath.toString().replace('\\', '/'));
        }
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
