/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.SyncIssueHandler;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

/**
 * Checks for dependencies to ensure Android compatibility
 */
public class DependencyChecker implements SyncIssueHandler {

    @NonNull
    private final String projectName;
    @NonNull
    private final String variantName;
    @NonNull
    private SyncIssueHandler syncIssueHandler;
    @NonNull
    private VariantType variantType;
    @Nullable
    private final VariantType testedVariantType;

    private final List<SyncIssue> syncIssues = Lists.newArrayList();

    /**
     * Contains API levels obtained from dependencies on the legacy com.google.android:android
     * artifact. Keys are specific versions of the artifact, values are the corresponding API
     * levels.
     */
    @NonNull
    private final Map<ModuleVersionIdentifier, Integer> legacyApiLevels = Maps.newHashMap();

    public DependencyChecker(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull SyncIssueHandler syncIssueHandler,
            @NonNull VariantType variantType,
            @Nullable VariantType testedVariantType) {
        this.projectName = projectName;
        this.variantName = variantName;
        this.syncIssueHandler = syncIssueHandler;
        this.variantType = variantType;
        this.testedVariantType = testedVariantType;
    }

    @NonNull
    public Map<ModuleVersionIdentifier, Integer> getLegacyApiLevels() {
        return legacyApiLevels;
    }

    @NonNull
    public List<SyncIssue> getSyncIssues() {
        return syncIssues;
    }

    @NonNull
    public String getVariantName() {
        return variantName;
    }


    /**
     * Checks if a given module should just be excluded from the dependency graph.
     *
     * @param id the module coordinates.
     *
     * @return true if the module should be excluded.
     */
    public boolean checkForExclusion(@NonNull ModuleVersionIdentifier id) {
        String group = id.getGroup();
        String name = id.getName();
        String version = id.getVersion();

        if ("com.google.android".equals(group) && "android".equals(name)) {
            int moduleLevel = getApiLevelFromMavenArtifact(version);
            legacyApiLevels.put(id, moduleLevel);

            handleIssue(
                    id.toString(),
                    SyncIssue.TYPE_DEPENDENCY_MAVEN_ANDROID,
                    SyncIssue.SEVERITY_WARNING,
                    String.format("Ignoring Android API artifact %s for %s", id, variantName));
            return true;
        }

        if (variantType == VariantType.UNIT_TEST) {
            return false;
        }

        if (("org.apache.httpcomponents".equals(group) && "httpclient".equals(name)) ||
                ("xpp3".equals(group) && name.equals("xpp3")) ||
                ("commons-logging".equals(group) && "commons-logging".equals(name)) ||
                ("xerces".equals(group) && "xmlParserAPIs".equals(name)) ||
                ("org.json".equals(group) && "json".equals(name)) ||
                ("org.khronos".equals(group) && "opengl-api".equals(name))) {

            handleIssue(
                    id.toString(),
                    SyncIssue.TYPE_DEPENDENCY_INTERNAL_CONFLICT,
                    SyncIssue.SEVERITY_WARNING,
                    String.format(
                            "WARNING: Dependency %s is ignored for %s as it may be conflicting with the internal version provided by Android.\n"
                                    +
                                    "         In case of problem, please repackage it with jarjar to change the class packages",
                            id, variantName));
            return true;
        }

        return false;
    }

    private static int getApiLevelFromMavenArtifact(@NonNull String version) {
        switch (version) {
            case "1.5_r3":
            case "1.5_r4":
                return 3;
            case "1.6_r2":
                return 4;
            case "2.1_r1":
            case "2.1.2":
                return 7;
            case "2.2.1":
                return 8;
            case "2.3.1":
                return 9;
            case "2.3.3":
                return 10;
            case "4.0.1.2":
                return 14;
            case "4.1.1.4":
                return 15;
        }

        return -1;
    }

    private void compareAndroidDependencies(
            @NonNull Map<String, AndroidDependency> compileMap,
            @NonNull Map<String, AndroidDependency> packageMap,
            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
            @NonNull Map<String, String> testedMap) {
        // For Libraries:
        // Only library projects can support provided aar.
        // However, package(publish)-only are still not supported (they don't make sense).
        // For now, provided only dependencies will be kept normally in the compile-graph.
        // However we'll want to not include them in the resource merging.
        // For Applications (and testing variants
        // All Android libraries must be in both lists.

        // go through the list of keys on the compile map, comparing to the package and the tested
        // one.

        for (String coordinateKey : compileMap.keySet()) {
            AndroidDependency compileLib = compileMap.get(coordinateKey);
            AndroidDependency packageMatch = packageMap.get(coordinateKey);

            if (packageMatch != null) {
                // found a match. Compare to tested, and skip if needed.
                skipTestDependency(mutableDependencyDataMap, packageMatch, testedMap);

                // remove it from the list of package dependencies
                packageMap.remove(coordinateKey);

                // compare versions.
                if (!compileLib.getCoordinates().getVersion()
                        .equals(packageMatch.getCoordinates().getVersion())) {
                    // wrong version, handle the error.
                    handleIssue(
                            coordinateKey,
                            SyncIssue.TYPE_MISMATCH_DEP,
                            SyncIssue.SEVERITY_ERROR,
                            String.format(
                                    "Conflict with dependency '%s' in project '%s'."
                                            + " Resolved versions for compilation (%s) and"
                                            + " packaging (%s) differ. This can generate runtime"
                                            + " errors due to mismatched resources.",
                                    coordinateKey,
                                    this.projectName,
                                    compileLib.getCoordinates().getVersion(),
                                    packageMatch.getCoordinates().getVersion()));
                }

            } else {
                // provided only dependency, which is only
                // possibly if the variant is a library or an atom.
                // However we also mark as provided dependency coming from app module that are
                // tested with a separate module. The way to differentiate this case is that
                // there is actually a matching library in the package list.
                MavenCoordinates resolvedCoordinates = compileLib.getCoordinates();

                if (variantType != VariantType.LIBRARY
                        && (testedVariantType != VariantType.LIBRARY || !variantType.isForTesting())) {
                    handleIssue(
                            resolvedCoordinates.toString(),
                            SyncIssue.TYPE_NON_JAR_PROVIDED_DEP,
                            SyncIssue.SEVERITY_ERROR,
                            String.format(
                                    "Project %s: Provided dependencies can only be jars. %s is an Android Library.",
                                    projectName,
                                    resolvedCoordinates.toString()));
                }

            }
        }

        // at this time, packageMap will only contain package-only dependencies.
        // which is not possible here, so flag them.
        for (AndroidDependency packageOnlyDep : packageMap.values()) {
            MavenCoordinates packagedCoords = packageOnlyDep.getCoordinates();
            handleIssue(
                    packagedCoords.toString(),
                    SyncIssue.TYPE_NON_JAR_PACKAGE_DEP,
                    SyncIssue.SEVERITY_ERROR,
                    String.format(
                            "Project %s: apk-only dependencies can only be jars. %s is an Android Library.",
                            projectName, packagedCoords));
        }
    }

    private void compareJavaDependencies(
            @NonNull Map<String, JavaDependency> compileMap,
            @NonNull Map<String, JavaDependency> packageMap,
            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
            @NonNull Map<String, String> testedMap) {
        // go through the list of keys on the compile map, comparing to the package and the tested
        // one.

        for (String coordinateKey : compileMap.keySet()) {
            JavaDependency packageMatch = packageMap.get(coordinateKey);

            if (packageMatch != null) {
                // found a match. Compare to tested, and skip if needed.
                skipTestDependency(mutableDependencyDataMap, packageMatch, testedMap);

                // remove it from the list of package dependencies
                packageMap.remove(coordinateKey);
            }
        }
    }

    private void skipTestDependency(
            @NonNull MutableDependencyDataMap mutableDependencyDataMap,
            @NonNull Dependency dependency,
            @NonNull Map<String, String> testedMap) {
        if (testedMap.isEmpty()) {
            return;
        }

        MavenCoordinates coordinates = dependency.getCoordinates();
        String testedVersion = testedMap.get(coordinates.getVersionlessId());

        // if there is no similar version in the test dependencies, nothing to do.
        if (testedVersion == null) {
            return;
        }

        // same artifact, skip packaging of the dependency in the test app
        // whether the version is a match or not.
        mutableDependencyDataMap.skip(dependency);

        // if the dependency is present in both tested and test artifact,
        // verify that they are the same version
        if (!testedVersion.equals(coordinates.getVersion())) {
            String artifactInfo =  coordinates.getGroupId() + ":" + coordinates.getArtifactId();
            handleIssue(
                    artifactInfo,
                    SyncIssue.TYPE_MISMATCH_DEP,
                    SyncIssue.SEVERITY_ERROR,
                    String.format(
                            "Conflict with dependency '%s' in project '%s'. Resolved versions for"
                                    + " app (%s) and test app (%s) differ. See"
                                    + " http://g.co/androidstudio/app-test-app-conflict"
                                    + " for details.",
                            artifactInfo,
                            this.projectName,
                            testedVersion,
                            coordinates.getVersion()));
        }
    }

    /**
     * collect a map of (key, library) for all  libraries.
     *
     * The key is either the gradle project path or the maven coordinates. The format of each
     * makes it impossible to have collisions.
     *
     * This only goes through the list and not the children of the library in it, so this expects
     * the dependency graph to have been flattened already.
     *
     * @param dependencies the dependencies
     */
    private static void collectSkippableLibraryMap(
            @NonNull Collection<Dependency> dependencies,
            @NonNull Map<String, AndroidDependency> androidMap,
            @NonNull Map<String, JavaDependency> javaMap) {

        for (Dependency dependency : dependencies) {
            if (dependency instanceof AndroidDependency) {
                MavenCoordinates coordinates = dependency.getCoordinates();
                androidMap.put(coordinates.getVersionlessId(), (AndroidDependency) dependency);
            } else if (dependency instanceof JavaDependency) {
                MavenCoordinates coordinates = dependency.getCoordinates();
                javaMap.put(coordinates.getVersionlessId(), (JavaDependency) dependency);
            }
        }
    }

    @NonNull
    @Override
    public SyncIssue handleIssue(@Nullable String data, int type, int severity,
            @NonNull String msg) {
        SyncIssue issue = syncIssueHandler.handleIssue(data, type, severity, msg);
        syncIssues.add(issue);
        return issue;
    }
}
