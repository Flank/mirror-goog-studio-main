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

import com.android.tools.bazel.model.BazelRule;
import com.android.tools.bazel.model.ImlModule;
import com.android.tools.bazel.model.ImlProject;
import com.android.tools.bazel.model.JavaImport;
import com.android.tools.bazel.model.JavaLibrary;
import com.android.tools.bazel.model.Package;
import com.android.tools.bazel.model.UnmanagedRule;
import com.android.tools.bazel.model.Workspace;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.util.JpsPathUtil;

public class GenerateBazelAction {

    public void generate(Path workspace, PrintWriter progress, Configuration config)
            throws IOException {
        String projectPath = workspace.resolve("tools/idea").toString();
        System.setProperty("idea.home.path", projectPath);
        HashMap<String, String> pathVariables = new HashMap<>();
        pathVariables.put("KOTLIN_BUNDLED", workspace.resolve("prebuilts/tools/common/kotlin-plugin-ij/Kotlin/kotlinc").toString());
        pathVariables.put("MAVEN_REPOSITORY", workspace.resolve("prebuilts/tools/common/m2/repository").toString());

        JpsProject project = JpsElementFactory.getInstance().createModel().getProject();
        JpsProjectLoader.loadProject(project, pathVariables, projectPath);
        System.err.println("Loaded project " + project.getName() + " with " + project.getModules().size() + " modules.");

        File projectDir = JpsModelSerializationDataService.getBaseDirectory(project);
        Workspace bazel = new Workspace(workspace.toFile());

        JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance()
                .getOrCreateCompilerConfiguration(project).getCompilerExcludes();
        List<File> excludedFiles = excludedFiles(excludes);

        // Map from file path to the bazel rule that provides it. Usually java_imports.
        Map<String, BazelRule> jarRules = Maps.newHashMap();
        Map<String, JavaLibrary> libraries = Maps.newHashMap();

        ImmutableMap<JpsModule, BazelModule> imlToBazel = createBazelModules(project);
        ImmutableSet<BazelModule> modules = ImmutableSet.copyOf(imlToBazel.values());

        // 1st pass: Creation.
        for (BazelModule bazelModule : modules) {
            String name = bazelModule.getName();
            String rel = workspace.relativize(bazelModule.getBaseDir()).toString();

            Package pkg = bazel.findPackage(rel);
            ImlModule iml = new ImlModule(pkg, config.nameRule(pkg.getName(), rel, name));
            for (JpsModule module : bazelModule.getModules()) {
                File base = JpsModelSerializationDataService.getBaseDirectory(module);
                File moduleFile = new File(base, module.getName() + ".iml");
                if (!moduleFile.exists()) {
                    throw new IllegalStateException("Cannot find module iml file: " + moduleFile);
                } else {
                    Path path = pkg.getPackageDir().toPath().relativize(moduleFile.toPath());
                    iml.addModuleFile(path.toString());
                }
            }
            bazelModule.rule = iml;

            // Add all the source and resource paths
            for (JpsModuleSourceRoot folder : bazelModule.getSourceRoots()) {
                File root = folder.getFile();
                if (root.exists()) {
                    String relativePath = pkg.getPackageDir().toPath().relativize(root.toPath())
                            .toString();
                    boolean source = false;
                    if (folder.getRootType().equals(JavaSourceRootType.TEST_SOURCE)) {
                        iml.addTestSource(relativePath);
                        source = true;
                    }
                    if (folder.getRootType().equals(JavaSourceRootType.SOURCE)) {
                        iml.addSource(relativePath);
                        source = true;
                    }
                    if (source) {
                        String prefix = ((JavaSourceRootProperties) folder.getProperties())
                                .getPackagePrefix();
                        if (!prefix.isEmpty()) {
                            iml.addPackagePrefix(relativePath, prefix);
                        }
                    }

                    if (folder.getRootType().equals(JavaResourceRootType.TEST_RESOURCE)) {
                        iml.addTestResource(relativePath);
                    } else if (folder.getRootType().equals(JavaResourceRootType.RESOURCE)) {
                        iml.addResource(relativePath);
                    }

                    // Projects can exclude specific files from compilation
                    for (File excludeFile : excludedFiles) {
                        if (excludeFile.toPath().startsWith(root.toPath())) {
                            iml.addExclude(pkg.getPackageDir().toPath()
                                    .relativize(excludeFile.toPath()).toString());
                        }
                    }
                }
            }
        }

        // 2nd pass: Dependencies.
        for (BazelModule module : modules) {
            File librariesDir = new File(projectDir, ".idea/libraries");
            Package librariesPkg = bazel
                    .findPackage(workspace.relativize(librariesDir.toPath()).toString());
            for (JpsDependencyElement dependency : module.getDependencies()) {
                JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance()
                        .getDependencyExtension(dependency);
                boolean isTest = (extension != null) &&
                        extension.getScope().equals(JpsJavaDependencyScope.TEST);
                boolean isExported = (extension != null) && extension.isExported();
                if (dependency instanceof JpsLibraryDependency) {
                    // A dependency to a jar file
                    JpsLibraryDependency libraryDependency = (JpsLibraryDependency) dependency;
                    JpsLibrary library = libraryDependency.getLibrary();
                    if (library == null) {
                        System.err.println(String.format("Module %s: invalid item '%s' in the dependencies list",
                                module.getName(), libraryDependency.getLibraryReference().getLibraryName()));
                        continue;  // Like IDEA, ignore dependencies on non-existent libraries.
                    }
                    List<ImlModule.Tag> scopes = new LinkedList<>();
                    if (isTest) {
                        scopes.add(ImlModule.Tag.TEST);
                    }

                    JavaLibrary namedLib = null;

                    JpsElementReference<? extends JpsCompositeElement> parent = libraryDependency
                            .getLibraryReference().getParentReference();
                    JpsCompositeElement resolved = parent.resolve();
                    if (!(resolved instanceof JpsModule)) {
                        //noinspection ConstantConditions - getLibraryName() is not null for module-level libraries.
                        String libName = library.getName().replaceAll(":", "_");
                        namedLib = libraries.get(libName.toLowerCase());
                        if (namedLib == null) {
                            namedLib = new JavaLibrary(librariesPkg, libName);
                            libraries.put(libName.toLowerCase(), namedLib);
                        }
                    }
                    List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
                    // Library files are sometimes returned in file system order. Which changes
                    // across systems. Choose alphabetical always:
                    Collections.sort(files);
                    for (File file : files) {
                        if (file.exists() &&
                                Files.getFileExtension(file.getName()).equals("jar")) {
                            if (file.getName().endsWith("-sources.jar")) {
                                continue;
                            }

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
                                } else {
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
                                        JavaImport javaImport = new JavaImport(jarPkg, target);
                                        javaImport.addJar(packageRelative);
                                        jarRule = javaImport;
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
                                    module.rule.addDependency(jarRule, isExported, scopes);
                                }
                            }
                        } else {
                            System.err.println("Module [" + dependency.getContainingModule().getName() + "] depends on non existing file: " + file);
                        }
                    }
                    if (namedLib != null && !namedLib.isEmpty()) {
                        module.rule.addDependency(namedLib, isExported, scopes);
                    }
                } else if (dependency instanceof JpsModuleDependency) {
                    // A dependency to another module
                    JpsModuleDependency moduleDependency = (JpsModuleDependency) dependency;
                    JpsModule dep = moduleDependency.getModule();
                    BazelModule bazelModuleDep = imlToBazel.get(dep);
                    List<ImlModule.Tag> scopes = new LinkedList<>();
                    scopes.add(ImlModule.Tag.MODULE);

                    // TODO: Figure out how to add test if we depend on a multi-module rule
                    if (bazelModuleDep.isSingle() && isTest) {
                        scopes.add(ImlModule.Tag.TEST);
                    }
                    if (bazelModuleDep != module) {
                        module.rule.addDependency(bazelModuleDep.rule, isExported, scopes);
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

        Set<ImlModule> roots = modules.stream()
                .map(module -> module.rule)
                .filter(BazelRule::isExport)
                .collect(Collectors.toSet());

        for (BazelModule module : modules) {
            roots.removeAll(module.rule.getDependencies());
        }

        Package projectPkg = bazel
                .findPackage(workspace.relativize(projectDir.toPath()).toString());
        ImlProject imlProject = new ImlProject(projectPkg, "android-studio");
        roots.stream().sorted(Comparator.comparing(BazelRule::getLabel))
                .forEach(imlProject::addModule);

        progress.println("Updating BUILD files...");
        CountingListener listener = new CountingListener(progress);
        bazel.generate(listener);

        progress.println(String.format("%d BUILD file(s) updated.", listener.getUpdatedPackages()));
    }

    /**
     * Excludes are parsed and stored as a "filter" like object. This would require us going through
     * the whole tree to find which files are excluded. In this case JPS and IJ code differ and both
     * parse the xml differently. For now we use reflection assuming the implementation class.
     */
    private List<File> excludedFiles(JpsCompilerExcludes excludes) {
        Field myFiles = null;
        try {
            myFiles = excludes.getClass().getDeclaredField("myFiles");
            Type genericType = myFiles.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType type = (ParameterizedType) genericType;
                if (type.getRawType().equals(Set.class)) {
                    Type[] args = type.getActualTypeArguments();
                    if (args.length == 1 && args[0].equals(File.class)) {
                        myFiles.setAccessible(true);
                        Object object = myFiles.get(excludes);
                        Set<File> set = (Set<File>) object;
                        return set.stream().sorted().collect(Collectors.toList());
                    }
                }

            }
        } catch (NoSuchFieldException e) {
        } catch (IllegalAccessException e) {
        }
        throw new IllegalStateException("Unexpected version of JpsCompilerExcludes");
    }

    private boolean isGenFile(String relJar) {
        return relJar.startsWith("bazel-genfiles");
    }

    @NotNull
    private ImmutableMap<JpsModule, BazelModule> createBazelModules(JpsProject project) {
        Graph<JpsModule> graph = GraphGenerator.create(new GraphGenerator.SemiGraph<JpsModule>() {

            @Override
            public Collection<JpsModule> getNodes() {
                return project.getModules();
            }

            @Override
            public Iterator<JpsModule> getIn(JpsModule jpsModule) {
                List<JpsDependencyElement> deps = jpsModule.getDependenciesList().getDependencies();
                List<JpsModule> list = new ArrayList<>();
                for (JpsDependencyElement dep : deps) {
                    if (dep instanceof JpsModuleDependency) {
                        list.add(((JpsModuleDependency) dep).getModule());
                    }
                }
                return list.iterator();
            }
        });

        DFSTBuilder<JpsModule> builder = new DFSTBuilder<>(graph);
        TIntArrayList scCs = builder.getSCCs();
        int k = 0;
        ImmutableMap.Builder<JpsModule, BazelModule> mapBuilder = ImmutableMap.builder();
        for (int i = 0; i < scCs.size(); i++) {
            BazelModule simple = new BazelModule();
            for (int j = 0; j < scCs.get(i); j++) {
                JpsModule module = builder.getNodeByTNumber(k++);
                simple.add(module);
                mapBuilder.put(module, simple);
                if (scCs.get(i) > 1 && j == 0) {
                    System.err.println("Found circular module dependency of " + scCs.get(i) + " modules. [" + module.getName() + "]");
                }
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
