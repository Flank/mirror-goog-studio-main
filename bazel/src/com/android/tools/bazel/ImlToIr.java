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
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.jetbrains.jps.model.JpsElement;
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

    // This is the public API of ImlToIr, keeping it an instance method in case we ever need to
    // mock it or write another implementation.
    @SuppressWarnings("MethodMayBeStatic")
    public IrProject convert(
            Path workspace, String projectPath, String imlGraph, BazelToolsLogger logger)
            throws IOException {
        projectPath = workspace.resolve(projectPath).toString();
        // Depending on class initialization order this property will be read, so it needs to be set.
        System.setProperty("idea.home.path", projectPath);

        HashMap<String, String> pathVariables = new HashMap<>();
        pathVariables.put("KOTLIN_BUNDLED", workspace.resolve("prebuilts/tools/common/kotlin-plugin-ij/Kotlin/kotlinc").toString());
        pathVariables.put("MAVEN_REPOSITORY", workspace.resolve("prebuilts/tools/common/m2/repository").toString());

        JpsProject project = JpsElementFactory.getInstance().createModel().getProject();
        JpsProjectLoader.loadProject(project, pathVariables, projectPath);
        logger.info(
                "Loaded project %s with %d modules.",
                project.getName(), project.getModules().size());

        IrProject irProject = new IrProject(workspace.toFile());

        JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance()
                .getOrCreateCompilerConfiguration(project).getCompilerExcludes();
        List<File> excludedFiles = excludedFiles(excludes);

        JpsGraph compileGraph = new JpsGraph(project, COMPILE_SCOPE, logger);
        JpsGraph testCompileGraph = new JpsGraph(project, TEST_COMPILE_SCOPE, logger);
        JpsGraph runtimeGraph = new JpsGraph(project, RUNTIME_COMPILE_SCOPE, logger);
        JpsGraph testCompileRuntimeGraph =
                new JpsGraph(project, RUNTIME_TEST_COMPILE_SCOPE, logger);

        Dot dot = new Dot("iml_graph");

        printCycleWarnings(logger, testCompileGraph);

        // We have to create the IrModules first because even iterating in topological order,
        // we do so on a test+compile scope, but there are still runtime dependency cycles.
        Map<JpsModule, IrModule> imlToIr = new HashMap<>();
        Map<JpsLibrary, IrLibrary> libraryToIr = new HashMap<>();
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
                        if (!ignoreWarnings(jpsModule)) {
                            logger.warning(
                                    "Module %s: invalid item '%s' in the dependencies list",
                                    jpsModule.getName(),
                                    libraryDependency.getLibraryReference().getLibraryName());
                        }
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
                        // "KotlinPlugin" is the library that upstream IntelliJ uses that points to
                        // files under idea/build that we usually don't create, they are copied
                        // there by build_studio.sh that most developers don't run. Instead, Android
                        // Studio has its own library, called "kotlin-plugin" that points to files
                        // in prebuilts. Here we ignore entries in the "KotlinPlugin" library that
                        // point to non-existing files. If the files exists, they are ignored later
                        // in IrToBazel.
                        if (!file.exists() && !"KotlinPlugin".equals(library.getName())) {
                            String libraryName = library.getName();
                            String dependencyDescription;
                            if (libraryName.equals("#")) {
                                dependencyDescription =
                                        "Module library in "
                                                + libraryDependency.getContainingModule().getName();
                            } else {
                                dependencyDescription = "Library " + libraryName;
                            }
                            logger.warning(
                                    dependencyDescription
                                            + " points to non existing file: "
                                            + file);
                        }
                        if (!file.exists() ||
                                !Files.getFileExtension(file.getName()).equals("jar") ||
                                file.getName().endsWith("-sources.jar")) {
                            continue;
                        }
                        irLibrary.addFile(file);
                    }
                    module.addDependency(irLibrary, isExported, scope);
                    libraryToIr.put(library, irLibrary);
                } else if (dependency instanceof JpsModuleDependency) {
                    // A dependency to another module
                    JpsModuleDependency moduleDependency = (JpsModuleDependency) dependency;
                    JpsModule dep = moduleDependency.getModule();
                    if (dep == null) {
                        if (!ignoreWarnings(module.getName())) {
                            logger.warning(
                                    "Invalid module dependency: "
                                            + moduleDependency.getModuleReference().getModuleName()
                                            + " from "
                                            + module.getName());
                        }
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

        Map<JpsModule, Set<JpsElement>> runtimeDeps =
                calculateNewDependencies(compileGraph, runtimeGraph);

        // Add extra runtime dependencies:
        for (Map.Entry<JpsModule, Set<JpsElement>> entry : runtimeDeps.entrySet()) {
            IrModule from = imlToIr.get(entry.getKey());
            for (JpsElement element : entry.getValue()) {
                if (element instanceof JpsModule) {
                    JpsModule module = (JpsModule) element;
                    IrModule to = imlToIr.get(module);
                    if (to != from) {
                        from.addDependency(to, false, IrModule.Scope.RUNTIME);
                        dot.addEdge(from.getName(), to.getName(), "blue", "dashed");
                    }
                } else if (element instanceof JpsLibrary) {
                    JpsLibrary library = (JpsLibrary) element;
                    IrLibrary lib = libraryToIr.get(library);
                    from.addDependency(lib, false, IrModule.Scope.RUNTIME);
                    dot.addEdge(from.getName(), lib.name, "blue", "dashed");
                } else {
                    throw new IllegalStateException(
                            "Unexpected dependency type "
                                    + element.getClass()
                                    + " for "
                                    + entry.getKey().getName());
                }
            }
        }

        Map<JpsModule, Set<JpsElement>> testRuntimeDeps =
                calculateNewDependencies(testCompileGraph, testCompileRuntimeGraph);
        for (Map.Entry<JpsModule, Set<JpsElement>> entry : testRuntimeDeps.entrySet()) {
            IrModule from = imlToIr.get(entry.getKey());
            Set<JpsElement> deps = new LinkedHashSet<>(entry.getValue());
            deps.removeAll(runtimeDeps.get(entry.getKey()));
            for (JpsElement element : deps) {
                if (element instanceof JpsModule) {
                    JpsModule module = (JpsModule) element;
                    IrModule to = imlToIr.get(module);
                    if (to != from) {
                        from.addDependency(to, false, IrModule.Scope.TEST_RUNTIME);
                        dot.addEdge(from.getName(), to.getName(), "green", "dashed");
                    }
                } else if (element instanceof JpsLibrary) {
                    JpsLibrary library = (JpsLibrary) element;
                    IrLibrary lib = libraryToIr.get(library);
                    from.addDependency(lib, false, IrModule.Scope.TEST_RUNTIME);
                    dot.addEdge(from.getName(), lib.name, "green", "dashed");
                } else {
                    throw new IllegalStateException(
                            "Unexpected dependency type "
                                    + element.getClass()
                                    + " for "
                                    + entry.getKey().getName());
                }
            }
        }

        if (imlGraph != null) {
            dot.saveTo(new File(imlGraph));
        }

        return irProject;
    }

    private static void printCycleWarnings(BazelToolsLogger logger, JpsGraph graph) {
        for (List<JpsModule> component : graph.getConnectedComponents()) {
            // If the component has more than one element, there is a cycle:
            if (component.size() > 1 && !isCycleAllowed(component)) {
                StringBuilder message = new StringBuilder();
                message.append("Found circular module dependency: ")
                        .append(component.size())
                        .append(" modules");
                for (JpsModule module : component) {
                    message.append("        ").append(module.getName());
                }

                logger.warning(message.toString());
            }
        }
    }

    /**
     * Checks if a circular dependency is something in the platform (which we know contains such
     * cycles) or if it involves our code as well.
     */
    private static boolean isCycleAllowed(List<JpsModule> cycle) {
        return cycle.stream().allMatch(ImlToIr::ignoreWarnings);
    }

    /**
     * Checks if warnings about the given module should be printed out.
     *
     * <p>We don't warn users about modules we don't maintain, i.e. platform modules.
     */
    public static boolean ignoreWarnings(JpsModule module) {
        return ignoreWarnings(module.getName());
    }

    private static boolean ignoreWarnings(String moduleName) {
        return moduleName.startsWith("intellij.platform")
                || moduleName.startsWith("intellij.idea")
                || moduleName.startsWith("intellij.c")
                || moduleName.startsWith("intellij.java");
    }

    private static String scopeToColor(IrModule.Scope scope) {
        switch (scope) {
            case COMPILE: return "black";
            case TEST: return "green";
            case RUNTIME: return "blue";
            case TEST_RUNTIME: return "green:blue";
        }
        return "";
    }

    private static Map<JpsModule, Set<JpsElement>> calculateNewDependencies(
            JpsGraph partial, JpsGraph complete) {
        Map<JpsModule, Set<JpsElement>> runtimeDeps = new LinkedHashMap<>();
        for (JpsModule module : partial.getModules()) {
            Set<JpsElement> newRuntimeDeps = new LinkedHashSet<>();
            Set<JpsElement> target = new LinkedHashSet<>(complete.getClosure(module));
            Set<JpsElement> current = partial.getClosure(module);
            target.removeAll(current);
            while (!target.isEmpty()) {
                JpsElement missing = target.iterator().next();
                newRuntimeDeps.add(missing);
                if (missing instanceof JpsModule) {
                    target.removeAll(partial.getClosure((JpsModule) missing));
                } else if (missing instanceof JpsLibrary) {
                    target.remove(missing);
                }
            }

            runtimeDeps.put(module, newRuntimeDeps);
        }
        return runtimeDeps;
    }

    private static IrModule createIrModule(List<JpsModule> modules) {
        String jpsModuleName =
                modules.stream()
                        .max(BY_NUM_ORDER_ENTRIES)
                        .orElseThrow(() -> new IllegalStateException("empty list of JpsModule"))
                        .getName();
        String name = jpsModuleName + (modules.size() == 1 ? "" : "_and_others");
        IrModule irModule = new IrModule(name);

        Path baseDir = null;
        // Find the common ancestor of all the modules
        for (JpsModule module : modules) {
            File base = JpsModelSerializationDataService.getBaseDirectory(module);
            if (base == null) {
                throw new IllegalStateException(
                        "Cannot determine base directory of module " + module.getName());
            }
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

    private static List<File> excludedFiles(JpsCompilerExcludes excludes) {
        List<File> excludedFiles = new ArrayList<>();
        excludedFiles.addAll(captureExcludedSet("myFiles", excludes));
        excludedFiles.addAll(captureExcludedSet("myDirectories", excludes));
        return excludedFiles;
    }

    /**
     * Excludes are parsed and stored as a "filter" like object. This would require us going through
     * the whole tree to find which files are excluded. In this case JPS and IJ code differ and both
     * parse the xml differently. For now we use reflection assuming the implementation class.
     */
    private static List<File> captureExcludedSet(String field, JpsCompilerExcludes excludes) {
        Field myFiles;
        try {
            myFiles = excludes.getClass().getDeclaredField(field);
            Type genericType = myFiles.getGenericType();
            if (genericType instanceof ParameterizedType) {
                ParameterizedType type = (ParameterizedType) genericType;
                if (type.getRawType().equals(Set.class)) {
                    Type[] args = type.getActualTypeArguments();
                    if (args.length == 1 && args[0].equals(File.class)) {
                        myFiles.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Set<File> set = (Set<File>) myFiles.get(excludes);
                        return set.stream().sorted().collect(Collectors.toList());
                    }
                }

            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // ignored
        }
        throw new IllegalStateException("Unexpected version of JpsCompilerExcludes");
    }

}
