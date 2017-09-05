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

package com.android.build.gradle.internal.api.dsl.model

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.TypedValue
import com.android.build.api.dsl.options.ExternalNativeBuildOptions
import com.android.build.api.dsl.options.JavaCompileOptions
import com.android.build.api.dsl.options.NdkOptions
import com.android.build.api.dsl.options.PostprocessingOptions
import com.android.build.api.dsl.options.ShaderOptions
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.gradle.internal.api.dsl.options.ExternalNativeBuildOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.JavaCompileOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.NdkOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.PostprocessingOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.ShaderOptionsImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.api.dsl.sealing.OptionalSupplier
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableMap
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import org.gradle.api.Action
import java.io.File

@Suppress("OverridingDeprecatedMember")
class BuildTypeImpl(
            private val named: String,
            private val deprecationReporter: DeprecationReporter,
            isueReporter: EvalIssueReporter)
        : SealableObject(isueReporter), BuildType {

    override fun getName() = named

    // backing properties for lists/sets/maps
    private val _buildConfigFields: SealableList<TypedValue> = SealableList.new(isueReporter)
    private val _resValues: SealableList<TypedValue> = SealableList.new(isueReporter)
    private val _matchingFallbacks: SealableList<String> = SealableList.new(isueReporter)
    private val _manifestPlaceholders: SealableMap<String, Any> = SealableMap.new(isueReporter)

    private val _ndkOptions = OptionalSupplier({ NdkOptionsImpl(isueReporter) })
    private val _javaCompileOptions = OptionalSupplier({ JavaCompileOptionsImpl(isueReporter) })
    private val _externalNativeBuildOptions = OptionalSupplier({ ExternalNativeBuildOptionsImpl(
            isueReporter) })
    private val _shaders = OptionalSupplier({ ShaderOptionsImpl(isueReporter) })
    private val _postprocessing = OptionalSupplier({ PostprocessingOptionsImpl(isueReporter)
    })

    override val ndkOptions: NdkOptions
        get() = _ndkOptions.get(isSealed())
    override val javaCompileOptions: JavaCompileOptions
        get() = _javaCompileOptions.get(isSealed())
    override val externalNativeBuildOptions: ExternalNativeBuildOptions
        get() = _externalNativeBuildOptions.get(isSealed())
    override val shaders: ShaderOptions
        get() = _shaders.get(isSealed())
    override val postprocessing: PostprocessingOptions
        get() = _postprocessing.get(isSealed())

    override fun ndkOptions(action: Action<NdkOptions>) {
        action.execute(_ndkOptions.get(isSealed()))
    }

    override fun javaCompileOptions(action: Action<JavaCompileOptions>) {
        action.execute(_javaCompileOptions.get(isSealed()))
    }

    override fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>) {
        // FIXME deduplicate?
        action.execute(_externalNativeBuildOptions.get(isSealed()))
    }

    override fun externalNativeBuildOptions(action: Action<ExternalNativeBuildOptions>) {
        // FIXME deduplicate?
        action.execute(_externalNativeBuildOptions.get(isSealed()))
    }

    override fun shaderOptions(action: Action<ShaderOptions>) {
        action.execute(_shaders.get(isSealed()))
    }

    override fun postprocessing(action: Action<PostprocessingOptions>) {
        action.execute(_postprocessing.get(isSealed()))
    }

    override var signingConfig: SigningConfig? = null
        set(value) {
            if (checkSeal()) {
                if (value !is SigningConfigImpl) {
                    issueReporter.reportError(
                            SyncIssue.TYPE_GENERIC,
                            "BuildType.signingConfig set with an object not from android.signingConfigs")
                }
                field = value
            }
        }

    override var buildConfigFields: MutableList<TypedValue>
        get() = _buildConfigFields
        set(value) {
            _buildConfigFields.reset(value)
        }

    override fun buildConfigField(type: String, name: String, value: String) {
        if (checkSeal()) {
            _buildConfigFields.add(TypedValueImpl(type, name, value))
        }
    }

    override var resValues: MutableList<TypedValue>
        get() = _resValues
        set(value) {
            _resValues.reset(value)
        }

    override fun resValue(type: String, name: String, value: String) {
        if (checkSeal()) {
            _resValues.add(TypedValueImpl(type, name, value))
        }
    }

    override var manifestPlaceholders: MutableMap<String, Any>
        get() = _manifestPlaceholders
        set(value) {
            _manifestPlaceholders.reset(value)
        }

    override var multiDexEnabled: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var multiDexKeepFile: File? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var multiDexKeepProguard: File? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var matchingFallbacks: MutableList<String>
        get() = _matchingFallbacks
        set(value) {
            _matchingFallbacks.reset(value)
        }

    override var debuggable: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var testCoverageEnabled: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var pseudoLocalesEnabled: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var jniDebuggable: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptDebuggable: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptOptimLevel: Int = 3
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var zipAlignEnabled: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var embedMicroApp: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var crunchPngs: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun initWith(that: BuildType) {
        val buildType: BuildTypeImpl = that as? BuildTypeImpl ?: throw IllegalArgumentException("BuildType not of expected type")

        if (checkSeal()) {
            _buildConfigFields.reset(buildType._buildConfigFields)
            _resValues.reset(buildType._resValues)
            _matchingFallbacks.reset(buildType._matchingFallbacks)
            _manifestPlaceholders.reset(buildType._manifestPlaceholders)

            _ndkOptions.copyFrom(buildType._ndkOptions)
            _javaCompileOptions.copyFrom(buildType._javaCompileOptions)

            _externalNativeBuildOptions.copyFrom(buildType._externalNativeBuildOptions)
            _shaders.copyFrom(buildType._shaders)
            _postprocessing.copyFrom(buildType._postprocessing)

            multiDexEnabled = that.multiDexEnabled
            multiDexKeepFile = that.multiDexKeepFile
            multiDexKeepProguard = that.multiDexKeepProguard
            debuggable = that.debuggable
            testCoverageEnabled = that.testCoverageEnabled
            pseudoLocalesEnabled = that.pseudoLocalesEnabled
            jniDebuggable = that.jniDebuggable
            renderscriptDebuggable = that.renderscriptDebuggable
            renderscriptOptimLevel = that.renderscriptOptimLevel
            zipAlignEnabled = that.zipAlignEnabled
            embedMicroApp = that.embedMicroApp
            crunchPngs = that.crunchPngs
        }
    }

    override fun seal() {
        super.seal()

        _buildConfigFields.seal()
        _resValues.seal()
        _matchingFallbacks.seal()
        _manifestPlaceholders.seal()

        _ndkOptions.instance?.seal()
        _javaCompileOptions.instance?.seal()
        _externalNativeBuildOptions.instance?.seal()
        _shaders.instance?.seal()
        _postprocessing.instance?.seal()
    }

    // --- DEPRECATED ---

    override fun isCrunchPngs(): Boolean  {
        deprecationReporter.reportDeprecatedUsage(
                "BuildType.crunchPngs",
                "BuildType.isCrunchPngs",
                DeprecationReporter.DeprecationTarget.VERSION_4_0)
        return crunchPngs
    }

    override fun setMatchingFallbacks(vararg fallbacks: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setMatchingFallbacks(fallback: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var minifyEnabled: Boolean
        get() {
            deprecationReporter.reportDeprecatedUsage(
                    "PostprocessingOptions",
                    "BuildType.minifyEnabled",
                    DeprecationReporter.DeprecationTarget.VERSION_4_0)
            return postprocessing.isObfuscate || postprocessing.isRemoveUnusedCode
        }
        set(value) {
            deprecationReporter.reportDeprecatedUsage(
                    "PostprocessingOptions",
                    "BuildType.minifyEnabled",
                    DeprecationReporter.DeprecationTarget.VERSION_4_0)
            postprocessing.isObfuscate = true
            postprocessing.isRemoveUnusedCode = true
        }

    override fun isMinifiedEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var shrinkResources: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun isShrinkResources(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val useProguard: Boolean?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun isUseProguard(): Boolean? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var crunchPngsDefault: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun isCrunchPngsDefault(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val applicationIdSuffix: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override var versionNameSuffix: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun isDebuggable(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isTestCoverageEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEmbedMicroApp(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isPseudoLocalesEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isJniDebuggable(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isRenderscriptDebuggable(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isZipAlignEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun proguardFile(proguardFile: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun proguardFiles(vararg files: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setProguardFiles(proguardFileIterable: Iterable<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun testProguardFile(proguardFile: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun testProguardFiles(vararg proguardFiles: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setTestProguardFiles(files: Iterable<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun consumerProguardFile(proguardFile: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun consumerProguardFiles(vararg proguardFiles: Any) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setConsumerProguardFiles(proguardFileIterable: Iterable<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}