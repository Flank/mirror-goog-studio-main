/*
 * Copyright (C) 2020 The Android Open Source Project
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

@file:JvmName("FakeVariantApiScope")
package com.android.build.gradle.internal.scope

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.scope.ProjectScope
import com.android.build.gradle.internal.scope.VariantApiScope
import com.android.build.gradle.internal.scope.VariantApiScopeImpl
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.ImmutableMap
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

@JvmOverloads
fun createFakeVariantApiScope(
    issueReporter: SyncIssueReporter = FakeSyncIssueReporter(),
    deprecationReporter: DeprecationReporter = FakeDeprecationReporter(),
    objectFactory: ObjectFactory = FakeObjectFactory.factory,
    providerFactory: ProviderFactory = FakeProviderFactory.factory,
    projectOptions: ProjectOptions = ProjectOptions(ImmutableMap.of()),
    fileResolver: (Any) -> File = { File(it.toString()) }
): VariantApiScope {
    val projectScope = ProjectScope(
        issueReporter,
        deprecationReporter,
        objectFactory,
        FakeLogger(),
        providerFactory,
        ProjectFactory.project.layout,
        projectOptions,
        fileResolver)

    return VariantApiScopeImpl(projectScope)
}
