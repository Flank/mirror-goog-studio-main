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

import com.android.tools.bazel.ir.IrLibrary;
import com.android.tools.bazel.ir.IrModule;
import com.android.tools.bazel.ir.IrNode;
import com.android.tools.bazel.ir.IrProject;
import com.android.tools.bazel.model.BazelRule;
import com.android.tools.bazel.model.ImlModule;
import com.android.tools.bazel.model.ImlProject;
import com.android.tools.bazel.model.JavaImport;
import com.android.tools.bazel.model.JavaLibrary;
import com.android.tools.bazel.model.Label;
import com.android.tools.bazel.model.Package;
import com.android.tools.bazel.model.UnmanagedRule;
import com.android.tools.bazel.model.Workspace;
import com.google.common.base.Verify;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class IrToBazel {

    private final boolean dryRun;
    private final BazelToolsLogger logger;

    public IrToBazel(BazelToolsLogger logger, boolean dryRun) {
        this.dryRun = dryRun;
        this.logger = logger;
    }

    public int convert(IrProject bazelProject, Configuration config) throws IOException {

        File projectDir = bazelProject.getBaseDir().toPath().resolve("tools/idea").toFile();
        Path workspace = bazelProject.getBaseDir().toPath();
        Workspace bazel = new Workspace(workspace.toFile());

        // Map from file path to the bazel rule that provides it. Usually java_imports.
        Map<String, BazelRule> jarRules = Maps.newHashMap();
        Map<String, JavaLibrary> libraries = Maps.newHashMap();
        Map<IrModule, ImlModule> rules = new HashMap<>();

        // 1st pass: Creation.
        for (IrModule bazelModule : bazelProject.modules) {
            String name = bazelModule.getName();
            String rel = workspace.relativize(bazelModule.getBaseDir()).toString();

            Package pkg = bazel.findPackage(rel);
            if (pkg == null) {
                throw new RuntimeException(
                        "Could not find package for module "
                                + rel
                                + " (does it not have a BUILD file yet?)");
            }
            ImlModule iml = new ImlModule(pkg, config.nameRule(pkg.getName(), rel, name));
            rules.put(bazelModule, iml);

            for (File file : bazelModule.getImls()) {
                iml.addModuleFile(pkg.getRelativePath(file));
            }

            for (File file : bazelModule.getSources()) {
                iml.addSource(pkg.getRelativePath(file));
            }

            for (File file : bazelModule.getTestSources()) {
                iml.addTestSource(pkg.getRelativePath(file));
            }

            for (File file : bazelModule.getResources()) {
                iml.addResource(pkg.getRelativePath(file));
            }

            for (File file : bazelModule.getTestResources()) {
                iml.addTestResource(pkg.getRelativePath(file));
            }

            for (Map.Entry<File, String> prefix : bazelModule.getPrefixes().entrySet()) {
                iml.addPackagePrefix(pkg.getRelativePath(prefix.getKey()), prefix.getValue());
            }

            for (File file : bazelModule.getExcludes()) {
                // A file can be a pointer to either a directory or a file.
                // If the file is a pointer to a directory then we must include *
                // to indicate all files in the directory.
                iml.addExclude(pkg.getRelativePath(file) + (file.isDirectory() ? "/*" : ""));
            }
        }

        // 2nd pass: Dependencies.
        for (IrModule module : bazelProject.modules) {
            File librariesDir = new File(projectDir, ".idea/libraries");
            Package librariesPkg = bazel
                    .findPackage(workspace.relativize(librariesDir.toPath()).toString());
            for (IrModule.Dependency<? extends IrNode> dependency : module
                    .getDependencies()) {
                List<ImlModule.Tag> scopes = new LinkedList<>();
                if (dependency.scope == IrModule.Scope.TEST) {
                    scopes.add(ImlModule.Tag.TEST);
                }
                if (dependency.scope == IrModule.Scope.RUNTIME) {
                    scopes.add(ImlModule.Tag.RUNTIME);
                }
                if (dependency.scope == IrModule.Scope.TEST_RUNTIME) {
                    scopes.add(ImlModule.Tag.TEST);
                    scopes.add(ImlModule.Tag.RUNTIME);
                }
                if (dependency.dependency instanceof IrLibrary) {
                    IrLibrary library = (IrLibrary) dependency.dependency;
                    JavaLibrary namedLib = null;
                    if (library.owner == null) {
                        //noinspection ConstantConditions - getLibraryName() is not null for module-level libraries.
                        String libName = library.name.replaceAll(":", "_");
                        namedLib = libraries.get(libName.toLowerCase());
                        if (namedLib == null) {
                            namedLib = new JavaLibrary(librariesPkg, libName);
                            libraries.put(libName.toLowerCase(), namedLib);
                        }
                    }
                    for (File file : library.getFiles()) {
                        String relJar = workspace.relativize(file.toPath()).toString();

                        if (relJar.startsWith("tools/idea/build/dependencies/build/kotlin")) {
                            // These files are used by the "KotlinPlugin" library and get copied
                            // there by build_studio.sh that most Studio developers don't run
                            // locally. They are not needed for our build, because Android Studio
                            // has its own library, called "kotlin-plugin", that points to files in
                            // prebuilts. Here we ignore these files, so that output of iml_to_build
                            // doesn't depend on whether build_studio.sh was run or not.
                            continue;
                        }

                        BazelRule jarRule = jarRules.get(relJar);
                        if (jarRule == null) {
                            if (isGenFile(relJar)) {
                                // Assume the rule is the same as the file name. This seems like a reasonable
                                // assumption and is good enough to handle the profilers jarjar case.
                                jarRule =
                                        new UnmanagedRule(
                                                bazel.findPackage(relJar),
                                                Files.getNameWithoutExtension(relJar));
                            } else if (isBinFile(relJar)) {
                                String targetName = Files.getNameWithoutExtension(relJar);
                                if (targetName.startsWith("lib")) {
                                    targetName = targetName.substring("lib".length());
                                }
                                jarRule = new UnmanagedRule(bazel.findPackage(relJar), targetName);
                            } else {
                                // This must be a prebuilt jar in the source tree.
                                Package jarPkg = bazel.findPackage(relJar);
                                if (jarPkg != null) {
                                    String packageRelative = jarPkg.getPackageDir().toPath()
                                            .relativize(file.toPath()).toString();

                                    // TODO: Fix all these dependencies correctly
                                    String target;
                                    if (relJar.startsWith("prebuilts/tools/common/m2")) {
                                        target = pickTargetNameForMavenArtifact(file);
                                    } else {
                                        target = packageRelative.replaceAll("\\.jar$", "");
                                    }
                                    try {
                                        JavaImport javaImport = new JavaImport(jarPkg, target);
                                        javaImport.addJar(packageRelative);
                                        jarRule = javaImport;
                                    } catch (IllegalStateException e) {
                                        throw new IllegalStateException("Cannot add jar " + packageRelative, e);
                                    }
                                } else {
                                    logger.warning("Cannot find package for %s", relJar);
                                }
                            }
                        }

                        if (jarRule != null) {
                            jarRules.put(relJar, jarRule);

                            if (namedLib != null) {
                                namedLib.addDependency(jarRule, true);
                            } else {
                                rules.get(module)
                                        .addDependency(jarRule, dependency.exported, scopes);
                            }
                        }
                    }
                    if (namedLib != null && !namedLib.isEmpty()) {
                        rules.get(module).addDependency(namedLib, dependency.exported, scopes);
                    }
                } else if (dependency.dependency instanceof IrModule) {
                    scopes.add(0, ImlModule.Tag.MODULE);
                    rules.get(module)
                            .addDependency(rules.get(dependency.dependency), dependency.exported,
                                    scopes);
                }
            }
        }

        // Manually export some additional jars that we need
        for (String s : config.getAdditionalImports()) {
            Label label = new Label(s);
            Package pkg = bazel.getPackage(label.pkg);
            JavaImport imp = new JavaImport(pkg, label.target);
            imp.addJar(label.target + ".jar");
            imp.setExport();
        }

        for (Package pkg : bazel.getPackages()) {
            for (BazelRule rule : pkg.getRules()) {
                if (config.shouldSuppress(rule)) {
                    rule.suppress();
                }
            }
        }

        Package projectPkg = bazel
                .findPackage(workspace.relativize(projectDir.toPath()).toString());
        ImlProject imlProject = new ImlProject(projectPkg, "android-studio");
        rules.values().stream().filter(ImlModule::isExport).sorted(Comparator.comparing(BazelRule::getLabel))
                .forEach(imlProject::addModule);

        logger.info("Updating BUILD files...");
        CountingListener listener = new CountingListener(logger, dryRun);
        bazel.generate(listener);

        logger.info("%d BUILD file(s) updated.", listener.getUpdatedPackages());
        return listener.getUpdatedPackages();
    }

    private static boolean isGenFile(String relJar) {
        return relJar.startsWith("bazel-genfiles");
    }

    private static boolean isBinFile(String relJar) {
        return relJar.startsWith("bazel-bin");
    }

    /**
     * Computes name for the {@code java_import} target corresponding to {@code file}.
     *
     * <p>The input file needs to be inside a directory structure of a Maven repository. The file's
     * extension and classifier are taken into account. For example:
     *
     * <ul>
     *   <li>Target for {@code junit-4.12.jar} is {@code jar}
     *   <li>Target for {@code trove4j-1.0.20160824.jar} is {@code jar}
     *   <li>Target for {@code guice-4.2.1-no_aop.jar} is {@code no_aop.jar}
     * </ul>
     */
    private static String pickTargetNameForMavenArtifact(File file) {
        String fileName = file.getName();
        File versionDir = file.getParentFile();
        Verify.verifyNotNull(versionDir, "'%s' is not a valid maven artifact file name.", fileName);
        String version = versionDir.getName();
        int indexOfVersion = fileName.lastIndexOf(version);
        Verify.verify(
                indexOfVersion > 0, "'%s' is not a valid maven artifact file name.", fileName);

        int indexOfExtension = fileName.lastIndexOf('.');
        Verify.verify(
                indexOfExtension > 0, "'%s' is not a valid maven artifact file name.", fileName);
        String result = fileName.substring(indexOfExtension + 1);

        int indexOfClassifier = indexOfVersion + version.length();
        Verify.verify(
                indexOfClassifier < fileName.length(),
                "'%s' is not a valid maven artifact file name.",
                fileName);
        if (fileName.charAt(indexOfClassifier) == '-') {
            String classifier = fileName.substring(indexOfClassifier + 1, indexOfExtension);
            result = classifier + "." + result;
        }

        return result;
    }

    private static class CountingListener implements Workspace.GenerationListener {
        private final BazelToolsLogger logger;
        private final boolean dryRun;
        private int updatedPackages = 0;

        private CountingListener(BazelToolsLogger logger, boolean dryRun) {
            this.logger = logger;
            this.dryRun = dryRun;
        }

        @Override
        public boolean packageUpdated(String packageName) {
            updatedPackages++;
            if (dryRun) {
                logger.info("%s/BUILD out of date.", packageName);
            } else {
                logger.info("Updated %s/BUILD", packageName);
            }
            return !dryRun;
        }

        @Override
        public void error(String description) {
            logger.error(description);
        }

        int getUpdatedPackages() {
            return updatedPackages;
        }
    }
}
