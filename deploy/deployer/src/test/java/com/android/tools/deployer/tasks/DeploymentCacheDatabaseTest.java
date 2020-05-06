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
package com.android.tools.deployer.tasks;

import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.DeploymentCacheDatabase;
import com.android.tools.deployer.OverlayId;
import com.android.tools.deployer.model.Apk;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for DeploymentCacheDatabase. */
public class DeploymentCacheDatabaseTest {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testSimpleAddAndRetrieve() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(10);
        db.store(
                "serialXYZ",
                "com.example.xyz",
                Lists.newArrayList(makeApk("mychecksum")),
                mockOverLayId());
        DeploymentCacheDatabase.Entry entry = db.get("serialXYZ", "com.example.xyz");
        Assert.assertEquals(1, entry.getApks().size());
        Assert.assertEquals("mychecksum", entry.getApks().get(0).checksum);
    }

    @Test
    public void testNotExists() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(10);
        db.store(
                "serialXYZ",
                "com.example.xyz",
                Lists.newArrayList(makeApk("mychecksum")),
                mockOverLayId());
        DeploymentCacheDatabase.Entry entry = db.get("serialXYZ", "com.example.xyz.missing");
        Assert.assertNull(entry);
    }

    @Test
    public void testReinstall() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(3);
        db.store(
                "serialXYZ",
                "com.example.xyz0",
                Lists.newArrayList(makeApk("mychecksum0")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.xyz1",
                Lists.newArrayList(makeApk("mychecksum1")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.xyz2",
                Lists.newArrayList(makeApk("mychecksum2")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.xyz3",
                Lists.newArrayList(makeApk("mychecksum3")),
                mockOverLayId());
        DeploymentCacheDatabase.Entry entry = db.get("serialXYZ", "com.example.xyz3");
        Assert.assertEquals(1, entry.getApks().size());
        Assert.assertEquals("mychecksum3", entry.getApks().get(0).checksum);

        DeploymentCacheDatabase.Entry evicted = db.get("serialXYZ", "com.example.xyz0");
        Assert.assertNull(evicted);
    }

    @Test
    public void testEvict() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(3);
        db.store(
                "serialXYZ",
                "com.example.xyz0",
                Lists.newArrayList(makeApk("mychecksum0")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.xyz1",
                Lists.newArrayList(makeApk("mychecksum1")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.xyz2",
                Lists.newArrayList(makeApk("mychecksum2")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.xyz3",
                Lists.newArrayList(makeApk("mychecksum3")),
                mockOverLayId());
        DeploymentCacheDatabase.Entry entry = db.get("serialXYZ", "com.example.xyz3");
        Assert.assertEquals(1, entry.getApks().size());
        Assert.assertEquals("mychecksum3", entry.getApks().get(0).checksum);
    }

    @Test
    public void testTwoDevices() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(10);
        db.store(
                "serial0",
                "com.example.xyz",
                Lists.newArrayList(makeApk("mychecksum0")),
                mockOverLayId());
        db.store(
                "serial1",
                "com.example.xyz",
                Lists.newArrayList(makeApk("mychecksum1")),
                mockOverLayId());

        DeploymentCacheDatabase.Entry entry0 = db.get("serial0", "com.example.xyz");
        Assert.assertEquals(1, entry0.getApks().size());
        Assert.assertEquals("mychecksum0", entry0.getApks().get(0).checksum);

        DeploymentCacheDatabase.Entry entry1 = db.get("serial1", "com.example.xyz");
        Assert.assertEquals(1, entry1.getApks().size());
        Assert.assertEquals("mychecksum1", entry1.getApks().get(0).checksum);
    }

    @Test
    public void testDifferentApp() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(10);
        db.store(
                "serialXYZ",
                "com.example.0",
                Lists.newArrayList(makeApk("mychecksum0")),
                mockOverLayId());
        db.store(
                "serialXYZ",
                "com.example.1",
                Lists.newArrayList(makeApk("mychecksum1")),
                mockOverLayId());

        DeploymentCacheDatabase.Entry entry0 = db.get("serialXYZ", "com.example.0");
        Assert.assertEquals(1, entry0.getApks().size());
        Assert.assertEquals("mychecksum0", entry0.getApks().get(0).checksum);

        DeploymentCacheDatabase.Entry entry1 = db.get("serialXYZ", "com.example.1");
        Assert.assertEquals(1, entry1.getApks().size());
        Assert.assertEquals("mychecksum1", entry1.getApks().get(0).checksum);
    }

    @Test
    public void testSwitchedDevice() throws DeployerException {
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(10);
        db.store(
                "serial0",
                "com.example.XYZ",
                Lists.newArrayList(makeApk("mychecksum0")),
                mockOverLayId());
        db.store(
                "serial1",
                "com.example.XYZ",
                Lists.newArrayList(makeApk("mychecksum1")),
                mockOverLayId());

        DeploymentCacheDatabase.Entry entry0 = db.get("serial0", "com.example.XYZ");
        Assert.assertEquals(1, entry0.getApks().size());
        Assert.assertEquals("mychecksum0", entry0.getApks().get(0).checksum);

        DeploymentCacheDatabase.Entry entry1 = db.get("serial1", "com.example.XYZ");
        Assert.assertEquals(1, entry1.getApks().size());
        Assert.assertEquals("mychecksum1", entry1.getApks().get(0).checksum);
    }

    @Test
    public void testPersist() throws DeployerException, IOException {
        File persistFile = tmpDir.newFile("dex.db");
        DeploymentCacheDatabase db = new DeploymentCacheDatabase(10, persistFile);
        db.store(
                "serialXYZ",
                "com.example.xyz",
                Lists.newArrayList(makeApk("mychecksum")),
                mockOverLayId());
        DeploymentCacheDatabase.Entry entry = db.get("serialXYZ", "com.example.xyz");
        Assert.assertEquals(1, entry.getApks().size());
        Assert.assertEquals("mychecksum", entry.getApks().get(0).checksum);

        db = new DeploymentCacheDatabase(10, persistFile);
        db.store(
                "serialXYZ",
                "com.example.xyz",
                Lists.newArrayList(makeApk("mychecksum")),
                mockOverLayId());
        entry = db.get("serialXYZ", "com.example.xyz");
        Assert.assertEquals(1, entry.getApks().size());
        Assert.assertEquals("mychecksum", entry.getApks().get(0).checksum);
    }

    private static Apk makeApk(String checksum) {
        return Apk.builder().setName("base.apk").setChecksum(checksum).build();
    }

    private static OverlayId mockOverLayId() throws DeployerException {
        return new OverlayId(new ArrayList<>());
    }
}
