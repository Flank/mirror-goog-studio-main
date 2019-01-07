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

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import java.io.File;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class D8DexSplitterTest {
    @Test
    public void testDiffApk() throws Exception {
        TemporaryFolder tmpdir = new TemporaryFolder();
        tmpdir.create();
        String apk1Location = getProcessPath("apk1.location");
        String apk2Location = getProcessPath("apk2.location");
        ApkEntry dex1 = new ApkEntry("classes.dex", 1, Apk.builder().setPath(apk1Location).build());
        ApkEntry dex2 = new ApkEntry("classes.dex", 2, Apk.builder().setPath(apk2Location).build());
        D8DexSplitter splitter = new D8DexSplitter();
        List<DexClass> before = splitter.split(dex1, c -> false);
        List<DexClass> after =
                splitter.split(dex2, c -> searchByName(before, c.name).checksum != c.checksum);

        // Unchanged classes -> No code.
        Assert.assertNull(searchByName(after, "testapk.Same").code);

        // Changed classes -> Code.
        Assert.assertNotNull(searchByName(after, "testapk.Changed").code);
    }

    public static DexClass searchByName(List<DexClass> classes, String name) {
        for (DexClass c : classes) {
            if (name.equals(c.name)) {
                System.out.println(c.name);
                return c;
            }
        }
        return null;
    }

    public static String getProcessPath(String property) {
        return new File(System.getProperty(property)).getAbsolutePath();
    }
}
