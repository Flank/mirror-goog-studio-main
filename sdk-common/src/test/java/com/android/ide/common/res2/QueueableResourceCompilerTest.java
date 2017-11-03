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

package com.android.ide.common.res2;

import static org.junit.Assert.assertFalse;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/*
 * Tests for {@link QueueableResourceCompiler}.
 */
public class QueueableResourceCompilerTest {

    /** Temporary folder to use in tests. */
    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void callToCompileOutputForDoesNotCreateDirectories() throws Exception {
        try (QueueableResourceCompiler aapt = QueueableResourceCompiler.NONE) {
            File outputDir = mTemporaryFolder.newFolder("empty");
            File input = new File(mTemporaryFolder.newFolder("values"), "values.xml");

            CompileResourceRequest request = new CompileResourceRequest(input, outputDir, "values");
            File output = aapt.compileOutputFor(request);

            assertFalse(output.exists());
            assertFalse(output.getParentFile().exists());
        }
    }
}
