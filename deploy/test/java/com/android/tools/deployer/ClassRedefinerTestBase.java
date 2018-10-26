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
package com.android.tools.deployer;

import com.android.tools.fakeandroid.ProcessRunner;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Assert;

public class ClassRedefinerTestBase {

    // Location of all the dex files to be swapped in to test hotswapping.
    private static final String DEX_SWAP_LOCATION =
            ProcessRunner.getProcessPath("app.swap.dex.location");

    /**
     * The ":test-app-swap" rule output contains a list of dex file that we can hotswap. Use this
     * method to extract a class from the output.
     *
     * @return The single file dex code of a given class compiled in our dex_library
     *     ":test-app-swap" rule.
     */
    protected static byte[] getSplittedDex(String name) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(DEX_SWAP_LOCATION));
        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            if (!entry.getName().equals(name)) {
                continue;
            }

            byte[] buffer = new byte[1024];
            ByteArrayOutputStream dexContent = new ByteArrayOutputStream();

            int len;
            while ((len = zis.read(buffer)) > 0) {
                dexContent.write(buffer, 0, len);
            }
            return dexContent.toByteArray();
        }
        zis.closeEntry();
        zis.close();
        Assert.fail("Cannot find " + name + " in " + DEX_SWAP_LOCATION);
        return null;
    }
}
