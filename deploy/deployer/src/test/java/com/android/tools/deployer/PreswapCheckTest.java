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

import static com.android.tools.deployer.ApkDiffer.ApkEntryStatus.CREATED;
import static com.android.tools.deployer.ApkDiffer.ApkEntryStatus.MODIFIED;

import java.util.HashMap;
import org.junit.Assert;
import org.junit.Test;

public class PreswapCheckTest {
    @Test
    public void testSimpleModifiedDex() {
        HashMap<String, ApkDiffer.ApkEntryStatus> diff = new HashMap<>();
        diff.put("META-INF/CERT.SF ", MODIFIED);
        diff.put("META-INF/CERT.RSA", MODIFIED);
        diff.put("META-INF/MANIFEST.MF", MODIFIED);
        diff.put("classes.dex", MODIFIED);
        String error = PreswapCheck.verify(diff);
        Assert.assertNull(error);
    }

    @Test
    public void testModifiedSharedObject() {
        HashMap<String, ApkDiffer.ApkEntryStatus> diff = new HashMap<>();
        diff.put("META-INF/CERT.SF ", MODIFIED);
        diff.put("META-INF/CERT.RSA", MODIFIED);
        diff.put("META-INF/MANIFEST.MF", MODIFIED);
        diff.put("lib/arm64-v8a/libnative-lib.so", MODIFIED);
        String error = PreswapCheck.verify(diff);
        Assert.assertNotNull(error);
    }

    @Test
    public void testAddedSharedObject() {
        HashMap<String, ApkDiffer.ApkEntryStatus> diff = new HashMap<>();
        diff.put("META-INF/CERT.SF ", MODIFIED);
        diff.put("META-INF/CERT.RSA", MODIFIED);
        diff.put("META-INF/MANIFEST.MF", MODIFIED);
        diff.put("classes.dex", MODIFIED);
        diff.put("lib/arm64-v8a/libnative-lib.so", CREATED);

        // This is technically possible. User could have changed some init method that previous didn't load the .so but now swap in that
        // Java method and potentially now load it. For that case, we need to let it swap and see if the VM is ok with the dex changes.
        String error = PreswapCheck.verify(diff);
        Assert.assertNull(error);
    }

    @Test
    public void testAddedDex() {
        HashMap<String, ApkDiffer.ApkEntryStatus> diff = new HashMap<>();
        diff.put("META-INF/CERT.SF ", MODIFIED);
        diff.put("META-INF/CERT.RSA", MODIFIED);
        diff.put("META-INF/MANIFEST.MF", MODIFIED);
        diff.put("classes.dex", MODIFIED);
        diff.put("classes02.dex", CREATED);
        // This is fine as long as the later dex comparison is ok with it. Added classes will not need to be swapped.
        String error = PreswapCheck.verify(diff);
        Assert.assertNull(error);
    }

    @Test
    public void testChangedManifest() {
        HashMap<String, ApkDiffer.ApkEntryStatus> diff = new HashMap<>();
        diff.put("META-INF/CERT.SF ", MODIFIED);
        diff.put("META-INF/CERT.RSA", MODIFIED);
        diff.put("META-INF/MANIFEST.MF", MODIFIED);
        diff.put("AndroidManifest.xml", MODIFIED);
        String error = PreswapCheck.verify(diff);
        Assert.assertNotNull(error);
    }

    @Test
    public void testChangedUnrelatedManifest() {
        HashMap<String, ApkDiffer.ApkEntryStatus> diff = new HashMap<>();
        diff.put("META-INF/CERT.SF ", MODIFIED);
        diff.put("META-INF/CERT.RSA", MODIFIED);
        diff.put("META-INF/MANIFEST.MF", MODIFIED);
        diff.put("Not-The-Real-AndroidManifest.xml", MODIFIED);
        String error = PreswapCheck.verify(diff);
        Assert.assertNull(error);
    }
}
