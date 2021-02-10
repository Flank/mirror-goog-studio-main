/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.*;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.deployer.model.Apk;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class OverlayIdTest {
    @Test
    public void testRealInstall() throws DeployerException {
        OverlayId realInstall = new OverlayId(makeApks(1));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                getFileHeader() + "Real APK ApkName0 has checksum of ApkChecksum0\n", stringRep);

        realInstall = new OverlayId(makeApks(1));
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "3af200fe91ea16478c0dee3561a59a14f3abb3d0eaaad049a95f88262b0c102e", sha);
    }

    @Test
    public void testSwaps() throws Exception {

        // *****************************************************
        // Fresh install
        // *****************************************************
        OverlayId realInstall = new OverlayId(makeApks(2));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n",
                stringRep);
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "572bf1f3899e02da013865345cd1491b050542347784865bf040e6b936bb70f4", sha);

        // *****************************************************
        // First IWI Swap
        // Doing first IWI swap on a real APK, adding a class.
        // *****************************************************
        OverlayId firstSwap = OverlayId.builder(realInstall).addOverlayFile("dex1.dex", 1L).build();
        stringRep = firstSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n",
                stringRep);
        sha = firstSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "f63e09245e5182e0406fa46798d1a159d4964835ecf6fa444aab8d60f06ca790", sha);

        // *****************************************************
        // Second IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying an existing class.
        // *****************************************************
        OverlayId secondSwap = OverlayId.builder(firstSwap).addOverlayFile("dex2.dex", 2L).build();
        stringRep = secondSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n"
                        + " Has overlayfile dex2.dex with checksum 2\n",
                stringRep);
        sha = secondSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "82e4ddc3d0609c877f94f3f490da498afb7fa646412c9e3ec00aa41f34fad096", sha);

        // *****************************************************
        // Third IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying dex2 again.
        // *****************************************************
        OverlayId thirdSwap =
                OverlayId.builder(secondSwap).addOverlayFile("dex2.dex", 200L).build();
        stringRep = thirdSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile dex1.dex with checksum 1\n"
                        + " Has overlayfile dex2.dex with checksum 200\n",
                stringRep);
        sha = thirdSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "de0ca2bc6919267e81741eeb93ec7990977e8406b6fdddc441c6b8f50ae418a6", sha);
    }

    @Test
    public void testResourceSwaps() throws Exception {

        // *****************************************************
        // Fresh install
        // *****************************************************
        OverlayId realInstall = new OverlayId(makeApks(2));
        String stringRep = realInstall.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n",
                stringRep);
        String sha = realInstall.getSha();
        Assert.assertEquals(
                "572bf1f3899e02da013865345cd1491b050542347784865bf040e6b936bb70f4", sha);

        // *****************************************************
        // First IWI Swap
        // Doing first IWI swap on a real APK, adding a resource.
        // *****************************************************
        OverlayId firstSwap =
                OverlayId.builder(realInstall).addOverlayFile("apk/res/1", 1L).build();
        stringRep = firstSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile apk/res/1 with checksum 1\n",
                stringRep);
        sha = firstSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "b41e7471c56edaf8ca8932865673fa1db1ab9499b755d2067bc7a09bd89fd217", sha);

        // *****************************************************
        // Second IWI Swap
        // Doing a IWI swap on the previous IWI install, modifying an existing resource.
        // *****************************************************
        OverlayId secondSwap =
                OverlayId.builder(firstSwap).addOverlayFile("apk/res/1", 11L).build();
        stringRep = secondSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile apk/res/1 with checksum 11\n",
                stringRep);
        sha = secondSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "0cd8fda9b3a54e1641f3b4f5239b8e53405159543525424940fb3b57b44f4eaa", sha);

        // *****************************************************
        // Third IWI Swap
        // Doing a IWI swap on the previous IWI install, adding another resource.
        // *****************************************************

        OverlayId thirdSwap = OverlayId.builder(secondSwap).addOverlayFile("apk/res/2", 2).build();
        stringRep = thirdSwap.getRepresentation();
        Assert.assertEquals(
                getFileHeader()
                        + "Real APK ApkName0 has checksum of ApkChecksum0\n"
                        + "Real APK ApkName1 has checksum of ApkChecksum1\n"
                        + " Has overlayfile apk/res/1 with checksum 11\n"
                        + " Has overlayfile apk/res/2 with checksum 2\n",
                stringRep);
        sha = thirdSwap.getSha();
        // SHA256 of the above string.
        Assert.assertEquals(
                "633b0d701f88a7abe60abcdf12d542dba4930302d6106ba5a5687e1f79c50785", sha);
    }

    @Test
    public void testRemoveFile() throws DeployerException {
        OverlayId realInstall = new OverlayId(makeApks(2));
        Assert.assertEquals(0, realInstall.getOverlayContents().size());

        OverlayId nextOverlayId =
                OverlayId.builder(realInstall)
                        .addOverlayFile("apk/res/1", 1L)
                        .addOverlayFile("apk/res/2", 11L)
                        .addOverlayFile("Class1.dex", 2L)
                        .addOverlayFile("Class2.dex", 22L)
                        .build();

        assertThat(nextOverlayId.getOverlayContents().allFiles())
                .containsExactly("apk/res/1", "apk/res/2", "Class1.dex", "Class2.dex");

        nextOverlayId =
                OverlayId.builder(nextOverlayId)
                        .removeOverlayFile("apk/res/2")
                        .removeOverlayFile("Class1.dex")
                        .build();

        assertThat(nextOverlayId.getOverlayContents().allFiles())
                .containsExactly("apk/res/1", "Class2.dex");
    }

    private List<Apk> makeApks(int size) {
        List<Apk> apks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            apks.add(
                    Apk.builder()
                            .setName("ApkName" + i)
                            .setChecksum("ApkChecksum" + i)
                            .setPackageName("overlay.id.test")
                            .setPath("/some/path")
                            .build());
        }
        return apks;
    }

    private static String getFileHeader() {
        return "Apply Changes Overlay ID\n" + "Schema Version " + OverlayId.SCHEMA_VERSION + "\n";
    }
}
