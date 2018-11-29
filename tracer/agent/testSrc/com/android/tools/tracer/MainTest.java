/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed undererr the Apache License, Version 2.0 (the "License");
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
package com.android.tools.tracer;

import com.android.tools.tracer.pkg.PkgClass;

public class MainTest {

    public static void main(String[] args) {
        System.out.println(">main");

        MainTest main = new MainTest();
        main.simple();
        main.twoReturns(true);
        main.twoReturns(false);
        main.itCatches();
        main.isAnnotated();
        main.isNotAnnotated();
        main.nestedCatches();
        try {
            main.itThrows();
        } catch (Exception e) {
        }

        try {
            main.callsAThrow();
        } catch (Exception e) {
        }

        try {
            main = new MainTest(1);
        } catch (Exception e) {
        }

        new Other();
        new PkgClass();
        Manual manual = new Manual();
        manual.customEvents();

        System.out.println("<main");
    }

    public MainTest(int withCatch) {
        System.out.println("><init>1");
        try {
            throw new Exception();
        } catch (Exception e) {

        }
        System.out.println("<<init>1");
    }

    public MainTest() {
        System.out.println("><init>");
    }

    public void simple() {
        System.out.println(">simple");
    }

    public int twoReturns(boolean b) {
        System.out.println(">twoReturns");
        if (b) {
            System.out.println("<twoReturns.1");
            return 1;
        } else {
            System.out.println("<twoReturns.2");
            return 2;
        }
    }

    public void itThrows() throws Exception {
        System.out.println(">itThrows");
        throw new Exception();
    }

    public void itCatches() {
        System.out.println("<itCatches");
        try {
            throw new Exception();
        } catch (Exception e) {
        }
        System.out.println(">itCatches");
    }

    public void callsAThrow() throws Exception {
        System.out.println(">callsAThrow");
        itThrows();
        System.out.println("<callsAThrow");
    }

    @com.android.annotations.Trace
    public void nestedCatches() {
        System.out.println(">nestedCatches");
        try {
            try {
                try {
                    System.out.println(">throw1");
                    throw new Exception();
                } catch (Exception e) {
                    System.out.println(">catch1");
                    throw e;
                } finally {
                    System.out.println(">finally1");
                }
            } catch (Exception e) {
                System.out.println(">catch2");
            } finally {
                System.out.println(">finally2");
            }
            return;
        } finally {
            System.out.println("<nestedCatches");
        }
    }

    public void isNotAnnotated() {}

    @Deprecated
    public void isAnnotated() {}
}
