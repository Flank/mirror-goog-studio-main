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
package com.example.test;

import com.example.app.AppInterface;
import com.example.app.JavaLibInterfaceImpl;
import com.example.app.LibInterfaceImpl;
import com.example.app.testFixtures.AppInterfaceTester;
import com.example.javalib.testFixtures.JavaLibInterfaceTester;
import com.example.lib.testFixtures.LibInterfaceTester;
import org.junit.Test;

public class UnitTest {

    @Test
    public void testAndroidLibTestFixturesDependency() {
        String name = "test";
        LibInterfaceTester tester = new LibInterfaceTester(name);
        tester.test(new LibInterfaceImpl(name));
    }

    @Test
    public void testJavaLibTestFixturesDependency() {
        int id = 1234;
        JavaLibInterfaceTester tester = new JavaLibInterfaceTester(id);
        tester.test(new JavaLibInterfaceImpl(id));
    }

    @Test
    public void testLocalAppTestFixturesDependency() {
        AppInterfaceTester tester = new AppInterfaceTester("test");
        tester.test(new AppInterfaceImpl());
    }

    private class AppInterfaceImpl implements AppInterface {
        @Override
        public String getName() {
            return "test";
        }
    }
}
