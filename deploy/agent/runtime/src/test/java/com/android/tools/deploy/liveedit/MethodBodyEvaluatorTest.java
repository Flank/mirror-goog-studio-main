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

import com.android.tools.idea.util.StudioPathManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Assert;

public class MethodBodyEvaluatorTest {

    @org.junit.Test
    public void testSimpleReturn() throws Exception {
        byte[] classInput = buildClass(TestTarget.class);
        TestTarget owner = new TestTarget();

        // return 5
        Object result =
                new MethodBodyEvaluator(classInput, "returnFive")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        Integer intResult = (Integer) result;
        Assert.assertEquals(owner.returnFive(), intResult.intValue());

        // return Happiness
        result =
                new MethodBodyEvaluator(classInput, "returnHappiness")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        String stringResult = (String) result;
        Assert.assertEquals(owner.returnHappiness(), stringResult.toString());

        result =
                new MethodBodyEvaluator(classInput, "returnField")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        stringResult = (String) result;
        Assert.assertEquals(owner.returnField(), stringResult.toString());

        result =
                new MethodBodyEvaluator(classInput, "returnPlusOne")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {1});
        intResult = (Integer) result;
        Assert.assertEquals(owner.returnPlusOne(1), intResult.intValue());

        result =
                new MethodBodyEvaluator(classInput, "returnSeventeen")
                        .eval(owner, TestTarget.class.getTypeName(), new Object[] {});
        intResult = (Integer) result;
        Assert.assertEquals(owner.returnSeventeen(), intResult.intValue());
    }

    /**
     * Extract the bytecode data from a class file from a class. If this is run from intellij, it
     * searches for .class file in the idea out director. If this is run from a jar file, it
     * extracts the class file from the jar.
     */
    private static byte[] buildClass(Class clazz) throws IOException {
        InputStream in = null;
        if (StudioPathManager.isRunningFromSources()) {
            String loc =
                    StudioPathManager.getSourcesRoot()
                            + "/tools/adt/idea/out/test/android.sdktools.deployer.deployer-runtime-support/"
                            + clazz.getName().replaceAll("\\.", "/")
                            + ".class";
            File file = new File(loc);
            if (file.exists()) {
                in = new FileInputStream(file);
            } else {
                in = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");
            }
        } else {
            in = clazz.getResourceAsStream(clazz.getSimpleName() + ".class");
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = in.read(buffer); len != -1; len = in.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }
}
