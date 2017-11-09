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

package com.android.build.gradle.internal.api.dsl.options

import com.android.builder.core.BuilderConstants
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.android.ide.common.signing.KeystoreHelper
import com.android.prefs.AndroidLocation
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.tooling.BuildException
import java.io.File

private val DEFAULT_PASSWORD = "android"
private val DEFAULT_ALIAS = "AndroidDebugKey"

class SigningConfigFactory(
            private val objectFactory: ObjectFactory,
            private val deprecationReporter: DeprecationReporter,
            private val issueReporter: EvalIssueReporter)
        : NamedDomainObjectFactory<SigningConfigImpl> {

    override fun create(name: String): SigningConfigImpl {
        val newInstance = objectFactory.newInstance(SigningConfigImpl::class.java,
                name, deprecationReporter, issueReporter)

        if (BuilderConstants.DEBUG == name) {
            try {
                newInstance.storeFile = File(KeystoreHelper.defaultDebugKeystoreLocation())
                newInstance.storePassword = DEFAULT_PASSWORD
                newInstance.keyAlias = DEFAULT_ALIAS
                newInstance.keyPassword = DEFAULT_PASSWORD
            } catch (e: AndroidLocation.AndroidLocationException) {
                throw BuildException("Failed to get default debug keystore location.", e)
            }

        }

        return newInstance
    }
}