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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class Workspace {

    static final String[] BUILD_FILES = {
            "BUILD.bazel.test",
            "BUILD.bazel",
            "BUILD"
    };

    private final File directory;

    private final Map<String, Package> packages = Maps.newLinkedHashMap();

    public Workspace(File directory) {
        this.directory = directory;
    }

    public void generate(GenerationListener listener) throws IOException {
        for (Package pkg : packages.values()) {
            pkg.generate(listener);
        }
    }

    public Package getPackage(String name) {
        return packages.get(name);
    }

    public ImmutableSet<Package> getPackages() {
        return ImmutableSet.copyOf(packages.values());
    }

    public File getDirectory() {
        return directory;
    }

    public Package findPackage(String rel) {
        if (rel.startsWith("bazel-genfiles")) {
            rel = rel.substring("bazel-genfiles".length() + 1);
        }

        File pkg = findBuildDirectory(new File(directory, rel));
        return loadPackage(pkg);
    }

    private Package loadPackage(File pkg) {
        if (pkg == null) {
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

    private void loadAllPackages(File dir) {
        if (isBuildDirectory(dir)) {
            loadPackage(dir);
        }
        for (File file : dir.listFiles()) {
            if (file.isDirectory() && !(dir == directory && file.getName().startsWith("bazel-"))) {
                loadAllPackages(file);
            }
        }
    }

    private File findBuildDirectory(@NotNull File dir) {
        if (isBuildDirectory(dir)) {
            return dir;
        }
        File parent = dir.getParentFile();
        return (parent == null) ? null : findBuildDirectory(parent);
    }

    private boolean isBuildDirectory(@NotNull File dir) {
        for (String name : BUILD_FILES) {
            if (new File(dir, name).isFile()) {
                return true;
            }
        }
        return false;
    }

    public interface GenerationListener {
        boolean packageUpdated(String packageName);
    }
}
