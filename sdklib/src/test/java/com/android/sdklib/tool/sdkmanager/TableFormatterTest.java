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
package com.android.sdklib.tool.sdkmanager;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;

/** Tests for {@link com.android.sdklib.tool.sdkmanager.TableFormatter} */
public class TableFormatterTest {

    @Test
    public void basic() {
        TableFormatter<MyClass> formatter = new TableFormatter<>();
        formatter.addColumn("int", c -> Integer.toString(c.myInt), 99, 99);
        formatter.addColumn("string", c -> c.myString, 99, 99);
        formatter.addColumn("double", c -> String.format("%.2f", c.myDouble), 99, 99);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        formatter.print(
                ImmutableList.of(
                        new MyClass(1, "foo", 1.234),
                        new MyClass(123, "something something", 2.3),
                        new MyClass(123456, "a", 4.56)),
                new PrintStream(baos));
        assertEquals("  int     | string              | double \n" +
                     "  ------- | -------             | -------\n" +
                     "  1       | foo                 | 1.23   \n" +
                     "  123     | something something | 2.30   \n" +
                     "  123456  | a                   | 4.56   \n",
                baos.toString());
    }

    @Test
    public void truncatedValues() {
        TableFormatter<MyClass> formatter = new TableFormatter<>();
        formatter.addColumn("int", c -> Integer.toString(c.myInt), 2, 3);
        formatter.addColumn("string", c -> c.myString, 3, 4);
        formatter.addColumn("double", c -> String.format("%.2f", c.myDouble), 1, 1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        formatter.print(
                ImmutableList.of(
                        new MyClass(1, "foo", 1.234),
                        new MyClass(123, "something something", 2.3),
                        new MyClass(123456789, "a", 1234.56)),
                new PrintStream(baos));
        assertEquals("  int      | string     | double \n" +
                     "  -------  | -------    | -------\n" +
                     "  1        | foo        | 1.23   \n" +
                     "  123      | som...hing | 2.30   \n" +
                     "  12...789 | a          | 1...6  \n",
                baos.toString());
    }

    @Test
    public void shortValues() {
        TableFormatter<MyClass> formatter = new TableFormatter<>();
        formatter.addColumn("a", c -> Integer.toString(c.myInt), 99, 99);
        formatter.addColumn("b", c -> c.myString, 99, 99);
        formatter.addColumn("c", c -> String.format("%.2f", c.myDouble), 99, 99);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        formatter.print(ImmutableList.of(new MyClass(1, "a", 1)), new PrintStream(baos));
        assertEquals("  a       | b       | c      \n" +
                     "  ------- | ------- | -------\n" +
                     "  1       | a       | 1.00   \n",
                baos.toString());
    }

    private static class MyClass {

        int myInt;
        String myString;
        double myDouble;

        public MyClass(int i, String s, double d) {
            myInt = i;
            myString = s;
            myDouble = d;
        }
    }
}
