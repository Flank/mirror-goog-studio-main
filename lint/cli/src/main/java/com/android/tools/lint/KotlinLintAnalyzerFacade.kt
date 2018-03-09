/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint

import com.intellij.core.CoreJavaFileManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mock.MockProject
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.PersistentFSConstants
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.CliModuleVisibilityManagerImpl
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.script.CliScriptDefinitionProvider
import org.jetbrains.kotlin.cli.jvm.compiler.ClasspathRootsResolver
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport
import org.jetbrains.kotlin.cli.jvm.compiler.CliVirtualFileFinderFactory
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmModulePathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.cli.jvm.index.JvmDependenciesIndexImpl
import org.jetbrains.kotlin.cli.jvm.index.SingleJavaFileRootsIndex
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleFinder
import org.jetbrains.kotlin.cli.jvm.modules.CliJavaModuleResolver
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.ModuleVisibilityManager
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver
import org.jetbrains.kotlin.resolve.lazy.declarations.CliDeclarationProviderFactoryService
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.kotlin.script.ScriptDependenciesProvider
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.uast.kotlin.KotlinUastBindingContextProviderService
import org.jetbrains.uast.kotlin.internal.CliKotlinUastBindingContextProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension
import java.io.File

// Cleanup remaining:
// * Find a way to test kotlin extensions (like the findViewById extension) ??
// * Find a way to do less work if ktFiles is empty

// Analyze PSI files with Kotlin compiler and produce binding context
// From https://github.com/JetBrains/kotlin/commits/rr/yan : fb82b72dc1892d377ccf98511d56ecce219c8098
object KotlinLintAnalyzerFacade {
    @JvmStatic
    fun analyze(files: List<File>, javaRoots: List<File>, project: MockProject): BindingTrace {

        if (ServiceManager.getService(project, LightClassGenerationSupport::class.java) == null) {
            KotlinLintAnalyzerFacade.registerProjectComponents(project)
        }

        val localFs = StandardFileSystems.local()
        val psiManager = PsiManager.getInstance(project)

        val virtualFiles = files.mapNotNull { localFs.findFileByPath(it.absolutePath) }
        val ktFiles = virtualFiles.mapNotNull { psiManager.findFile(it) }.filterIsInstance<KtFile>()

        return analyzePsi(ktFiles, javaRoots, project)
    }

    @JvmStatic
    private fun analyzePsi(
        ktFiles: List<KtFile>,
        javaRoots: List<File>,
        project: MockProject
    ): BindingTrace {

        val trace = CliLightClassGenerationSupport.NoScopeRecordCliBindingTrace()
        val localFs = StandardFileSystems.local()
        // We can't figure out if the given directory is a binary or a source root, so we add
        // it to both lists
        val javaBinaryRoots = javaRoots
            .mapNotNull { localFs.findFileByPath(it.absolutePath) }
            .map { JavaRoot(it, JavaRoot.RootType.BINARY) }

        val javaSourceRoots = javaRoots
            .filter { it.isDirectory }
            .mapNotNull { localFs.findFileByPath(it.absolutePath) }
            .map { JavaRoot(it, JavaRoot.RootType.SOURCE) }

        // If project already depends on Kotlin, there should already be the correct standard
        // libraries on the classpath. But if not (e.g. tests), use them from lint's own
        // classpath.
        val extra = if (!hasKotlinStdlib(javaRoots)) findKotlinStandardLibraries() else emptyList()

        val allJavaRoots = javaBinaryRoots + javaSourceRoots + extra
        val compilerConfiguration = createCompilerConfiguration("lintWithKotlin", allJavaRoots)

        for (registrar in compilerConfiguration.getList(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS)) {
            registrar.registerProjectComponents(project, compilerConfiguration)
        }

        fun createPackagePartProvider(scope: GlobalSearchScope): JvmPackagePartProvider {
            return JvmPackagePartProvider(
                compilerConfiguration.languageVersionSettings,
                scope
            ).apply {
                addRoots(allJavaRoots)
            }
        }

        project.picoContainer.unregisterComponent(DeclarationProviderFactoryService::class.java.name)
        project.registerService(
            DeclarationProviderFactoryService::class.java,
            CliDeclarationProviderFactoryService(ktFiles)
        )

        PersistentFSConstants::class.java.getDeclaredField("ourMaxIntellisenseFileSize")
            .apply { isAccessible = true }
            .setInt(null, FileUtilRt.LARGE_FOR_CONTENT_LOADING)

        val scriptDefinitionProvider =
            ScriptDefinitionProvider.getInstance(project) as? CliScriptDefinitionProvider
        if (scriptDefinitionProvider != null) {
            scriptDefinitionProvider.setScriptDefinitions(
                compilerConfiguration.getList(JVMConfigurationKeys.SCRIPT_DEFINITIONS)
            )

            ScriptDependenciesProvider.getInstance(project).let { importsProvider ->
                compilerConfiguration.addJvmClasspathRoots(
                    ktFiles.mapNotNull(importsProvider::getScriptDependencies)
                        .flatMap { it.classpath }
                        .distinctBy { it.absolutePath })
            }
        }

        val jdkHome = compilerConfiguration.get(JVMConfigurationKeys.JDK_HOME)
        val jrtFileSystem =
            VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.JRT_PROTOCOL)
        val javaModuleFinder = CliJavaModuleFinder(jdkHome?.path?.let { path ->
            jrtFileSystem?.findFileByPath(path + URLUtil.JAR_SEPARATOR)
        })

        val outputDirectory =
            compilerConfiguration.get(JVMConfigurationKeys.MODULES)?.singleOrNull()?.getOutputDirectory()
                    ?: compilerConfiguration.get(JVMConfigurationKeys.OUTPUT_DIRECTORY)?.absolutePath

        val classpathRootsResolver = ClasspathRootsResolver(
            PsiManager.getInstance(project),
            PrintingMessageCollector(System.err, MessageRenderer.WITHOUT_PATHS, true),
            compilerConfiguration.getList(JVMConfigurationKeys.ADDITIONAL_JAVA_MODULES),
            this::contentRootToVirtualFile,
            javaModuleFinder,
            !compilerConfiguration.getBoolean(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE),
            outputDirectory?.let(this::findLocalFile)
        )

        val (initialRoots, javaModules) =
                classpathRootsResolver.convertClasspathRoots(
                    compilerConfiguration.getList(
                        JVMConfigurationKeys.CONTENT_ROOTS
                    )
                )

        val javaModuleResolver = CliJavaModuleResolver(
            classpathRootsResolver.javaModuleGraph,
            javaModules, javaModuleFinder.systemModules.toList()
        )
        project.registerServiceIfNeeded(JavaModuleResolver::class.java, javaModuleResolver)

        val (roots, singleJavaFileRoots) =
                initialRoots.partition { (file) -> file.isDirectory || file.extension != JavaFileType.DEFAULT_EXTENSION }

        val rootsIndex = JvmDependenciesIndexImpl(roots)

        val finderFactory = CliVirtualFileFinderFactory(rootsIndex)
        project.registerServiceIfNeeded(MetadataFinderFactory::class.java, finderFactory)
        project.registerServiceIfNeeded(VirtualFileFinderFactory::class.java, finderFactory)

        (ServiceManager.getService(
            project,
            CoreJavaFileManager::class.java
        ) as KotlinCliJavaFileManagerImpl).initialize(
            rootsIndex,
            packagePartProviders,
            SingleJavaFileRootsIndex(singleJavaFileRoots),
            compilerConfiguration.getBoolean(JVMConfigurationKeys.USE_FAST_CLASS_FILES_READING)
        )

        TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            project, ktFiles, trace, compilerConfiguration, ::createPackagePartProvider
        )

        return trace
    }

    private val packagePartProviders = mutableListOf<JvmPackagePartProvider>()

    @JvmStatic
    private fun registerProjectComponents(project: MockProject) {
        KotlinCoreEnvironment.registerPluginExtensionPoints(project)
        val area = Extensions.getArea(project)

        val cliLightClassGenerationSupport = CliLightClassGenerationSupport(project)
        project.registerService(
            LightClassGenerationSupport::class.java,
            cliLightClassGenerationSupport
        )
        project.registerService(
            CliLightClassGenerationSupport::class.java,
            cliLightClassGenerationSupport
        )
        project.registerService(CodeAnalyzerInitializer::class.java, cliLightClassGenerationSupport)

        area.getExtensionPoint(PsiElementFinder.EP_NAME).registerExtension(
            JavaElementFinder(
                project,
                cliLightClassGenerationSupport
            )
        )

        project.registerServiceIfNeeded(
            KotlinUastBindingContextProviderService::class.java,
            CliKotlinUastBindingContextProviderService::class.java
        )

        project.registerService(
            ModuleVisibilityManager::class.java,
            CliModuleVisibilityManagerImpl(false)
        )

        AnalysisHandlerExtension.registerExtension(project, UastAnalysisHandlerExtension())

        KotlinCoreEnvironment.registerProjectServices(project, null)
    }

    private fun findJarRoot(file: File): VirtualFile? {
        val fileSystem = LintCoreApplicationEnvironment.get().jarFileSystem
        return fileSystem.findFileByPath(file.path + URLUtil.JAR_SEPARATOR)
    }

    private fun findLocalFile(path: String): VirtualFile? =
        LintCoreApplicationEnvironment.get().localFileSystem.findFileByPath(path)

    private fun findLocalFile(root: JvmContentRoot): VirtualFile? {
        val file = findLocalFile(root.file.absolutePath)
        if (file == null) {
            println("Classpath entry points to a non-existent location: \${root.file}")
        }
        return file
    }

    private fun contentRootToVirtualFile(root: JvmContentRoot): VirtualFile? {
        if (root is JvmClasspathRoot) {
            return if (root.file.isFile) {
                findJarRoot(root.file)
            } else {
                findLocalFile(root)
            }
        }
        if (root is JvmModulePathRoot) {
            return if (root.file.isFile) {
                findJarRoot(root.file)
            } else {
                findLocalFile(root)
            }
        }
        if (root is JavaSourceRoot) {
            return findLocalFile(root)
        }
        throw IllegalStateException("Unexpected root: \$root")
    }

    private fun <T> MockProject.registerServiceIfNeeded(intf: Class<T>, impl: T) {
        if (ServiceManager.getService(this, intf) == null) {
            registerService(intf, impl)
        }
    }

    private fun <T> MockProject.registerServiceIfNeeded(intf: Class<T>, impl: Class<out T>) {
        if (ServiceManager.getService(this, intf) == null) {
            registerService(intf, impl)
        }
    }

    private fun createCompilerConfiguration(
        moduleName: String,
        javaRoots: List<JavaRoot>
    ): CompilerConfiguration {
        val configuration = CompilerConfiguration()
        configuration.put(CommonConfigurationKeys.MODULE_NAME, moduleName)
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        if (configuration.getList(JVMConfigurationKeys.SCRIPT_DEFINITIONS).isEmpty()) {
            configuration.add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, StandardScriptDefinition)
        }

        val javaSources = javaRoots
            .filter { it.type == JavaRoot.RootType.SOURCE }
            .mapNotNull { it.file.canonicalPath }
            .map(::File)

        val classpath = javaRoots
            .filter { it.type == JavaRoot.RootType.BINARY }
            .mapNotNull { it.file.canonicalPath }
            .map(::File)

        configuration.addJavaSourceRoots(javaSources)
        configuration.addJvmClasspathRoots(classpath)

        return configuration
    }

    private fun hasKotlinStdlib(roots: List<File>): Boolean {
        for (root in roots) {
            val path = root.path
            if (path.contains("kotlin-stdlib-") &&
                root.name.startsWith("kotlin-stdlib-")
            ) {
                return true
            }
        }

        return false
    }

    private fun findKotlinStandardLibraries(): List<JavaRoot> {
        val classPath: String = System.getProperty("java.class.path")
        val paths = mutableListOf<JavaRoot>()
        for (path in classPath.split(File.pathSeparatorChar)) {
            if (path.contains("kotlin-")) {
                val file = File(path)
                val name = file.name
                if (name.startsWith("kotlin-stdlib") ||
                    name.startsWith("kotlin-reflect") ||
                    name.startsWith("kotlin-script-runtime")
                ) {
                    val localFs = StandardFileSystems.local()
                    val virtualFile = localFs.findFileByPath(path) ?: continue
                    paths.add(JavaRoot(virtualFile, JavaRoot.RootType.BINARY))
                }
            }
        }
        return paths
    }
}