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

import com.android.tools.bazel.Configuration;
import com.android.tools.bazel.StringBuilderLogger;
import com.android.tools.utils.WorkspaceUtils;
import org.junit.Test;

/**
 * Test to verify that iml and build files are consistent
 *
 * <p>i.e. that the iml-to-build tool does not need to be run.
 */
public class ImlToBazelConsistencyTest {

    @Test
    public void testNoMissingUpdates() throws Exception {
        StringBuilderLogger logger = new StringBuilderLogger();
        try {
            Configuration config = new Configuration();
            config.dryRun = true;
            int updated =
                    ImlToBazel.run(
                            config,
                            WorkspaceUtils.findWorkspace(),
                            /*project=*/ "tools/adt/idea",
                            logger);
            if (updated != 0) {
                fail("IML and build files are inconsistent", logger, null);
            }
        } catch (Exception e) {
             fail("IML to build tool threw an exception", logger, e);
        }
    }

    private static void fail(String message, StringBuilderLogger logger, Throwable cause) {
        throw new AssertionError(
                message
                        + "\n"
                        + "Please re-run the iml to build tool [1], or revert the iml changes.\n"
                        + "\n"
                        + "iml-to-build tool output:\n  |  "
                        + String.join("\n  |  ", logger.getOutput())
                        + "\n\n"
                        + "[1]: https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-master-dev/bazel/#build-files\n"
                        + "",
                cause);
    }

}
