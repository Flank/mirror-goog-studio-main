/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import java.util.Optional

data class FakeResolvedVariantResult(
    private val owner: ComponentIdentifier? = null,
    private val attributes: AttributeContainer? = null,
    private val displayName: String? = null,
    private val capabilities: MutableList<Capability>? = null,
    private val externalVariant: Optional<ResolvedVariantResult> = Optional.empty()
): ResolvedVariantResult {

    override fun getOwner(): ComponentIdentifier = owner ?: error("value not set")
    override fun getAttributes(): AttributeContainer = attributes ?: error("value not set")
    override fun getDisplayName(): String = displayName ?: error("value not set")
    override fun getCapabilities(): MutableList<Capability> =
        capabilities ?: error("value not set")
    override fun getExternalVariant(): Optional<ResolvedVariantResult> = externalVariant
}
