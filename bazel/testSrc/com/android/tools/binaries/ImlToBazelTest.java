/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.binaries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.file.Paths;
import org.junit.Test;

public class ImlToBazelTest {

    @Test
    public void testExpected() throws Exception {
        int updated =
                ImlToBazel.run(
                        Paths.get("tools/base/bazel/test/iml_to_bazel").toAbsolutePath(),
                        ".",
                        null,
                        true,
                        false);
        assertEquals(0, updated);
    }

    @Test
    public void testWarningsAsErrors() throws Exception {
        try {
            ImlToBazel.run(
                    Paths.get("tools/base/bazel/test/iml_to_bazel").toAbsolutePath(),
                    ".",
                    null,
                    true,
                    true);
            fail("Expected a failure.");
        } catch (ImlToBazel.ProjectLoadingException e) {
            // There's a cycle in the project, we should be a warning.
            assertEquals(e.issues, 1);
        }
    }
}
