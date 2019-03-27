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

package com.android.tools.binaries;

import static java.util.Collections.emptyList;

import com.android.tools.maven.AetherUtils;
import com.android.tools.maven.MavenCoordinates;
import com.android.tools.maven.MavenRepository;
import com.android.tools.utils.WorkspaceUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Command line tool to download a new Maven artifact to prebuilts, together with all the transitive
 * dependencies.
 *
 * <p>It also creates {@code java_import} Bazel rules for newly created jars.
 */
public class AddDependency {

    public static void main(String[] argsArray) throws Exception {
        List<String> args = Lists.newArrayList(argsArray);

        Path repoDirectory;
        if (!MavenCoordinates.isMavenCoordinate(args.get(0)) && !args.get(0).startsWith("--")) {
            repoDirectory = Paths.get(args.get(0));
            args.remove(0);
        } else {
            repoDirectory = WorkspaceUtils.findPrebuiltsRepository();
        }

        if (!Files.isDirectory(repoDirectory)) {
            usage();
        }

        new AddDependency(repoDirectory).run(args);
    }

    private final MavenRepository mRepo;

    private AddDependency(Path localRepo) {
        mRepo = new MavenRepository(localRepo);
    }

    private void run(List<String> args) throws DependencyResolutionException, IOException {
        JavaImportGenerator imports = new JavaImportGenerator(mRepo);
        List<RemoteRepository> repositories = Lists.newArrayList(AetherUtils.REPOSITORIES);

        boolean ignoreDeps = false;
        Map<Boolean, List<String>> argsByFlag =
                args.stream().collect(Collectors.partitioningBy(s -> s.startsWith("--")));

        List<String> coordinates = argsByFlag.get(false);
        for (String flag : argsByFlag.get(true)) {
            if (flag.startsWith("--repo=")) {
                String repoUrl = flag.substring("--repo=".length());
                repositories.add(
                        0, new RemoteRepository.Builder(repoUrl, "default", repoUrl).build());
            } else if (flag.equals("--ignore-deps")) {
                ignoreDeps = true;
            } else {
                System.err.println("Unknown flag " + flag);
                System.exit(1);
            }
        }

        List<Dependency> roots =
                coordinates
                        .stream()
                        .map(DefaultArtifact::new)
                        .map(artifact -> new Dependency(artifact, JavaScopes.COMPILE))
                        .collect(Collectors.toList());

        DependencySelector dependencySelector;
        if (ignoreDeps) {
            dependencySelector =
                    new DependencySelector() {
                        @Override
                        public boolean selectDependency(Dependency dependency) {
                            return roots.contains(dependency);
                        }

                        @Override
                        public DependencySelector deriveChildSelector(
                                DependencyCollectionContext context) {
                            return this;
                        }
                    };
        } else {
            dependencySelector = AetherUtils.buildDependencySelector(emptyList());
        }
        mRepo.getRepositorySystemSession().setDependencySelector(dependencySelector);

        CollectRequest request = new CollectRequest();
        request.setDependencies(roots);
        request.setRepositories(repositories);

        DependencyResult result = mRepo.resolveDependencies(new DependencyRequest(request, null));

        for (ArtifactResult artifactResult : result.getArtifactResults()) {
            imports.generateImportRules(artifactResult.getArtifact());
            copyNotice(artifactResult.getArtifact());
        }
    }

    private void copyNotice(Artifact artifact) {
        File artifactDir = artifact.getFile().getParentFile();
        String[] possibleNames = {"LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt"};
        try {
            File currentDir = artifactDir.getCanonicalFile();
            while (!currentDir.equals(mRepo.getDirectory().toFile().getCanonicalFile())) {
                for (String name : possibleNames) {
                    File possibleLicense = new File(currentDir, name);
                    if (possibleLicense.exists()) {
                        Files.copy(
                                possibleLicense.toPath(),
                                (new File(artifactDir, "NOTICE")).toPath());
                        return;
                    }
                }
                currentDir = currentDir.getParentFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println(
                "No NOTICE found for "
                        + artifact
                        + "! It must be added manually if "
                        + artifact
                        + " will ever by used by the commandlinetools sdk component.");
    }

    private static void usage() {
        System.err.println(
                "Usage: add_dependency path/to/m2/repository com.example:foo:1.0 com.example:bar:2.0 ...");
        System.exit(1);
    }
}
