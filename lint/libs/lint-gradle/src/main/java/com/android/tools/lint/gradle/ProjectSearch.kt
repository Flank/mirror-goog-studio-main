/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.lint.gradle

import com.android.builder.model.AndroidProject
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.tools.lint.detector.api.LmModuleAndroidLibraryProject
import com.android.tools.lint.detector.api.LmModuleJavaLibraryProject
import com.android.tools.lint.detector.api.LmModuleLibraryProject
import com.android.tools.lint.detector.api.LmModuleProject
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.gradle.api.ToolingRegistryProvider
import com.android.tools.lint.model.DefaultLmMavenName
import com.android.tools.lint.model.LmAndroidLibrary
import com.android.tools.lint.model.LmFactory
import com.android.tools.lint.model.LmJavaLibrary
import com.android.tools.lint.model.LmMavenName
import com.android.tools.lint.model.LmModule
import com.android.tools.lint.model.LmVariant
import com.intellij.pom.java.LanguageLevel
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File
import org.gradle.api.Project as GradleProject

/**
 * Class which creates a lint project hierarchy based on a corresponding Gradle project
 * hierarchy, looking up project dependencies by name, creating wrapper projects for Java
 * libraries, looking up tooling models for each project, etc.
 */
class ProjectSearch {
    private val libraryProjects = mutableMapOf<LmAndroidLibrary, Project>()
    private val libraryProjectsByCoordinate = mutableMapOf<LmMavenName, LmModuleLibraryProject>()
    private val namedProjects = mutableMapOf<String, Project>()
    private val javaLibraryProjects = mutableMapOf<LmJavaLibrary, Project>()
    private val javaLibraryProjectsByCoordinate = mutableMapOf<LmMavenName, LmModuleLibraryProject>()
    private val appProjects = mutableMapOf<GradleProject, Project>()
    private val gradleProjects = mutableMapOf<GradleProject, LmModule>()

    private fun getBuildModule(gradleProject: GradleProject): LmModule? {
        return gradleProjects[gradleProject] ?: run {
            val newModel = createLintBuildModel(gradleProject)
            if (newModel != null) {
                gradleProjects[gradleProject] = newModel
            }
            newModel
        }
    }

    /**
     * Given a Gradle project, compute the builder model and lint models and then
     * call getProject with those
     */
    fun getProject(
        lintClient: LintGradleClient,
        gradleProject: GradleProject,
        variantName: String?
    ): Project? {
        val module = getBuildModule(gradleProject)
        if (module != null && variantName != null) {
            val variant = module.findVariant(variantName)
            if (variant != null) {
                return getProject(lintClient, variant, gradleProject)
            }

            // Just use the default variant.
            // TODO: Use DSL to designate the default variants for this (not
            // yet available, but planned.)
            module.defaultVariant()?.let { defaultVariant ->
                return getProject(lintClient, defaultVariant, gradleProject)
            }
        }

        return createNonAgpProject(gradleProject, lintClient, variantName)
    }

    private fun createNonAgpProject(
        gradleProject: GradleProject,
        lintClient: LintGradleClient,
        variantName: String?
    ): LintJavaProject? {
        // Make plain vanilla project; this is what happens for Java projects (which
        // don't have a Gradle model)
        val convention = gradleProject.convention.findPlugin(
            JavaPluginConvention::class.java
        ) ?: return null

        // Language level: Currently not needed. The way to get it is via
        //   convention.getSourceCompatibility()
        val language = LanguageLevel.parse(convention.sourceCompatibility.name)

        // Sources
        val sourceSets = convention.sourceSets
        val sources: MutableList<File> = mutableListOf()
        val classes: MutableList<File> = mutableListOf()
        val libs: MutableList<File> = mutableListOf()
        val tests: MutableList<File> = mutableListOf()
        for (sourceSet in sourceSets) {
            if (sourceSet.name == SourceSet.TEST_SOURCE_SET_NAME) {
                // We don't model the full test source set yet (e.g. its dependencies),
                // only its source files
                val javaSrc = sourceSet.java
                for (dir in javaSrc.srcDirs) {
                    if (dir.exists()) {
                        tests.add(dir)
                    }
                }
                continue
            }
            val javaSrc = sourceSet.java
            // There are also resource directories, in case we want to
            // model those here eventually
            for (dir in javaSrc.srcDirs) {
                if (dir.exists()) {
                    sources.add(dir)
                }
            }
            for (file in sourceSet.output.classesDirs) {
                if (file.exists()) {
                    classes.add(file)
                }
            }
            for (file in sourceSet.compileClasspath.files) {
                if (file.exists()) {
                    libs.add(file)
                }
            }

            // TODO: Boot classpath? We don't have access to that here, so for
            // now the LintCliClient just falls back to the running Gradle JVM and looks
            // up its class path.
        }
        val projectDir = gradleProject.projectDir
        val dependencies: MutableList<Project> = mutableListOf()
        val project = LintJavaProject(
            lintClient, projectDir, dependencies, sources, classes, libs, tests, language
        )

        // Dependencies
        val configurations = gradleProject.configurations
        val compileConfiguration = configurations.getByName("compileClasspath")
        for (dependency in compileConfiguration.allDependencies) {
            if (dependency is ProjectDependency) {
                val p = dependency.dependencyProject
                val lintProject = getProject(lintClient, p.path, p, variantName)
                    ?: continue
                dependencies.add(lintProject)
            } else if (dependency is ExternalDependency) {
                val name = dependency.getName()
                // group or version null: this will be the case for example with
                //    repositories { flatDir { dirs 'myjars' } }
                //    dependencies { compile name: 'guava-18.0' }
                val group = dependency.getGroup() ?: continue
                val version = dependency.getVersion() ?: continue
                val coordinates = DefaultLmMavenName(group, name, version)
                val javaLib = javaLibraryProjectsByCoordinate[coordinates]
                    ?: continue // Create wrapper? Unfortunately we don't have the actual .jar file
                javaLib.isExternalLibrary = true
                dependencies.add(javaLib)
            } else if (dependency is FileCollectionDependency) {
                val files = dependency.resolve()
                libs.addAll(files)
            }
        }
        return project
    }

    private fun getProject(
        client: LintGradleClient,
        variant: LmVariant,
        gradleProject: GradleProject
    ): Project {
        val cached = appProjects[gradleProject]
        if (cached != null) {
            return cached
        }
        val dir = gradleProject.projectDir
        val manifest = client.mergedManifest
        val lintProject = LmModuleProject(client, dir, dir, variant, manifest)
        lintProject.kotlinSourceFolders = client.getKotlinSourceFolders(gradleProject)
        appProjects[gradleProject] = lintProject
        lintProject.gradleProject = true

        // DELIBERATELY calling getDependencies here (and Dependencies#getProjects() below) :
        // the new hierarchical model is not working yet.
        val dependencies = variant.mainArtifact.dependencies
        for (library in dependencies.direct) {
            if (library.project != null) {
                // Handled below
                continue
            }
            if (library is LmAndroidLibrary) {
                lintProject.addDirectLibrary(getLibrary(client, library, gradleProject, variant))
            }
        }

        // Dependencies.getProjects() no longer passes project names in all cases, so
        // look up from Gradle project directly
        var processedProjects: MutableList<String?>? = null
        val configurations = gradleProject.configurations
        val compileConfiguration =
            configurations.getByName(variant.name + "CompileClasspath")
        for (dependency in compileConfiguration.allDependencies) {
            if (dependency is ProjectDependency) {
                val p = dependency.dependencyProject
                // Libraries don't have to use the same variant name as the
                // consuming app. In fact they're typically not: libraries generally
                // use the release variant. We can look up the variant name
                // in AndroidBundle#getProjectVariant, though it's always null
                // at the moment. So as a fallback, search for existing
                // code.
                val depProject = getProject(client, p, variant.name)
                if (depProject != null) {
                    if (processedProjects == null) {
                        processedProjects = mutableListOf()
                    }
                    processedProjects.add(p.path)
                    lintProject.addDirectLibrary(depProject)
                }
            }
        }
        for (library in dependencies.direct) {
            if (library is LmJavaLibrary) {
                val projectName = library.project
                if (projectName != null) {
                    if (processedProjects != null && processedProjects.contains(projectName)) {
                        continue
                    }
                    val libLintProject =
                        getProject(client, projectName, gradleProject, variant.name)
                    if (libLintProject != null) {
                        lintProject.addDirectLibrary(libLintProject)
                        continue
                    }
                }
                lintProject.addDirectLibrary(getLibrary(client, library))
            }
        }
        return lintProject
    }

    private fun getProject(
        client: LintGradleClient,
        path: String,
        gradleProject: GradleProject,
        variantName: String?
    ): Project? {
        val cached = namedProjects[path]
        if (cached != null) {
            return cached
        }
        val namedProject = gradleProject.findProject(path)
        if (namedProject != null) {
            val project = getProject(client, namedProject, variantName)
            if (project != null) {
                namedProjects[path] = project
                return project
            }
        }
        return null
    }

    private fun getLibrary(
        client: LintGradleClient,
        library: LmAndroidLibrary,
        gradleProject: GradleProject,
        variant: LmVariant
    ): Project {
        var cached = libraryProjects[library]
        if (cached != null) {
            return cached
        }
        val coordinates = library.resolvedCoordinates
        cached = libraryProjectsByCoordinate[coordinates]
        if (cached != null) {
            return cached
        }
        if (library.project != null) {
            val project =
                getProject(client, library.project!!, gradleProject, variant.name)
            if (project != null) {
                libraryProjects[library] = project
                return project
            }
        }
        val dir = library.folder
        val project = LmModuleAndroidLibraryProject(client, dir, dir, library)
        project.kotlinSourceFolders = client.getKotlinSourceFolders(library.project)
        project.setMavenCoordinates(coordinates)
        if (library.project == null) {
            project.isExternalLibrary = true
        }
        libraryProjects[library] = project
        libraryProjectsByCoordinate[coordinates] = project
        for (dependent in library.dependencies) {
            if (dependent is LmAndroidLibrary) {
                project.addDirectLibrary(
                    getLibrary(client, dependent, gradleProject, variant)
                )
            } else {
                // TODO What do we do here? Do we create a wrapper JavaLibrary project?
            }
        }
        return project
    }

    private fun getLibrary(client: LintGradleClient, library: LmJavaLibrary): Project {
        var cached = javaLibraryProjects[library]
        if (cached != null) {
            return cached
        }
        val coordinates = library.resolvedCoordinates
        cached = javaLibraryProjectsByCoordinate[coordinates]
        if (cached != null) {
            return cached
        }
        val dir = library.jarFiles.first()
        val project = LmModuleJavaLibraryProject(client, dir, dir, library)
        project.setMavenCoordinates(coordinates)
        project.isExternalLibrary = true
        javaLibraryProjects[library] = project
        javaLibraryProjectsByCoordinate[coordinates] = project
        for (dependent in library.dependencies) {
            // just a sanity check; Java libraries cannot depend on Android libraries
            if (dependent is LmJavaLibrary) {
                project.addDirectLibrary(
                    getLibrary(client, dependent)
                )
            }
        }
        return project
    }

    private fun createLintBuildModel(
        gradleProject: GradleProject
    ): LmModule? {
        val pluginContainer = gradleProject.plugins
        for (p in pluginContainer) {
            val provider = p as? ToolingRegistryProvider ?: continue
            val registry: ToolingModelBuilderRegistry = provider.modelBuilderRegistry
            val project = createAndroidProject(gradleProject, registry)
            return LmFactory().create(project, gradleProject.rootDir)
        }
        return null
    }

    private fun createAndroidProject(
        project: GradleProject,
        toolingRegistry: ToolingModelBuilderRegistry
    ): IdeAndroidProject {
        val modelName = AndroidProject::class.java.name
        val modelBuilder = toolingRegistry.getBuilder(modelName)
        val ext = project.extensions.extraProperties
        // setup the level 3 sync.
        // Ensure that projects are constructed serially since otherwise
        // it's possible for a race condition on the below property
        // to trigger occasional NPE's like the one in b.android.com/38117575
        synchronized(ext) {
            ext[AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED] =
                AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD.toString()
            return try {
                val model = modelBuilder.buildAll(modelName, project) as AndroidProject
                val factory = IdeDependenciesFactory()
                // Sync issues are not used in lint.
                IdeAndroidProjectImpl.create(model, factory, model.variants, emptyList())
            } finally {
                ext[AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED] = null
            }
        }
    }
}
