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

import com.android.tools.bazel.model.*;
import com.android.tools.bazel.model.Package;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;


public class GenerateBazelAction extends AnAction {
    private static final String KOTLIN_RUNTIME = "KotlinJavaRuntime";
    private static final String KOTLIN_TEST = "KotlinTest";
    private static final Logger LOG = Logger.getInstance(GenerateBazelAction.class);

    public GenerateBazelAction() {
        super("Generate Bazel files");
    }

    public void actionPerformed(AnActionEvent event) {
        final Project project = event.getData(PlatformDataKeys.PROJECT);
        assert project != null;

        ConsoleDialog dialog = new ConsoleDialog(project, "Generating BUILD files...");
        AsyncResult<Boolean> result = dialog.showAndGetOk();
        ((CompilerConfigurationImpl) CompilerConfiguration.getInstance(project)).convertPatterns();
        try {
            generate(project, dialog.getWriter(), new StudioConfiguration());
        } catch (Throwable e) {
            e.printStackTrace(dialog.getWriter());
            LOG.error(e);
        } finally {
            result.getResult();
        }
    }


    private void generate(Project project, PrintWriter progress, Configuration config) throws IOException {
        File workspace = findWorkspace(project);
        Workspace bazel = new Workspace(workspace);
        Package toolsBaseBazel = bazel.findPackage("tools/base/bazel");

        final ExcludesConfiguration excludesConfiguration = CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();
        ExcludeEntryDescription[] excludeEntries = excludesConfiguration.getExcludeEntryDescriptions();

        Map<String, JavaImport> imports = Maps.newHashMap();
        Map<String, JavaLibrary> libraries = Maps.newHashMap();

        progress.append("Processing modules...").append("\n");
        ImmutableMap<Module, BazelModule> imlToBazel = createBazelModules(project);
        ImmutableSet<BazelModule> modules = ImmutableSet.copyOf(imlToBazel.values());

        // 1st pass: Creation.
        for (BazelModule bazelModule : modules) {
            String name = bazelModule.getName();
            String rel = FileUtil.getRelativePath(workspace, bazelModule.getBaseDir());

            Package pkg = bazel.findPackage(rel);
            ImlModule iml = new ImlModule(pkg, config.nameRule(pkg.getName(), rel, name));
            bazelModule.rule = iml;

            // Add all the source and resource paths
            for (ContentEntry contentEntry : bazelModule.getContentEntries()) {
                final SourceFolder[] folders = contentEntry.getSourceFolders();

                for (SourceFolder folder : folders) {
                    VirtualFile root = folder.getFile();
                    if (root != null) {
                        String sourceLocalPath = PathUtil.getLocalPath(root);
                        assert sourceLocalPath != null;
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
        }

        // 2nd pass: Dependencies.
        for (BazelModule module : modules) {
            File librariesDir = new File(VfsUtil.virtualToIoFile(project.getBaseDir()), ".idea/libraries");
            Package librariesPkg = bazel.findPackage(FileUtil.getRelativePath(workspace, librariesDir));

            for (OrderEntry orderEntry : module.getOrderEntries()) {
                if (orderEntry instanceof LibraryOrderEntry) {
                    // A dependency to a jar file
                    LibraryOrderEntry libraryEntry = (LibraryOrderEntry) orderEntry;
                    List<ImlModule.Tag> scopes = new LinkedList<>();
                    if (libraryEntry.getScope().equals(DependencyScope.TEST)) {
                        scopes.add(ImlModule.Tag.TEST);
                    }

                    JavaLibrary namedLib = null;
                    if (!libraryEntry.isModuleLevel()) {
                        //noinspection ConstantConditions - getLibraryName() is not null for module-level libraries.
                        String libName = libraryEntry.getLibraryName().replaceAll(":", "_");
                        namedLib = libraries.get(libName.toLowerCase());
                        if (namedLib == null) {
                            namedLib = new JavaLibrary(librariesPkg, libName);
                            switch (libName) {
                                case KOTLIN_RUNTIME:
                                    namedLib.addDependency(
                                            new JavaImport(toolsBaseBazel, "kotlin-runtime"), true);
                                    break;
                                case KOTLIN_TEST:
                                    namedLib.addDependency(
                                            new JavaImport(toolsBaseBazel, "kotlin-test"), true);
                                    break;
                            }
                            libraries.put(libName.toLowerCase(), namedLib);
                        }
                    }

                    Library library = libraryEntry.getLibrary();
                    if (library != null) {
                        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
                        for (VirtualFile libFile : files) {
                            if (libFile.getFileType() instanceof ArchiveFileType) {
                                File jarFile = VfsUtil.virtualToIoFile(libFile);
                                if (Files.getFileExtension(jarFile.getName()).equals("jar")) {
                                    if (jarFile.getName().endsWith("-sources.jar")) {
                                        continue;
                                    }

                                    String relJar = FileUtil.getRelativePath(workspace, jarFile);
                                    assert relJar != null;
                                    JavaImport imp = imports.get(relJar);
                                    if (imp == null) {
                                        String label = config.mapImportJar(relJar);
                                        Package jarPkg = bazel.findPackage(relJar);
                                        if (label != null) {
                                            Label pkgAndTarget = new Label(label);
                                            jarPkg = new Package(null, pkgAndTarget.pkg);
                                            imp = new JavaImport(jarPkg, pkgAndTarget.target);
                                            imports.put(relJar, imp);
                                        } else if (jarPkg != null) {
                                            String packageRelative =
                                                    FileUtil.getRelativePath(
                                                            jarPkg.getPackageDir(), jarFile);
                                            assert packageRelative != null;

                                            // TODO: Fix all these dependencies correctly
                                            String target;
                                            if (relJar.startsWith("prebuilts/tools/common/m2")) {
                                                target = "jar";
                                            } else {
                                                target = packageRelative.replaceAll("\\.jar$", "");
                                            }
                                            imp = new JavaImport(jarPkg, target);
                                            imp.addJar(packageRelative);
                                            imports.put(relJar, imp);
                                        } else if (!isKotlinRelated(library)) {
                                            LOG.error("Cannot find package for:" + relJar);
                                        }
                                    }
                                    if (imp != null) {
                                        if (namedLib != null) {
                                            namedLib.addDependency(imp, true);
                                        } else {
                                            module.rule.addDependency(imp, libraryEntry.isExported(), scopes);
                                        }
                                    }
                                } else if (!isKotlinRelated(library)) {
                                    LOG.error("Cannot find file for: " + libFile);
                                }
                            }
                        }
                        if (namedLib != null && !namedLib.isEmpty()) {
                            module.rule.addDependency(namedLib, libraryEntry.isExported(), scopes);
                        }
                    } else {
                        LOG.error("No library for entry " + libraryEntry);
                    }
                } else if (orderEntry instanceof ModuleOrderEntry) {
                    // A dependency to another module
                    ModuleOrderEntry entry = (ModuleOrderEntry) orderEntry;
                    Module dep = entry.getModule();
                    BazelModule bazelModuleDep = imlToBazel.get(dep);
                    List<ImlModule.Tag> scopes = new LinkedList<>();
                    scopes.add(ImlModule.Tag.MODULE);
                    // TODO: Figure out how to add test if we depend on a multi-module rule
                    if (bazelModuleDep.isSingle() && entry.getScope().equals(DependencyScope.TEST)) {
                        scopes.add(ImlModule.Tag.TEST);
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

        for (Package pkg : bazel.getPackages()) {
            for (BazelRule rule : pkg.getRules()) {
              if (config.shouldSuppress(rule)) {
                rule.suppress();
              }
            }
        }

        progress.println("Updating BUILD files...");
        CountingListener listener = new CountingListener(progress);
        bazel.generate(listener);

        if (listener.getUpdatedPackages() == 0) {
            progress.println("OK: No changes needed.");
        } else {
            progress.println(
                    String.format("ATTENTION: %d files updated.", listener.getUpdatedPackages()));
        }
    }

    /**
     * Checks if the given library has something to do with Kotlin. BUILD files don't point to
     * Kotlin jars bundled with the IJ plugin, instead we use jars from prebuilts.
     */
    private boolean isKotlinRelated(Library library) {
        return KOTLIN_RUNTIME.equals(library.getName()) || KOTLIN_TEST.equals(library.getName());
    }

    private File findWorkspace(Project project) {
        File dir = VfsUtil.virtualToIoFile(project.getBaseDir());
        while (dir != null && !new File(dir, "WORKSPACE").exists()) {
            dir = dir.getParentFile();
        }
        return dir;
    }

    @NotNull
    private ImmutableMap<Module, BazelModule> createBazelModules(Project project) {
        List<Module> all = Arrays.asList(ModuleManager.getInstance(project).getModules());
        List<Chunk<Module>> chunks = ModuleCompilerUtil.getSortedModuleChunks(project, all);

        ImmutableMap.Builder<Module, BazelModule> mapBuilder = ImmutableMap.builder();
        for (Chunk<Module> chunk : chunks) {

            BazelModule simple = new BazelModule();
            for (Module module : chunk.getNodes()) {
                simple.add(module);
                mapBuilder.put(module, simple);
            }
        }
        return mapBuilder.build();
    }

    private static class Label {
        final String pkg;
        final String target;

        Label(String label) {
            if (label.startsWith("@")) {
                int endOfRepo = label.indexOf("//");
                if (endOfRepo < 0) {
                    throw new RuntimeException("invalid label: " + label);
                }
                label = label.substring(endOfRepo);
            }
            if (!label.startsWith("//")) {
                throw new RuntimeException("invalid label: " + label);
            }
            // Find the package/suffix separation:
            int colonIndex = label.indexOf(':');
            int splitAt = colonIndex >= 0 ? colonIndex : label.length();
            pkg = label.substring("//".length(), splitAt);
            String suffix = label.substring(splitAt);
            // ('suffix' is empty, or starts with a colon.)

            // "If packagename and version are elided, the colon is not necessary."
            target = suffix.isEmpty()
                // Target name is last package segment: (works in slash-free case too.)
                ? pkg.substring(pkg.lastIndexOf('/') + 1)
                // Target name is what's after colon:
                : suffix.substring(1);
        }
    }

    private static class CountingListener implements Workspace.GenerationListener {
        private final PrintWriter printWriter;
        private int updatedPackages = 0;

        private CountingListener(PrintWriter printWriter) {
            this.printWriter = printWriter;
        }

        @Override
        public void packageUpdated(String packageName) {
            updatedPackages++;
            printWriter.append("Updated ").append(packageName).append("/BUILD").println();
        }

        int getUpdatedPackages() {
            return updatedPackages;
        }
    }
}
