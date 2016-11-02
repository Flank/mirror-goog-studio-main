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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
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
 * RepoBuilder repo.zip [a.pom a1.jar a2.jar] [b.pom b1.jar] ...
 *
 * After the zip argument the arguments are a secquence of "pom" + list of artifacts.
 */
public class RepoBuilder {


    public static void main(String[] args) throws Exception {
        new RepoBuilder().run(args);
    }

    private final Map<String, Path> mResolved;
    private final MavenRepository mRepo;

    public RepoBuilder() {
        mResolved = Maps.newHashMap();
        mRepo = new MavenRepository(Files.createTempDir().toPath(), new RepoResolver());
    }

    public void run(String[] args) throws Exception {
        File zip = new File(args[0]);
        Zipper zipper = new Zipper();

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            Model model = null;
            for (int i = 1; i < args.length; i++) {
                File file = new File(args[i]);
                if (args[i].endsWith(".pom")) {

                    Path pomFile = Paths.get(args[i]);
                    model = mRepo.getPomEffectiveModel(pomFile);
                    mResolved.put(model.getId(), pomFile);
                    String path = mRepo.relativize(mRepo.getPomPath(model)).toString();
                    zipper.addFileToZip(file, out, path, false);
                } else {
                    if (i == 1) {
                        throw new IllegalArgumentException("First file to add must be a pom file.");
                    }
                    String path = mRepo.relativize(mRepo.getJarPath(model)).toString();
                    zipper.addFileToZip(file, out, path, false);
                }
            }
        }
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
