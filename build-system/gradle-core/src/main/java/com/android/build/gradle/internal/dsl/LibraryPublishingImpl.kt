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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.LibraryPublishing
import com.android.build.api.dsl.LibrarySingleVariant
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import javax.inject.Inject

abstract class LibraryPublishingImpl @Inject constructor(dslService: DslServices)
    : LibraryPublishing, AbstractPublishing<LibrarySingleVariantImpl>(dslService) {

    override fun singleVariant(variantName: String) {
        addSingleVariant(variantName, LibrarySingleVariantImpl::class.java)
    }

    override fun singleVariant(variantName: String, action: LibrarySingleVariant.() -> Unit) {
        addSingleVariantAndConfigure(variantName, LibrarySingleVariantImpl::class.java, action)
    }

    fun singleVariant(variantName: String, action: Action<LibrarySingleVariant>) {
        addSingleVariantAndConfigure(variantName, LibrarySingleVariantImpl::class.java) {
            action.execute(this) }
    }
}
