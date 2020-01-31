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
package com.android.builder.internal

import com.android.builder.model.BaseConfig
import com.android.builder.model.ClassField
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import java.io.File
import java.io.Serializable

/**
 * An object that contain a BuildConfig configuration
 */
abstract class BaseConfigImpl : Serializable,
    BaseConfig {
    /**
     * Application id suffix. It is appended to the "base" application id when calculating the final
     * application id for a variant.
     *
     *
     * In case there are product flavor dimensions specified, the final application id suffix
     * will contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on. All of these will have a dot in
     * between e.g. &quot;defaultSuffix.dimension1Suffix.dimensions2Suffix&quot;.
     */
    override var applicationIdSuffix: String? = null

    open fun applicationIdSuffix(applicationIdSuffix: String?) {
        this.applicationIdSuffix = applicationIdSuffix
    }

    /**
     * Version name suffix. It is appended to the "base" version name when calculating the final
     * version name for a variant.
     *
     *
     * In case there are product flavor dimensions specified, the final version name suffix will
     * contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on.
     */
    override var versionNameSuffix: String? = null

    open fun versionNameSuffix(versionNameSuffix: String?) {
        this.versionNameSuffix = versionNameSuffix
    }

    private val mBuildConfigFields: MutableMap<String, ClassField> =
        Maps.newTreeMap()
    private val mResValues: MutableMap<String, ClassField> =
        Maps.newTreeMap()
    private val mProguardFiles: MutableList<File> =
        Lists.newArrayList()
    private val mConsumerProguardFiles: MutableList<File> =
        Lists.newArrayList()
    private val mTestProguardFiles: MutableList<File> =
        Lists.newArrayList()
    private val mManifestPlaceholders: MutableMap<String, Any> =
        Maps.newHashMap()
    /**
     * Whether Multi-Dex is enabled for this variant.
     */
    override var multiDexEnabled: Boolean? = null
    /**
     * Text file with additional ProGuard rules to be used to determine which classes are compiled
     * into the main dex file.
     *
     * If set, rules from this file are used in combination with the default rules used by the
     * build system.
     */
    override var multiDexKeepProguard: File? = null
    /**
     * Text file that specifies additional classes that will be compiled into the main dex file.
     *
     * Classes specified in the file are appended to the main dex classes computed using
     * `aapt`.
     *
     * If set, the file should contain one class per line, in the following format:
     * `com/example/MyClass.class`
     */
    override var multiDexKeepFile: File? = null

    /**
     * @see .getApplicationIdSuffix
     */
    fun setApplicationIdSuffix(applicationIdSuffix: String?): BaseConfigImpl {
        this.applicationIdSuffix = applicationIdSuffix
        return this
    }

    /**
     * @see .getVersionNameSuffix
     */
    fun setVersionNameSuffix(versionNameSuffix: String?): BaseConfigImpl {
        this.versionNameSuffix = versionNameSuffix
        return this
    }

    /**
     * Adds a BuildConfig field.
     */
    fun addBuildConfigField(field: ClassField) {
        mBuildConfigFields[field.name] = field
    }

    /**
     * Adds a generated resource value.
     */
    fun addResValue(field: ClassField) {
        mResValues[field.name] = field
    }

    /**
     * Adds a generated resource value.
     */
    fun addResValues(values: Map<String, ClassField>) {
        mResValues.putAll(values)
    }

    /**
     * Returns the BuildConfig fields.
     */
    override var buildConfigFields: Map<String, ClassField>
        get() = mBuildConfigFields
        set(fields) {
            mBuildConfigFields.clear()
            mBuildConfigFields.putAll(fields)
        }

    /**
     * Adds BuildConfig fields.
     */
    fun addBuildConfigFields(fields: Map<String, ClassField>) {
        mBuildConfigFields.putAll(fields)
    }

    /**
     * Returns the generated resource values.
     */
    override var resValues: MutableMap<String, ClassField>
        get() = mResValues
        set(fields) {
            mResValues.clear()
            mResValues.putAll(fields)
        }

    /** {@inheritDoc}  */
    override val proguardFiles: MutableList<File>
        get() = mProguardFiles

    /**
     * ProGuard rule files to be included in the published AAR.
     *
     *
     * These proguard rule files will then be used by any application project that consumes the
     * AAR (if ProGuard is enabled).
     *
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    override val consumerProguardFiles: MutableList<File>
        get() = mConsumerProguardFiles

    override val testProguardFiles: MutableList<File>
        get() = mTestProguardFiles

    /**
     * Returns the manifest placeholders.
     *
     *
     * See
     * [Inject Build Variables into the Manifest](https://developer.android.com/studio/build/manifest-build-variables.html).
     */
    override var manifestPlaceholders: MutableMap<String, Any>
        get() = mManifestPlaceholders
        set(manifestPlaceholders) {
            mManifestPlaceholders.clear()
            mManifestPlaceholders.putAll(manifestPlaceholders)
        }

    /**
     * Adds manifest placeholders.
     *
     *
     * See 
     * [Inject Build Variables into the Manifest](https://developer.android.com/studio/build/manifest-build-variables.html).
     */
    fun addManifestPlaceholders(manifestPlaceholders: Map<String, Any>) {
        mManifestPlaceholders.putAll(manifestPlaceholders)
    }

    fun setManifestPlaceholders(manifestPlaceholders: Map<String, Any>): Void? {
        mManifestPlaceholders.clear()
        mManifestPlaceholders.putAll(manifestPlaceholders)
        return null
    }

    // Here to stop the gradle decorator breaking on the duplicate set methods.
    open fun manifestPlaceholders(manifestPlaceholders: Map<String, Any>) {
        setManifestPlaceholders(manifestPlaceholders)
    }

    open fun _initWith(that: BaseConfig) {
        buildConfigFields = that.buildConfigFields
        resValues = that.resValues as MutableMap<String, ClassField>
        applicationIdSuffix = that.applicationIdSuffix
        versionNameSuffix = that.versionNameSuffix
        mProguardFiles.clear()
        mProguardFiles.addAll(that.proguardFiles)
        mConsumerProguardFiles.clear()
        mConsumerProguardFiles.addAll(that.consumerProguardFiles)
        mTestProguardFiles.clear()
        mTestProguardFiles.addAll(that.testProguardFiles)
        mManifestPlaceholders.clear()
        mManifestPlaceholders.putAll(that.manifestPlaceholders)
        multiDexEnabled = that.multiDexEnabled
        multiDexKeepFile = that.multiDexKeepFile
        multiDexKeepProguard = that.multiDexKeepProguard
    }

    override fun toString(): String {
        return "BaseConfigImpl{" +
                "applicationIdSuffix=" + applicationIdSuffix +
                ", versionNameSuffix=" + versionNameSuffix +
                ", mBuildConfigFields=" + mBuildConfigFields +
                ", mResValues=" + mResValues +
                ", mProguardFiles=" + mProguardFiles +
                ", mConsumerProguardFiles=" + mConsumerProguardFiles +
                ", mManifestPlaceholders=" + mManifestPlaceholders +
                ", mMultiDexEnabled=" + multiDexEnabled +
                ", mMultiDexKeepFile=" + multiDexKeepFile +
                ", mMultiDexKeepProguard=" + multiDexKeepProguard +
                '}'
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
