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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.SystemUtils;

/** Utility for generating a BUILD file from a {@link ResolutionResult} object. */
public class BuildFileWriter {

    /**
     * The prefix for the repository, relative to the generated BUILD file. The full path for all
     * artifacts will be: $BUILD_FILE_PATH / repoPrefix / artifactPath
     */
    private final Path repoPrefix;

    /** The file writer for the generated BUILD file. */
    private final FileWriter fileWriter;

    /**
     * Names of rules already generated. Used to avoid generating the same BUILD rule more than
     * once.
     */
    private final Set<String> generatedRuleNames = new HashSet<>();

    public BuildFileWriter(Path repoPath, String filePath) throws IOException {
        repoPrefix = Paths.get("").toAbsolutePath().relativize(repoPath);
        fileWriter = new FileWriter(filePath);
    }

    /**
     * Generates a BUILD file from the given {@link ResolutionResult} object.
     *
     * <p>Inside the generated BUILD file, it puts:
     * <ul>
     *     <li>maven_import rules for {@link ResolutionResult.dependencies},</li>
     *     <li>maven_artifact rules for {@link ResolutionResult.conflictLosers},</li>
     *     <li>maven_artifact rules for {@link ResolutionResult.parents},</li>
     * </ul>
     */
    public void write(ResolutionResult result) throws Exception {
        fileWriter.append(
                "load(\"@//tools/base/bazel:maven.bzl\", \"maven_artifact\", \"maven_import\", \"maven_pom\")\n\n");
        fileWriter.append("# Bazel rules auto-generated from maven repo.");
        for (ResolutionResult.Dependency dep : result.dependencies) {
            write(dep, false);
        }
        for (ResolutionResult.Dependency dep : result.conflictLosers) {
            write(dep, true);
        }
        for (ResolutionResult.Parent parent : result.parents) {
            write(parent);
        }
        fileWriter.close();
    }

    private void write(ResolutionResult.Dependency dep, boolean isConflictLoser)
            throws IOException {
        if (dep.file == null) return;

        if (!dep.file.endsWith(".jar")) {
            throw new RuntimeException("Unsupported file: " + dep.file);
        }

        Map<String, String> coord_parts = parseCoord(dep.coord);
        String classifier = coord_parts.getOrDefault("classifier", "default");
        if (classifier.equals("sources")) {
            // Do not treat source jars as regular dependencies. They can only be artifacts
            // attached to other non-source dependencies.
            return;
        }

        // Deduce the repo path of the artifact from the file.
        Path artifactRepoPath = Paths.get(dep.file).getParent();

        if (isConflictLoser) {
            // Conflict losers must include their version in the rule name.
            String ruleNameWithVersion = ruleNameFromCoord(dep.coord, true);
            if (generatedRuleNames.add(ruleNameWithVersion)) {
                fileWriter.append("\n");
                fileWriter.append("maven_artifact(\n");
                fileWriter.append(String.format("    name = \"%s\",\n", ruleNameWithVersion));
                fileWriter.append(String.format("    pom = \"%s/%s\",\n", repoPrefix, pathToString(dep.pomPath)));
                fileWriter.append(String.format("    repo_root_path = \"%s\",\n", pathToString(repoPrefix)));
                fileWriter.append(String.format("    repo_path = \"%s\",\n", pathToString(artifactRepoPath)));
                if (dep.parentCoord != null) {
                    String parentRuleName = ruleNameFromCoord(dep.parentCoord, true);
                    fileWriter.append(String.format("    parent = \"%s\",\n", parentRuleName));
                }
                String[] originalDepRuleNames =
                        Arrays.stream(dep.originalDependencies)
                                .map(
                                        d -> {
                                            // We might have already created a maven_import() rule
                                            // for this target. If we did, then we should use the
                                            // unversioned rule name.
                                            String ruleWithoutVersion = ruleNameFromCoord(d, false);
                                            if (generatedRuleNames.contains(ruleWithoutVersion)) {
                                                return ruleWithoutVersion;
                                            } else {
                                                // Fall back to use versioned rule name.
                                                return ruleNameFromCoord(d, true);
                                            }
                                        })
                                .toArray(String[]::new);

                if (originalDepRuleNames.length != 0) {
                    fileWriter.append("    deps = [\n");
                    for (String dependency : originalDepRuleNames) {
                        fileWriter.append(String.format("        \"%s\",\n", dependency));
                    }
                    fileWriter.append("    ],\n");
                }

                fileWriter.append(")\n");
            }
        } else {
            String ruleName = ruleNameFromCoord(dep.coord);
            fileWriter.append("\n");
            fileWriter.append("maven_import(\n");
            fileWriter.append(String.format("    name = \"%s\",\n", ruleName));
            // TODO: Implement classifiers only if we really need them.
            fileWriter.append("    classifiers = [],\n");
            if (dep.parentCoord != null) {
                String parentRuleName = ruleNameFromCoord(dep.parentCoord, true);
                fileWriter.append(String.format("    parent = \"%s\",\n", parentRuleName));
            }
            fileWriter.append("    jars = [\n");
            fileWriter.append(String.format("        \"%s/%s\"\n", repoPrefix, pathToString(dep.file)));
            fileWriter.append("    ],\n");
            for (Map.Entry<String, List<String>> scopedDeps : dep.directDependencies.entrySet()) {
                String scope = scopedDeps.getKey();
                List<String> deps = scopedDeps.getValue();
                if (!deps.isEmpty()) {
                    switch (scope) {
                        case "compile":
                            fileWriter.append("    exports = [\n");
                            break;
                        case "runtime":
                            fileWriter.append("    deps = [\n");
                            break;
                        default:
                            throw new IllegalStateException("Scope " + scope + " is not supported");
                    }
                    for (String d : deps) {
                        fileWriter.append(String.format("        \"%s\",\n", ruleNameFromCoord(d)));
                    }
                    fileWriter.append("    ],\n");
                }
            }
            // Original dependencies use version numbers in their rule names.
            String[] originalDepRuleNames =
                    Arrays.stream(dep.originalDependencies)
                            .map((String d) -> ruleNameFromCoord(d, true))
                            .toArray(String[]::new);
            if (originalDepRuleNames.length != 0) {
                fileWriter.append("    original_deps = [\n");
                for (String originalDependency : originalDepRuleNames) {
                    fileWriter.append(String.format("        \"%s\",\n", originalDependency));
                }
                fileWriter.append("    ],\n");
            } else {
                fileWriter.append("    original_deps = [],\n");
            }
            fileWriter.append(String.format("    pom = \"%s/%s\",\n", repoPrefix, pathToString(dep.pomPath)));
            fileWriter.append(String.format("    repo_root_path = \"%s\",\n", repoPrefix));
            fileWriter.append(String.format("    repo_path = \"%s\",\n", pathToString(artifactRepoPath)));
            if (dep.srcjar != null) {
                fileWriter.append(
                        String.format("    srcjar = \"%s/%s\",\n", repoPrefix, pathToString(dep.file)));
            }
            fileWriter.append("    visibility = [\"//visibility:public\"],\n");
            fileWriter.append(")\n");
            if (!generatedRuleNames.add(ruleName)) {
                throw new RuntimeException("Rule already exists: " + ruleName);
            }
        }
    }

    private void write(ResolutionResult.Parent parent) throws IOException {
        // Generate rule for parent, if a parent exists, and if it's not created already.
        String ruleName = ruleNameFromCoord(parent.coord, true);
        if (!generatedRuleNames.add(ruleName)) return;

        fileWriter.append("\n");
        fileWriter.append("maven_artifact(\n");
        fileWriter.append(String.format("    name = \"%s\",\n", ruleName));
        fileWriter.append(String.format("    pom = \"%s/%s\",\n", repoPrefix, pathToString(parent.pomPath)));
        fileWriter.append(String.format("    repo_root_path = \"%s\",\n", repoPrefix));
        // Deduce the repo path of the artifact from the pom file.
        Path artifactRepoPath = Paths.get(parent.pomPath).getParent();
        fileWriter.append(String.format("    repo_path = \"%s\",\n", pathToString(artifactRepoPath)));
        if (parent.parentCoord != null) {
            String parentRuleName = ruleNameFromCoord(parent.parentCoord, true);
            fileWriter.append(String.format("    parent = \"%s\",\n", parentRuleName));
        }
        fileWriter.append(")\n");
    }

    /** Parses a Maven coordinate into a map whose keys are the names of the coordinate fields. */
    public static Map<String, String> parseCoord(String coord) {
        String[] pieces = coord.split(":");
        String group = pieces[0];
        String artifact = pieces[1];

        if (pieces.length == 3) {
            String version = pieces[2];
            return new HashMap<String, String>() {
                {
                    put("group", group);
                    put("artifact", artifact);
                    put("version", version);
                }
            };
        } else if (pieces.length == 4) {
            String packaging = pieces[2];
            String version = pieces[3];
            return new HashMap<String, String>() {
                {
                    put("group", group);
                    put("artifact", artifact);
                    put("packaging", packaging);
                    put("version", version);
                }
            };
        } else if (pieces.length == 5) {
            String packaging = pieces[2];
            String classifier = pieces[3];
            String version = pieces[4];
            return new HashMap<String, String>() {
                {
                    put("group", group);
                    put("artifact", artifact);
                    put("packaging", packaging);
                    put("classifier", classifier);
                    put("version", version);
                }
            };
        } else {
            throw new RuntimeException("Could not parse maven coordinate: " + coord);
        }
    }

    /**
     * Converts the given Maven coordinate into a string that can be used as a Bazel rule name.
     *
     * @param coord the Maven coordinate for which to generate a Bazel rule name
     * @param useVersion If true, the version field in the given coordinate will also be a part of
     *     the generated rule name.
     */
    public static String ruleNameFromCoord(String coord, boolean useVersion) {
        Map<String, String> parts = parseCoord(coord);

        String ruleName;
        if (parts.containsKey("classifier")) {
            ruleName =
                    String.join(
                            ".",
                            new String[] {
                                parts.get("group"), parts.get("artifact"), parts.get("classifier")
                            });
        } else {
            ruleName = String.join(".", new String[] {parts.get("group"), parts.get("artifact")});
        }

        if (useVersion) {
            return ruleName + "_" + parts.get("version");
        } else {
            return ruleName;
        }
    }

    /** See ruleNameFromCoord(String,boolean) above. */
    public static String ruleNameFromCoord(String coord) {
        return ruleNameFromCoord(coord, false);
    }

    /** Converts path to forward slash separated string. */
    private static String pathToString(Path path) {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return path.toString();
        }

        return path.toString().replaceAll("\\\\", "/");
    }

    /** Converts a string that represents a path into forward slash separated string. */
    private static String pathToString(String input) {
      return pathToString(Paths.get(input));
    }
}
