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

import static org.junit.Assert.assertEquals;

import com.android.testutils.TestUtils;
import java.io.File;
import java.util.HashMap;
import org.junit.Test;

public class DeployerTest {

    private static final String BASE = "tools/base/deploy/deployer/test/resource/";

    @Test
    public void testCentralDirectoryParse() {
        File file = TestUtils.getWorkspaceFile(BASE + "base.apk.remotecd");
        ZipCentralDirectory zcd = new ZipCentralDirectory(file);
        HashMap<String, Long> crcs = new HashMap<>();
        zcd.getCrcs(crcs);
        assertEquals(1007, crcs.size());

        long manifestCrc = crcs.get("AndroidManifest.xml");
        assertEquals(0x5804A053, manifestCrc);
    }

    @Test
    public void testApkId() {
        File file = TestUtils.getWorkspaceFile(BASE + "sample.apk");
        Apk apk = new Apk(file.getAbsolutePath());
        assertEquals(apk.getDigestString(), "ec25d183db88a0ad6c9cc7199388d31331907e09");
    }
}
