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

package com.android.tools.bazel.model;

import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class Workspace {
    private final File directory;

    private final Map<String, Package> packages = Maps.newLinkedHashMap();

    public Workspace(File directory) {
        this.directory = directory;
    }

    public void generate(PrintWriter progress) throws IOException {
        for (Package pkg : packages.values()) {
            pkg.generate(progress);
        }
    }

    public Package getPackage(String name) {
        return packages.get(name);
    }

    public File getDirectory() {
        return directory;
    }

    public Package findPackage(String rel) {
        File pkg = findBuildDirectory(new File(directory, rel));
        if (pkg == null) {
            System.err.println("Invalid package directory " + rel);
            return null;
        }
        String label = directory.toPath().relativize(pkg.toPath()).normalize().toString();

        Package result = packages.get(label);
        if (result == null) {
            result = new Package(this, label);
            packages.put(label, result);
        }
        return result;
    }


    private File findBuildDirectory(File child) {
        File file = child == null ? null : new File(child, "BUILD");
        while (file != null && !(file.exists() && !file.isDirectory())) {
            child = child.getParentFile();
            file = child == null ? null : new File(child, "BUILD");
        }
        return child;
    }
}
