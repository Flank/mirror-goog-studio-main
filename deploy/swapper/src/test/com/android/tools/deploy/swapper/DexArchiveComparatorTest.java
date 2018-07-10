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
package com.android.tools.deploy.swapper;

import java.io.File;
import org.junit.Assert;
import org.junit.Test;

public class DexArchiveComparatorTest {
    @Test
    public void testDiffApk() throws Exception {
        String apk1Location = getProcessPath("apk1.location");
        String apk2Location = getProcessPath("apk2.location");

        DexArchiveComparator.Result result =
                new DexArchiveComparator()
                        .compare(
                                new File(apk1Location),
                                new File(apk2Location),
                                new String[] {"classes.dex"});

        Assert.assertEquals(1, result.changedClasses.size());

        DexArchiveComparator.Entry changed = result.changedClasses.get(0);
        Assert.assertEquals("com.android.tools.deploy.swapper.testapk.Changed", changed.name);
        Assert.assertNotNull(changed.dex);
    }

    public static String getProcessPath(String property) {
        return System.getProperty("user.dir") + File.separator + System.getProperty(property);
    }
}
