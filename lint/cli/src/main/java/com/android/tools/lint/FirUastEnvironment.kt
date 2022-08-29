/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.FirKotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.FirKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.FirCliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.providers.KotlinPsiDeclarationProviderFactory
import org.jetbrains.uast.kotlin.providers.impl.KotlinStaticPsiDeclarationProviderFactory
import java.io.File
import kotlin.concurrent.withLock

/** This class is FIR version of [UastEnvironment] */
class FirUastEnvironment private constructor(
    override val coreAppEnv: CoreApplicationEnvironment,
    override val ideaProject: MockProject,
    override val kotlinCompilerConfig: CompilerConfiguration,
    override val projectDisposable: Disposable
) : UastEnvironment {

    class Configuration private constructor(
        override val kotlinCompilerConfig: CompilerConfiguration
    ) : UastEnvironment.Configuration {
        override var javaLanguageLevel: LanguageLevel? = null

        companion object {
            @JvmStatic
            fun create(enableKotlinScripting: Boolean): Configuration =
                Configuration(createKotlinCompilerConfig(enableKotlinScripting))
        }
    }

    /** In FIR UAST, even Kotlin files are analyzed lazily. */
    override fun analyzeFiles(ktFiles: List<File>) {
        // TODO: addKtFilesFromSrcJars ?
    }

    companion object {
        @JvmStatic
        fun create(config: UastEnvironment.Configuration): FirUastEnvironment {
            val parentDisposable = Disposer.newDisposable("FirUastEnvironment.create")
            val analysisSession = createAnalysisSession(parentDisposable, config)
            return FirUastEnvironment(
                analysisSession.coreApplicationEnvironment,
                analysisSession.mockProject,
                config.kotlinCompilerConfig,
                parentDisposable
            )
        }
    }
}

private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
    val config = createCommonKotlinCompilerConfig()

    // TODO: NO_JDK ?

    // TODO: if [enableKotlinScripting], register FIR version of scripting compiler plugin if any

    return config
}

private fun createAnalysisSession(
    parentDisposable: Disposable,
    config: UastEnvironment.Configuration
): StandaloneAnalysisAPISession {
    // [configureApplicationEnvironment] will register app disposable and dispose it at [UastEnvironment#disposeApplicationEnvironment].
    // I.e., we manage the application lifecycle manually.
    CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value = "true"

    val analysisSession = buildStandaloneAnalysisAPISession(
        projectDisposable = parentDisposable,
    ) {
        buildKtModuleProviderByCompilerConfiguration(config.kotlinCompilerConfig)
    }
    appLock.withLock { configureFirApplicationEnvironment(analysisSession.coreApplicationEnvironment) }
    configureFirProjectEnvironment(analysisSession, config)

    return analysisSession
}

private fun configureFirProjectEnvironment(
    analysisAPISession: StandaloneAnalysisAPISession,
    config: UastEnvironment.Configuration
) {
    val project = analysisAPISession.mockProject

    val projectStructureProvider = project.getService(ProjectStructureProvider::class.java)
    // TODO: will be part of analysis API session creation
    project.registerService(
        KotlinPsiDeclarationProviderFactory::class.java,
        KotlinStaticPsiDeclarationProviderFactory(
            project,
            projectStructureProvider.getKtBinaryModules(),
            analysisAPISession.coreApplicationEnvironment.jarFileSystem as CoreJarFileSystem
        )
    )

    project.registerService(
        FirKotlinUastResolveProviderService::class.java,
        FirCliKotlinUastResolveProviderService::class.java
    )

    configureProjectEnvironment(project, config)
}

private fun configureFirApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
    configureApplicationEnvironment(appEnv) {
        it.addExtension(UastLanguagePlugin.extensionPointName, FirKotlinUastLanguagePlugin())

        it.application.registerService(
            BaseKotlinUastResolveProviderService::class.java,
            FirCliKotlinUastResolveProviderService::class.java
        )
    }
}
