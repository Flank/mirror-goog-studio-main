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
import com.android.tools.bazel.model.JavaImport;
import com.android.tools.bazel.model.Package;
import com.android.tools.bazel.model.Workspace;
import com.google.common.collect.Maps;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class GenerateBazelAction extends AnAction {
    public GenerateBazelAction() {
        super("Generate Bazel files");
    }

    public void actionPerformed(AnActionEvent event) {
        final Project project = event.getData(PlatformDataKeys.PROJECT);

        ConsoleDialog dialog = new ConsoleDialog(project, "Generating BUILD files...");
        AsyncResult<Boolean> result = dialog.showAndGetOk();
        ((CompilerConfigurationImpl) CompilerConfiguration.getInstance(project)).convertPatterns();
        try {
            generate(project, dialog.getWriter(), new StudioConfiguration());
        } catch (Throwable e) {
            e.printStackTrace(dialog.getWriter());
            e.printStackTrace();
        } finally {
            result.getResult();
        }
    }


    private void generate(Project project, PrintWriter progress, Configuration config) throws IOException {
        File workspace = findWorkspace(project);
        Workspace bazel = new Workspace(workspace);

        final ExcludesConfiguration excludesConfiguration = CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();
        ExcludeEntryDescription[] excludeEntries = excludesConfiguration.getExcludeEntryDescriptions();

        config.configureWorkspace(bazel);

        Map<String, JavaImport> imports = Maps.newHashMap();
        Map<Module, BazelModule> inverse = Maps.newHashMap();

        progress.append("Processing modules...").append("\n");
        List<BazelModule> modules = getModules(project, inverse);

        // 1st pass: Creation.
        for (BazelModule bazelModule : modules) {
            String name = bazelModule.getName();
            String rel = FileUtil.getRelativePath(workspace, bazelModule.getBaseDir());

            Package pkg = bazel.findPackage(rel);
            ImlModule iml = new ImlModule(pkg, config.nameRule(rel, name));
            bazelModule.rule = iml;

            // Add all the source and resource paths
            for (ContentEntry contentEntry : bazelModule.getContentEntries()) {
                final SourceFolder[] folders = contentEntry.getSourceFolders();

                for (SourceFolder folder : folders) {
                    VirtualFile root = folder.getFile();
                    if (root != null) {
                        String sourceLocalPath = PathUtil.getLocalPath(root);
                        File sourceDirectory = new File(sourceLocalPath);
                        String relativePath = FileUtil.getRelativePath(pkg.getPackageDir(), sourceDirectory);

                        if (folder.getRootType() instanceof JavaSourceRootType) {
                            if (folder.isTestSource()) {
                                iml.addTestSource(relativePath);
                            } else {
                                iml.addSource(relativePath);
                            }
                        } else {
                            if (folder.isTestSource()) {
                                iml.addTestResource(relativePath);
                            } else {
                                iml.addResource(relativePath);
                            }
                        }

                        // Projects can exclude specific files from compilation
                        for (ExcludeEntryDescription entry : excludeEntries) {
                            VirtualFile vf = entry.getVirtualFile();
                            if (vf != null) {
                                File excludeFile = VfsUtil.virtualToIoFile(vf);
                                if (FileUtil.isAncestor(sourceDirectory, excludeFile, true)) {
                                    String relExclude = FileUtil.getRelativePath(pkg.getPackageDir(), excludeFile);
                                    iml.addExclude(relExclude);
                                }
                            }
                        }

                    }
                }
            }
            iml.setTestData(config.getTestData().get(name));
            iml.setTestTimeout(config.getTestTimeout().get(name));
            iml.setTestClass(config.getTestClass().get(name));
        }

        // 2nd pass: Dependencies.
        for (BazelModule module : modules) {
            for (OrderEntry orderEntry : module.getOrderEntries()) {
                if (orderEntry instanceof LibraryOrderEntry) {
                    // A dependency to a jar file
                    LibraryOrderEntry libraryEntry = (LibraryOrderEntry) orderEntry;
                    Library library = libraryEntry.getLibrary();
                    if (library != null) {
                        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
                        for (VirtualFile libFile : files) {
                            if (libFile.getFileType() instanceof ArchiveFileType) {
                                VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(libFile);
                                if (jar != null) {
                                    if (jar.getName().endsWith("-sources.jar")) {
                                        continue;
                                    }

                                    File jarFile = VfsUtil.virtualToIoFile(jar);
                                    String relJar = FileUtil.getRelativePath(workspace, jarFile);
                                    JavaImport imp = imports.get(relJar);
                                    if (imp == null) {
                                        Package jarPkg = bazel.findPackage(relJar);
                                        if (jarPkg != null) {
                                            String packageRelative = FileUtil.getRelativePath(jarPkg.getPackageDir(), new File(jar.getPath()));
                                            // TODO: Fix all these dependencies correctly
                                            String label = config.mapImportJar(relJar);
                                            String target;
                                            if (label != null) {
                                                Label pkgAndTarget = new Label(label);
                                                jarPkg = new Package(null, pkgAndTarget.pkg);
                                                target = pkgAndTarget.target;
                                            } else if (relJar.startsWith("prebuilts/tools/common/m2")) {
                                                target = "jar";
                                            } else {
                                                target = packageRelative.replaceAll("\\.jar$", "");
                                            }
                                            imp = new JavaImport(jarPkg, target);
                                            imp.addJar(packageRelative);
                                            imports.put(relJar, imp);
                                        } else {
                                            System.err.println("Cannot find package for:" + relJar);
                                        }
                                    }
                                    if (imp != null) {
                                        List<String> scopes = new LinkedList<>();
                                        if (libraryEntry.getScope().equals(DependencyScope.TEST)) {
                                            scopes.add("test");
                                        }
                                        module.rule.addDependency(imp, libraryEntry.isExported(), scopes);
                                    }
                                } else {
                                    System.err.println("Cannot find file for: " + libFile);
                                }
                            }
                        }
                    } else {
                        System.err.println("No library for entry " + libraryEntry);
                    }
                } else if (orderEntry instanceof ModuleOrderEntry) {
                    // A dependency to another module
                    ModuleOrderEntry entry = (ModuleOrderEntry) orderEntry;
                    Module dep = entry.getModule();
                    BazelModule bazelModuleDep = inverse.get(dep);
                    List<String> scopes = new LinkedList<>();
                    scopes.add("module");
                    // TODO: Figure out how to add test if we depend on a multi-module rule
                    if (bazelModuleDep.isSingle() && entry.getScope().equals(DependencyScope.TEST)) {
                        scopes.add("test");
                    }
                    if (bazelModuleDep != module) {
                        module.rule.addDependency(bazelModuleDep.rule, entry.isExported(), scopes);
                    }
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

        // Let's generate only what's needed for the exported targets.
        for (String s : config.getLabelsToExport()) {
            Label label = new Label(s);
            bazel.getPackage(label.pkg).getRule(label.target).setExport();
        }

        progress.append("Saving BUILD files...\n");
        bazel.generate();

        for (Map.Entry<String, String> entry : config.getCopySpec().entrySet()) {
            FileUtil.copy(new File(workspace, entry.getKey()), new File(workspace, entry.getValue()));

        }
        progress.append("Done.\n");
    }

    private File findWorkspace(Project project) {
        File dir = VfsUtil.virtualToIoFile(project.getBaseDir());
        while (dir != null && !new File(dir, "WORKSPACE").exists()) {
            dir = dir.getParentFile();
        }
        return dir;
    }

    @NotNull
    private List<BazelModule> getModules(Project project, Map<Module, BazelModule> inverse) {
        List<Module> all = Arrays.asList(ModuleManager.getInstance(project).getModules());
        List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(project, all);

        List<BazelModule> modules = new LinkedList<>();
        for (Chunk<Module> chunk : chunks) {

            BazelModule simple = new BazelModule();
            for (Module module : chunk.getNodes()) {
                simple.add(module);
                inverse.put(module, simple);
            }
            modules.add(simple);
        }
        return modules;
    }

    private class Label {
        final public String pkg;
        final public String target;

        public Label(String label) {
            String[] split = label.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("Label is malformed: " + label);
            }
            this.pkg = split[0];
            this.target = split[1];
        }
    }
}