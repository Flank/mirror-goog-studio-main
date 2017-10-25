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

package com.android.build.gradle.internal.plugin

import com.android.build.api.dsl.variant.Variant
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.extensions.BuildPropertiesImpl
import com.android.build.gradle.internal.api.dsl.extensions.VariantAwarePropertiesImpl
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl
import com.android.build.gradle.internal.api.dsl.model.BaseFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.DefaultConfigImpl
import com.android.build.gradle.internal.api.dsl.model.FallbackStrategyImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.sourcesets.FilesProvider
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueHandlerImpl
import com.android.build.gradle.internal.variant2.ContainerFactory
import com.android.build.gradle.internal.variant2.DslModelDataImpl
import com.android.build.gradle.internal.variant2.VariantBuilder
import com.android.build.gradle.internal.variant2.VariantFactory2
import com.android.build.gradle.internal.variant2.VariantModelData
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.internal.reflect.Instantiator
import java.io.File

/**
 * Secondary plugin delegate that is typed to the actual plugin being applied.
 */
interface TypedPluginDelegate<E: BaseExtension2> {

    fun getVariantFactories(): List<VariantFactory2<E>>

    fun createNewExtension(
            extensionContainer: ExtensionContainer,
            buildProperties: BuildPropertiesImpl,
            variantExtensionProperties: VariantOrExtensionPropertiesImpl,
            variantAwareProperties: VariantAwarePropertiesImpl,
            deprecationReporter: DeprecationReporter,
            issueReporter: EvalIssueReporter): E

    fun createDefaults(extension: E)

}

/**
 * Main plugin delegate that does all the work. The plugin is mostly calling into this
 * to drive the lifecycle.
 */
class PluginDelegate<out E: BaseExtension2>(
        projectPath: String,
        private val instantiator: Instantiator,
        private val extensionContainer: ExtensionContainer,
        private val configurationContainer: ConfigurationContainer,
        private val containerFactory: ContainerFactory,
        private val filesProvider: FilesProvider,
        private val logger: Logger,
        projectOptions: ProjectOptions,
        private val typedDelegate: TypedPluginDelegate<E>) {

    private lateinit var dslModelData: DslModelDataImpl<E>
    private lateinit var variantModelData: VariantModelData
    private lateinit var newExtension: E

    private val issueReporter = SyncIssueHandlerImpl(
            SyncOptions.getModelQueryMode(projectOptions), logger)
    private val deprecationReporter = DeprecationReporterImpl(issueReporter, projectPath)

    init {
        if (projectOptions.hasDeprecatedOptions()) {
            issueReporter.reportError(
                    EvalIssueReporter.Type.GENERIC, projectOptions.deprecatedOptionsErrorMessage)
        }
    }

    fun prepareForEvaluation(): E {
        // create the default config implementation
        val baseFlavor = BaseFlavorImpl(deprecationReporter, issueReporter)
        val defaultConfig = DefaultConfigImpl(
                VariantPropertiesImpl(issueReporter),
                BuildTypeOrProductFlavorImpl(
                        deprecationReporter, issueReporter) { baseFlavor.postprocessing },
                ProductFlavorOrVariantImpl(issueReporter),
                FallbackStrategyImpl(deprecationReporter, issueReporter),
                baseFlavor,
                issueReporter)

        dslModelData = DslModelDataImpl(
                defaultConfig,
                typedDelegate.getVariantFactories(),
                configurationContainer,
                filesProvider,
                containerFactory,
                instantiator,
                deprecationReporter,
                issueReporter,
                logger)

        variantModelData = VariantModelData(issueReporter)

        newExtension = typedDelegate.createNewExtension(
                extensionContainer,
                BuildPropertiesImpl(dslModelData, issueReporter),
                VariantOrExtensionPropertiesImpl(issueReporter),
                VariantAwarePropertiesImpl(
                        dslModelData,
                        variantModelData,
                        deprecationReporter,
                        issueReporter),
                deprecationReporter,
                issueReporter)

        typedDelegate.createDefaults(newExtension)

        return newExtension
    }

    fun afterEvaluate(): List<Variant> {
        // callback for the afterEvaluate
        val preVariantActions = newExtension.preVariantCallbacks
        for (action in preVariantActions) {
            action.execute(null)
        }

        // seal the DSL.
        newExtension.seal()
        dslModelData.seal()

        // compute the variants
        dslModelData.afterEvaluateCompute()
        val builder = VariantBuilder(
                dslModelData,
                newExtension,
                deprecationReporter,
                issueReporter)
        builder.generateVariants()
        val variants = builder.variants
        val variantShims = builder.shims

        // run the variant API
        variantModelData.runVariantCallbacks(variantShims)

        // post-variant API
        for (action in newExtension.postVariants) {
            action.execute(variantShims)
        }

        // seal the variants
        for (variant in variants) {
            variant.seal()
        }
        // and additional data (callbacks)
        variantModelData.seal()

        // create the tasks
        // FIXME implement

        return variantShims
    }
}

/**
 * Single wrapper around a [Project] that implements all our abstraction layers
 */
class ProjectWrapper(private val project: Project) : FilesProvider, ContainerFactory {

    // --- FilesProvider

    override fun file(file: Any): File {
        return project.file(file)
    }

    override fun files(vararg files: Any): ConfigurableFileCollection {
        return project.files(*files)
    }

    override fun fileTree(args: Map<String, *>): ConfigurableFileTree {
        return project.fileTree(args)
    }

    // --- ContainerFactory

    override fun <T> createContainer(
            itemClass: Class<T>,
            factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T> {
        return project.container(itemClass, factory)
    }
}
