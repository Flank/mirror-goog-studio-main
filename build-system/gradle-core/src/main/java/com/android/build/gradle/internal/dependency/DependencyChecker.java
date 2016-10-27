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
import com.android.builder.dependency.DependenciesMutableData;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.SkippableLibrary;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.tools.ant.taskdefs.Java;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    public String getProjectName() {
        return projectName;
    }

    @NonNull
    public String getVariantName() {
        return variantName;
    }

    /**
     * Validate the dependencies after they have been fully resolved.
     *
     * This will compare the compile/package graphs, as well as the graphs of an optional
     * tested variant.
     *
     *
     * @param variantDependencies the variant dependencies
     * @param testedVariantDeps an optional tested dependencies.
     */
    public void validate(
            @NonNull VariantDependencies variantDependencies,
            @Nullable VariantDependencies testedVariantDeps) {
        // tested map if applicable.
        Map<String, String> testedMap = collectTestedDependencyMap(testedVariantDeps);

        final DependencyContainer flattenedCompileDependencies = variantDependencies
                .getFlattenedCompileDependencies();
        final DependencyContainer flattenedPackageDependencies = variantDependencies
                .getFlattenedPackageDependencies();

        compareAndroidDependencies(
                flattenedCompileDependencies.getAndroidDependencies(),
                flattenedPackageDependencies.getAndroidDependencies(),
                flattenedPackageDependencies.getDependenciesMutableData(),
                testedMap);

        compareJavaDependencies(
                flattenedCompileDependencies.getJarDependencies(),
                flattenedPackageDependencies.getJarDependencies(),
                flattenedPackageDependencies.getDependenciesMutableData(),
                testedMap);
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
            @NonNull List<AndroidLibrary> compileLibs,
            @NonNull List<AndroidLibrary> packageLibs,
            @NonNull DependenciesMutableData packageLibsMutableData,
            @NonNull Map<String, String> testedMap) {
        // For Libraries:
        // Only library projects can support provided aar.
        // However, package(publish)-only are still not supported (they don't make sense).
        // For now, provided only dependencies will be kept normally in the compile-graph.
        // However we'll want to not include them in the resource merging.
        // For Applications (and testing variants
        // All Android libraries must be in both lists.

        Map<String, AndroidLibrary> compileMap = collectSkippableLibraryMap(compileLibs);
        Map<String, AndroidLibrary> packageMap = collectSkippableLibraryMap(packageLibs);

        // go through the list of keys on the compile map, comparing to the package and the tested
        // one.

        for (String coordinateKey : compileMap.keySet()) {
            AndroidLibrary compileLib = compileMap.get(coordinateKey);
            AndroidLibrary packageMatch = packageMap.get(coordinateKey);

            if (packageMatch != null) {
                // found a match. Compare to tested, and skip if needed.
                skipTestDependency(packageLibsMutableData, (SkippableLibrary) packageMatch, testedMap);

                // remove it from the list of package dependencies
                packageMap.remove(coordinateKey);

                // compare versions.
                if (!compileLib.getResolvedCoordinates().getVersion()
                        .equals(packageMatch.getResolvedCoordinates().getVersion())) {
                    // wrong version, handle the error.
                    handleIssue(
                            coordinateKey,
                            SyncIssue.TYPE_MISMATCH_DEP,
                            SyncIssue.SEVERITY_ERROR,
                            String.format(
                                    "Conflict with dependency '%s'. Resolved versions for"
                                            + " compilation (%s) and packaging (%s) differ. This can "
                                            + "generate runtime errors due to mismatched resources.",
                                    coordinateKey,
                                    compileLib.getResolvedCoordinates().getVersion(),
                                    packageMatch.getResolvedCoordinates().getVersion()));
                }

            } else {
                // provided only dependency, which is only
                // possibly if the variant is a library or an atom.
                // However we also mark as provided dependency coming from app module that are
                // tested with a separate module. The way to differentiate this case is that
                // there is actually a matching library in the package list.
                MavenCoordinates resolvedCoordinates = compileLib.getResolvedCoordinates();

                if (variantType != VariantType.LIBRARY
                        && variantType != VariantType.ATOM
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
        for (AndroidLibrary packageOnlyLib : packageMap.values()) {
            MavenCoordinates packagedCoords = packageOnlyLib.getResolvedCoordinates();
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
            @NonNull List<JavaLibrary> compileJars,
            @NonNull List<JavaLibrary> packageJars,
            @NonNull DependenciesMutableData packageDependenciesMutableData,
            @NonNull Map<String, String> testedMap) {
        Map<String, JavaLibrary> compileMap = collectSkippableLibraryMap(compileJars);
        Map<String, JavaLibrary> packageMap = collectSkippableLibraryMap(packageJars);

        // go through the list of keys on the compile map, comparing to the package and the tested
        // one.

        for (String coordinateKey : compileMap.keySet()) {
            JavaLibrary packageMatch = packageMap.get(coordinateKey);

            if (packageMatch != null) {
                // found a match. Compare to tested, and skip if needed.
                skipTestDependency(packageDependenciesMutableData, (SkippableLibrary) packageMatch, testedMap);

                // remove it from the list of package dependencies
                packageMap.remove(coordinateKey);
            }
        }
    }

    private void skipTestDependency(
            @NonNull DependenciesMutableData dependenciesMutableData,
            @NonNull SkippableLibrary library,
            @NonNull Map<String, String> testedMap) {
        if (testedMap.isEmpty()) {
            return;
        }

        MavenCoordinates coordinates = library.getResolvedCoordinates();
        String testedVersion = testedMap.get(computeVersionLessCoordinateKey(coordinates));

        // if there is no similar version in the test dependencies, nothing to do.
        if (testedVersion == null) {
            return;
        }

        // same artifact, skip packaging of the dependency in the test app
        // whether the version is a match or not.
        dependenciesMutableData.skip(library);

        // if the dependency is present in both tested and test artifact,
        // verify that they are the same version
        if (!testedVersion.equals(coordinates.getVersion())) {
            String artifactInfo =  coordinates.getGroupId() + ":" + coordinates.getArtifactId();
            handleIssue(
                    artifactInfo,
                    SyncIssue.TYPE_MISMATCH_DEP,
                    SyncIssue.SEVERITY_ERROR,
                    String.format(
                            "Conflict with dependency '%s'. Resolved versions for"
                                    + " app (%s) and test app (%s) differ. See"
                                    + " http://g.co/androidstudio/app-test-app-conflict"
                                    + " for details.",
                            artifactInfo,
                            testedVersion,
                            coordinates.getVersion()));
        }
    }

    /**
     * Returns a map representing the tested dependencies. This represents only the packaged
     * ones as they are the one that matters when figuring out what to skip in the test
     * graphs.
     *
     * The map represents (dependency key, version) where the key is basically
     * the coordinates minus the version.
     *
     * @return the map
     *
     * @see #computeVersionLessCoordinateKey(MavenCoordinates)
     */
    private static Map<String, String> collectTestedDependencyMap(
            @Nullable VariantDependencies testedVariantDeps) {
        if (testedVariantDeps == null) {
            return ImmutableMap.of();
        }

        DependencyContainer packageDependencies = testedVariantDeps.getFlattenedPackageDependencies();

        List<JavaLibrary> testedJars = packageDependencies.getJarDependencies();
        List<AndroidLibrary> testedLibs = packageDependencies.getAndroidDependencies();

        Map<String, String> map = Maps.newHashMapWithExpectedSize(
                testedJars.size() + testedLibs.size());

        fillTestedDependencyMap(testedJars, map);
        fillTestedDependencyMap(testedLibs, map);

        return map;
    }

    private static void fillTestedDependencyMap(
            @NonNull Collection<? extends Library> dependencies,
            @NonNull Map<String, String> map) {
        // the list is the flattened list already so no need to recursively go through
        for (Library library : dependencies) {
            // ignore sub-modules
            if (library.getProject() == null) {
                MavenCoordinates coordinates = library.getResolvedCoordinates();
                map.put(
                        computeVersionLessCoordinateKey(coordinates),
                        coordinates.getVersion());
            }
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
     * @return the map.
     */
    private static <T extends Library> Map<String, T> collectSkippableLibraryMap(
            @NonNull Collection<T> dependencies) {

        Map<String, T> map = Maps.newHashMapWithExpectedSize(
                dependencies.size());

        for (T library : dependencies) {
            if (library.getProject() != null) {
                map.put(library.getProject(), library);
            } else {
                MavenCoordinates coordinates = library.getResolvedCoordinates();
                map.put(computeVersionLessCoordinateKey(coordinates), library);
            }
        }

        return map;
    }

    /**
     * Compute a version-less key representing the given coordinates.
     * @param coordinates the coordinates
     * @return the key.
     */
    @NonNull
    public static String computeVersionLessCoordinateKey(@NonNull MavenCoordinates coordinates) {
        if (coordinates instanceof MavenCoordinatesImpl) {
            return ((MavenCoordinatesImpl) coordinates).getVersionLessId();
        }
        StringBuilder sb = new StringBuilder(coordinates.getGroupId());
        sb.append(':').append(coordinates.getArtifactId());
        if (coordinates.getClassifier() != null) {
            sb.append(':').append(coordinates.getClassifier());
        }
        return sb.toString();
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
