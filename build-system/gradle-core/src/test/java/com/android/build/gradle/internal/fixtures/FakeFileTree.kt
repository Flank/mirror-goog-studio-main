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

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File

class FakeFileTree(allFiles: Collection<File>) : FileTree, FakeFileCollection(allFiles) {

    override fun plus(p0: FileTree): FileTree {
        TODO("Not yet implemented")
    }

    override fun matching(p0: Closure<Any>): FileTree {
        TODO("Not yet implemented")
    }

    override fun matching(p0: Action<in PatternFilterable>): FileTree {
        TODO("Not yet implemented")
    }

    override fun matching(p0: PatternFilterable): FileTree {
        TODO("Not yet implemented")
    }

    override fun visit(p0: FileVisitor): FileTree {
        TODO("Not yet implemented")
    }

    override fun visit(p0: Closure<Any>): FileTree {
        TODO("Not yet implemented")
    }

    override fun visit(p0: Action<in FileVisitDetails>): FileTree {
        TODO("Not yet implemented")
    }
}
