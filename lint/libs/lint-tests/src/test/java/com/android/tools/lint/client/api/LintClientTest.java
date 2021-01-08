/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import static com.android.tools.lint.client.api.LintClient.CLIENT_UNIT_TESTS;

import com.android.testutils.TestUtils;
import com.android.tools.lint.LintCliClient;
import com.google.common.truth.Truth;
import java.io.File;
import junit.framework.TestCase;
import kotlin.UninitializedPropertyAccessException;

@SuppressWarnings("javadoc")
public class LintClientTest extends TestCase {

    public void testApiLevel() {
        LintCliClient client = new LintCliClient(CLIENT_UNIT_TESTS);
        int max = client.getHighestKnownApiLevel();
        assertTrue(max >= 16);
    }

    public void testClient() {
        assertTrue(!LintClient.isGradle() || !LintClient.isStudio());
    }

    public void testVersion() {
        LintCliClient client =
                new LintCliClient(CLIENT_UNIT_TESTS) {
                    @Override
                    public File getSdkHome() {
                        return TestUtils.getSdk().toFile();
                    }
                };
        String revision = client.getClientRevision();
        Truth.assertThat(revision).isNotNull();
        Truth.assertThat(revision).isNotEmpty();
        String displayRevision = client.getClientDisplayRevision();
        Truth.assertThat(displayRevision).isNotNull();
        Truth.assertThat(displayRevision).isNotEmpty();
    }

    private static File file(String path) {
        return new File(path.replace('/', File.separatorChar));
    }

    public void testRelative() {
        LintCliClient client = new LintCliClient(CLIENT_UNIT_TESTS);
        assertEquals(
                file("../../d/e/f").getPath(),
                client.getRelativePath(file("a/b/c"), file("d/e/f")));
        assertEquals(
                file("../d/e/f").getPath(), client.getRelativePath(file("a/b/c"), file("a/d/e/f")));
        assertEquals(
                file("../d/e/f").getPath(),
                client.getRelativePath(file("1/2/3/a/b/c"), file("1/2/3/a/d/e/f")));
        assertEquals(file("c").getPath(), client.getRelativePath(file("a/b/c"), file("a/b/c")));
        assertEquals(
                file("../../e").getPath(),
                client.getRelativePath(file("a/b/c/d/e/f"), file("a/b/c/e")));
        assertEquals(
                file("d/e/f").getPath(),
                client.getRelativePath(file("a/b/c/e"), file("a/b/c/d/e/f")));
    }

    public void testClientName() {
        LintClient.Companion.resetClientName();
        try {
            LintClient.getClientName();
            fail("Expected accessing client name before initialization to fail");
        } catch (UninitializedPropertyAccessException t) {
            // pass
        }
        LintClient.setClientName(CLIENT_UNIT_TESTS);
        LintClient.getClientName();
    }
}
