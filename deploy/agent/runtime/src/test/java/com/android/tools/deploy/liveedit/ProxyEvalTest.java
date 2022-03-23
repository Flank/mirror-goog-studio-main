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

import static com.android.tools.deploy.liveedit.ProxyTestClasses.Driver;
import static com.android.tools.deploy.liveedit.ProxyTestClasses.ModifyStatic;
import static com.android.tools.deploy.liveedit.ProxyTestClasses.Pythagorean;
import static com.android.tools.deploy.liveedit.Utils.buildClass;

import java.util.HashMap;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ProxyEvalTest {
    static {
        LiveEditStubs.init(ProxyEvalTest.class.getClassLoader());
    }

    private static HashMap<String, byte[]> newClassBytes;

    @BeforeClass
    public static void before() throws Exception {
        newClassBytes = new HashMap<>();
        newClassBytes.put(
                "com/android/tools/deploy/liveedit/ProxyTestClasses$Pythagorean",
                buildClass(ProxyTestClasses2.Pythagorean.class));
        newClassBytes.put(
                "com/android/tools/deploy/liveedit/ProxyTestClasses$Driver",
                buildClass(ProxyTestClasses2.Driver.class));
        newClassBytes.put(
                "com/android/tools/deploy/liveedit/ProxyTestClasses$Function2",
                buildClass(ProxyTestClasses2.Function2.class));
        newClassBytes.put(
                "com/android/tools/deploy/liveedit/ProxyTestClasses$ModifyStatic",
                buildClass(ProxyTestClasses2.ModifyStatic.class));
        newClassBytes.put(
                "com/android/tools/deploy/liveedit/ProxyTestClasses$AddedMethods",
                buildClass(ProxyTestClasses2.AddedMethods.class));
        newClassBytes.put(
                "com/android/tools/deploy/liveedit/ProxyTestClasses$Function3",
                buildClass(ProxyTestClasses2.Function3.class));
    }

    // Test proxy instance method invocations from LE and non-LE methods.
    @Test
    public void testProxyCalls() throws Exception {
        String driverName = "com/android/tools/deploy/liveedit/ProxyTestClasses$Driver";
        String implName = "com/android/tools/deploy/liveedit/ProxyTestClasses$Pythagorean";

        LiveEditStubs.addClass(driverName, buildClass(Driver.class), false);
        LiveEditStubs.addClass(implName, buildClass(Pythagorean.class), true);

        // Interpreted result w/ proxies should be the same as non-interpreted result.
        long actual = new ProxyTestClasses.Driver().liveEditedMethod(6L, 8L);
        Assert.assertEquals(
                actual,
                LiveEditStubs.stubJ(
                        driverName,
                        "liveEditedMethod",
                        "(JJ)J",
                        new Object[] {null, null, 6L, 8L}));
    }

    // Test using a proxy class to change interface.
    @Test
    public void testProxyChangeInterface() throws Exception {
        String driverName = "com/android/tools/deploy/liveedit/ProxyTestClasses$Driver";
        String implName = "com/android/tools/deploy/liveedit/ProxyTestClasses$Pythagorean";

        // Change Pythagorean from Function2 to Function3, and change Pythagorean.apply(Long, Long)
        // to apply(Long, Long, String)
        LiveEditStubs.addClass(driverName, getNewClassData(driverName), false);
        LiveEditStubs.addClass(implName, getNewClassData(implName), true);

        Assert.assertEquals(
                16,
                LiveEditStubs.stubJ(
                        driverName,
                        "liveEditedMethod",
                        "(JJ)J",
                        new Object[] {null, null, 6L, 8L}));

        // Change back to original signature.
        LiveEditStubs.addClass(driverName, buildClass(Driver.class), false);
        LiveEditStubs.addClass(implName, buildClass(Pythagorean.class), true);

        long actual = new ProxyTestClasses.Driver().liveEditedMethod(6L, 8L);
        Assert.assertEquals(
                actual,
                LiveEditStubs.stubJ(
                        driverName,
                        "liveEditedMethod",
                        "(JJ)J",
                        new Object[] {null, null, 6L, 8L}));
    }

    // Test that we can add new statics to non-proxied LiveEdit classes.
    @Test
    public void testAddStatic() throws Exception {
        String driverName = "com/android/tools/deploy/liveedit/ProxyTestClasses$AddedMethods";

        Assert.assertEquals(0, ProxyTestClasses.AddedMethods.callsAddedMethod());

        // Add new static to AddedMethods and update AddedMethods.callsAddedMethod() to call it.
        LiveEditStubs.addClass(driverName, getNewClassData(driverName), false);

        Assert.assertEquals(
                1, LiveEditStubs.stubI(driverName, "callsAddedMethod", "()I", new Object[2]));
    }

    // Test that modifications to static methods of proxied classes are interpreted.
    @Test
    public void testProxiedStatic() throws Exception {
        String driverName = "com/android/tools/deploy/liveedit/ProxyTestClasses$Driver";
        String implName = "com/android/tools/deploy/liveedit/ProxyTestClasses$ModifyStatic";

        LiveEditStubs.addClass(driverName, buildClass(Driver.class), false);
        LiveEditStubs.addClass(implName, buildClass(ModifyStatic.class), true);

        Assert.assertEquals(
                0, LiveEditStubs.stubI(driverName, "liveEditedMethod", "()I", new Object[2]));

        LiveEditStubs.addClass(driverName, getNewClassData(driverName), false);
        LiveEditStubs.addClass(implName, getNewClassData(implName), true);

        Assert.assertEquals(
                5, LiveEditStubs.stubI(driverName, "liveEditedMethod", "()I", new Object[2]));
    }

    private byte[] getNewClassData(String name) throws Exception {
        return newClassBytes.get(name);
    }
}
