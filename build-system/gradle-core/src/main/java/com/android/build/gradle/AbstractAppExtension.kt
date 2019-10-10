package com.android.build.gradle

import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import java.util.ArrayList
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

/**
 * The `android` extension for application plugins.
 *
 *
 * For the base module, see [com.android.build.gradle.BaseExtension]
 *
 *
 * For optional apks, this class is used directly.
 */
open class AbstractAppExtension(
    project: Project,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    isBaseModule: Boolean
) : TestedExtension(
    project,
    projectOptions,
    globalScope,
    buildTypes,
    productFlavors,
    signingConfigs,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    isBaseModule
) {

    /**
     * Returns a collection of [build variants](https://developer.android.com/studio/build/build-variants.html) that
     * the app project includes.
     *
     *
     * To process elements in this collection, you should use the [
 * `all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)) iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     *
     * The following sample iterates through all `applicationVariants` elements to [inject a
 * build variable into the manifest](https://developer.android.com/studio/build/manifest-build-variables.html):
     *
     * <pre>
     * android.applicationVariants.all { variant -&gt;
     * def mergedFlavor = variant.getMergedFlavor()
     * // Defines the value of a build variable you can use in the manifest.
     * mergedFlavor.manifestPlaceholders = [hostName:"www.example.com/${variant.versionName}"]
     * }
    </pre> *
     */
    val applicationVariants: DomainObjectSet<ApplicationVariant>

    private val variantActionList = ArrayList<Action<Variant<*>>>()
    private val variantPropertiesActionList = ArrayList<Action<VariantProperties>>()

    init {
        applicationVariants = project.objects.domainObjectSet(ApplicationVariant::class.java)
    }

    /**
     * Registers an [Action] to be executed on each [Variant] of the project.
     *
     * @param action an [Action] taking a [Variant] as a parameter.
     */
    @Incubating
    fun onVariants(action: Action<Variant<*>>) {
        variantActionList.add(action)
        // TODO: b/142715610 Resolve when onVariants is called with variants already existing the
        // applicationVariantList.
    }

    /**
     * Registers an [Action] to be executed on each [VariantProperties] of the project.
     *
     * @param action an [Action] taking a [VariantProperties] as a parameter.
     */
    @Incubating
    fun onVariantsProperties(action: Action<VariantProperties>) {
        variantPropertiesActionList.add(action)
        // TODO: b/142715610 Resolve when onVariants is called with variants already existing the
        // applicationVariantList.
    }

    override fun addVariant(variant: BaseVariant, variantScope: VariantScope) {
        applicationVariants.add(variant as ApplicationVariant)
        // TODO: move these 2 calls from the addVariant method.
        variantActionList.forEach { action -> action.execute(variantScope.variantData.publicVariantApi) }
        variantPropertiesActionList.forEach { action ->
            action.execute(
                variantScope.variantData.publicVariantPropertiesApi
            )
        }

    }
}
