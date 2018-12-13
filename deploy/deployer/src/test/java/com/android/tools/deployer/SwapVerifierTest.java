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

import static com.android.tools.deployer.model.FileDiff.Status.CREATED;
import static com.android.tools.deployer.model.FileDiff.Status.MODIFIED;

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.FileDiff;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SwapVerifierTest {

    @Test
    public void testSimpleModifiedDex() throws DeployerException {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("classes.dex", MODIFIED));
        new SwapVerifier().verify(diffs, false);
    }

    @Test
    public void testModifiedSharedObject() {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("lib/arm64-v8a/libnative-lib.so", MODIFIED));
        try {
            new SwapVerifier().verify(diffs, false);
        } catch (DeployerException e) {
            Assert.assertEquals(DeployerException.Error.CANNOT_SWAP_STATIC_LIB, e.getError());
        }
    }

    @Test
    public void testAddedSharedObject() throws DeployerException {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("classes.dex", MODIFIED));
        diffs.add(makeDiff("lib/arm64-v8a/libnative-lib.so", CREATED));

        // This is technically possible. User could have changed some init method that previous didn't load the .so but now swap in that
        // Java method and potentially now load it. For that case, we need to let it swap and see if the VM is ok with the dex changes.
        new SwapVerifier().verify(diffs, false);
    }

    @Test
    public void testAddedDex() throws DeployerException {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("classes.dex", MODIFIED));
        diffs.add(makeDiff("classes02.dex", CREATED));
        // This is fine as long as the later dex comparison is ok with it. Added classes will not need to be swapped.
        new SwapVerifier().verify(diffs, false);
    }

    @Test
    public void testChangedManifest() {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("AndroidManifest.xml", MODIFIED));
        try {
            new SwapVerifier().verify(diffs, false);
        } catch (DeployerException e) {
            Assert.assertEquals(DeployerException.Error.CANNOT_SWAP_MANIFEST, e.getError());
        }
    }

    @Test
    public void testChangedUnrelatedManifest() {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("Not-The-Real-AndroidManifest.xml", MODIFIED));
        try {
            new SwapVerifier().verify(diffs, false);
        } catch (DeployerException e) {
            Assert.assertEquals(DeployerException.Error.CANNOT_SWAP_RESOURCE, e.getError());
        }
    }

    @Test
    public void testChangedResourcesCodeSwapOnly() throws DeployerException {
        List<FileDiff> diffs = new ArrayList<>();
        diffs.add(makeDiff("META-INF/CERT.SF ", MODIFIED));
        diffs.add(makeDiff("META-INF/CERT.RSA", MODIFIED));
        diffs.add(makeDiff("META-INF/MANIFEST.MF", MODIFIED));
        diffs.add(makeDiff("Not-The-Real-AndroidManifest.xml", MODIFIED));
        new SwapVerifier().verify(diffs, true);
    }

    private FileDiff makeDiff(String name, FileDiff.Status status) {
        ApkEntry left =
                new ApkEntry(name, 0, Apk.builder().setName("apk1").setChecksum("abcd").build());
        ApkEntry right =
                new ApkEntry(name, 0, Apk.builder().setName("apk2").setChecksum("abcd").build());
        return new FileDiff(left, right, status);
    }
}
