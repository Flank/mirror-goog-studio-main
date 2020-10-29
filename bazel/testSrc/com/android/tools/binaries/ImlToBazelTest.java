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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.bazel.Configuration;
import com.android.tools.bazel.StringBuilderLogger;
import java.nio.file.Paths;
import org.junit.Test;

public class ImlToBazelTest {

    @Test
    public void testProjectWithErrors() throws Exception {
        StringBuilderLogger logger = new StringBuilderLogger();
        try {
            Configuration config = new Configuration();
            config.dryRun = true;
            ImlToBazel.run(
                    config,
                    Paths.get("tools/base/bazel/test/iml_to_bazel").toAbsolutePath(),
                    ".",
                    logger);
            fail("Expected a failure.");
        } catch (ImlToBazel.ProjectLoadingException e) {
            // There's a cycle in the project, we should be a warning.
            assertEquals(e.issues, 5);
            assertArrayEquals(
                    new String[] {
                        "Loaded project iml_to_bazel with 19 modules.",
                        "Found circular module dependency: 2 modules        test_runtime_cycle_end        test_runtime_cycle_start",
                        "Found circular module dependency: 2 modules        runtime_test_cycle_end        runtime_test_cycle_start",
                        "Found circular module dependency: 2 modules        runtime_compile_cycle_end        runtime_compile_cycle_start",
                        "Found circular module dependency: 2 modules        compile_test_cycle_end        compile_test_cycle_start",
                        "Found circular module dependency: 2 modules        compile_runtime_cycle_end        compile_runtime_cycle_start"
                    },
                    logger.getOutput().toArray());
        }
    }
}
