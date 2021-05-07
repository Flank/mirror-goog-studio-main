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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"

class DexFileParserTest {

    private val PATH = "tools/base/profgen/profgen/testData/hello.jar"

    @Test
    fun test() {
        val apk = Apk(TestUtils.resolveWorkspacePath(PATH).toFile())
        assertThat(apk.dexes).hasSize(1)
        val dex = apk.dexes[0]

        // only one class `Hello` was defined in classes.jar
        assertThat(dex.classDefPool).hasLength(1)
        assertThat(dex.typePool[dex.classDefPool[0]]).isEqualTo("LHello;")

        val voidMethod = DexMethod("LHello;", "voidMethod", DexPrototype("V", emptyList()))
        val intMethod = DexMethod("LHello;", "method", DexPrototype("I", listOf(STRING)))
        val strLength = DexMethod(STRING, "length", DexPrototype("I", emptyList()))
        val objInit = DexMethod(OBJECT, "<init>", DexPrototype("V", emptyList()))
        val helloInit = DexMethod("LHello;", "<init>", DexPrototype("V", emptyList()))

        assertThat(dex.methodPool).isEqualTo(listOf(
            helloInit, intMethod, voidMethod, objInit, strLength
        ))
    }

    @Test
    fun testParseParams() {
        assertThat(splitParameters("ILa/B;ZILa/C;J")).isEqualTo(
            listOf("I", "La/B;", "Z", "I", "La/C;", "J")
        )
        assertThat(splitParameters("")).isEqualTo(emptyList<String>())
        assertThat(splitParameters("IJ")).isEqualTo(listOf("I", "J"))
        assertThat(splitParameters("La/C;")).isEqualTo(listOf("La/C;"))
        assertThat(splitParameters("LB;La/C;")).isEqualTo(listOf("LB;", "La/C;"))

        assertThat(splitParameters("[I[[La/B;IJ[B")).isEqualTo(
            listOf("[I", "[[La/B;", "I", "J", "[B")
        )
    }
}
