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

package com.android.tools.bazel;

import com.android.tools.bazel.model.ImlModule;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;

/**
 * A collection of modules with cyclic dependencies that form a strongly connected component.
 */
public class BazelModule {

    private static final Comparator<JpsModule> BY_NAME = Comparator.comparing(JpsModule::getName);

    private static final Comparator<JpsModule> BY_NUM_ORDER_ENTRIES =
            Comparator.comparingInt(module -> module.getDependenciesList().getDependencies().size());

    private SortedSet<JpsModule> modules = new TreeSet<>(BY_NAME);

    public ImlModule rule = null;

    public BazelModule() {
    }

    public void add(JpsModule module) {
        modules.add(module);
    }

    public String getName() {
        return modules.stream().max(BY_NUM_ORDER_ENTRIES).get().getName()
            + (isSingle() ? "" : "_and_others");
    }

    public boolean isSingle() {
        return modules.size() == 1;
    }

    public Path getBaseDir() {
        Path common = null;
        // Find the common ancestor of all the modules
        for (JpsModule module : modules) {
            File base = JpsModelSerializationDataService.getBaseDirectory(module);
            if (base != null) {
                Path path = base.toPath();
                if (common == null) {
                    common = path;
                } else {
                    // Move common "up" until it covers the current module
                    while (!path.startsWith(common)) {
                        common = common.getParent();
                    }
                }
            } else {
                System.err.println(module.getName() + " has no base directory.");
            }
        }
        return common;
    }

    public List<JpsModuleSourceRoot> getSourceRoots() {
        List<JpsModuleSourceRoot> entries = new LinkedList<>();
        for (JpsModule module : modules) {
            entries.addAll(module.getSourceRoots());
        }
        return entries;
    }

    public List<JpsDependencyElement> getDependencies() {
        List<JpsDependencyElement> dependencies = new LinkedList<>();
        for (JpsModule module : modules) {
            dependencies.addAll(module.getDependenciesList().getDependencies());
        }
        return dependencies;
    }

    public Set<JpsModule> getModules() {
        return modules;
    }
}
