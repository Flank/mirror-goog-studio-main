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
package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.scope.TaskConfigAction
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

/**
 * Task to compile a directory containing R.java file(s) and jar the result.
 *
 * For namespaced libraries, there will be exactly one R.java file, but for applications there will
 * be a regenerated one per dependency.
 *
 * In the future, this might not call javac at all, but it needs to be profiled first.
 */
@CacheableTask
open class CompileRClassTask : JavaCompile() {

    class ConfigAction(
            private val name: String,
            private val rClassSource: FileCollection,
            private val rClassDir: File) : TaskConfigAction<CompileRClassTask> {

        override fun getName() = name

        override fun getType() = CompileRClassTask::class.java

        override fun execute(task: CompileRClassTask) {
            task.classpath = task.project.files()
            task.source(rClassSource)
            task.destinationDir = rClassDir
        }
    }

}
