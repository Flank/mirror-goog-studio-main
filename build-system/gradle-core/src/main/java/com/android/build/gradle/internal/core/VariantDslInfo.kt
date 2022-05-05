/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.core.dsl.AarProducingComponentDslInfo
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.ApkProducingComponentDslInfo
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.DynamicFeatureVariantDslInfo
import com.android.build.gradle.internal.core.dsl.InstrumentedTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.core.dsl.PublishableVariantDslInfo
import com.android.build.gradle.internal.core.dsl.TestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo
import com.android.build.gradle.internal.core.dsl.TestedComponentDslInfo
import com.android.build.gradle.internal.core.dsl.UnitTestComponentDslInfo

@Deprecated("Do not use or add any new properties here, use the fine-grained interfaces")
interface VariantDslInfo: AarProducingComponentDslInfo,
    AndroidTestComponentDslInfo,
    ApkProducingComponentDslInfo,
    ApplicationVariantDslInfo,
    ComponentDslInfo,
    DynamicFeatureVariantDslInfo,
    InstrumentedTestComponentDslInfo,
    LibraryVariantDslInfo,
    PublishableVariantDslInfo,
    TestComponentDslInfo,
    TestedComponentDslInfo,
    TestFixturesComponentDslInfo,
    TestProjectVariantDslInfo,
    UnitTestComponentDslInfo,
    com.android.build.gradle.internal.core.dsl.VariantDslInfo
