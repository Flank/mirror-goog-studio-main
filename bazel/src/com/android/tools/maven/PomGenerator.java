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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

/**
 * A tool to create pom files. Usage:
 * PomGenerator target.pom <coordinates> <dep1> ... <depn>
 *
 * Where, coordinates and deps are one of the following:
 * .- A string of the form group:artifact:version
 * .- A .pom file path where to get this information from.
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
        File target = new File(args[0]);
        String input = args[1];

        Model model;
        if (MavenCoordinates.isMavenCoordinate(input)) {
            model = new Model();
            MavenCoordinates coordinates = new MavenCoordinates(input);
            model.setGroupId(coordinates.groupId);
            model.setArtifactId(coordinates.artifactId);
            model.setVersion(coordinates.version);
        } else {
            model = pomToModel(input);
        }

        List<Dependency> deps = new LinkedList<>();
        for (int i = 2; i < args.length; i++) {
            Dependency dependency = new Dependency();
            MavenCoordinates coordinates;
            if (MavenCoordinates.isMavenCoordinate(args[i])) {
                coordinates = new MavenCoordinates(args[i]);
            } else {
                Model dependent = pomToModel(args[i]);
                coordinates = new MavenCoordinates(dependent);
            }

            dependency.setGroupId(coordinates.groupId);
            dependency.setArtifactId(coordinates.artifactId);
            dependency.setVersion(coordinates.version);

            deps.add(dependency);
        }
        model.setDependencies(deps);
        modelToPom(model, target);
    }
}
