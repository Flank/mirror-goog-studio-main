/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.repository_generator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Represents the dependency resolution result */
public class ResolutionResult {
    /** Dependencies in the dependency tree obtained after conflict resolution. */
    public List<Dependency> dependencies = new ArrayList<>();

    /** Dependencies in the dependency graph that are eliminated after conflict resolution. */
    public List<Dependency> conflictLosers = new ArrayList<>();

    /** Transitive parents of all dependencies and conflictLosers. */
    public List<Parent> parents = new ArrayList<>();

    public static class Dependency {
        public String coord;
        public String file;
        public String pomPath;
        public String parentCoord;
        public String srcjar;
        public String[] originalDependencies;
        public Map<String, List<String>> directDependencies;
        public Map<String, String> conflictResolution;

        public Dependency(
                String coord,
                String file,
                String pomPath,
                String parentCoord,
                String srcjar,
                String[] originalDependencies,
                Map<String, List<String>> directDependencies,
                Map<String, String> conflictResolution) {
            this.coord = coord;
            this.file = file;
            this.pomPath = pomPath;
            this.parentCoord = parentCoord;
            this.srcjar = srcjar;
            this.originalDependencies = originalDependencies;
            this.directDependencies = directDependencies;
            this.conflictResolution = conflictResolution;
        }
    }

    public static class Parent {
        public String coord;
        public String pomPath;
        public String parentCoord;

        public Parent(String coord, String pomPath, String parentCoord) {
            this.coord = coord;
            this.pomPath = pomPath;
            this.parentCoord = parentCoord;
        }
    }

    public void sortByCoord() {
        dependencies.sort(Comparator.comparing(d -> d.coord));
        conflictLosers.sort(Comparator.comparing(d -> d.coord));
        parents.sort(Comparator.comparing(d -> d.coord));
    }
}
