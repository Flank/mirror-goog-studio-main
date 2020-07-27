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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class IteratorDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return IteratorDetector()
    }

    fun testLinkedHashMap() {
        lint().files(
            java(
                """
                package test.pkg;

                import android.os.Build;
                import android.support.annotation.RequiresApi;

                import java.util.Collection;
                import java.util.HashMap;
                import java.util.LinkedHashMap;
                import java.util.Map;
                import java.util.Map.Entry;
                import java.util.Set;
                import java.util.Spliterator;
                import java.util.Spliterators;
                import java.util.stream.Stream;
                import java.util.stream.StreamSupport;

                public class LinkedHashmapTest {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    public void test() {
                        Map<String, String> map1 = new HashMap<>();
                        Set<String> c1a = map1.keySet();
                        Collection<String> c1b = map1.values();
                        Set<Entry<String, String>> c1c = map1.entrySet();
                        Spliterator<String> keys1a = c1a.spliterator();
                        Spliterator<String> keys1b = c1b.spliterator();
                        Spliterator<Entry<String, String>> keys1c = c1c.spliterator(); // OK (not a LinkedHashMap)
                        Spliterator<String> keys1 = Spliterators.spliterator(c1a, c1a.spliterator().characteristics());// OK

                        Map<String, String> map2 = new LinkedHashMap<>();
                        Set<String> c2a = map2.keySet();
                        Collection<String> c2b = map2.values();
                        Set<Entry<String, String>> c2c = map2.entrySet();

                        Spliterator<String> keys2a = c2a.spliterator(); // Warn
                        Spliterator<String> keys2b = c2b.spliterator(); // Warn
                        Spliterator<Entry<String, String>> keys2c = c2c.spliterator(); // Warn
                        Spliterator<String> keys2 = Spliterators.spliterator(c2a, c2a.spliterator().characteristics());// OK

                        Stream<String> stream1 = c2a.stream(); // Warn
                        StreamSupport.stream(c2a.spliterator(), false); // Warn

                        Spliterators.spliterator(c2a, c2a.spliterator().characteristics()); // OK
                        StreamSupport.stream(keys2, false); // OK
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.os.Build
                import android.support.annotation.RequiresApi
                import java.util.*
                import java.util.stream.StreamSupport

                class LinkedHashmapTest {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    fun test() {
                        val map1 = HashMap<String, String>()
                        val c1a = map1.keys
                        val c1b = map1.values
                        val c1c = map1.entries
                        val keys1a = c1a.spliterator()
                        val keys1b = c1b.spliterator()
                        val keys1c = c1c.spliterator() // OK (not a LinkedHashMap)
                        val keys1 = Spliterators.spliterator(c1a, c1a.spliterator().characteristics())// OK

                        val map2 = LinkedHashMap<String, String>()
                        val c2a = map2.keys
                        val c2b = map2.values
                        val c2c = map2.entries

                        val keys2a = c2a.spliterator() // Warn
                        val keys2b = c2b.spliterator() // Warn
                        val keys2c = c2c.spliterator() // Warn
                        val keys2 = Spliterators.spliterator(c2a, c2a.spliterator().characteristics())// OK

                        val stream1 = c2a.stream() // Warn
                        StreamSupport.stream(c2a.spliterator(), false) // Warn

                        Spliterators.spliterator(c2a, c2a.spliterator().characteristics()) // OK
                        StreamSupport.stream(keys2, false) // OK
                    }
                }
                """
            )
        ).run().expect(
            """
            src/test/pkg/LinkedHashmapTest.java:34: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2a, c2a.spliterator().characteristics()) [BrokenIterator]
                    Spliterator<String> keys2a = c2a.spliterator(); // Warn
                                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.java:35: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2b, c2b.spliterator().characteristics()) [BrokenIterator]
                    Spliterator<String> keys2b = c2b.spliterator(); // Warn
                                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.java:36: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2c, c2c.spliterator().characteristics()) [BrokenIterator]
                    Spliterator<Entry<String, String>> keys2c = c2c.spliterator(); // Warn
                                                                ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.java:39: Warning: LinkedHashMap#stream was broken in API 24 and 25. Workaround: Use java.util.stream.StreamSupport.stream(spliterator, false) [BrokenIterator]
                    Stream<String> stream1 = c2a.stream(); // Warn
                                             ~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.java:40: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2a, c2a.spliterator().characteristics()) [BrokenIterator]
                    StreamSupport.stream(c2a.spliterator(), false); // Warn
                                         ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.kt:26: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2a, c2a.spliterator().characteristics()) [BrokenIterator]
                                    val keys2a = c2a.spliterator() // Warn
                                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.kt:27: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2b, c2b.spliterator().characteristics()) [BrokenIterator]
                                    val keys2b = c2b.spliterator() // Warn
                                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.kt:28: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2c, c2c.spliterator().characteristics()) [BrokenIterator]
                                    val keys2c = c2c.spliterator() // Warn
                                                 ~~~~~~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.kt:31: Warning: LinkedHashMap#stream was broken in API 24 and 25. Workaround: Use java.util.stream.StreamSupport.stream(spliterator, false) [BrokenIterator]
                                    val stream1 = c2a.stream() // Warn
                                                  ~~~~~~~~~~~~
            src/test/pkg/LinkedHashmapTest.kt:32: Warning: LinkedHashMap#spliterator was broken in API 24 and 25. Workaround: Use java.util.Spliterators.spliterator(c2a, c2a.spliterator().characteristics()) [BrokenIterator]
                                    StreamSupport.stream(c2a.spliterator(), false) // Warn
                                                         ~~~~~~~~~~~~~~~~~
            0 errors, 10 warnings
            """
        )
    }

    fun testVector() {
        lint().files(
            java(
                """
                package test.pkg;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.ListIterator;
                import java.util.Vector;

                public class VectorTest {
                    public void testListIterator() {
                        ListIterator<String> ok1 = new Vector<String>().listIterator(); // no add
                        ListIterator<String> ok2 = new ArrayList<String>().listIterator();
                        ok2.add("test");

                        ListIterator<String> error1 = new Vector<String>().listIterator();
                        error1.add("test");
                        List<String> vectors = new Vector<>();
                        ListIterator<String> error2 = vectors.listIterator();
                        error2.add("test");
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import java.util.ArrayList
                import java.util.Vector

                class VectorTest2 {
                    fun testListIterator() {
                        val ok1 = Vector<String>().listIterator() // no add
                        val ok2 = ArrayList<String>().listIterator()
                        ok2.add("test")

                        val error1 = Vector<String>().listIterator()
                        error1.add("test")
                        val vectors = Vector<String>()
                        val error2 = vectors.listIterator()
                        error2.add("test")
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/VectorTest.java:15: Warning: Vector#listIterator was broken in API 24 and 25; it can return hasNext()=false before the last element. Consider switching to ArrayList with synchronization if you need it. [BrokenIterator]
                    error1.add("test");
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/VectorTest.java:18: Warning: Vector#listIterator was broken in API 24 and 25; it can return hasNext()=false before the last element. Consider switching to ArrayList with synchronization if you need it. [BrokenIterator]
                    error2.add("test");
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/VectorTest2.kt:13: Warning: Vector#listIterator was broken in API 24 and 25; it can return hasNext()=false before the last element. Consider switching to ArrayList with synchronization if you need it. [BrokenIterator]
                    error1.add("test")
                    ~~~~~~~~~~~~~~~~~~
            src/test/pkg/VectorTest2.kt:16: Warning: Vector#listIterator was broken in API 24 and 25; it can return hasNext()=false before the last element. Consider switching to ArrayList with synchronization if you need it. [BrokenIterator]
                    error2.add("test")
                    ~~~~~~~~~~~~~~~~~~
            0 errors, 4 warnings
            """
        )
    }
}
