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
                4:1:com.google.Bar arrayArg(int,com.google.Foo[]):0 -> b
                5:1:com.google.Bar array2DArg(int,com.google.Foo[][]):0 -> c
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
        assertMethod(obf, "Lcom/google/Foo;->arrayArg(I[Lcom/google/Foo;)Lcom/google/Bar;", "La/b;->b(I[La/b;)Lc;")
        assertMethod(obf, "Lcom/google/Foo;->array2DArg(I[[Lcom/google/Foo;)Lcom/google/Bar;", "La/b;->c(I[[La/b;)Lc;")
    }

    @Test
    fun testMergedClasses() {
        val mappingTxt = """
            com.google.Foo -> a.b:
                1:1:com.google.Bar someMethod1():0 -> d
            com.google.Bar -> a.b:
                1:1:com.google.Bar someMethod2(boolean,com.google.Bar):0 -> d
        """.trimIndent()
        val obf = mappingTxt.byteInputStream(StandardCharsets.UTF_8).reader().use { ObfuscationMap(it) }
        assertType(obf, "Lcom/google/not/Present;", "Lcom/google/not/Present;")
        assertTypes(obf, listOf("Lcom/google/Bar;", "Lcom/google/Foo;"), "La/b;")
        assertMethod(obf, "Lcom/google/Foo;->someMethod1()Lcom/google/Bar;", "La/b;->d()La/b;")
        assertMethod(obf, "Lcom/google/Bar;->someMethod2(ZLcom/google/Bar;)Lcom/google/Bar;", "La/b;->d(ZLa/b;)La/b;")
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
        assertEquals(1, deobfuscated.size, "expected only one mapping")
        assertEquals(
            deobfuscated[0],
            original,
            "Expected '$original' but found '$deobfuscated"
        )
    }

    fun assertTypes(obf: ObfuscationMap, originals: List<String>, obfuscated: String) {
        val deobfuscated = obf.deobfuscate(obfuscated).toSet()
        assertEquals(
            originals.toSet(),
            deobfuscated,
            "Expected '$originals' but found '$deobfuscated"
        )
    }
}
