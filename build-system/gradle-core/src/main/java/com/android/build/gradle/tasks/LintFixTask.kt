/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

open class LintFixTask : LintBaseTask() {

    private var variantInputMap: Map<String, LintBaseTask.VariantInputs>? = null
    private var allInputs: ConfigurableFileCollection? = null

    @InputFiles
    @Optional
    fun getAllInputs(): FileCollection? {
        return allInputs
    }

    @TaskAction
    fun lint() {
        runLint(LintFixTaskDescriptor())
    }

    private inner class LintFixTaskDescriptor : LintBaseTask.LintBaseTaskDescriptor() {

        override val variantName: String?
            get() = null

        override var autoFix: Boolean
            get() = true
            set(value) {
                super.autoFix = value
            }

        override fun getVariantInputs(variantName: String): LintBaseTask.VariantInputs? {
            return variantInputMap!![variantName]
        }
    }

    class GlobalConfigAction(
        globalScope: GlobalScope, private val variantScopes: Collection<VariantScope>
    ) : LintBaseTask.BaseConfigAction<LintFixTask>(globalScope) {

        override fun getName(): String {
            return TaskManager.LINT_FIX
        }

        override fun getType(): Class<LintFixTask> {
            return LintFixTask::class.java
        }

        override fun execute(lintTask: LintFixTask) {
            super.execute(lintTask)

            lintTask.description =
                    "Runs lint on all variants and applies any safe suggestions to the source code."
            lintTask.variantName = ""
            lintTask.group = "cleanup"

            lintTask.allInputs = globalScope.project.files()

            lintTask.variantInputMap = variantScopes.asSequence().map { variantScope ->
                val inputs = LintBaseTask.VariantInputs(variantScope)
                lintTask.allInputs!!.from(inputs.allInputs)
                inputs
            }.associateBy { it.name }
        }
    }
}
