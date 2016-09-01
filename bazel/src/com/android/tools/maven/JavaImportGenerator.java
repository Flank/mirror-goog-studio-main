package com.android.tools.maven;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Binary that generates a BUILD file with a single java_import for every {@code *.pom} file in a
 * local m2 repository.
 */
public class JavaImportGenerator {
    static final String RULE_NAME = "jar";
    private static final String GENERATED_WARNING =
            "# This BUILD file was generated by //tools/base/bazel:java_import_generator, please do not edit.";

    public static void main(String[] args) throws IOException, ArtifactDescriptorException {

        if (args.length != 1) {
            usage();
        }

        Path repoDirectory = Paths.get(args[0]);
        if (!repoDirectory.getFileName().toString().equals("repository")
                || !repoDirectory.getParent().getFileName().toString().equals("m2")) {
            usage();
        }

        new JavaImportGenerator(repoDirectory).processPomFiles();
    }

    private static void usage() {
        System.err.println("Usage: JavaImportGenerator path/to/m2/repository");
        System.exit(1);
    }

    private final Path mRepoDirectory;
    private final ModelBuilder mModelBuilder;
    private final LocalModelResolver mModelResolver;

    private JavaImportGenerator(Path repoDirectory) {
        mRepoDirectory = checkNotNull(repoDirectory);
        mModelBuilder = new DefaultModelBuilderFactory().newInstance();
        mModelResolver = new LocalModelResolver(mRepoDirectory);
    }

    private void processPomFiles() throws IOException, ArtifactDescriptorException {
        Files.walk(mRepoDirectory)
                .filter(path -> hasExtension(path, ".pom"))
                .forEach(
                        pom -> {
                            try {
                                processPomFile(pom);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });

        System.out.println();
    }

    private static boolean hasExtension(Path path, String extension) {
        return path.toString().endsWith(extension);
    }

    private void processPomFile(Path pomFile) throws IOException {
        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setModelSource(new FileModelSource(pomFile.toFile()));
        request.setModelResolver(mModelResolver);
        request.setSystemProperties(System.getProperties());
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        Model pomModel;
        try {
            pomModel = mModelBuilder.build(request).getEffectiveModel();
        } catch (ModelBuildingException e) {
            System.err.println("Failed to parse " + mRepoDirectory.relativize(pomFile));
            return;
        }

        if ("aar".equals(pomModel.getPackaging()) || "pom".equals(pomModel.getPackaging())) {
            return;
        }

        Path jarFile =
                mRepoDirectory.resolve(
                        mModelResolver.getPathForArtifact(
                                pomModel.getGroupId(),
                                pomModel.getArtifactId(),
                                pomModel.getVersion(),
                                "jar"));

        if (!Files.exists(jarFile)) {
            System.err.println("Missing jar file: " + mRepoDirectory.relativize(jarFile));
            return;
        }

        generateJavaImportRule(jarFile);
    }

    public static void generateJavaImportRule(Artifact artifact) {
        Path jar = artifact.getFile().toPath();
        Path buildFile = jar.getParent().resolve("BUILD");

        if (!Files.exists(buildFile)) {
            try {
                generateJavaImportRule(jar);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void generateJavaImportRule(Path jarFile) throws IOException {
        Path directory = jarFile.getParent();
        Path buildFile = directory.resolve("BUILD");
        if (Files.exists(buildFile)) {
            Files.delete(buildFile);
        }
        try (FileWriter fileWriter = new FileWriter(buildFile.toFile())) {
            fileWriter.append(GENERATED_WARNING);
            fileWriter.append(System.lineSeparator());
            fileWriter.append(System.lineSeparator());

            fileWriter.append(
                    String.format(
                            "java_import(name = \""
                                    + RULE_NAME
                                    + "\", jars = [\"%s\"], visibility = [\"//visibility:public\"])",
                            jarFile.getFileName()));
            fileWriter.append(System.lineSeparator());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
