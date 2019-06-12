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

import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

class FakeGradleDirectoryProperty(private var value: Directory? = null): DirectoryProperty {

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in Directory>): Provider<S> {
        TODO("not yet implemented")
    }

    override fun isPresent() = value != null

    override fun getOrElse(p0: Directory) = if (isPresent) value else p0

    override fun <S : Any?> map(p0: Transformer<out S, in Directory>?): Provider<S> {
        TODO("not yet implemented")
    }

    override fun get() = value ?: throw IllegalStateException("Value not set")

    override fun getOrNull() = value

    override fun value(value: Directory?): DirectoryProperty {
        this.value = value
        return this
    }

    override fun set(value: Directory?) {
        this.value = value
    }

    override fun set(value: Provider<out Directory>) {
        TODO("not yet implemented")
    }

    override fun set(var1: File?) {
        TODO("not yet implemented")
    }

    override fun convention(convention: Directory): DirectoryProperty {
        TODO("not yet implemented")
    }

    override fun convention(convention: Provider<out Directory>): DirectoryProperty {
        TODO("not yet implemented")
    }

    override fun finalizeValue() {
        TODO("not yet implemented")
    }

    override fun getAsFile(): Provider<File> {
        return this as Provider<File>
    }

    override fun getAsFileTree(): FileTree {
        TODO("not yet implemented")
    }

    override fun file(var1: String): Provider<RegularFile> {
        TODO("not yet implemented")
    }

    override fun file(var1: Provider<out CharSequence>): Provider<RegularFile> {
        TODO("not yet implemented")
    }

    override fun dir(var1: String): Provider<Directory> {
        TODO("not yet implemented")
    }

    override fun dir(var1: Provider<out CharSequence>): Provider<Directory> {
        TODO("not yet implemented")
    }
}