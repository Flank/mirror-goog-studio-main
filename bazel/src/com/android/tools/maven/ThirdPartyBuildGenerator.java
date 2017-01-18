package com.android.tools.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tools.utils.WorkspaceUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

/**
 * Binary that generates a BUILD file (most likely in //tools/base/third_party) which mimics the
 * Maven dependency graph using java_libraries with exports.
 */
public class ThirdPartyBuildGenerator {
    private static final String PREBUILTS_BAZEL_PACKAGE = "//prebuilts/tools/common/m2/repository/";
    private static final String GENERATED_WARNING =
            "# This BUILD file was generated by //tools/base/bazel:third_party_build_generator, please do not edit.";

    /**
     * Dependencies excluded from the resolution process.
     *
     * <p>We don't use asm-all (that some libraries depend on), instead we use the individual
     * packages.
     */
    static final ImmutableList<Exclusion> EXCLUSIONS =
            ImmutableList.of(
                    new Exclusion("org.ow2.asm", "asm-all", "*", "*"),
                    new Exclusion("org.ow2.asm", "asm-debug-all", "*", "*"));

    public static void main(String[] argsArray) throws Exception {
        List<String> args = Lists.newArrayList(argsArray);
        Path buildFile;
        Path localRepo;

        if (!args.isEmpty() && !MavenCoordinates.isMavenCoordinate(args.get(0))) {
            buildFile = Paths.get(args.get(0));
            localRepo = Paths.get(args.get(1));
            args.remove(0);
            args.remove(0);
        } else {
            Path workspace = WorkspaceUtils.findWorkspace();
            buildFile = workspace.resolve("tools/base/third_party/BUILD");
            localRepo = WorkspaceUtils.findPrebuiltsRepository();
        }

        if (!Files.isDirectory(localRepo) || !Files.isRegularFile(buildFile)) {
            usage();
        }

        Set<Artifact> artifacts;
        if (!args.isEmpty()) {
            artifacts = args.stream().map(DefaultArtifact::new).collect(Collectors.toSet());
        } else {
            Path dependenciesProperties =
                    WorkspaceUtils.findWorkspace()
                            .resolve("tools/buildSrc/base/dependencies.properties");
            Properties dependencies = new Properties();
            try(InputStream inputStream = Files.newInputStream(dependenciesProperties)) {
                dependencies.load(inputStream);
            }
            artifacts = new HashSet<>();
            for (String key: dependencies.stringPropertyNames()) {
                artifacts.add(new DefaultArtifact(dependencies.getProperty(key)));
            }
        }

        new ThirdPartyBuildGenerator(buildFile, localRepo).generateBuildFile(artifacts);
    }

    private static void usage() {
        System.out.println(
                "Usage: third_party_build_generator [path/to/BUILD path/to/m2/repository] com.example:foo:1.0 ...");
        System.out.println("");
        System.err.println(
                "If the paths to m2 repo and BUILD are omitted, the ones from current WORKSPACE "
                        + "will be used.");
        System.exit(1);
    }

    private final Path mBuildFile;
    private final MavenRepository mRepo;

    private ThirdPartyBuildGenerator(Path buildFile, Path localRepo) {
        mBuildFile = checkNotNull(buildFile);
        mRepo = new MavenRepository(localRepo);
    }

    private void generateBuildFile(Set<Artifact> artifacts)
            throws DependencyCollectionException, IOException, ArtifactDescriptorException,
                    DependencyResolutionException {
        SortedMap<String, Artifact> versions = computeEffectiveVersions(artifacts);

        Files.createDirectories(mBuildFile.getParent());
        if (Files.exists(mBuildFile)) {
            Files.delete(mBuildFile);
        }

        try (FileWriter fileWriter = new FileWriter(mBuildFile.toFile())) {
            fileWriter.append(GENERATED_WARNING);
            fileWriter.append(System.lineSeparator());
            fileWriter.append("load(\"//tools/base/bazel:maven.bzl\", \"maven_java_library\")");
            fileWriter.append(System.lineSeparator());
            fileWriter.append(System.lineSeparator());

            for (Map.Entry<String, Artifact> entry : versions.entrySet()) {
                String ruleName = entry.getKey();

                List<String> deps = new ArrayList<>();

                Artifact artifact = entry.getValue();
                deps.addAll(getDirectDependencies(artifact, versions.keySet()));

                fileWriter.append(
                        String.format(
                                // Don't bother with formatting, we run buildifier on the result
                                // anyway.
                                "maven_java_library(name = \"%s\", export_artifact = \"%s\", exports = [%s], visibility = [\"//visibility:public\"])",
                                ruleName,
                                getJarTarget(artifact),
                                deps.stream()
                                        .map(s -> '"' + s + '"')
                                        .collect(Collectors.joining(", "))));
                fileWriter.append(System.lineSeparator());
            }
        }
    }

    private List<String> getDirectDependencies(Artifact artifact, Set<String> allArtifacts)
            throws ArtifactDescriptorException {
        ArtifactDescriptorResult descriptor = mRepo.readArtifactDescriptor(artifact);

        return descriptor
                .getDependencies()
                .stream()
                .filter(dependency -> JavaScopes.COMPILE.equals(dependency.getScope()))
                .filter(dependency -> !dependency.isOptional())
                // This can be false, if a library was excluded using Maven exclusions.
                .filter(dependency -> allArtifacts.contains(getRuleName(dependency.getArtifact())))
                .map(dependency -> ":" + getRuleName(dependency.getArtifact()))
                .collect(Collectors.toList());
    }

    private String getJarTarget(Artifact artifact) {
        Path jar = mRepo.getRelativePath(artifact);
        return PREBUILTS_BAZEL_PACKAGE + jar.getParent() + ":" + JavaImportGenerator.JAR_RULE_NAME;
    }

    private SortedMap<String, Artifact> computeEffectiveVersions(Set<Artifact> artifacts)
            throws DependencyCollectionException, DependencyResolutionException, IOException {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setDependencies(
                artifacts
                        .stream()
                        .map(artifact -> new Dependency(artifact, JavaScopes.COMPILE))
                        .collect(Collectors.toList()));

        collectRequest.setRepositories(AetherUtils.REPOSITORIES);

        DefaultRepositorySystemSession session = mRepo.getRepositorySystemSession();

        session.setDependencyGraphTransformer(
                new ConflictResolver(
                        new HighestVersionSelector(artifacts),
                        new JavaScopeSelector(),
                        new SimpleOptionalitySelector(),
                        new JavaScopeDeriver()));
        session.setDependencySelector(AetherUtils.buildDependencySelector(EXCLUSIONS));

        DependencyResult result = mRepo.resolveDependencies(new DependencyRequest(collectRequest, null));

        SortedMap<String, Artifact> versions = new TreeMap<>();

        JavaImportGenerator imports = new JavaImportGenerator(mRepo);
        result.getRoot()
                .accept(
                        new DependencyVisitor() {
                            @Override
                            public boolean visitEnter(DependencyNode node) {
                                Artifact artifact = node.getArtifact();
                                if (artifact != null) {

                                    versions.put(getRuleName(artifact), artifact);
                                    try {
                                        imports.generateImportRules(artifact);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }

                                return true;
                            }

                            @Override
                            public boolean visitLeave(DependencyNode node) {
                                return true;
                            }
                        });
        return versions;
    }

    private static String getRuleName(Artifact artifact) {
        return artifact.getGroupId() + "_" + artifact.getArtifactId();
    }
}
