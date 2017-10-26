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

package com.android.build.gradle.internal.fixtures

import com.android.build.gradle.internal.api.sourcesets.FilesProvider
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import java.io.File

class FakeFilesProvider : FilesProvider {

    override fun file(file: Any) = File(file.toString())

    override fun files(vararg files: Any): ConfigurableFileCollection {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun fileTree(args: Map<String, *>): ConfigurableFileTree {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}