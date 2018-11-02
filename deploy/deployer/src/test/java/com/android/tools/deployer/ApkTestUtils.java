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

import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import org.junit.Assert;

public class ApkTestUtils {

    public static void assertDexClassEquals(
            String apkChecksum,
            String dexName,
            long dexChecksum,
            String name,
            long checksum,
            DexClass clazz) {
        assertApkFileEquals(apkChecksum, dexName, dexChecksum, clazz.dex);
        Assert.assertEquals(name, clazz.name);
        Assert.assertEquals(checksum, clazz.checksum);
    }

    public static void assertApkFileEquals(
            String apkChecksum, String fileName, long fileChecksum, ApkEntry file) {
        Assert.assertEquals(apkChecksum, file.apk.checksum);
        Assert.assertEquals(fileName, file.name);
        Assert.assertEquals(fileChecksum, file.checksum);
    }
}
