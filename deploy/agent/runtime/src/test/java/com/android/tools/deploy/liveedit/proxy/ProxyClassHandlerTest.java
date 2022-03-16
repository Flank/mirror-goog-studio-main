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

import java.lang.reflect.Modifier;
import org.junit.Assert;
import org.junit.Test;

public class ProxyClassHandlerTest {

    // ProxyClassHandler and ProxyClassHandler.invokeMethod() are accessed cross-classloader and
    // must be public.
    @Test
    public void ensurePublic() throws Exception {
        Assert.assertTrue(Modifier.isPublic(ProxyClassHandler.class.getModifiers()));
        Assert.assertTrue(
                Modifier.isPublic(
                        ProxyClassHandler.class
                                .getMethod(
                                        "invokeMethod",
                                        Object.class,
                                        String.class,
                                        String.class,
                                        new Object[0].getClass())
                                .getModifiers()));
    }
}
