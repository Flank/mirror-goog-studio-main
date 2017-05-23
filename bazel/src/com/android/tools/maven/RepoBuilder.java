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

import com.android.tools.utils.Zipper;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Builds a maven repo inside a zip file. Usage:
 *
 * <p>RepoBuilder repo.zip [a.pom a1.jar a2.jar] [b.pom b1.jar] ...
 *
 * <p>After the zip argument the arguments are a sequence of "pom" + list of artifacts.
 */
public class RepoBuilder {


    public static void main(String[] args) throws Exception {
        System.exit(new RepoBuilder().run(Arrays.asList(args)));
    }

    private final Map<String, Path> mResolved;
    private final MavenRepository mRepo;

    public RepoBuilder() {
        mResolved = Maps.newHashMap();
        mRepo = new MavenRepository(Files.createTempDir().toPath(), new RepoResolver());
    }

    private static void usage(String message) {
        System.err.println("Error: " + message);
        System.err.println("Usage: repo_builder <output-file> <files>...|@<file>");
    }

    private int run(List<String> args) throws Exception {
        Options options = parseOptions(args.iterator());
        if (options.out == null) {
            usage("Output file not specified.");
            return 1;
        }
        if (!options.files.isEmpty()) {
            if (!options.files.get(0).endsWith(".pom")) {
                usage("First file to add must be a pom file.");
                return 1;
            }
        }

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(options.out))) {
            Zipper zipper = new Zipper();
            Model model = null;
            for (String inputFile : options.files) {
                String classifier = null;
                if (inputFile.contains(":")) {
                    String[] classified = inputFile.split(":");
                    inputFile = classified[0];
                    classifier = classified[1];
                }
                File file = new File(inputFile);
                if (inputFile.endsWith(".pom")) {
                    Path pomFile = file.toPath();
                    model = mRepo.getPomEffectiveModel(pomFile);
                    mResolved.put(model.getId(), pomFile);
                    String path = mRepo.relativize(mRepo.getPomPath(model)).toString();
                    zipper.addFileToZip(file, out, path, false);
                } else {
                    assert model != null;
                    String path = mRepo.relativize(mRepo.getArtifactPath(model, classifier)).toString();
                    zipper.addFileToZip(file, out, path, false);
                }
            }
            out.flush();
        }

        return 0;
    }

    private static Options parseOptions(Iterator<String> it) throws IOException {
        Options options = new Options();
        if (it.hasNext()) {
            options.out = new File(it.next());
        }

        while (it.hasNext()) {
            String arg = it.next();
            if (arg.startsWith("@")) {
                options.files.addAll(java.nio.file.Files.readAllLines(Paths.get(arg.substring(1))));
            } else {
                options.files.add(arg);
            }
        }
        return options;
    }

    private static class Options {
        public File out;
        public List<String> files = new LinkedList<>();
    }

    class RepoResolver implements ModelResolver {

        @Override
        @SuppressWarnings("deprecation") // This is the ModelResolver API
        public ModelSource resolveModel(String groupId, String artifactId, String version)
                throws UnresolvableModelException {
            Path pom = mRepo.getPomPath(new DefaultArtifact(groupId, artifactId, "pom", version));
            return new FileModelSource(mRepo.getDirectory().resolve(pom).toFile());
        }

        @Override
        @SuppressWarnings("deprecation") // This is the ModelResolver API
        public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            return new FileModelSource(mResolved.get(parent.getId()).toFile());
        }

        @Override
        public void addRepository(Repository repository) throws InvalidRepositoryException {
        }

        @Override
        public void addRepository(Repository repository, boolean replace)
                throws InvalidRepositoryException {
        }

        @Override
        public ModelResolver newCopy() {
            return new RepoResolver();
        }
    }
}
