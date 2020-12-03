/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.profiler.transform;

import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.profiler.ProfilerTransform;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TransformTest {

    @Test
    public void testModuleClassFileAsInput() throws Exception {
        // Verify a class file of a CONSTANT_Module can be processed.
        // It's introduced in Java 9 (class file format 53.0).
        File input =
                TestUtils.resolveWorkspacePath(
                                "tools/base/profiler/transform/testData/module-info.class")
                        .toFile();
        File output = new File("output.class");
        ProfilerTransform transform = new ProfilerTransform();
        transform.accept(new FileInputStream(input), new FileOutputStream(output));
        assertTrue(output.length() > 0);
    }
}
