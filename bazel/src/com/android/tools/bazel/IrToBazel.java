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
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class IrToBazel {

    private final boolean dryRun;

    public IrToBazel(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int convert(IrProject bazelProject, PrintWriter progress, Configuration config)
            throws IOException {

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
                iml.addExclude(pkg.getRelativePath(file));
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
                                        target = "jar";
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
                                    System.err.println("Cannot find package for:" + relJar);
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

        progress.println("Updating BUILD files...");
        CountingListener listener = new CountingListener(progress, dryRun);
        bazel.generate(listener);

        progress.println(String.format("%d BUILD file(s) updated.", listener.getUpdatedPackages()));
        return listener.getUpdatedPackages();
    }

    private static boolean isGenFile(String relJar) {
        return relJar.startsWith("bazel-genfiles");
    }

    private static boolean isBinFile(String relJar) {
        return relJar.startsWith("bazel-bin");
    }


    private static class CountingListener implements Workspace.GenerationListener {
        private final PrintWriter printWriter;
        private final boolean dryRun;
        private int updatedPackages = 0;

        private CountingListener(PrintWriter printWriter, boolean dryRun) {
            this.printWriter = printWriter;
            this.dryRun = dryRun;
        }

        @Override
        public boolean packageUpdated(String packageName) {
            updatedPackages++;
            printWriter.append("Updated ").append(packageName).append("/BUILD").println();
            return !dryRun;
        }

        int getUpdatedPackages() {
            return updatedPackages;
        }
    }
}
