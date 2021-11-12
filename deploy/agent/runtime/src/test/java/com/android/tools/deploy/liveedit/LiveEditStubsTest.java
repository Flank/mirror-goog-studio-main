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
package com.android.tools.deploy.liveedit;

import java.lang.reflect.Method;
import org.junit.Assert;

public class LiveEditStubsTest {
    private static final String ADD_NAME = "addToCache";

    @org.junit.Test
    public void testExpectedMethods() {
        Method[] methods = LiveEditStubs.class.getDeclaredMethods();

        Method addClassMethod = null;
        int addClassMethodCount = 0;
        for (Method method : methods) {
            if (method.getName().equals(ADD_NAME)) {
                addClassMethod = method;
                addClassMethodCount++;
            }
        }

        Assert.assertNotNull("Unable to find " + ADD_NAME + "method", addClassMethod);
        Assert.assertEquals("Too many " + ADD_NAME + "method", 1, addClassMethodCount);

        Class[] parameters = addClassMethod.getParameterTypes();
        Assert.assertEquals("Bad " + ADD_NAME + "parameter", 2, parameters.length);
        Assert.assertEquals("Bad " + ADD_NAME + "parameter", parameters[0], String.class);
        Assert.assertEquals("Bad " + ADD_NAME + "parameter", parameters[1], byte[].class);
    }
}
