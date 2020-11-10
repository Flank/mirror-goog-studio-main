/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.signflinger;

import com.android.zipflinger.BytesSource;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipArchive;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.Assert;
import org.junit.Test;

public class V1SigningTest extends BaseSigning {

    private static final String CREATED_BY = "SignflingerTest Created-By";
    private static final String BUILT_BY = "SignflingerTest Built-By";

    @Test
    public void simpleZipWithOneFile() throws Exception {
        super.testSimpleZipWithOneFile();
    }

    @Test
    public void incrementalSimpleFileTrustManifest() throws Exception {
        super.testIncrementalSimpleFileTrustManifest();
    }

    @Test
    public void incrementalSimpleFileNoTrustManifest() throws Exception {
        super.testIncrementalSimpleFileNoTrustManifest();
    }

    @Override
    protected SignedApkOptions getOptions(SignerConfig signerConfig, boolean trustManifest) {
        return getOptions(signerConfig, trustManifest, new HashMap<>());
    }

    private SignedApkOptions getOptions(
            SignerConfig signerConfig, boolean trustManifest, HashMap<String, String> attributes) {
        SignedApkOptions.Builder builder =
                new SignedApkOptions.Builder()
                        .setV2Enabled(false)
                        .setV1Enabled(true)
                        .setV1TrustManifest(trustManifest)
                        .setMinSdkVersion(21)
                        .setPrivateKey(signerConfig.getPrivateKey())
                        .setCertificates(signerConfig.getCertificates())
                        .setExecutor(Utils.createExecutor());

        if (attributes.containsKey(SignedApk.MANIFEST_CREATED_BY)) {
            builder.setV1CreatedBy(attributes.get(SignedApk.MANIFEST_CREATED_BY));
        }

        if (attributes.containsKey(SignedApk.MANIFEST_BUILT_BY)) {
            builder.setV1BuiltBy(attributes.get(SignedApk.MANIFEST_BUILT_BY));
        }

        return builder.build();
    }

    @Test
    public void createdByAndBuiltBy() throws Exception {
        File androidManifest = workspace.getDummyAndroidManifest();
        File zipFile = workspace.createZip(5, 2000, "testCreatedBy.zip", androidManifest);

        // Sign
        HashMap<String, String> manifestAttributes = new HashMap<>();
        manifestAttributes.put(SignedApk.MANIFEST_CREATED_BY, CREATED_BY);
        manifestAttributes.put(SignedApk.MANIFEST_BUILT_BY, BUILT_BY);
        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions options = getOptions(signerConfig, false, manifestAttributes);
        try (SignedApk signedApk = new SignedApk(zipFile, options)) {}
        // Check content of manifest file
        verifyManifestAttributes(zipFile, manifestAttributes);
        Utils.verifyApk(zipFile);

        // Incremental update (make sure Created-By and Built-By are kept).
        options = getOptions(signerConfig, true);
        try (SignedApk signedApk = new SignedApk(zipFile, options)) {
            signedApk.add(new BytesSource(new byte[100], "c", 0));
        }
        // Check content of manifest file
        verifyManifestAttributes(zipFile, manifestAttributes);
        Utils.verifyApk(zipFile);
    }

    @Test
    public void v1FilesAreCompressed() throws Exception {
        File androidManifest = workspace.getDummyAndroidManifest();
        File zipFile = workspace.createZip(1, 10, "testV1FilesAreCompressed.zip", androidManifest);

        SignerConfig signerConfig = Signers.getDefaultRSASigner(workspace);
        SignedApkOptions options = getOptions(signerConfig, false);
        try (SignedApk signedApk = new SignedApk(zipFile, options)) {}

        Map<String, Entry> entries = ZipArchive.listEntries(zipFile.toPath());

        Entry manifest = entries.get(SignedApk.MANIFEST_ENTRY_NAME);
        Assert.assertTrue("MANIFEST.MF is not compressed", manifest.isCompressed());

        Entry cert = entries.get("META-INF/CERT.SF");
        Assert.assertTrue("MANIFEST.MF is not compressed", cert.isCompressed());

        Entry certRSA = entries.get("META-INF/CERT.RSA");
        Assert.assertTrue("MANIFEST.MF is not compressed", certRSA.isCompressed());
    }

    private void verifyManifestAttributes(File zipFile, HashMap<String, String> expectedAttributes)
            throws IOException {
        try (ZipArchive zipArchive = new ZipArchive(zipFile.toPath())) {
            ByteBuffer byteBuffer = zipArchive.getContent(SignedApk.MANIFEST_ENTRY_NAME);
            Manifest manifest =
                    new Manifest(
                            new ByteArrayInputStream(byteBuffer.array(), 0, byteBuffer.limit()));
            Attributes attributes = manifest.getMainAttributes();
            for (String key : expectedAttributes.keySet()) {
                checkAttribute(attributes, key, expectedAttributes.get(key));
            }
        }
    }

    private void checkAttribute(Attributes attributes, String key, String expected) {
        String error = String.format("Wrong manifest value for key '%s'", key);
        String value = attributes.getValue(key);
        Assert.assertEquals(error, expected, value);
    }
}
