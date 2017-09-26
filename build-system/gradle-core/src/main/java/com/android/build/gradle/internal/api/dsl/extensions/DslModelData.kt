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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.model.BuildTypeFactory
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorFactory
import com.android.build.gradle.internal.api.dsl.options.SigningConfigFactory
import com.android.build.gradle.internal.api.sourcesets.AndroidSourceSetFactory
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

/**
 * Internal Store for items we want to control outside of the DSL classes.
 */
class DslModelData(
        project: Project,
        instantiator: Instantiator, //FIXME repoace with ObjectFactory in 4.2
        deprecationReporter: DeprecationReporter,
        issueReporter: EvalIssueReporter) {

    val sourceSets: NamedDomainObjectContainer<AndroidSourceSet> = project.container(
            AndroidSourceSet::class.java,
            AndroidSourceSetFactory(instantiator,
                    deprecationReporter,
                    issueReporter))

    val productFlavors: NamedDomainObjectContainer<ProductFlavor> = project.container(
            ProductFlavor::class.java,
            ProductFlavorFactory(instantiator, deprecationReporter, issueReporter))

    val buildTypes: NamedDomainObjectContainer<BuildType> = project.container(
            BuildType::class.java,
            BuildTypeFactory(instantiator, deprecationReporter, issueReporter))

    val singingConfigs: NamedDomainObjectContainer<SigningConfig> = project.container(
            SigningConfig::class.java,
            SigningConfigFactory(instantiator, deprecationReporter, issueReporter))
}