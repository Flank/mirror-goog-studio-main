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

import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals


class ObfuscationMapTests {
    @Test
    fun testParsing() {
        val obf = ObfuscationMap(testData("mapping.txt"))

        assertMethod(
            obf,
            "Landroidx/compose/foundation/Background;->draw(Landroidx/compose/ui/graphics/drawscope/ContentDrawScope;)V",
            "Le1/a;->n(Lu0/d;)V"
        )
    }

    @Test
    fun testSimpleMapping() {
        val mappingTxt = """
            com.google.Foo -> a.b:
                com.google.Bar field -> c
                1:1:void com.google.Foo.inlinedMethod():0:0 -> d
                1:1:com.google.Bar someMethod():0 -> d
            com.google.Bar -> c:
                int field -> q
                1:1:com.google.Bar someMethod2(boolean,com.google.Bar):0 -> d
        """.trimIndent()
        val obf = mappingTxt.byteInputStream(StandardCharsets.UTF_8).reader().use { ObfuscationMap(it) }
        assertType(obf, "Lcom/google/not/Present;", "Lcom/google/not/Present;")
        assertType(obf, "Lcom/google/Bar;", "Lc;")
        assertType(obf, "Lcom/google/Foo;", "La/b;")
        assertMethod(obf, "Lcom/google/Foo;->someMethod()Lcom/google/Bar;", "La/b;->d()Lc;")
        assertMethod(obf, "Lcom/google/Bar;->someMethod2(ZLcom/google/Bar;)Lcom/google/Bar;", "Lc;->d(ZLc;)Lc;")
    }

    fun assertMethod(obf: ObfuscationMap, original: String, obfuscated: String) {
        val origMethod = parseDexMethod(original)
        val obfMethod = parseDexMethod(obfuscated)
        val deobfuscated = obf.deobfuscate(obfMethod)
        assertEquals(
            deobfuscated,
            origMethod,
            "Expected '$origMethod' but found '$deobfuscated"
        )
    }

    fun assertType(obf: ObfuscationMap, original: String, obfuscated: String) {
        val deobfuscated = obf.deobfuscate(obfuscated)
        assertEquals(
            deobfuscated,
            original,
            "Expected '$original' but found '$deobfuscated"
        )
    }
}