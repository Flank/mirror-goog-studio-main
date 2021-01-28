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
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsSdkDependency;
import org.jetbrains.jps.model.module.JpsTestModuleProperties;
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

    // This is the public API of ImlToIr, keeping it an instance method in case we ever need to
    // mock it or write another implementation.
    @SuppressWarnings("MethodMayBeStatic")
    public IrProject convert(
            Configuration config, Path workspace, String projectPath, BazelToolsLogger logger)
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

        IrProject irProject = new IrProject(workspace.toFile(), projectPath);

        JpsJavaCompilerConfiguration compilerConfiguration =
                JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
        JpsCompilerExcludes excludes = compilerConfiguration.getCompilerExcludes();
        JpsJavaCompilerOptions compilerOptions =
                compilerConfiguration.getCompilerOptions(compilerConfiguration.getJavaCompilerId());
        List<File> excludedFiles = excludedFiles(excludes);

        JpsGraph graph = new JpsGraph(project, logger);
        Dot dot = new Dot("iml_graph");
        if (logger.getErrorCount() > 0) {
            return irProject;
        }

        Map<JpsModule, IrModule> imlToIr = new HashMap<>();
        Map<JpsLibrary, IrLibrary> libraryToIr = new HashMap<>();
        for (JpsModule jpsModule : graph.getModulesInTopologicalOrder()) {
            IrModule module = createIrModule(jpsModule, compilerOptions);
            if (config.ignoreModule(workspace, module)) {
                continue;
            }
            irProject.modules.add(module);
            imlToIr.put(jpsModule, module);

            // Check if this is a test module with an associated production module that should be
            // treated as a Kotlin friend. I.e., detect an iml line like this:
            // <component name="TestModuleProperties" production-module="module.name" />
            JpsTestModuleProperties testModuleProperties =
                    JpsJavaExtensionService.getInstance().getTestModuleProperties(jpsModule);
            if (testModuleProperties != null) {
                JpsModule jpsFriend = testModuleProperties.getProductionModule();
                if (jpsFriend != null) {
                    module.addTestFriend(imlToIr.get(jpsFriend));
                }
            }

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
                boolean isProvided =
                        (extension != null)
                                && extension.getScope().equals(JpsJavaDependencyScope.PROVIDED);
                boolean isExported = (extension != null) && !isRuntime && extension.isExported();
                IrModule.Scope scope;
                if (isTest) scope = IrModule.Scope.TEST;
                else if (isRuntime) scope = IrModule.Scope.RUNTIME;
                else if (isProvided) scope = IrModule.Scope.PROVIDED;
                else scope = IrModule.Scope.COMPILE;

                if (dependency instanceof JpsLibraryDependency) {
                    // A dependency to a jar file
                    JpsLibraryDependency libraryDependency = (JpsLibraryDependency) dependency;
                    JpsLibrary library = libraryDependency.getLibrary();

                    if (library == null) {
                        String libraryName =
                                libraryDependency.getLibraryReference().getLibraryName();
                        if (!ignoreWarnings(jpsModule.getName(), libraryName)) {
                            logger.warning(
                                    "Module %s: invalid item '%s' in the dependencies list",
                                    jpsModule.getName(), libraryName);
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
                    IrLibrary irLibrary = libraryToIr.get(library);
                    if (irLibrary == null) {
                        irLibrary = new IrLibrary(library.getName(), owner);
                        List<File> files = library.getFiles(JpsOrderRootType.COMPILED);
                        // Newer versions of jps sort the files correctly, for now using legacy
                        // sorting if not strict
                        for (File file : files) {
                            if (file.getPath().contains("$SDK_PLATFORM$")) {
                                // Libraries containing these files cannot be resolved and will
                                // point to unmanaged rules.
                                continue;
                            }
                            // "KotlinPlugin" is the library that upstream IntelliJ uses that points
                            // to
                            // files under idea/build that we usually don't create, they are copied
                            // there by build_studio.sh that most developers don't run. Instead,
                            // Android
                            // Studio has its own library, called "kotlin-plugin" that points to
                            // files
                            // in prebuilts. Here we ignore entries in the "KotlinPlugin" library
                            // that
                            // point to non-existing files. If the files exists, they are ignored
                            // later
                            // in IrToBazel.
                            if (!file.exists() && !"KotlinPlugin".equals(library.getName())) {
                                String libraryName = library.getName();
                                String dependencyDescription;
                                if (libraryName.equals("#")) {
                                    dependencyDescription =
                                            "Module library in "
                                                    + libraryDependency
                                                            .getContainingModule()
                                                            .getName();
                                } else {
                                    dependencyDescription = "Library " + libraryName;
                                }
                            }
                            if (!Files.getFileExtension(file.getName()).equals("jar")
                                    || file.getName().endsWith("-sources.jar")) {
                                continue;
                            }
                            irLibrary.addFile(file);
                        }
                        libraryToIr.put(library, irLibrary);
                    }
                    module.addDependency(irLibrary, isExported, scope);
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
                } else if (dependency instanceof JpsSdkDependency) {
                    JpsSdkDependency sdk = (JpsSdkDependency) dependency;
                    String sdkName = sdk.getSdkReference().getSdkName();
                    if (sdkName.equals("Android Studio")) {
                        IrLibrary irSdk = new IrLibrary("studio-sdk", null);
                        module.addDependency(irSdk, false, IrModule.Scope.COMPILE);
                    }
                }
            }
        }

        if (config.imlGraph != null) {
            dot.saveTo(new File(config.imlGraph));
        }

        return irProject;
    }

    /**
     * Checks if warnings about the given module should be printed out.
     *
     * <p>We don't warn users about modules we don't maintain, i.e. platform modules.
     */
    public static boolean ignoreWarnings(String moduleName) {
        return ignoreWarnings(moduleName, "");
    }

    private static boolean ignoreWarnings(String moduleName, String libraryName) {
        if (moduleName.startsWith("intellij.platform")
                || moduleName.startsWith("intellij.idea")
                || moduleName.startsWith("intellij.c")
                || moduleName.startsWith("intellij.java")) return true;
        if (libraryName.equals("studio-platform")
                || libraryName.startsWith("studio-plugin-")
                || libraryName.equals("studio-sdk")
                || libraryName.equals("intellij-updater")) {
            return true;
        }
        return false;
    }

    private static String scopeToColor(IrModule.Scope scope) {
        switch (scope) {
            case COMPILE: return "black";
            case TEST: return "green";
            case RUNTIME: return "blue";
            case TEST_RUNTIME: return "green:blue";
            case PROVIDED:
                return "red";
        }
        return "";
    }

    private static IrModule createIrModule(
            JpsModule module, JpsJavaCompilerOptions jpsCompilerOptions) {

        File base = JpsModelSerializationDataService.getBaseDirectory(module);
        if (base == null) {
            throw new IllegalStateException(
                    "Cannot determine base directory of module " + module.getName());
        }
        File moduleFile = new File(base, module.getName() + ".iml");
        if (!moduleFile.exists()) {
            throw new IllegalStateException("Cannot find module iml file: " + moduleFile);
        }
        IrModule irModule = new IrModule(module.getName(), moduleFile, base);

        String additionalOptions =
                jpsCompilerOptions.ADDITIONAL_OPTIONS_OVERRIDE.getOrDefault(
                        module.getName(), jpsCompilerOptions.ADDITIONAL_OPTIONS_STRING);
        if (additionalOptions != null && !additionalOptions.isEmpty()) {
            String currentOptions = irModule.getCompilerOptions();
            if (currentOptions == null) {
                irModule.setCompilerOptions(additionalOptions);
            } else if (!currentOptions.equals(additionalOptions)) {
                throw new IllegalStateException(
                        "Conflicting compiler options specified by module " + module.getName());
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
                    String prefix =
                            ((JavaSourceRootProperties) root.getProperties()).getPackagePrefix();
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
