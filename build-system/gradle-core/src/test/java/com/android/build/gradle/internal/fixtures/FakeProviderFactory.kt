/*
 * Copyright (C) 2019 The Android Open Source Project
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

import org.gradle.api.Action
import org.gradle.api.file.FileContents
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec
import java.util.concurrent.Callable

class FakeProviderFactory : ProviderFactory {
    override fun <T : Any?> provider(p0: Callable<out T>): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun environmentVariable(p0: String): Provider<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun environmentVariable(p0: Provider<String>): Provider<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun systemProperty(p0: String): Provider<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun systemProperty(p0: Provider<String>): Provider<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any?, P : ValueSourceParameters?> of(
        p0: Class<out ValueSource<T, P>>,
        p1: Action<in ValueSourceSpec<P>>
    ): Provider<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun fileContents(p0: RegularFile): FileContents {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun fileContents(p0: Provider<RegularFile>): FileContents {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}