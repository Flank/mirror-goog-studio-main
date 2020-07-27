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

package com.android.tools.lint

import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_SRCJAR
import com.android.tools.lint.UastEnvironment.Companion.disposeApplicationEnvironment
import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.impl.ZipHandler
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.util.io.URLUtil.JAR_SEPARATOR
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY
import org.jetbrains.kotlin.cli.common.messages.GradleStyleMessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.CliModuleAnnotationsResolver
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ModuleAnnotationsResolver
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class provides the setup and configuration needed use VFS/PSI/UAST on the command line.
 *
 * Basic usage:
 *   1. Create a configuration via [UastEnvironment.Configuration.create] and mutate it as needed.
 *   2. Create a project environment via [UastEnvironment.create].
 *      You can create multiple environments in the same process (one for each "module").
 *   3. Call [analyzeFiles] to initialize PSI machinery and precompute resolve information.
 *   4. Analyze PSI/UAST.
 *   5. When finished, call [dispose].
 *   6. Once *all* [UastEnvironment]s are disposed, call [disposeApplicationEnvironment]
 *      to clean up some global resources, especially if running in a long-living daemon process.
 */
class UastEnvironment private constructor(
    // Luckily, the Kotlin compiler already has the machinery for creating an IntelliJ
    // application environment (because Kotlin uses IntelliJ to parse Java). So most of
    // the work here is delegated to the Kotlin compiler.
    private val kotlinCompilerEnv: KotlinCoreEnvironment,
    private val projectDisposable: Disposable
) {
    val ideaProject: MockProject
        get() = kotlinCompilerEnv.projectEnvironment.project

    val kotlinCompilerConfig: CompilerConfiguration
        get() = kotlinCompilerEnv.configuration

    /** A configuration is just a container for the classpath, compiler flags, etc. */
    class Configuration private constructor(val kotlinCompilerConfig: CompilerConfiguration) {
        companion object {
            @JvmStatic
            fun create(): Configuration = Configuration(createKotlinCompilerConfig())
        }

        fun addSourceRoots(sourceRoots: List<File>) {
            kotlinCompilerConfig.addJavaSourceRoots(sourceRoots)
            // Note: the Kotlin compiler would normally add KotlinSourceRoots to the configuration
            // too, to be used by KotlinCoreEnvironment when computing the set of KtFiles to
            // analyze. However, Lint already computes the list of KtFiles on its own in LintDriver.
        }

        fun addClasspathRoots(classpathRoots: List<File>) {
            kotlinCompilerConfig.addJvmClasspathRoots(classpathRoots)
        }

        // Defaults to LanguageLevel.HIGHEST.
        var javaLanguageLevel: LanguageLevel? = null

        // Defaults to LanguageVersionSettingsImpl.DEFAULT.
        var kotlinLanguageLevel: LanguageVersionSettings
            get() = kotlinCompilerConfig.languageVersionSettings
            set(value) {
                kotlinCompilerConfig.languageVersionSettings = value
            }
    }

    companion object {
        init {
            // We don't bundle .dll files in the Gradle plugin for native file system access;
            // prevent warning logs on Windows when it's not found (see b.android.com/260180)
            System.setProperty("idea.use.native.fs.for.win", "false")

            // By default the Kotlin compiler will dispose the application environment when there
            // are no projects left. However, we turn this behavior off and instead manage the
            // application lifecycle ourselves. (It turns out that the Kotlin Gradle plugin already
            // sets the keepalive property to true anyway, which is picked up by Lint if running in
            // the same Gradle daemon process. So setting the property here ensures consistency.)
            System.setProperty(KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY, "true")
        }

        /**
         * Creates a new [UastEnvironment] suitable for analyzing both Java and Kotlin code.
         * You must still call [analyzeFiles] before doing anything with PSI/UAST.
         * When finished using the environment, call [dispose].
         */
        @JvmStatic
        fun create(config: Configuration): UastEnvironment {
            val parentDisposable = Disposer.newDisposable("UastEnvironment.create")
            val kotlinEnv = createKotlinCompilerEnv(parentDisposable, config)
            return UastEnvironment(kotlinEnv, parentDisposable)
        }

        /**
         * Disposes the global application environment, which is created implicitly by the first
         * [UastEnvironment]. Only call this once *all* [UastEnvironment]s have been disposed.
         */
        @JvmStatic
        fun disposeApplicationEnvironment() {
            // Note: if we later decide to keep the app env alive forever in the Gradle daemon, we
            // should still clear some caches between builds (see CompileServiceImpl.clearJarCache).
            val appEnv = KotlinCoreEnvironment.applicationEnvironment ?: return
            Disposer.dispose(appEnv.parentDisposable)
            checkApplicationEnvironmentDisposed()
            ZipHandler.clearFileAccessorCache()
        }

        @JvmStatic
        fun checkApplicationEnvironmentDisposed() {
            check(KotlinCoreEnvironment.applicationEnvironment == null)
        }
    }

    /**
     * Analyzes the given files so that PSI/UAST resolve works correctly.
     *
     * For now, only Kotlin files need to be analyzed upfront; Java code is resolved lazily.
     * However, this method must still be called for Java-only projects in order to properly
     * initialize the PSI machinery.
     *
     * Calling this function multiple times clears previous analysis results.
     */
    fun analyzeFiles(ktFiles: List<File>) {
        val ktPsiFiles = mutableListOf<KtFile>()

        // Convert files to KtFiles.
        val fs = StandardFileSystems.local()
        val psiManager = PsiManager.getInstance(ideaProject)
        for (ktFile in ktFiles) {
            val vFile = fs.findFileByPath(ktFile.absolutePath) ?: continue
            val ktPsiFile = psiManager.findFile(vFile) as? KtFile ?: continue
            ktPsiFiles.add(ktPsiFile)
        }

        // TODO: This is a hack to get resolve working for Kotlin declarations in srcjars,
        //  which has historically been needed in google3. We should investigate whether this is
        //  still needed. In particular, we should ensure we do not add srcjars from dependencies,
        //  because that could lead to a lot of extra work for the compiler.
        //  Note: srcjars are tested by ApiDetectorTest.testSourceJars() and testSourceJarsKotlin().
        addKtFilesFromSrcJars(ktPsiFiles)

        // TODO: This is a hack needed because TopDownAnalyzerFacadeForJVM calls
        //  KotlinCoreEnvironment.createPackagePartProvider(), which permanently adds additional
        //  PackagePartProviders to the environment. This significantly slows down resolve over
        //  time. The root issue is that KotlinCoreEnvironment was not designed to be reused
        //  repeatedly for multiple analyses---which we do when checkDependencies=true. This hack
        //  should be removed when we move to a model where UastEnvironment is used only once.
        resetPackagePartProviders()

        val perfManager = kotlinCompilerConfig.get(CLIConfigurationKeys.PERF_MANAGER)
        perfManager?.notifyAnalysisStarted()

        // Run the Kotlin compiler front end.
        // The result is implicitly associated with the IntelliJ project environment.
        // TODO: Consider specifying a sourceModuleSearchScope, which can be used to support
        //  partial compilation by giving the Kotlin compiler access to the compiled output
        //  of the module being analyzed. See KotlinToJVMBytecodeCompiler for an example.
        TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            ideaProject,
            ktPsiFiles,
            CliBindingTraceForLint(),
            kotlinCompilerConfig,
            kotlinCompilerEnv::createPackagePartProvider
        )

        perfManager?.notifyAnalysisFinished(
            files = ktPsiFiles.size,
            lines = ktPsiFiles.sumBy { StringUtil.getLineBreakCount(it.text) },
            additionalDescription = "UastEnvironment.analyzeFiles"
        )
    }

    private fun addKtFilesFromSrcJars(out: MutableList<KtFile>) {
        val jarFs = StandardFileSystems.jar()
        val psiManager = PsiManager.getInstance(ideaProject)
        val roots = kotlinCompilerConfig.getList(CLIConfigurationKeys.CONTENT_ROOTS)

        for (root in roots) {
            // Check if this is a srcjar.
            if (root !is JavaSourceRoot) continue
            if (!root.file.name.endsWith(DOT_SRCJAR)) continue
            val jarRoot = jarFs.findFileByPath(root.file.path + JAR_SEPARATOR) ?: continue

            // Collect Kotlin files.
            VfsUtilCore.iterateChildrenRecursively(jarRoot, null) { file ->
                if (file.name.endsWith(DOT_KT) || file.name.endsWith(DOT_KTS)) {
                    val psiFile = psiManager.findFile(file)
                    if (psiFile is KtFile) {
                        out.add(psiFile)
                    }
                }
                true // Continues the traversal.
            }
        }
    }

    private fun resetPackagePartProviders() {
        run {
            // Clear KotlinCoreEnvironment.packagePartProviders.
            val field = KotlinCoreEnvironment::class.java.getDeclaredField("packagePartProviders")
            field.isAccessible = true
            val list = field.get(kotlinCompilerEnv) as MutableList<*>
            list.clear()
        }
        run {
            // Clear CliModuleAnnotationsResolver.packagePartProviders.
            val field =
                CliModuleAnnotationsResolver::class.java.getDeclaredField("packagePartProviders")
            field.isAccessible = true
            val instance = ModuleAnnotationsResolver.getInstance(ideaProject)
            val list = field.get(instance) as MutableList<*>
            list.clear()
        }
    }

    fun dispose() {
        Disposer.dispose(projectDisposable)
    }
}

private fun createKotlinCompilerConfig(): CompilerConfiguration {
    val config = CompilerConfiguration()

    config.put(CommonConfigurationKeys.MODULE_NAME, "lint-module")
    config.put(JVMConfigurationKeys.NO_JDK, true)

    // We're not running compiler checks, but we still want to register a logger
    // in order to see warnings related to misconfiguration.
    val logger = PrintingMessageCollector(System.err, GradleStyleMessageRenderer(), false)
    config.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, logger)

    // The Kotlin compiler uses a fast, ASM-based class file reader.
    // However, Lint still relies on representing class files with PSI.
    config.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

    // Registers the scripting compiler plugin to support build.gradle.kts files.
    config.add(
        ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS,
        ScriptingCompilerConfigurationComponentRegistrar()
    )

    return config
}

private fun createKotlinCompilerEnv(
    parentDisposable: Disposable,
    config: UastEnvironment.Configuration
): KotlinCoreEnvironment {
    val env = KotlinCoreEnvironment
        .createForProduction(parentDisposable, config.kotlinCompilerConfig, JVM_CONFIG_FILES)
    appLock.withLock { configureApplicationEnvironment(env.projectEnvironment.environment) }
    configureProjectEnvironment(env.projectEnvironment.project, config)
    return env
}

private fun configureProjectEnvironment(
    project: MockProject,
    config: UastEnvironment.Configuration
) {
    // UAST support.
    @Suppress("DEPRECATION") // TODO: Migrate to using UastFacade instead.
    project.registerService(UastContext::class.java, UastContext(project))
    AnalysisHandlerExtension.registerExtension(project, UastAnalysisHandlerExtension())
    project.registerService(
        KotlinUastResolveProviderService::class.java,
        CliKotlinUastResolveProviderService::class.java
    )

    // Annotation support.
    project.registerService(
        ExternalAnnotationsManager::class.java,
        LintExternalAnnotationsManager::class.java
    )
    project.registerService(
        InferredAnnotationsManager::class.java,
        LintInferredAnnotationsManager::class.java
    )

    // Java language level.
    val javaLanguageLevel = config.javaLanguageLevel
    if (javaLanguageLevel != null) {
        LanguageLevelProjectExtension.getInstance(project).languageLevel = javaLanguageLevel
    }
}

// In parallel builds the Kotlin compiler will reuse the application environment
// (see KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction).
// So we need a lock to ensure that we only configure the application environment once.
private val appLock = ReentrantLock()
private var appConfigured = false

private fun configureApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
    check(appLock.isHeldByCurrentThread)

    if (appConfigured) return

    if (!Logger.isInitialized()) {
        Logger.setFactory(::IdeaLoggerForLint)
    }

    // Mark the registry as loaded, otherwise there are warnings upon registry value lookup.
    Registry.getInstance().markAsLoaded()

    // The Kotlin compiler does not use UAST, so we must configure it ourselves.
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        UastLanguagePlugin.extensionPointName,
        UastLanguagePlugin::class.java
    )
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        UEvaluatorExtension.EXTENSION_POINT_NAME,
        UEvaluatorExtension::class.java
    )
    appEnv.addExtension(UastLanguagePlugin.extensionPointName, JavaUastLanguagePlugin())
    appEnv.addExtension(UastLanguagePlugin.extensionPointName, KotlinUastLanguagePlugin())
    appEnv.addExtension(UEvaluatorExtension.EXTENSION_POINT_NAME, KotlinEvaluatorExtension())

    // These extensions points seem to be needed too, probably because Lint
    // triggers different IntelliJ code paths than the Kotlin compiler does.
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        CustomExceptionHandler.KEY,
        CustomExceptionHandler::class.java
    )
    CoreApplicationEnvironment.registerApplicationExtensionPoint(
        DiagnosticSuppressor.EP_NAME,
        DiagnosticSuppressor::class.java
    )

    appConfigured = true
    Disposer.register(
        appEnv.parentDisposable,
        Disposable {
            appConfigured = false
        }
    )
}

// A Kotlin compiler BindingTrace optimized for Lint.
private class CliBindingTraceForLint : CliBindingTrace() {
    override fun <K, V> record(slice: WritableSlice<K, V>, key: K, value: V) {
        // Copied from NoScopeRecordCliBindingTrace.
        when (slice) {
            BindingContext.LEXICAL_SCOPE,
            BindingContext.DATA_FLOW_INFO_BEFORE -> return
        }
        super.record(slice, key, value)
    }

    // Lint does not need compiler checks, so disable them to improve performance slightly.
    override fun wantsDiagnostics(): Boolean = false

    override fun report(diagnostic: Diagnostic) {
        // Even with wantsDiagnostics=false, some diagnostics still come through. Ignore them.
        // Note: this is a great place to debug errors such as unresolved references.
    }
}

// Most Logger.error() calls exist to trigger bug reports but are
// otherwise recoverable. E.g. see commit 3260e41111 in the Kotlin compiler.
// Thus we want to log errors to stderr but not throw exceptions (similar to the IDE).
private class IdeaLoggerForLint(category: String) : DefaultLogger(category) {
    override fun error(message: String?, t: Throwable?, vararg details: String?) {
        if (IdeaLoggerForLint::class.java.desiredAssertionStatus()) {
            throw AssertionError(message, t)
        } else {
            dumpExceptionsToStderr(message + attachmentsToString(t), t, *details)
        }
    }
}
