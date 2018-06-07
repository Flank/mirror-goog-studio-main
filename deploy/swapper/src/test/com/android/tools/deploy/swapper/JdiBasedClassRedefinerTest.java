/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deploy.swapper;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.sun.jdi.ReferenceType;
import java.net.ServerSocket;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A list of tests for {@link JdiBasedClassRedefiner}.
 *
 * <p>It uses the {@link FakeAndroidDriver} to invoke ART not to test ART's behaviors, instead, we
 * use it to verify that the way we redefine classes in simple app is what the developer expected.
 */
public class JdiBasedClassRedefinerTest {
    private FakeAndroidDriver android;

    public static final String LOCAL_HOST = "127.0.0.1";
    public int debuggerPort;

    @Before
    public void setUp() throws Exception {
        debuggerPort = findNextAvailablePort();
        android = new FakeAndroidDriver(LOCAL_HOST, debuggerPort);
        android.start();
    }

    @Test
    public void testGetReferenceTypeByName() throws Exception {
        JdiBasedClassRedefiner redefiner =
                new JdiBasedClassRedefiner(
                        JdiBasedClassRedefiner.attach("localhost", debuggerPort));
        ReferenceType objRef = redefiner.getReferenceTypeByName("java.lang.Object").get(0);
        Assert.assertEquals("Ljava/lang/Object;", objRef.signature());
    }

    /**
     * Find a port that we can open and then close it. We can then instruct that debugger to listen
     * to it.
     */
    private static int findNextAvailablePort() {
        int port;
        try {
            ServerSocket socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();
        } catch (Exception e) {
            port = -1;
        }
        return port;
    }
}
