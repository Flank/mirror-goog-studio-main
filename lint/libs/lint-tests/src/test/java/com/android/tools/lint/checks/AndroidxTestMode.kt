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

package com.android.tools.lint.checks

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.checks.infrastructure.SourceTransformationTestMode
import com.android.tools.lint.checks.infrastructure.TestFile
import java.io.File

internal class AndroidxTestMode : SourceTransformationTestMode(
    description = "AndroidX Test Mode",
    "AbstractCheckTest.ANDROIDX_TEST_MODE",
    "androidx"
) {
    private fun applies(file: TestFile): Boolean {
        if (!(file.targetRelativePath.endsWith(DOT_KT) || file.targetRelativePath.endsWith(DOT_JAVA))) {
            return false
        }
        val source = file.contents
        return source.contains("android.support.")
    }

    override fun applies(context: TestModeContext): Boolean {
        return context.projects.any { project ->
            project.files.any { file -> applies(file) }
        }
    }

    private fun replaceSource(file: File): Boolean {
        val source = file.readText()
        var rewritten = transformMessage(source)

        // Rename file?
        val path = file.path.replace(File.separatorChar, '/')
        val begin = path.indexOf("/android/support/")
        if (begin != -1) {
            val name = path.substring(begin + 1).substringBeforeLast('.').replace('/', '.')
            val newName = AndroidxNameUtils.getNewName(name)
            if (newName != name) {
                val newFile = File(
                    path.substring(0, begin + 1) + newName.replace('.', '/') +
                        '.' + path.substringAfterLast('.')
                )

                // Package lookup is inexact (there are many identical android support packages that map to new androidx packages),
                // so use the top level class' package if possible:
                var pkgStart = rewritten.indexOf("package ")
                if (pkgStart != -1) {
                    pkgStart += "package ".length
                    var end = rewritten.indexOf('\n', pkgStart)
                    val semi = rewritten.indexOf(';', pkgStart)
                    if (end == -1) {
                        end = rewritten.length
                    }
                    if (semi != -1 && semi < end) {
                        end = semi
                    }
                    val newPkg = newName.substringBeforeLast('.')
                    rewritten = rewritten.substring(0, pkgStart) + newPkg + rewritten.substring(end)
                }

                file.delete()
                newFile.parentFile.mkdirs()
                newFile.writeText(rewritten)
                return true
            }
        }

        if (rewritten != source) {
            file.writeText(rewritten)
            return true
        }
        return false
    }

    override fun before(context: TestModeContext): Any? {
        val projectFolders = context.projectFolders

        var unchanged = true
        projectFolders.forEach { root ->
            root.walk()
                .filter { it.isFile && (it.path.endsWith(DOT_JAVA) || it.path.endsWith(DOT_KT)) }
                .forEach {
                    if (replaceSource(it)) {
                        unchanged = false
                    }
                }
        }

        return if (unchanged) CANCEL else null
    }

    override fun transformMessage(message: String): String = migrateToAndroidX(message)

    fun migrateToAndroidX(source: String): String {
        var rewritten = source
        var i = rewritten.length
        while (true) {
            val begin = rewritten.lastIndexOf("android.support.", i)
            if (begin == -1) {
                break
            }
            var end = begin
            while (rewritten[end] == '.' || Character.isJavaIdentifierPart(rewritten[end])) {
                end++
            }
            val supportName = rewritten.substring(begin, end)
            val androidxName = AndroidxNameUtils.getNewName(supportName)
            if (androidxName != supportName) {
                rewritten = rewritten.substring(0, begin) + androidxName + rewritten.substring(end)
            }

            i = begin - 1
        }

        return rewritten
    }

    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        This test mode checks tests that
        tests referencing the old `android.support` packages also correctly
        handle the newer AndroidX names.
        """.trimIndent()
}
