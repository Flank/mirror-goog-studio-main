/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.*;

import com.android.tools.deployer.model.Apk;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.EnumSet;
import org.junit.Test;

public class OverlayDifferTest {

    private static final EnumSet<ChangeType> ALL = EnumSet.allOf(ChangeType.class);
    private static final EnumSet<ChangeType> NONE = EnumSet.noneOf(ChangeType.class);

    private static final long BASE_CHECKSUM = 0;
    private static final long CHECKSUM_A = 1;
    private static final long CHECKSUM_B = 2;

    @Test
    public void testModifyFileInOverlay() throws DeployerException {
        // File exists in base APK
        // File is currently in overlay
        // File is modified in new APK

        OverlayDiffer differ = new OverlayDiffer(ALL);

        Apk baseApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0000")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayId baseOverlay = new OverlayId(ImmutableList.of(baseApk));

        OverlayId currentOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes1.dex", CHECKSUM_A)
                        .build();

        Apk newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", CHECKSUM_B)
                        .build();

        OverlayDiffer.Result result = differ.diff(ImmutableList.of(newApk), currentOverlay);
        assertEquals(1, result.filesToAdd.size());
        assertEquals(
                "base.apk/classes1.dex",
                Iterables.getOnlyElement(result.filesToAdd).getQualifiedPath());
        assertEquals(CHECKSUM_B, Iterables.getOnlyElement(result.filesToAdd).getChecksum());
        assertTrue(result.filesToRemove.isEmpty());
    }

    @Test
    public void testModifyFileInBase() throws DeployerException {
        // File exists in base APK
        // The overlay is empty
        // File is modified in new APK
        OverlayDiffer differ = new OverlayDiffer(ALL);

        Apk baseApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0000")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayId baseOverlay = new OverlayId(ImmutableList.of(baseApk));

        Apk newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", CHECKSUM_A)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayDiffer.Result result = differ.diff(ImmutableList.of(newApk), baseOverlay);
        assertEquals(1, result.filesToAdd.size());
        assertEquals("classes0.dex", Iterables.getOnlyElement(result.filesToAdd).getName());
        assertEquals(CHECKSUM_A, Iterables.getOnlyElement(result.filesToAdd).getChecksum());
        assertTrue(result.filesToRemove.isEmpty());

        // File exists in base APK
        // The overlay is not empty
        // File is NOT currently in overlay
        // File is modified in new APK

        OverlayId currentOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes0.dex", CHECKSUM_A)
                        .build();

        newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", CHECKSUM_A)
                        .addApkEntry("classes1.dex", CHECKSUM_B)
                        .build();

        result = differ.diff(ImmutableList.of(newApk), currentOverlay);
        assertEquals(1, result.filesToAdd.size());
        assertEquals("classes1.dex", Iterables.getOnlyElement(result.filesToAdd).getName());
        assertEquals(CHECKSUM_B, Iterables.getOnlyElement(result.filesToAdd).getChecksum());
        assertTrue(result.filesToRemove.isEmpty());
    }

    @Test
    public void testUndo() throws DeployerException {
        // File exists in base APK
        // File is currently in overlay
        // File is modified to be identical to the base APK contents

        OverlayDiffer differ = new OverlayDiffer(ALL);

        Apk baseApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0000")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayId baseOverlay = new OverlayId(ImmutableList.of(baseApk));

        OverlayId currentOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes1.dex", CHECKSUM_A)
                        .build();

        Apk newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayDiffer.Result result = differ.diff(ImmutableList.of(newApk), currentOverlay);
        assertTrue(result.filesToAdd.isEmpty());
        assertEquals(1, result.filesToRemove.size());
        assertEquals("base.apk/classes1.dex", Iterables.getOnlyElement(result.filesToRemove));

        // File exists in base APK
        // File is currently in overlay
        // File is modified to be returned to the previous overlay

        OverlayId firstOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes0.dex", CHECKSUM_A)
                        .build();

        currentOverlay =
                OverlayId.builder(firstOverlay)
                        .addOverlayFile("base.apk/classes0.dex", CHECKSUM_B)
                        .build();

        newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", CHECKSUM_A)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        result = differ.diff(ImmutableList.of(newApk), currentOverlay);
        assertEquals(1, result.filesToAdd.size());
        assertEquals("classes0.dex", Iterables.getOnlyElement(result.filesToAdd).getName());
        assertEquals(CHECKSUM_A, Iterables.getOnlyElement(result.filesToAdd).getChecksum());
        assertTrue(result.filesToRemove.isEmpty());
    }

    @Test
    public void testAddNewFile() throws DeployerException {
        // File does not exist in base APK
        // File is added to overlay

        OverlayDiffer differ = new OverlayDiffer(ALL);

        Apk baseApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0000")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayId baseOverlay = new OverlayId(ImmutableList.of(baseApk));

        Apk newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .addApkEntry("classes2.dex", CHECKSUM_A)
                        .build();

        OverlayDiffer.Result result = differ.diff(ImmutableList.of(newApk), baseOverlay);
        assertEquals(1, result.filesToAdd.size());
        assertEquals("classes2.dex", Iterables.getOnlyElement(result.filesToAdd).getName());
        assertEquals(CHECKSUM_A, Iterables.getOnlyElement(result.filesToAdd).getChecksum());
        assertTrue(result.filesToRemove.isEmpty());

        // File does not exist in base APK
        // File exists in overlay
        // File is modified

        OverlayId currentOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes2.dex", CHECKSUM_A)
                        .build();

        newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .addApkEntry("classes2.dex", CHECKSUM_B)
                        .build();

        result = differ.diff(ImmutableList.of(newApk), baseOverlay);
        assertEquals(1, result.filesToAdd.size());
        assertEquals("classes2.dex", Iterables.getOnlyElement(result.filesToAdd).getName());
        assertEquals(CHECKSUM_B, Iterables.getOnlyElement(result.filesToAdd).getChecksum());
        assertTrue(result.filesToRemove.isEmpty());
    }

    @Test
    public void testDeleteFile() throws DeployerException {
        // File exists in base APK
        // File is deleted; exception

        OverlayDiffer differ = new OverlayDiffer(ALL);

        Apk baseApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0000")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayId baseOverlay = new OverlayId(ImmutableList.of(baseApk));

        Apk newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .build();

        try {
            differ.diff(ImmutableList.of(newApk), baseOverlay);
            fail();
        } catch (DeployerException e) {
            assertEquals(e.getError(), DeployerException.Error.UNSUPPORTED_IWI_FILE_DELETE);
        }

        // File does not exist in base APK
        // File exists in overlay
        // File is deleted

        OverlayId currentOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes2.dex", CHECKSUM_A)
                        .build();

        newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .addApkEntry("classes1.dex", BASE_CHECKSUM)
                        .build();

        OverlayDiffer.Result result = differ.diff(ImmutableList.of(newApk), currentOverlay);
        assertTrue(result.filesToAdd.isEmpty());
        assertEquals(1, result.filesToRemove.size());
        assertEquals("base.apk/classes2.dex", Iterables.getOnlyElement(result.filesToRemove));

        // File exists in base APK
        // File exists in overlay
        // File is deleted; exception

        currentOverlay =
                OverlayId.builder(baseOverlay)
                        .addOverlayFile("base.apk/classes1.dex", CHECKSUM_A)
                        .build();

        newApk =
                Apk.builder()
                        .setName("base.apk")
                        .setChecksum("0001")
                        .addApkEntry("classes0.dex", BASE_CHECKSUM)
                        .build();

        try {
            differ.diff(ImmutableList.of(newApk), currentOverlay);
            fail();
        } catch (DeployerException e) {
            assertEquals(e.getError(), DeployerException.Error.UNSUPPORTED_IWI_FILE_DELETE);
        }
    }
}
