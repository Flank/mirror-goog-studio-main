/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.Ndk
import com.android.build.api.dsl.Shaders
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.AbstractBuildType
import com.android.builder.core.BuilderConstants
import com.android.builder.errors.IssueReporter
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.BaseConfig
import com.android.builder.model.CodeShrinker
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/** DSL object to configure build types.  */
open class BuildType @Inject constructor(
    private val name: String,
    private val dslServices: DslServices
) :
    AbstractBuildType(), CoreBuildType, Serializable,
    com.android.build.api.dsl.BuildType<AnnotationProcessorOptions> {

    /**
     * Name of this build type.
     */
    override fun getName(): String {
        return name
    }

    /** Whether this build type should generate a debuggable apk.  */
    override var isDebuggable: Boolean = false
        get() = // Accessing coverage data requires a debuggable package.
            field || isTestCoverageEnabled
        set(value) {
            field = value
        }

    /**
     * Whether test coverage is enabled for this build type.
     *
     * If enabled this uses Jacoco to capture coverage and creates a report in the build
     * directory.
     *
     * The version of Jacoco can be configured with:
     * ```
     * android {
     *     jacoco {
     *         version = '0.6.2.201302030002'
     *     }
     * }
     * ```
     */
    override var isTestCoverageEnabled: Boolean = false

    /**
     * Specifies whether the plugin should generate resources for pseudolocales.
     *
     * A pseudolocale is a locale that simulates characteristics of languages that cause UI,
     * layout, and other translation-related problems when an app is localized. Pseudolocales can
     * aid your development workflow because you can test and make adjustments to your UI before you
     * finalize text for translation.
     *
     * When you set this property to `true` as shown below, the plugin generates
     * resources for the following pseudo locales and makes them available in your connected
     * device's language preferences: `en-XA` and `ar-XB`.
     *
     * ```
     * android {
     *     buildTypes {
     *         debug {
     *             pseudoLocalesEnabled true
     *         }
     *     }
     * }
     * ```
     *
     * When you build your app, the plugin includes the pseudolocale resources in your APK. If
     * you notice that your APK does not include those locale resources, make sure your build
     * configuration isn't limiting which locale resources are packaged with your APK, such as using
     * the `resConfigs` property to
     * [remove unused locale resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     *
     * To learn more, read
     * [Test Your App with Pseudolocales](https://d.android.com/guide/topics/resources/pseudolocales.html).
     */
    override var isPseudoLocalesEnabled: Boolean = false

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    override var isJniDebuggable: Boolean = false

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    override var isRenderscriptDebuggable: Boolean = false

    /** Optimization level to use by the renderscript compiler.  */
    override var renderscriptOptimLevel = 3

    /** Whether zipalign is enabled for this build type.  */
    override var isZipAlignEnabled: Boolean = true

    /**
     * Describes how code postProcessing is configured. We don't allow mixing the old and new DSLs.
     */
    enum class PostProcessingConfiguration {
        POSTPROCESSING_BLOCK, OLD_DSL
    }

    override val ndkConfig: NdkOptions = dslServices.newInstance(NdkOptions::class.java)
    override val externalNativeBuildOptions: ExternalNativeBuildOptions = dslServices.newInstance(
        ExternalNativeBuildOptions::class.java, dslServices
    )

    private val _postProcessing: PostProcessingBlock = dslServices.newInstance(
        PostProcessingBlock::class.java,
        dslServices
    )
    private var _postProcessingConfiguration: PostProcessingConfiguration? = null
    private var postProcessingDslMethodUsed: String? = null
    private var _shrinkResources = false

    /*
     * (Non javadoc): Whether png crunching should be enabled if not explicitly overridden.
     *
     * Can be removed once the AaptOptions crunch method is removed.
     */
    override var isCrunchPngsDefault = true

    // FIXME remove: b/149431538
    @Suppress("DEPRECATION")
    private val _isDefaultProperty: Property<Boolean> =
        dslServices.property(Boolean::class.java).convention(false)

    var _matchingFallbacks: ImmutableList<String>? = null

    /**
     * Specifies a sorted list of build types that the plugin should try to use when a direct
     * variant match with a local module dependency is not possible.
     *
     *
     * Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, when you build a "freeDebug" version of your app, the
     * plugin tries to match it with "freeDebug" versions of the local library modules the app
     * depends on.
     *
     *
     * However, there may be situations in which **your app includes build types that a
     * dependency does not**. For example, consider if your app includes a "stage" build type, but
     * a dependency includes only a "debug" and "release" build type. When the plugin tries to build
     * the "stage" version of your app, it won't know which version of the dependency to use, and
     * you'll see an error message similar to the following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     * project :app
     * ```
     *
     *
     * In this situation, you can use `matchingFallbacks` to specify alternative
     * matches for the app's "stage" build type, as shown below:
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     buildTypes {
     *         release {
     *             // Because the dependency already includes a "release" build type,
     *             // you don't need to provide a list of fallbacks here.
     *         }
     *         stage {
     *             // Specifies a sorted list of fallback build types that the
     *             // plugin should try to use when a dependency does not include a
     *             // "stage" build type. You may specify as many fallbacks as you
     *             // like, and the plugin selects the first build type that's
     *             // available in the dependency.
     *             matchingFallbacks = ['debug', 'qa', 'release']
     *         }
     *     }
     * }
     * ```
     *
     *
     * Note that there is no issue when a library dependency includes a build type that your app
     * does not. That's because the plugin simply never requests that build type from the
     * dependency.
     *
     * @return the names of product flavors to use, in descending priority order
     */
    var matchingFallbacks: List<String>
        get() = _matchingFallbacks ?: ImmutableList.of()
        set(value) { _matchingFallbacks = ImmutableList.copyOf(value) }

    fun setMatchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks =
            ImmutableList.copyOf(fallbacks)
    }

    fun setMatchingFallbacks(fallback: String) {
        matchingFallbacks = ImmutableList.of(fallback)
    }

    override val javaCompileOptions: JavaCompileOptions =
        dslServices.newInstance(JavaCompileOptions::class.java, dslServices)

    override fun javaCompileOptions(action: com.android.build.api.dsl.JavaCompileOptions<AnnotationProcessorOptions>.() -> Unit) {
        action.invoke(javaCompileOptions)
    }

    override val shaders: ShaderOptions = dslServices.newInstance(ShaderOptions::class.java)

    /**
     * Initialize the DSL object with the debug signingConfig. Not meant to be used from the build
     * scripts.
     */
    fun init(debugSigningConfig: SigningConfig?) {
        init()
        if (BuilderConstants.DEBUG == name) {
            assert(debugSigningConfig != null)
            setSigningConfig(debugSigningConfig)
        }
    }

    /**
     * Initialize the DSL object without the signingConfig. Not meant to be used from the build
     * scripts.
     */
    fun init() {
        if (BuilderConstants.DEBUG == name) {
            setDebuggable(true)
            isEmbedMicroApp = false
            isCrunchPngsDefault = false
        }
    }

    /** The signing configuration. e.g.: `signingConfig signingConfigs.myConfig`  */
    override var signingConfig: SigningConfig? = null

    override fun setSigningConfig(signingConfig: com.android.builder.model.SigningConfig?): com.android.builder.model.BuildType {
        this.signingConfig = signingConfig as SigningConfig?
        return this
    }

    /**
     * Whether a linked Android Wear app should be embedded in variant using this build type.
     *
     * Wear apps can be linked with the following code:
     *
     * ```
     * dependencies {
     *     freeWearApp project(:wear:free') // applies to variant using the free flavor
     *     wearApp project(':wear:base') // applies to all other variants
     * }
     * ```
     */
    override var isEmbedMicroApp: Boolean = true

    /** Whether this product flavor should be selected in Studio by default  */
    override fun getIsDefault(): Property<Boolean> {
        return _isDefaultProperty
    }

    override var isDefault: Boolean
        get() = _isDefaultProperty.get()
        set(value) { _isDefaultProperty.set(value) }

    fun setIsDefault(isDefault: Boolean) {
        this.isDefault = isDefault
    }

    override fun _initWith(that: BaseConfig) {
        super._initWith(that)
        val thatBuildType =
            that as BuildType
        ndkConfig._initWith(thatBuildType.ndkConfig)
        javaCompileOptions.annotationProcessorOptions._initWith(
            thatBuildType.javaCompileOptions.annotationProcessorOptions
        )
        _shrinkResources = thatBuildType._shrinkResources
        shaders._initWith(thatBuildType.shaders)
        externalNativeBuildOptions._initWith(thatBuildType.externalNativeBuildOptions)
        _postProcessing.initWith(that.postprocessing)
        isCrunchPngs = thatBuildType.isCrunchPngs
        isCrunchPngsDefault = thatBuildType.isCrunchPngsDefault
        matchingFallbacks = thatBuildType.matchingFallbacks
        // we don't want to dynamically link these values. We just want to copy the current value.
        isDefault = thatBuildType.isDefault
    }

    /** Override as DSL objects have no reason to be compared for equality.  */
    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    /** Override as DSL objects have no reason to be compared for equality.  */
    override fun equals(o: Any?): Boolean {
        return this === o
    }
    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.
    /**
     * Adds a new field to the generated BuildConfig class.
     *
     *
     * The field is generated as: `<type> <name> = <value>;`
     *
     *
     * This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun buildConfigField(
        type: String,
        name: String,
        value: String
    ) {
        val alreadyPresent = buildConfigFields[name]
        if (alreadyPresent != null) {
            val message = String.format(
                "BuildType(%s): buildConfigField '%s' value is being replaced: %s -> %s",
                getName(), name, alreadyPresent.value, value
            )
            dslServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                message
            )
        }
        addBuildConfigField(ClassFieldImpl(type, name, value))
    }

    /**
     * Adds a new generated resource.
     *
     *
     * This is equivalent to specifying a resource in res/values.
     *
     *
     * See [Resource Types](http://developer.android.com/guide/topics/resources/available-resources.html).
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    fun resValue(
        type: String,
        name: String,
        value: String
    ) {
        val alreadyPresent = resValues[name]
        if (alreadyPresent != null) {
            val message = String.format(
                "BuildType(%s): resValue '%s' value is being replaced: %s -> %s",
                getName(), name, alreadyPresent.value, value
            )
            dslServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                message
            )
        }
        addResValue(ClassFieldImpl(type, name, value))
    }

    override var proguardFiles: MutableList<File>
        get() = super.proguardFiles
        set(value) {
            // Override to handle the proguardFiles = ['string'] case (see PluginDslTest.testProguardFiles_*)
            setProguardFiles(value)
        }

    override fun proguardFile(proguardFile: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "proguardFile")
        proguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun proguardFiles(vararg files: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "proguardFiles")
        for (file in files) {
            proguardFile(file)
        }
        return this
    }

    /**
     * Sets the ProGuard configuration files.
     *
     *
     * There are 2 default rules files
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     *
     * They are located in the SDK. Using `getDefaultProguardFile(String filename)` will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    fun setProguardFiles(proguardFileIterable: Iterable<*>): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setProguardFiles")
        proguardFiles.clear()
        proguardFiles(
            *Iterables.toArray(
                proguardFileIterable,
                Any::class.java
            )
        )
        return this
    }

    override var testProguardFiles: MutableList<File>
        get() = super.testProguardFiles
        set(value) {
            // Override to handle the testProguardFiles = ['string'] case.
            setTestProguardFiles(value)
        }

    override fun testProguardFile(proguardFile: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "testProguardFile")
        testProguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun testProguardFiles(vararg proguardFiles: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "testProguardFiles")
        for (proguardFile in proguardFiles) {
            testProguardFile(proguardFile)
        }
        return this
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    fun setTestProguardFiles(files: Iterable<*>): BuildType {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "setTestProguardFiles"
        )
        testProguardFiles.clear()
        testProguardFiles(
            *Iterables.toArray(
                files,
                Any::class.java
            )
        )
        return this
    }

    override var consumerProguardFiles: MutableList<File>
        get() = super.consumerProguardFiles
        set(value) {
            // Override to handle the consumerProguardFiles = ['string'] case.
            setConsumerProguardFiles(value)
        }

    override fun consumerProguardFile(proguardFile: Any): BuildType {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "consumerProguardFile"
        )
        consumerProguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun consumerProguardFiles(vararg proguardFiles: Any): BuildType {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "consumerProguardFiles"
        )
        for (proguardFile in proguardFiles) {
            consumerProguardFile(proguardFile)
        }
        return this
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    fun setConsumerProguardFiles(proguardFileIterable: Iterable<*>): BuildType {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "setConsumerProguardFiles"
        )
        consumerProguardFiles.clear()
        consumerProguardFiles(
            *Iterables.toArray(
                proguardFileIterable,
                Any::class.java
            )
        )
        return this
    }

    fun ndk(action: Action<NdkOptions>) {
        action.execute(ndkConfig)
    }

    override val ndk: Ndk
        get() = ndkConfig

    override fun ndk(action: Ndk.() -> Unit) {
        action.invoke(ndk)
    }

    /**
     * Configure native build options.
     */
    fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>): ExternalNativeBuildOptions {
        action.execute(externalNativeBuildOptions)
        return externalNativeBuildOptions
    }

    /**
     * Configure shader compiler options for this build type.
     */
    fun shaders(action: Action<ShaderOptions>) {
        action.execute(shaders)
    }

    override fun shaders(action: Shaders.() -> Unit) {
        action.invoke(shaders)
    }

    /**
     * Whether removal of unused java code is enabled.
     *
     * Default is false.
     */
    override var isMinifyEnabled: Boolean = false
        get() =
            // Try to return a sensible value for the model and other Gradle plugins inspecting the DSL.
            if (_postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
                field
            } else {
                (_postProcessing.isRemoveUnusedCode ||
                        _postProcessing.isObfuscate ||
                        _postProcessing.isOptimizeCode)
            }
        set(value) {
            checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL,
                "setMinifyEnabled"
            )
            field = value
        }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    override var isShrinkResources: Boolean
        get() =
            // Try to return a sensible value for the model and other Gradle plugins inspecting the DSL.
            if (_postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
                _shrinkResources
            } else {
                _postProcessing.isRemoveUnusedResources
            }
        set(value) {
            checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL,
                "setShrinkResources"
            )
            this._shrinkResources = value
        }

    /**
     * Specifies whether to always use ProGuard for code and resource shrinking.
     *
     * By default, when you enable code shrinking by setting
     * [`minifyEnabled`](com.android.build.gradle.internal.dsl.BuildType.html#com.android.build.gradle.internal.dsl.BuildType:minifyEnabled) to `true`, the Android plugin uses R8. If you set
     * this property to `true`, the Android plugin uses ProGuard.
     *
     * To learn more, read
     * [Shrink, obfuscate, and optimize your app](https://developer.android.com/studio/build/shrink-code.html).
     */
    override var isUseProguard: Boolean?
        get() = if (_postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
            false
        } else {
            _postProcessing.codeShrinkerEnum == CodeShrinker.PROGUARD
        }
        set(_: Boolean?) {
            checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setUseProguard")
            if (dslChecksEnabled.get()) {
                dslServices.deprecationReporter
                    .reportObsoleteUsage(
                        "useProguard", DeprecationReporter.DeprecationTarget.DSL_USE_PROGUARD
                    )
            }
    }

    fun setUseProguard(useProguard: Boolean) {
        isUseProguard = useProguard
    }

    /**
     * Whether to crunch PNGs.
     *
     * Setting this property to `true` reduces of PNG resources that are not already
     * optimally compressed. However, this process increases build times.
     *
     * PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     */
    override var isCrunchPngs: Boolean? = null

    /** This DSL is incubating and subject to change.  */
    @get:Internal
    @get:Incubating
    val postprocessing: PostProcessingBlock
        get() {
            checkPostProcessingConfiguration(
                PostProcessingConfiguration.POSTPROCESSING_BLOCK, "getPostProcessing"
            )
            return _postProcessing
        }

    /** This DSL is incubating and subject to change.  */
    @Incubating
    @Internal
    fun postprocessing(action: Action<PostProcessingBlock>) {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.POSTPROCESSING_BLOCK, "postProcessing"
        )
        action.execute(_postProcessing)
    }

    /** Describes how postProcessing was configured. Not to be used from the DSL.  */
    // TODO(b/140406102): Should be internal.
    val postProcessingConfiguration: PostProcessingConfiguration
        // If the user didn't configure anything, stick to the old DSL.
        get() = _postProcessingConfiguration ?: PostProcessingConfiguration.OLD_DSL

    /**
     * Checks that the user is consistently using either the new or old DSL for configuring bytecode
     * postProcessing.
     */
    private fun checkPostProcessingConfiguration(
        used: PostProcessingConfiguration,
        methodName: String
    ) {
        if (!dslChecksEnabled.get()) {
            return
        }
        if (_postProcessingConfiguration == null) {
            _postProcessingConfiguration = used
            postProcessingDslMethodUsed = methodName
        } else if (_postProcessingConfiguration != used) {
            assert(postProcessingDslMethodUsed != null)
            val message: String
            message = when (used) {
                PostProcessingConfiguration.POSTPROCESSING_BLOCK -> // TODO: URL with more details.
                    String.format(
                        "The `postProcessing` block cannot be used with together with the `%s` method.",
                        postProcessingDslMethodUsed
                    )
                PostProcessingConfiguration.OLD_DSL -> // TODO: URL with more details.
                    String.format(
                        "The `%s` method cannot be used with together with the `postProcessing` block.",
                        methodName
                    )
                else -> throw AssertionError("Unknown value $used")
            }
            dslServices.issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                message,
                methodName
            )
        }
    }

    override fun initWith(that: com.android.builder.model.BuildType): BuildType {
        // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (that === this) {
            return this
        }
        dslChecksEnabled.set(false)
        return try {
            super.initWith(that) as BuildType
        } finally {
            dslChecksEnabled.set(true)
        }
    }

    companion object {
        private const val serialVersionUID = 1L
        /**
         * Whether the current thread should check that the both the old and new way of configuring
         * bytecode postProcessing are not used at the same time.
         *
         * The checks are disabled during [.initWith].
         */
        private val dslChecksEnabled =
            ThreadLocal.withInitial { true }
    }
}
