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

import com.android.build.api.dsl.ApiVersion
import com.android.build.api.dsl.model.ProductFlavorOrVariant
import com.android.build.api.dsl.options.InstrumentationOptions
import com.android.build.api.dsl.options.VectorDrawablesOptions
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.Action

class ProductFlavorOrVariantImpl(
            issueReporter: EvalIssueReporter)
        : SealableObject(issueReporter), ProductFlavorOrVariant {

    private val _resConfigs: SealableList<String> = SealableList.new(issueReporter)

    override var applicationId: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var versionCode: Int? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var versionName: String? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var minSdkVersion: ApiVersion? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var targetSdkVersion: ApiVersion? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var maxSdkVersion: Int? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptTargetApi: Int? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptSupportModeEnabled: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptSupportModeBlasEnabled: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptNdkModeEnabled: Boolean? = null
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun setMinSdkVersion(minSdkVersion: Int) {
        this.minSdkVersion = ApiVersionImpl.of(minSdkVersion)
    }

    override fun minSdkVersion(minSdkVersion: Int) {
        this.minSdkVersion = ApiVersionImpl.of(minSdkVersion)
    }

    override fun setMinSdkVersion(minSdkVersion: String) {
        this.minSdkVersion = ApiVersionImpl.of(minSdkVersion)
    }

    override fun minSdkVersion(minSdkVersion: String) {
        this.minSdkVersion = ApiVersionImpl.of(minSdkVersion)
    }


    override fun setTargetSdkVersion(targetSdkVersion: Int) {
        this.targetSdkVersion = ApiVersionImpl.of(targetSdkVersion)
    }

    override fun targetSdkVersion(targetSdkVersion: Int) {
        this.targetSdkVersion = ApiVersionImpl.of(targetSdkVersion)
    }

    override fun setTargetSdkVersion(targetSdkVersion: String) {
        this.targetSdkVersion = ApiVersionImpl.of(targetSdkVersion)
    }

    override fun targetSdkVersion(targetSdkVersion: String) {
        this.targetSdkVersion = ApiVersionImpl.of(targetSdkVersion)
    }

    override var resConfigs: MutableList<String>
        get() = _resConfigs
        set(value) {
            _resConfigs.reset(value)
        }

    override val vectorDrawables: VectorDrawablesOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun vectorDrawables(action: Action<VectorDrawablesOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val instrumentationOptions: InstrumentationOptions
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun instrumentationOptions(action: Action<InstrumentationOptions>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    internal fun initWith(that: ProductFlavorOrVariantImpl) {
        if (checkSeal()) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}