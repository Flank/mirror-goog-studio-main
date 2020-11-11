/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.repository.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.testframework.MockFileOp;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Tests for {@link License}
 */
public class LicenseTest {

    @Test
    public void testAccept() {
        MockFileOp fop = new MockFileOp();
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        License l = factory.createLicenseType("my license", "lic1");
        License l2 = factory.createLicenseType("my license 2", "lic2");
        Path root = fop.toPath("/sdk");
        assertFalse(l.checkAccepted(root));
        assertFalse(l2.checkAccepted(root));
        l.setAccepted(root);
        assertTrue(l.checkAccepted(root));
        assertFalse(l2.checkAccepted(root));
    }

    @Test
    public void testMultiSameIdAccept() {
        MockFileOp fop = new MockFileOp();
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        License l = factory.createLicenseType("my license", "lic1");
        License l2 = factory.createLicenseType("my license 2", "lic1");
        Path root = fop.toPath("/sdk");
        assertFalse(l.checkAccepted(root));
        assertFalse(l2.checkAccepted(root));
        l.setAccepted(root);
        assertTrue(l.checkAccepted(root));
        assertFalse(l2.checkAccepted(root));
        l2.setAccepted(root);
        assertTrue(l.checkAccepted(root));
        assertTrue(l2.checkAccepted(root));
    }

    /** Since we tell users the files control the license acceptance, make sure they work. */
    @Test
    public void testLicenseFile() throws Exception {
        MockFileOp fop = new MockFileOp();
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        License lic1 = factory.createLicenseType("my license", "lic1");
        License lic1a = factory.createLicenseType("my license rev 2", "lic1");
        License lic2 = factory.createLicenseType("my license 2", "lic2");
        Path root = fop.toPath("/sdk").toAbsolutePath();
        lic1.setAccepted(root);
        lic1a.setAccepted(root);
        lic2.setAccepted(root);
        Path licenseDir = root.resolve(License.LICENSE_DIR);
        Path[] licenseFiles = Files.list(licenseDir).toArray(Path[]::new);
        assertEquals(2, licenseFiles.length);
        Path lic1File = licenseDir.resolve("lic1");
        byte[] lic1FileContent = Files.readAllBytes(lic1File);
        Files.delete(lic1File);
        assertFalse(lic1.checkAccepted(root));
        assertFalse(lic1a.checkAccepted(root));
        assertTrue(lic2.checkAccepted(root));

        fop = new MockFileOp();
        root = fop.toPath(root.toString());
        assertFalse(lic1.checkAccepted(root));
        fop.recordExistingFile(lic1File.toString(), lic1FileContent);
        assertTrue(lic1.checkAccepted(root));
        assertTrue(lic1a.checkAccepted(root));
    }

    @Test
    public void unexpectedDefaultCharset() throws Exception {
        CommonFactory factory = RepoManager.getCommonModule().createLatestFactory();
        License lic1 = factory.createLicenseType("根源", "not ASCII");

        Charset newValue = Charset.forName("windows-1252");
        Field Charset_defaultCharset = Charset.class.getDeclaredField("defaultCharset");
        Charset_defaultCharset.setAccessible(true);
        Charset oldValue = (Charset) Charset_defaultCharset.get(null);
        try {
            Charset_defaultCharset.set(null, newValue);
            assertEquals("53c2da7eee3322de5414c7aef59fb04881307a03", lic1.getLicenseHash());
        } finally {
            Charset_defaultCharset.set(null, oldValue);
        }
    }
}
