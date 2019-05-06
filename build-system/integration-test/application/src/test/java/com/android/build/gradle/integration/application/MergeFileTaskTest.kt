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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.android.build.gradle.internal.tasks.MergeFileTask
import java.io.File

class MergeFileTaskTest {
    @Rule
    @JvmField
    var tmp = TemporaryFolder()


    @Test
    fun testFilesMerged() {

        val numFilesToMerge = 3
        val inputs = mutableSetOf<File>()
        var totalTxt = ""

        for (i in 1..numFilesToMerge) {
            System.err.println(i)

            val file = tmp.newFile("inputfile$i.txt")
            val txt = "Hello world $i"
            file.writeText(txt)
            totalTxt += txt + "\n"
            inputs.add(file)
        }

        val output = tmp.root.resolve("output.txt")

        MergeFileTask.mergeFiles(inputs, output)

        assertThat(output.readText()).matches(totalTxt)
    }
}