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

import org.apache.maven.model.Model;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipOutputStream;

/**
 * Builds a maven repo inside a zip file. Usage:
 *
 * RepoBuilder repo.zip [a.pom a1.jar a2.jar] [b.pom b1.jar] ...
 *
 * After the zip argument the arguments are a secquence of "pom" + list of artifacts.
 */
public class RepoBuilder {

    public static void main(String[] args) throws Exception {

        File repo = new File(args[0]);
        Zipper zipper = new Zipper();

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(repo))) {
            Model model = null;
            for (int i = 1; i < args.length; i++) {
                File file = new File(args[i]);
                if (args[i].endsWith(".pom")) {
                    model = PomGenerator.pomToModel(args[i]);
                    zipper.addFileToZip(file, out, getArtifactName(model, "pom"), false);
                } else {
                    if (i == 1) {
                        throw new IllegalArgumentException("First file to add must be a pom file.");
                    }
                    zipper.addFileToZip(file, out, getArtifactName(model, "jar"), false);
                }
            }
        }
    }

    private static String getArtifactName(Model model, String ext) {
        String groups = model.getGroupId().replaceAll("\\.", "/");
        String directory = groups + "/" + model.getArtifactId() + "/" + model.getVersion();
        return directory + "/" + model.getArtifactId() + "-" + model.getVersion() + "." + ext;
    }
}
