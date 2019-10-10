package com.android.build.gradle

import com.android.build.api.variant.AppVariant
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.LibraryVariantProperties
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet
import java.util.Collections

/**
 * The {@code android} extension for {@code com.android.library} projects.
 *
 * <p>Apply this plugin to your project to <a
 * href="https://developer.android.com/studio/projects/android-library.html">create an Android
 * library</a>.
 */
open class LibraryExtension(
    project: Project,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo
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
    false
), com.android.build.api.dsl.LibraryExtension {

    private val libraryVariantList: DomainObjectSet<LibraryVariant> =
        project.objects.domainObjectSet(LibraryVariant::class.java)

    private var _packageBuildConfig = true

    private var _aidlPackageWhiteList: MutableCollection<String>? = null

    /**
     * Returns a collection of
     * [build variants](https://developer.android.com/studio/build/build-variants.html)
     * that the library project includes.
     *
     * To process elements in this collection, you should use
     * [`all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all-org.gradle.api.Action-).
     * That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     * The following sample iterates through all `libraryVariants` elements to
     * [inject a build variable into the manifest](https://developer.android.com/studio/build/manifest-build-variables.html):
     *
     * ```
     * android.libraryVariants.all { variant ->
     *     def mergedFlavor = variant.getMergedFlavor()
     *     // Defines the value of a build variable you can use in the manifest.
     *     mergedFlavor.manifestPlaceholders = [hostName:"www.example.com"]
     * }
     * ```
     */
    val libraryVariants: DefaultDomainObjectSet<LibraryVariant>
        get() = libraryVariantList as DefaultDomainObjectSet<LibraryVariant>

    override fun addVariant(variant: BaseVariant, variantScope: VariantScope) {
        libraryVariantList.add(variant as LibraryVariant)
    }

    override var aidlPackageWhiteList: MutableCollection<String>?
        get() = _aidlPackageWhiteList
        set(value) = value?.let { aidlPackageWhiteList(*it.toTypedArray()) } ?: Unit

    fun aidlPackageWhiteList(vararg aidlFqcns: String) {
        if (_aidlPackageWhiteList == null) {
            _aidlPackageWhiteList = Lists.newArrayList()
        }
        Collections.addAll(_aidlPackageWhiteList!!, *aidlFqcns)
    }

    override fun onVariants(action: Action<com.android.build.api.variant.LibraryVariant>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onVariantsProperties(action: Action<LibraryVariantProperties>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
