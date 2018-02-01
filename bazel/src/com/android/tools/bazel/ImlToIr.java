/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.bazel.ir.IrProject;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

/**
 * Converts a jps project to an internal representation (IrProject).
 *
 * The converted result guarantees that there are no cycles, while preserving the same dependencies.
 *
 * For compile+test cycles in the original project a single IrModule will be created and the dependencies
 * updated.
 *
 * Runtime dependencies are converted to non-transitive thus allowing BUILD file generation with
 * no cycles.
 */
public class ImlToIr {

    private static final Comparator<JpsModule> BY_NUM_ORDER_ENTRIES =
            Comparator.comparingInt(module -> module.getDependenciesList().getDependencies().size());
    public static final ImmutableSet<JpsJavaDependencyScope> RUNTIME_COMPILE_SCOPE = ImmutableSet
            .of(JpsJavaDependencyScope.COMPILE, JpsJavaDependencyScope.RUNTIME);
    public static final ImmutableSet<JpsJavaDependencyScope> TEST_COMPILE_SCOPE = ImmutableSet
            .of(JpsJavaDependencyScope.TEST, JpsJavaDependencyScope.COMPILE);
    public static final ImmutableSet<JpsJavaDependencyScope> COMPILE_SCOPE = ImmutableSet
            .of(JpsJavaDependencyScope.COMPILE);
    public static final ImmutableSet<JpsJavaDependencyScope> RUNTIME_TEST_COMPILE_SCOPE = ImmutableSet
            .of(JpsJavaDependencyScope.TEST, JpsJavaDependencyScope.COMPILE, JpsJavaDependencyScope.RUNTIME);

    public IrProject convert(Path workspace, String projectPath, String imlGraph, PrintWriter writer) throws IOException {
        projectPath = workspace.resolve(projectPath).toString();

        HashMap<String, String> pathVariables = new HashMap<>();
        pathVariables.put("KOTLIN_BUNDLED", workspace.resolve("prebuilts/tools/common/kotlin-plugin-ij/Kotlin/kotlinc").toString());
        pathVariables.put("MAVEN_REPOSITORY", workspace.resolve("prebuilts/tools/common/m2/repository").toString());

        JpsProject project = JpsElementFactory.getInstance().createModel().getProject();
        JpsProjectLoader.loadProject(project, pathVariables, projectPath);
        writer.println("Loaded project " + project.getName() + " with " + project.getModules().size() + " modules.");

        IrProject irProject = new IrProject(workspace.toFile());

        JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance()
                .getOrCreateCompilerConfiguration(project).getCompilerExcludes();
        List<File> excludedFiles = excludedFiles(excludes);

        JpsGraph compileGraph = new JpsGraph(project, COMPILE_SCOPE);
        JpsGraph testCompileGraph = new JpsGraph(project, TEST_COMPILE_SCOPE);
        JpsGraph runtimeGraph = new JpsGraph(project, RUNTIME_COMPILE_SCOPE);
        JpsGraph testCompileRuntimeGraph = new JpsGraph(project, RUNTIME_TEST_COMPILE_SCOPE);

        Dot dot = new Dot("iml_graph");

        printCycleWarnings(writer, testCompileGraph);

        // We have to create the IrModules first because even iterating in topological order,
        // we do so on a test+compile scope, but there are still runtime dependency cycles.
        Map<JpsModule, IrModule> imlToIr = new HashMap<>();
        for (List<JpsModule> component : testCompileGraph.getConnectedComponents()) {
            IrModule irModule = createIrModule(component);
            irProject.modules.add(irModule);
            for (JpsModule module : component) {
                imlToIr.put(module, irModule);
            }
        }

        for (JpsModule jpsModule : testCompileGraph.getModules()) {
            IrModule module = imlToIr.get(jpsModule);

            for (JpsModuleSourceRoot folder : jpsModule.getSourceRoots()) {
                File root = folder.getFile();
                if (root.exists()) {
                    // Projects can exclude specific files from compilation
                    for (File excludeFile : excludedFiles) {
                        if (excludeFile.toPath().startsWith(root.toPath())) {
                            module.addExcludeFile(excludeFile);
                        }
                    }
                }
            }

            for (JpsDependencyElement dependency : jpsModule.getDependenciesList().getDependencies()) {
                JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance()
                        .getDependencyExtension(dependency);
                boolean isTest = (extension != null) &&
                        extension.getScope().equals(JpsJavaDependencyScope.TEST);
                boolean isRuntime = (extension != null) &&
                        extension.getScope().equals(JpsJavaDependencyScope.RUNTIME);
                boolean isExported = (extension != null) && !isRuntime && extension.isExported();
                IrModule.Scope scope = isTest ? IrModule.Scope.TEST : isRuntime ? IrModule.Scope.RUNTIME : IrModule.Scope.COMPILE;

                if (dependency instanceof JpsLibraryDependency) {
                    // A dependency to a jar file
                    JpsLibraryDependency libraryDependency = (JpsLibraryDependency) dependency;
                    JpsLibrary library = libraryDependency.getLibrary();

                    if (library == null) {
                        System.err.println(String.format(
                                "Module %s: invalid item '%s' in the dependencies list",
                                jpsModule.getName(),
                                libraryDependency.getLibraryReference().getLibraryName()));
                        continue;  // Like IDEA, ignore dependencies on non-existent libraries.
                    }
                    JpsElementReference<? extends JpsCompositeElement> parent = libraryDependency
                            .getLibraryReference().getParentReference();
                    JpsCompositeElement resolved = parent.resolve();
                    IrModule owner = null;
                    if (resolved instanceof JpsModule) {
                        owner = imlToIr.get(resolved);
                    }
                    IrLibrary irLibrary = new IrLibrary(library.getName(), owner);
                    List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
                    // Library files are sometimes returned in file system order. Which changes
                    // across systems. Choose alphabetical always:
                    Collections.sort(files);
                    for (File file : files) {
                        if (!file.exists()) {
                          System.err.println("Library \"" + library.getName() + "\" points to non existing file: " + file);
                        }
                        if (!file.exists() ||
                                !Files.getFileExtension(file.getName()).equals("jar") ||
                                file.getName().endsWith("-sources.jar")) {
                            continue;
                        }
                        irLibrary.addFile(file);
                    }
                    module.addDependency(irLibrary, isExported, scope);
                } else if (dependency instanceof JpsModuleDependency) {
                    // A dependency to another module
                    JpsModuleDependency moduleDependency = (JpsModuleDependency) dependency;
                    JpsModule dep = moduleDependency.getModule();
                    if (dep == null) {
                        System.err.println("Invalid module dependency: " + moduleDependency.getModuleReference().getModuleName() + " from " + module.getName());
                    } else {
                        dot.addEdge(jpsModule.getName(), dep.getName(), scopeToColor(scope));
                        IrModule irDep = imlToIr.get(dep);
                        if (irDep == null) {
                            throw new IllegalStateException(
                                    "Cannot find dependency " + dep.getName() + " from " +
                                            module.getName());
                        }
                        if (irDep != jpsModule) {
                            module.addDependency(irDep, isExported, scope);
                        }
                    }
                }
            }
        }

        Map<JpsModule, Set<JpsModule>> runtimeDeps = calculateNewDependencies(compileGraph, runtimeGraph);

        // Add extra runtime dependencies:
        for (Map.Entry<JpsModule, Set<JpsModule>> entry : runtimeDeps.entrySet()) {
            IrModule from = imlToIr.get(entry.getKey());
            for (JpsModule module : entry.getValue()) {
                IrModule to = imlToIr.get(module);
                if (to != from) {
                    from.addDependency(to, false, IrModule.Scope.RUNTIME);
                    dot.addEdge(from.getName(), to.getName(), "blue", "dashed");
                }
            }
        }

        Map<JpsModule, Set<JpsModule>> testRuntimeDeps = calculateNewDependencies(testCompileGraph, testCompileRuntimeGraph);
        for (Map.Entry<JpsModule, Set<JpsModule>> entry : testRuntimeDeps.entrySet()) {
            IrModule from = imlToIr.get(entry.getKey());
            Set<JpsModule> deps = new LinkedHashSet<>(entry.getValue());
            deps.removeAll(runtimeDeps.get(entry.getKey()));
            for (JpsModule module : deps) {
                IrModule to = imlToIr.get(module);
                if (to != from) {
                    from.addDependency(to, false, IrModule.Scope.TEST_RUNTIME);
                    dot.addEdge(from.getName(), to.getName(), "green", "dashed");
                }
            }
        }

        if (imlGraph != null) {
            dot.saveTo(new File(imlGraph));
        }

        return irProject;
    }

    private void printCycleWarnings(PrintWriter writer, JpsGraph graph) {
        for (List<JpsModule> component : graph.getConnectedComponents()) {
            // If the component has more than one element, there is a cycle:
            if (component.size() > 1) {
                writer.println("Found circular module dependency: " + component.size() + " modules");
                for (JpsModule module : component) {
                    writer.println("        " + module.getName());
                }
            }
        }
    }

    private String scopeToColor(IrModule.Scope scope) {
        switch (scope) {
            case COMPILE: return "black";
            case TEST: return "green";
            case RUNTIME: return "blue";
            case TEST_RUNTIME: return "green:blue";
        }
        return "";
    }

    private Map<JpsModule, Set<JpsModule>> calculateNewDependencies(JpsGraph partial, JpsGraph complete) {
        Map<JpsModule, Set<JpsModule>> runtimeDeps = new LinkedHashMap<>();
        for (JpsModule module : partial.getModules()) {
            Set<JpsModule> newRuntimeDeps = new LinkedHashSet<>();
            Set<JpsModule> target = new LinkedHashSet<>(complete.getClosure(module));
            Set<JpsModule> current = partial.getClosure(module);
            target.removeAll(current);
            while (!target.isEmpty()) {
                JpsModule missing = target.iterator().next();
                newRuntimeDeps.add(missing);
                target.removeAll(partial.getClosure(missing));
            }
            runtimeDeps.put(module, newRuntimeDeps);
        }
        return runtimeDeps;
    }

    public IrModule createIrModule(List<JpsModule> modules) {
        String name = modules.stream().max(BY_NUM_ORDER_ENTRIES).get().getName() +
                (modules.size() == 1 ? "" : "_and_others");
        IrModule irModule = new IrModule(name);

        Path baseDir = null;
        // Find the common ancestor of all the modules
        for (JpsModule module : modules) {
            File base = JpsModelSerializationDataService.getBaseDirectory(module);
            File moduleFile = new File(base, module.getName() + ".iml");
            if (!moduleFile.exists()) {
                throw new IllegalStateException("Cannot find module iml file: " + moduleFile);
            }
            irModule.addIml(moduleFile);

            Path path = base.toPath();
            if (baseDir == null) {
                baseDir = path;
            } else {
                // Move common "up" until it covers the current module
                while (!path.startsWith(baseDir)) {
                    baseDir = baseDir.getParent();
                }
            }

            for (JpsModuleSourceRoot root : module.getSourceRoots()) {
                File file = root.getFile();
                if (file.exists()) {
                    boolean source = false;
                    if (root.getRootType().equals(JavaSourceRootType.TEST_SOURCE)) {
                        irModule.addTestSource(file);
                        source = true;
                    }
                    if (root.getRootType().equals(JavaSourceRootType.SOURCE)) {
                        irModule.addSource(file);
                        source = true;
                    }
                    if (source) {
                        String prefix = ((JavaSourceRootProperties) root.getProperties())
                                .getPackagePrefix();
                        if (!prefix.isEmpty()) {
                            irModule.addPrefix(file, prefix);
                        }
                    }

                    if (root.getRootType().equals(JavaResourceRootType.TEST_RESOURCE)) {
                        irModule.addTestResource(file);
                    } else if (root.getRootType().equals(JavaResourceRootType.RESOURCE)) {
                        irModule.addResource(file);
                    }
                }
            }
        }
        irModule.setBaseDir(baseDir);
        return irModule;
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

}
