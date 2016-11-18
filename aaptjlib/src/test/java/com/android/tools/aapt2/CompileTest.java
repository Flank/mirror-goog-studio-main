/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.aapt2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CompileTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilePng() throws Exception {
        File drawable = temporaryFolder.newFolder("drawable");
        File lena = new File(drawable, "lena.png");
        File out = temporaryFolder.newFolder("out");

        try (InputStream is = CompileTest.class.getResourceAsStream("/lena.png");
                FileOutputStream fos = new FileOutputStream(lena)) {
            assertNotNull(is);
            byte[] buf = new byte[1024 * 1024];
            int r;
            while ((r = is.read(buf)) >= 0) {
                fos.write(buf, 0, r);
            }
        }

        Aapt2Jni.compile(Arrays.asList("-o", out.getAbsolutePath(), lena.getAbsolutePath()));
        File expectedOut = new File(out, Aapt2RenamingConventions.compilationRename(lena));
        assertTrue(expectedOut.exists());
    }
}
