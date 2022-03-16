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
package com.android.tools.deploy.liveedit;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class LambdaSuperTest {
    private static final String TEST_CLASS = "com/android/tools/deploy/liveedit/LambdasKt";

    static {
        LiveEditStubs.init(ProxyEvalTest.class.getClassLoader());
    }

    @BeforeClass
    public static void before() throws Exception {
        JarFile jar =
                new JarFile(
                        new File(
                                LambdasKt.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI()));
        List<JarEntry> files =
                jar.stream()
                        .filter(entry -> entry.getName().endsWith(".class"))
                        .collect(Collectors.toList());
        for (JarEntry entry : files) {
            if (entry.getName().equals(TEST_CLASS + ".class")) {
                byte[] classData = ByteStreams.toByteArray(jar.getInputStream(entry));
                LiveEditStubs.addClass(TEST_CLASS, classData, false);
            }
            if (entry.getName().startsWith(TEST_CLASS + "$")) {
                String internalName =
                        entry.getName().substring(0, entry.getName().length() - ".class".length());
                byte[] classData = ByteStreams.toByteArray(jar.getInputStream(entry));
                LiveEditStubs.addClass(internalName, classData, true);
            }
        }
    }

    @Test
    public void testSuspend() throws Exception {
        int actual = LambdasKt.testSuspend();
        Assert.assertEquals(
                actual, LiveEditStubs.stubI(TEST_CLASS, "testSuspend", "()I", new Object[2]));
    }

    @Test
    public void testRestrictedSuspend() throws Exception {
        int actual = LambdasKt.testRestrictedSuspend();
        Assert.assertEquals(
                actual,
                LiveEditStubs.stubI(TEST_CLASS, "testRestrictedSuspend", "()I", new Object[2]));
    }

    @Test
    public void testAsyncAwait() throws Exception {
        int actual = LambdasKt.testAsyncAwait();
        Assert.assertEquals(
                actual, LiveEditStubs.stubI(TEST_CLASS, "testAsyncAwait", "()I", new Object[2]));
    }

    @Test
    public void testLaunchJoin() throws Exception {
        int actual = LambdasKt.testLaunchJoin();
        Assert.assertEquals(
                actual, LiveEditStubs.stubI(TEST_CLASS, "testLaunchJoin", "()I", new Object[2]));
    }
}
