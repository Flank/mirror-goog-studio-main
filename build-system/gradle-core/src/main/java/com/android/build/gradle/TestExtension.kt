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
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet

/** {@code android} extension for {@code com.android.test} projects. */
open class TestExtension(
    project: Project,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo
) : BaseExtension(
    project,
    projectOptions,
    globalScope,
    buildTypes,
    productFlavors,
    signingConfigs,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    false
), TestAndroidConfig, com.android.build.api.dsl.TestExtension {

    private val applicationVariantList: DomainObjectSet<ApplicationVariant> =
        project.objects.domainObjectSet(ApplicationVariant::class.java)

    private var _targetProjectPath: String? = null

    private val variantActionList : List<Action<Variant<VariantProperties>>> = mutableListOf()
    private val variantPropertiesActionList : List<Action<VariantProperties>> = mutableListOf()


    /**
     * The list of Application variants. Since the collections is built after evaluation, it
     * should be used with Gradle's `all` iterator to process future items.
     */
    val applicationVariants: DefaultDomainObjectSet<ApplicationVariant>
        get() = applicationVariantList as DefaultDomainObjectSet<ApplicationVariant>

    override fun addVariant(variant: BaseVariant, variantScope: VariantScope) {
        applicationVariantList.add(variant as ApplicationVariant)
    }

    /**
     * The Gradle path of the project that this test project tests.
     */
    override var targetProjectPath: String?
        get() = _targetProjectPath
        set(value) = targetProjectPath(value)

    open fun targetProjectPath(targetProjectPath: String?) {
        checkWritability()
        _targetProjectPath = targetProjectPath
    }

    /**
     * The variant of the tested project.
     *
     * Default is 'debug'
     *
     * @deprecated This is deprecated, test module can now test all flavors.
     */
    override var targetVariant: String
        get() = ""
        set(value) = targetVariant(value)

    open fun targetVariant(targetVariant: String) {
        checkWritability()
        System.err.println("android.targetVariant is deprecated, all variants are now tested.")
    }

    override val testBuildType: String?
        get() = null

    override fun onVariants(action: Action<Variant<VariantProperties>>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onVariantsProperties(action: Action<VariantProperties>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
