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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

/**
 * A tool to create pom files. Usage:
 * PomGenerator target.pom  <options>
 *
 * Options:
 * -o <pom> Where to write the pom file.
 * -i <pom> [optional] The pom file to use as base for this pom.
 * --group <group> [optional] The group to use.
 * --artifact <artifact> [optional] The artifact name to use.
 * --version <version> [optional] The version to use.
 * --deps [pom1:...:pomn] The list of poms to set as dependencies.
 */
public class PomGenerator {

    static Model pomToModel(String pathToPom) throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(pathToPom));
        MavenXpp3Reader reader = new MavenXpp3Reader();
        return reader.read(in);
    }

    private static void modelToPom(Model model, File pom) throws Exception {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileOutputStream out = new FileOutputStream(pom)) {
            writer.write(out, model);
        }
    }

    public static void main(String[] args) throws Exception {
        new PomGenerator().run(Arrays.asList(args));
    }

    private void run(List<String> args) throws Exception {
        File in = null;
        File out = null;
        List<File> deps = null;
        String group = null;
        String artifact = null;
        String version = null;
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("-i") && it.hasNext()) {
                in = new File(it.next());
            } else if (arg.equals("-o") && it.hasNext()) {
                out = new File(it.next());
            } else if (arg.equals("--deps") && it.hasNext()) {
                String val = it.next();
                if (!val.isEmpty()) {
                    deps = Arrays.stream(val.split(":"))
                            .map(File::new)
                            .collect(Collectors.toList());
                }
            } else if (arg.equals("--group") && it.hasNext()) {
                group = it.next();
            } else if (arg.equals("--artifact") && it.hasNext()) {
                artifact = it.next();
            } else if (arg.equals("--version") && it.hasNext()) {
                version = it.next();
            }
        }
        if (out == null) {
            System.err.println("Output file must be specified.");
            return;
        }
        generatePom(in, out, deps, group, artifact, version);
    }

    private void generatePom(File in, File out, List<File> deps2, String group, String artifact, String version) throws Exception {
        // Avoid any manipulation if it is a copy:
        if (in != null && out != null && deps2 == null && group == null && artifact == null && version == null) {
            Files.copy(in.toPath(), out.toPath());
            return;
        }

        Model model;
        if (in == null) {
            model = new Model();
            model.setModelVersion("4.0.0");
        } else {
            model = pomToModel(in.getAbsolutePath());
        }

        if (group != null) {
            model.setGroupId(group);
        }
        if (artifact != null) {
            model.setArtifactId(artifact);
        }
        if (version != null) {
            model.setVersion(version);
        }
        if (deps2 != null) {
            List<Dependency> deps = new LinkedList<>();
            for (File dep : deps2) {
                Dependency dependency = new Dependency();
                MavenCoordinates coordinates;
                Model dependent = pomToModel(dep.getAbsolutePath());
                coordinates = new MavenCoordinates(dependent);

                dependency.setGroupId(coordinates.groupId);
                dependency.setArtifactId(coordinates.artifactId);
                dependency.setVersion(coordinates.version);

                deps.add(dependency);
            }
            model.setDependencies(deps);
        }
        modelToPom(model, out);
    }
}
