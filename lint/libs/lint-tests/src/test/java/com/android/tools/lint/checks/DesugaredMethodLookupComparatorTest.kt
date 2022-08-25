/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DesugaredMethodLookupComparatorTest(
    val owner: String, val name: String, val desc: String, val combined: String, val result: Int
) {
    companion object {
        val lookup = DesugaredMethodLookup(arrayOf())

        @JvmStatic
        @Parameterized.Parameters
        fun inputData(): Collection<Array<Any>> {
            return listOf(
                arrayOf(
                    "java.util.Collection", "stream", "()",
                    "java/util/Collection#stream()Ljava/util/stream/Stream;",
                    0
                ), arrayOf(
                    "javax/util/Comparator", "thenComparing", "(Ljava/util/function/Function;Ljava/util/Comparator;)",
                    "java/util/Comparator#thenComparing(Ljava/util/function/Function;)Ljava/util/Comparator;",
                    -1
                ), arrayOf(
                    "java/util/Collections", "spliterator", "()",
                    "java/util/Collection#spliterator()Ljava/util/Spliterator;",
                    -1
                ), arrayOf(
                    "java/util/List", "of", "(Ljava/lang/Object;)",
                    "java/util/List#of()Ljava/util/List;",
                    -1
                ), arrayOf(
                    "java/util/Collection", "spliterators", "()",
                    "java/util/Collection#spliterator()Ljava/util/Spliterator;",
                    -1
                ), arrayOf(
                    "java.lang.Long", "toUnsignedString", "(JI)",
                    "java/lang/Long#toUnsignedString(J)Ljava/lang/String;",
                    -1
                ), arrayOf(
                    "java/util/Comparator", "thenComparing", "(Ljava/util/function/Function;Ljava/util/Comparator;)",
                    "javax/util/Comparator#thenComparing(Ljava/util/function/Function;)Ljava/util/Comparator;",
                    1
                ), arrayOf(
                    "java/util/Collection", "spliterator", "()",
                    "java/util/Collections#spliterator()Ljava/util/Spliterator;",
                    1
                ), arrayOf(
                    "java/util/List", "of", "()",
                    "java/util/List#of(Ljava/lang/Object;)Ljava/util/List;",
                    1
                ), arrayOf(
                    "java/util/Collection", "spliterator", "()",
                    "java/util/Collection#spliterators()Ljava/util/Spliterator;",
                    1
                )
            )
        }
    }

    @Test
    fun testComparatorEquals() {
        assertThat(Integer.signum(lookup.compare(owner, name, desc, combined)))
            .named("""DesugaredMethodLookupTest.lookup.compare("$owner", "$name", "$desc", "$combined")""")
            .isEqualTo(result)
    }
}
