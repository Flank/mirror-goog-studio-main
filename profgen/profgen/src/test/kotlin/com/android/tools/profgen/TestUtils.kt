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

package com.android.tools.profgen

import com.android.testutils.TestUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path
import kotlin.test.fail

fun testData(relativePath: String): File {
    return testDataPath(relativePath).toFile()
}

fun testDataPath(relativePath: String): Path {
    return workspacePath("tools/base/profgen/profgen/testData").resolve(relativePath)
}

fun workspacePath(relativePath: String): Path {
    return TestUtils.resolveWorkspacePath(relativePath)
}

fun HumanReadableProfile(
    vararg strings: String,
    onError: (Int, Int, String) -> Unit
) : HumanReadableProfile? {
    val text = strings.joinToString("\n")
    return HumanReadableProfile(ByteArrayInputStream(text.toByteArray()).reader(), onError)
}
