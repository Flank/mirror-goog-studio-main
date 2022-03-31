/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.dsl.AndroidResources
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidResourcesTest {

    private val androidResources: AndroidResources by lazy {
        val dslServices = createDslServices()
        dslServices.newDecoratedInstance(AaptOptions::class.java, dslServices)
    }

    @Test
    fun setIgnoreAssetsPattern() {
        androidResources.ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
        assertThat(androidResources.ignoreAssetsPattern).isEqualTo("!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~")
        assertThat(androidResources.ignoreAssetsPatterns).containsExactly(
                "!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~"
        ).inOrder()
        androidResources.ignoreAssetsPattern = null
        assertThat(androidResources.ignoreAssetsPattern).isNull()
        assertThat(androidResources.ignoreAssetsPatterns).isEmpty()
    }

    @Test
    fun setIgnoreAssetsPatterns() {
        androidResources.ignoreAssetsPatterns += listOf("!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~")
        assertThat(androidResources.ignoreAssetsPattern).isEqualTo("!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~")
        assertThat(androidResources.ignoreAssetsPatterns).containsExactly(
                "!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~"
        ).inOrder()
        androidResources.ignoreAssetsPatterns.clear()
        assertThat(androidResources.ignoreAssetsPattern).isNull()
        assertThat(androidResources.ignoreAssetsPatterns).isEmpty()
    }
}
