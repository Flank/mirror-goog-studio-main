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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask

/**
 * Returns a [TestLintTask] to be used by checks that run on Studio
 * source code.
 *
 * TODO: upstream this to lint itself, for test of java-only checks.
 * TODO: Rename the file.
 */
internal fun studioLint(): TestLintTask {
    val task = object : TestLintTask() {

        /**
         * Creates a project description for the given files and
         * marks it with [ProjectDescription.Type.JAVA], which makes
         * [com.android.tools.lint.checks.infrastructure.TestLintClient.addBootClassPath]
         * use the current JVM classes.
         */
        override fun files(vararg files: TestFile): TestLintTask {
            val description = ProjectDescription(*files).type(ProjectDescription.Type.JAVA)
            super.projects(description)
            return this
        }
    }
    task.allowMissingSdk()
    return task
}
