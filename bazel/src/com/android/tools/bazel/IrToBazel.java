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
import com.android.tools.bazel.model.FileGroup;
import com.android.tools.bazel.model.ImlModule;
import com.android.tools.bazel.model.JavaImport;
import com.android.tools.bazel.model.JavaLibrary;
import com.android.tools.bazel.model.Package;
import com.android.tools.bazel.model.UnmanagedRule;
import com.android.tools.bazel.model.Workspace;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class IrToBazel {

    private final Configuration config;
    private final BazelToolsLogger logger;

    public IrToBazel(BazelToolsLogger logger, Configuration config) {
        this.config = config;
        this.logger = logger;
    }

    public int convert(IrProject bazelProject) throws IOException {

        File projectDir =
                bazelProject.getBaseDir().toPath().resolve(bazelProject.getProjectPath()).toFile();
        Path workspace = bazelProject.getBaseDir().toPath();
        Workspace bazel = new Workspace(workspace.toFile(), bazelProject.id());

        // Map from file path to the bazel rule that provides it. Usually java_imports.
        Map<String, BazelRule> jarRules = Maps.newHashMap();
        Map<String, JavaLibrary> libraries = Maps.newHashMap();
        Map<IrLibrary, JavaImport> imports = Maps.newHashMap();
        Map<String, FileGroup> groups = Maps.newHashMap();
        Map<IrModule, ImlModule> rules = new HashMap<>();
        Map<String, UnmanagedRule> unmanaged = new HashMap<>();
        Map<String, JavaImport> reuse = Maps.newHashMap();

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
            name = config.nameRule(pkg.getName(), rel, name);
            name = bazelProject.id().isEmpty() ? name : bazelProject.id() + "." + name;
            ImlModule iml = new ImlModule(pkg, name);
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
            ImlModule imlModule = rules.get(module);
            for (IrModule friend : module.getTestFriends()) {
                imlModule.addTestFriend(rules.get(friend));
            }

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
                    if (config.strict) {
                        // TODO: Update iml files to have the right names.
                        Map<String, String> UNMANAGED = ImmutableMap.of(
                                "studio-sdk", "studio-sdk",
                                "studio-plugin", "studio-sdk-plugin",
                                "intellij-updater", "studio-sdk-updater"
                        );
                        IrLibrary library = (IrLibrary) dependency.dependency;
                        JavaImport javaImport = imports.get(library);
                        if (javaImport == null) {
                            Map.Entry<String, String> unmanagedEntry = null;
                            for (Map.Entry<String, String> entry : UNMANAGED.entrySet()) {
                                if (library.name.startsWith(entry.getKey()) &&
                                        library.owner == null) {
                                    unmanagedEntry = entry;
                                    break;
                                }
                            }
                            if (unmanagedEntry != null) {
                                String newName = library.name.replaceAll(
                                        unmanagedEntry.getKey(),
                                        unmanagedEntry.getValue());
                                UnmanagedRule rule = unmanaged.get(newName);
                                if (rule == null) {
                                    rule = new UnmanagedRule(
                                            bazel.findPackage("prebuilts/studio/intellij-sdk"),
                                            newName);
                                    unmanaged.put(newName, rule);
                                }
                                imlModule.addDependency(rule, dependency.exported, scopes);
                                continue;
                            }
                            if (library.owner != null && library.owner != module) {
                                throw new IllegalStateException(
                                        "Module library belongs to a different module");
                            }
                            Package libPackage =
                                    library.owner == null ? librariesPkg : imlModule.getPackage();
                            String libName = library.getName().replaceAll(":", "_");
                            libName =
                                    bazelProject.id().isEmpty()
                                            ? libName
                                            : bazelProject.id() + "." + libName;

                            // Group library files by package
                            ArrayList<Package> pkgs = new ArrayList<>();
                            ArrayList<List<String>> sources = new ArrayList<>();
                            Package last = null;
                            for (File file : library.getFiles()) {
                                String relJar = workspace.relativize(file.toPath()).toString();
                                Package jarPkg = bazel.findPackage(relJar);
                                String relToPkg =
                                        jarPkg.getPackageDir()
                                                .toPath()
                                                .relativize(file.toPath())
                                                .toString();

                                Package pkg = null;
                                String source = null;

                                if (isBinFile(relJar)) {
                                    // There is already a rule, use the current package and a full
                                    // path
                                    pkg = libPackage;
                                    String targetName = new File(relJar).getName();
                                    source = "//" + jarPkg.getName() + ":" + targetName;
                                } else if (jarPkg == libPackage) {
                                    // This must be a prebuilt jar in the source tree.
                                    pkg = libPackage;
                                    source = relToPkg;
                                } else {
                                    pkg = jarPkg;
                                    source = relToPkg;
                                }

                                if (pkg == last) {
                                    sources.get(sources.size() - 1).add(source);
                                } else {
                                    pkgs.add(pkg);
                                    sources.add(new ArrayList<>(Arrays.asList(source)));
                                }
                                last = pkg;
                            }

                            // Generate the rules
                            // In order to generate palatable rules, we specialize common cases
                            if (pkgs.size() == 1 && libPackage != librariesPkg) {
                                // No need to have file groups, we can use a java_import where the
                                // files are (except for named project level libraries)
                                Package pkg = pkgs.get(0);
                                String key = pkg.getName() + "@";
                                for (String src : sources.get(0)) {
                                    key += src + ":";
                                }
                                javaImport = reuse.get(key);
                                if (javaImport == null) {
                                    javaImport = new JavaImport(pkg, libName);
                                    for (String src : sources.get(0)) {
                                        javaImport.addJar(src);
                                    }
                                    reuse.put(key, javaImport);
                                }
                            } else {
                                javaImport = new JavaImport(libPackage, libName);
                                // General case
                                for (int i = 0; i < pkgs.size(); i++) {
                                    Package pkg = pkgs.get(i);
                                    List<String> srcs = sources.get(i);
                                    if (pkg == libPackage) {
                                        for (String src : srcs) {
                                            javaImport.addJar(src);
                                        }
                                    } else {
                                        String key = pkg.getName() + ":" + libName;
                                        FileGroup fileGroup = groups.get(key);
                                        if (fileGroup == null) {
                                            fileGroup = new FileGroup(pkg, libName + "_files");
                                            for (String src : srcs) {
                                                fileGroup.addSource(src);
                                            }
                                            groups.put(key, fileGroup);
                                        }
                                        javaImport.addJar(fileGroup.getLabel());
                                    }
                                }
                            }
                            imports.put(library, javaImport);
                        }
                        imlModule.addDependency(javaImport, dependency.exported, scopes);
                        if (library.owner == module
                                && !scopes.contains(ImlModule.Tag.TEST)
                                && !dependency.scope.equals(IrModule.Scope.PROVIDED)) {
                            imlModule.addBundledDep(javaImport);
                        }
                    } else {
                        addLegacyLibraryDependency(
                                bazelProject,
                                workspace,
                                bazel,
                                jarRules,
                                libraries,
                                rules,
                                unmanaged,
                                module,
                                librariesPkg,
                                dependency,
                                scopes);
                    }
                } else if (dependency.dependency instanceof IrModule) {
                    scopes.add(0, ImlModule.Tag.MODULE);
                    imlModule.addDependency(
                            rules.get(dependency.dependency), dependency.exported, scopes);
                }
            }
        }

        for (Package pkg : bazel.getPackages()) {
            for (BazelRule rule : pkg.getRules()) {
                if (config.shouldSuppress(rule)) {
                    rule.suppress();
                }
            }
        }

        logger.info("Updating BUILD files...");
        CountingListener listener = new CountingListener(logger, config.dryRun);
        bazel.generate(listener);

        logger.info("%d BUILD file(s) updated.", listener.getUpdatedPackages());
        return listener.getUpdatedPackages();
    }

    private void addLegacyLibraryDependency(
            IrProject bazelProject,
            Path workspace,
            Workspace bazel,
            Map<String, BazelRule> jarRules,
            Map<String, JavaLibrary> libraries,
            Map<IrModule, ImlModule> rules,
            Map<String, UnmanagedRule> unmanaged,
            IrModule module,
            Package librariesPkg,
            IrModule.Dependency<? extends IrNode> dependency,
            List<ImlModule.Tag> scopes) {
        IrLibrary library = (IrLibrary) dependency.dependency;
        JavaLibrary namedLib = null;
        if (library.name.equals("studio-sdk") && library.owner == null) {
            UnmanagedRule rule = unmanaged.get("studio-sdk");
            if (rule == null) {
                rule =
                        new UnmanagedRule(
                                bazel.findPackage("prebuilts/studio/intellij-sdk"), "studio-sdk");
                unmanaged.put("studio-sdk", rule);
            }
            rules.get(module).addDependency(rule, dependency.exported, scopes);
        } else if (library.owner == null) {
            //noinspection ConstantConditions - getLibraryName() is not null for module-level
            // libraries.
            String libName = library.name.replaceAll(":", "_");
            libName = bazelProject.id().isEmpty() ? libName : bazelProject.id() + "." + libName;
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
                if (isBinFile(relJar)) {
                    String targetName = Files.getNameWithoutExtension(relJar);
                    if (targetName.startsWith("lib")) {
                        targetName = targetName.substring("lib".length());
                    }
                    jarRule = new UnmanagedRule(bazel.findPackage(relJar), targetName);
                } else {
                    // This must be a prebuilt jar in the source tree.
                    Package jarPkg = bazel.findPackage(relJar);
                    if (jarPkg != null) {
                        String packageRelative =
                                jarPkg.getPackageDir()
                                        .toPath()
                                        .relativize(file.toPath())
                                        .toString();

                        // TODO: Fix all these dependencies correctly
                        String target;
                        if (relJar.startsWith("prebuilts/tools/common/m2")) {
                            target = pickTargetNameForMavenArtifact(file);
                        } else {
                            target = packageRelative.replaceAll("\\.jar$", "");
                            if (!bazelProject.id().isEmpty()) {
                                target = target.replaceFirst("([^/]*)$", bazelProject.id() + ".$1");
                            }
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
                    ImlModule imlModule = rules.get(module);
                    imlModule.addDependency(jarRule, dependency.exported, scopes);
                }
            }
        }
        if (namedLib != null && !namedLib.isEmpty()) {
            rules.get(module).addDependency(namedLib, dependency.exported, scopes);
        }
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
