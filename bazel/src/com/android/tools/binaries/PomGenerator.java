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

import com.android.tools.maven.MavenCoordinates;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

/**
 * A tool to create pom files. Usage:
 * <pre>
 * {@code
 * PomGenerator <options>
 *
 * Options:
 * -o <pom> Where to write the pom file.
 * -i <pom> [optional] The pom file to use as base for this pom.
 * --group <group> [optional] The group to use.
 * --artifact <artifact> [optional] The artifact name to use.
 * --version <version> [optional] The version to use.
 * --deps [pom1:...:pomn] The list of poms to set as dependencies.
 * --exclusion group1:artifact1 group2:artifact2 Exclusion to be added to the POM file.
 *     Can be specified multiple times.
 * }
 * </pre>
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

    private final Multimap<String, String> exclusions = HashMultimap.create();

    private void run(List<String> args) throws Exception {
        File in = null;
        File out = null;
        List<File> deps = null;
        List<File> exports = null;
        String group = null;
        String artifact = null;
        String version = null;
        String description = null;
        String name = null;
        String version_property = null;
        List<File> properties_files = null;
        boolean export = false;
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
            } else if (arg.equals("--exports") && it.hasNext()) {
                String val = it.next();
                if (!val.isEmpty()) {
                    exports =
                            Arrays.stream(val.split(":"))
                                    .map(File::new)
                                    .collect(Collectors.toList());
                }
            } else if (arg.equals("--group") && it.hasNext()) {
                group = it.next();
            } else if (arg.equals("--artifact") && it.hasNext()) {
                artifact = it.next();
            } else if (arg.equals("--version") && it.hasNext()) {
                version = it.next();
            } else if (arg.equals("--description") && it.hasNext()) {
                description = it.next();
            } else if (arg.equals("--pom_name") && it.hasNext()) {
                name = it.next();
            } else if (arg.equals("--version_property") && it.hasNext()) {
                version_property = it.next();
            } else if (arg.equals("--properties") && it.hasNext()) {
                String val = it.next();
                if (!val.isEmpty()) {
                    properties_files =
                            Arrays.stream(val.split(":"))
                                    .map(File::new)
                                    .collect(Collectors.toList());
                }
            } else if (arg.equals("--exclusion")) {
                exclusions.putAll(it.next(), Splitter.on(',').split(it.next()));
            } else if (arg.equals("-x")) {
                export = true;
            }
        }
        if (out == null) {
            System.err.println("Output file must be specified.");
            return;
        }
        if (version != null && version_property != null) {
            System.err.println("version and version_property should not both be set.");
            return;
        }
        if (version_property != null) {
            if (properties_files == null) {
                System.err.println("version_property needs a properties file.");
                return;
            }
            version = getVersionFromPropertiesFiles(properties_files, version_property);
        }
        generatePom(in, out, deps, exports, group, artifact, version, description, name, export);
    }

    private static String getVersionFromPropertiesFiles(
            final List<File> propertiesFiles, final String version_property) throws IOException {
        Properties properties = new Properties();
        for (File propertiesFile : propertiesFiles) {
            try (FileInputStream fis = new FileInputStream(propertiesFile)) {
                properties.load(fis);
            }
        }
        if (!version_property.contains("$")) {
            // Simple case, not a format string.
            return properties.getProperty(version_property);
        }
        // Replace all instances of ${myVersionKey} with myVersionValue
        String version = version_property;
        for (String name : properties.stringPropertyNames()) {
            version = version.replace("${" + name + "}", properties.getProperty(name));
        }
        if (version.contains("$")) {
            throw new IOException(
                    "Invalid version_property='"
                            + version_property
                            + "'\n"
                            + "available names: "
                            + String.join(", ", properties.stringPropertyNames()));
        }
        return version;
    }

    /**
     * Writes the pom file in {@code out}, based on {@code in}. If there are no attributes to modify
     * from the original file, or if the {@code export} flag is forced, then the file input file is
     * copied as-is.
     */
    public void generatePom(
            File in,
            File out,
            List<File> pomDependencies,
            List<File> pomExports,
            String group,
            String artifact,
            String version,
            String description,
            String name,
            boolean export)
            throws Exception {
        // Avoid any manipulation if it is an export:
        if ((in != null
                        && out != null
                        && pomDependencies == null
                        && pomExports == null
                        && group == null
                        && artifact == null
                        && description == null
                        && name == null
                        && version == null)
                || export) {
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
        if (description != null) {
            model.setDescription(description);
        }
        if (name != null) {
            model.setName(name);
        }
        List<Dependency> deps = new LinkedList<>();
        if (pomExports != null) {
            for (File pom : pomExports) {
                addDependencyTo(pom, "compile", deps);
            }
        }
        if (pomDependencies != null) {
            for (File pom : pomDependencies) {
                addDependencyTo(pom, "runtime", deps);
            }
        }
        model.setDependencies(deps);
        modelToPom(model, out);
    }

    private void addDependencyTo(File pom, String scope, List<Dependency> deps) throws Exception {
        Dependency dependency = new Dependency();
        MavenCoordinates coordinates;
        Model dependent = pomToModel(pom.getAbsolutePath());
        coordinates = new MavenCoordinates(dependent);
        if (dependent.getPackaging().equals("pom")) {
            // If the target has "pom" packaging, then the dependency
            // must also be declared as a pom type dependency.
            dependency.setType("pom");
        }

        dependency.setGroupId(coordinates.groupId);
        dependency.setArtifactId(coordinates.artifactId);
        dependency.setVersion(coordinates.version);
        dependency.setScope(scope);

        Collection<String> exclusionStrings =
                exclusions.get(coordinates.groupId + ":" + coordinates.artifactId);
        if (exclusionStrings != null) {
            for (String exclusionString : exclusionStrings) {
                List<String> parts = Splitter.on(':').splitToList(exclusionString);
                Exclusion exclusion = new Exclusion();
                exclusion.setGroupId(parts.get(0));
                exclusion.setArtifactId(parts.get(1));
                dependency.addExclusion(exclusion);
            }
        }

        deps.add(dependency);
    }
}
