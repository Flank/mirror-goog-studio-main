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

import com.android.tools.maven.AetherUtils;
import com.android.tools.maven.MavenCoordinates;
import com.android.tools.maven.MavenRepository;
import com.android.tools.utils.WorkspaceUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
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

    /** Packages that are distributed with the SDK, we won't be able to find them in JCenter. */
    static final ImmutableList<Exclusion> EXCLUSIONS =
            ImmutableList.of(
                    new Exclusion("com.android.support", "*", "*", "*"),
                    new Exclusion("com.android.support.constraint", "*", "*", "*"),
                    new Exclusion("com.android.support.test", "*", "*", "*"),
                    new Exclusion("com.google.android.gms", "*", "*", "*"));

    public static void main(String[] argsArray) throws Exception {
        List<String> args = Lists.newArrayList(argsArray);

        Path repoDirectory;
        if (!MavenCoordinates.isMavenCoordinate(args.get(0))) {
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

    private void run(List<String> coordinates) throws DependencyResolutionException, IOException {
        JavaImportGenerator imports = new JavaImportGenerator(mRepo);

        CollectRequest request = new CollectRequest();
        request.setDependencies(
                coordinates
                        .stream()
                        .map(DefaultArtifact::new)
                        .map(artifact -> new Dependency(artifact, JavaScopes.COMPILE))
                        .collect(Collectors.toList()));
        request.setRepositories(AetherUtils.REPOSITORIES);

        mRepo.getRepositorySystemSession()
                .setDependencySelector(AetherUtils.buildDependencySelector(EXCLUSIONS));

        DependencyResult result = mRepo.resolveDependencies(new DependencyRequest(request, null));

        for (ArtifactResult artifactResult : result.getArtifactResults()) {
            imports.generateImportRules(artifactResult.getArtifact());
        }
    }

    private static void usage() {
        System.err.println(
                "Usage: add_dependency path/to/m2/repository com.example:foo:1.0 com.example:bar:2.0 ...");
        System.exit(1);
    }
}
