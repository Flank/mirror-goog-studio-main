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
import java.nio.file.Path;
import java.util.Map;

public class Workspace {
    private final File directory;

    private final Map<String, Package> packages = Maps.newLinkedHashMap();

    public Workspace(File directory) {
        this.directory = directory;
    }

    public void generate() throws IOException {
        if (!new File(directory, "WORKSPACE").exists()) {
            throw new IllegalStateException("Invalid workspace directory " + directory);
        }
        for (Package pkg : packages.values()) {
            pkg.generate();
        }
    }

    public void createPackagesInDirectory(String relDir) {
        File dir = new File(directory, relDir);
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                Path rel = directory.toPath().relativize(file.toPath());
                createPackage(rel.toString());
            }
        }
    }

    public Package getPackage(String name) {
        return packages.get(name);
    }

    public void createPackage(String rel) {
        Package pkg = packages.get(rel);
        if (pkg == null) {
            pkg = new Package(this, rel);
            packages.put(rel, pkg);
        }
    }

    public File getDirectory() {
        return directory;
    }

    public Package findPackage(String rel) {
        if (rel.startsWith("prebuilts/tools/common/m2")) {
            String packageName = rel.substring(0, rel.lastIndexOf('/'));
            Package result = packages.get(packageName);
            if (result == null) {
                result = new Package(this, packageName);
            }
            return result;
        }

        if (!rel.endsWith("/")) {
            rel = rel + "/";
        }
        for (String pkg : packages.keySet()) {
            Package aPackage = packages.get(pkg);
            String norm =  (pkg.endsWith("/") ? pkg : pkg + "/");
            if (rel.startsWith(norm)) {
                return aPackage;
            }
        }
        System.err.println("Invalid package directory " + rel);
        return null;
    }
}
