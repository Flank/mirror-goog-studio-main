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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.apksig.ApkSignerEngine;
import com.android.apksig.DefaultApkSignerEngine;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.zipflinger.Archive;
import com.android.zipflinger.BytesSource;
import com.android.zipflinger.Source;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipInfo;
import com.android.zipflinger.ZipSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.Deflater;

public class SignedApk implements Archive {

    private final ZipArchive archive;
    private final ApkSignerEngine signer;
    private final SignedApkOptions options;
    static final String MANIFEST_ENTRY_NAME = "META-INF/MANIFEST.MF";
    static final String MANIFEST_CREATED_BY = "Created-By";
    static final String MANIFEST_BUILT_BY = "Built-By";
    static final String MANIFEST_VERSION = "Manifest-Version";

    public SignedApk(@NonNull File file, @NonNull SignedApkOptions options)
            throws InvalidKeyException, IOException {
        this.options = options;
        if (options.v1Enabled) {
            // Improve V1 signing performance by briefly caching zip entry content.
            this.archive = new CachedZipArchive(file);
        } else {
            this.archive = new ZipArchive(file);
        }

        // TODO: Exploit bottom-up parsing of Android and request
        // zipflinger to not generate virtual entries on close

        DefaultApkSignerEngine.SignerConfig signerConfig =
                new DefaultApkSignerEngine.SignerConfig.Builder(
                                "CERT", options.privateKey, options.certificates)
                        .build();
        List<DefaultApkSignerEngine.SignerConfig> signerConfigs = new ArrayList<>();
        signerConfigs.add(signerConfig);
        signer =
                new DefaultApkSignerEngine.Builder(signerConfigs, options.minSdkVersion)
                        .setV1SigningEnabled(options.v1Enabled)
                        .setV2SigningEnabled(options.v2Enabled)
                        .setV3SigningEnabled(false)
                        .setCreatedBy(options.v1CreatedBy)
                        .setOtherSignersSignaturesPreserved(false)
                        .build();
        if (options.executor != null) {
            signer.setExecutor(options.executor);
        }

        initWithV1();
    }

    private void initWithV1() throws IOException {
        if (!options.v1Enabled) {
            return;
        }

        if (!options.v1TrustManifest) {
            archive.delete(MANIFEST_ENTRY_NAME);
        }

        ByteBuffer manifestByteBuffer = archive.getContent(MANIFEST_ENTRY_NAME);
        byte[] manifestBytes;
        if (manifestByteBuffer != null) {
            manifestBytes = new byte[manifestByteBuffer.remaining()];
            manifestByteBuffer.get(manifestBytes);
        } else {
            manifestBytes = createDefaultManifest();
            BytesSource bytesSource =
                    new BytesSource(manifestBytes, MANIFEST_ENTRY_NAME, Deflater.NO_COMPRESSION);
            archive.add(bytesSource);
        }

        Set<String> filesToSign = new HashSet<>(archive.listEntries());
        Set<String> signedEntries = signer.initWith(manifestBytes, filesToSign);
        filesToSign.removeAll(signedEntries);
        for (String entryName : filesToSign) {
            ApkSignerEngine.InspectJarEntryRequest req = signer.outputJarEntry(entryName);
            processRequest(req);
        }
    }

    /** See Archive.add documentation */
    @Override
    public void add(@NonNull BytesSource source) throws IOException {
        archive.add(source);
        if (options.v1Enabled) {
            ApkSignerEngine.InspectJarEntryRequest req = signer.outputJarEntry(source.getName());
            processRequest(req);
        }
    }

    /** See Archive.add documentation */
    @Override
    public void add(@NonNull ZipSource sources) throws IOException {
        archive.add(sources);
        if (options.v1Enabled) {
            for (Source source : sources.getSelectedEntries()) {
                ApkSignerEngine.InspectJarEntryRequest req =
                        signer.outputJarEntry(source.getName());
                processRequest(req);
            }
        }
    }

    /** See Archive.delete documentation */
    @Override
    public void delete(@NonNull String name) {
        archive.delete(name);
        if (options.v1Enabled) {
            signer.outputJarEntryRemoved(name);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            finishV1();
            finishV2();
        } finally {
            signer.close();
            if (!archive.isClosed()) {
                archive.close();
            }
        }
    }

    private byte[] createDefaultManifest() throws IOException {
        Manifest manifest = new Manifest();
        Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.putValue(MANIFEST_CREATED_BY, options.v1CreatedBy);
        mainAttributes.putValue(MANIFEST_BUILT_BY, options.v1BuiltBy);
        mainAttributes.putValue(MANIFEST_VERSION, "1.0");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        manifest.write(os);
        return os.toByteArray();
    }

    private void finishV2() throws IOException {
        if (!options.v2Enabled) {
            return;
        }

        ZipInfo zipInfo = archive.closeWithInfo();
        try (RandomAccessFile raf = new RandomAccessFile(archive.getFile(), "rw")) {
            byte[] sigBlock = v2Sign(raf, zipInfo);
            ApkSigningBlock.addToArchive(raf, sigBlock, zipInfo);
        } catch (IOException
                | NoSuchAlgorithmException
                | InvalidKeyException
                | SignatureException
                | ApkFormatException e) {
            throw new IllegalStateException(e);
        }

    }

    private void processRequest(@Nullable ApkSignerEngine.InspectJarEntryRequest req)
            throws IOException {
        if (req == null) {
            return;
        }
        String name = req.getEntryName();
        ByteBuffer content = archive.getContent(name);
        if (content == null) {
            String err = String.format("Cannot find and therefore inspect entry %s.", name);
            throw new IllegalStateException(err);
        }
        req.getDataSink().consume(content);
        req.done();
    }

    private void finishV1() throws IOException {
        if (!options.v1Enabled) {
            return;
        }

        // Check whether we need to output additional JAR entries which comprise the v1 signature
        ApkSignerEngine.OutputJarSignatureRequest addV1SignatureRequest;
        try {
            addV1SignatureRequest = signer.outputJarEntries();
        } catch (Exception e) {
            throw new IOException("Failed to generate v1 signature", e);
        }
        if (addV1SignatureRequest == null) {
            return;
        }

        for (ApkSignerEngine.OutputJarSignatureRequest.JarEntry entry :
                addV1SignatureRequest.getAdditionalJarEntries()) {
            archive.delete(entry.getName());
            BytesSource source =
                    new BytesSource(entry.getData(), entry.getName(), Deflater.BEST_SPEED);
            archive.add(source);
            ApkSignerEngine.InspectJarEntryRequest request = signer.outputJarEntry(entry.getName());
            processRequest(request);
        }
        addV1SignatureRequest.done();
    }

    @NonNull
    private byte[] v2Sign(@NonNull RandomAccessFile raf, @NonNull ZipInfo zipInfo)
            throws ApkFormatException, SignatureException, NoSuchAlgorithmException,
                    InvalidKeyException, IOException {
        DataSource beforeCentralDir =
                DataSources.asDataSource(raf, zipInfo.payload.first, zipInfo.payload.size());
        DataSource centralDir = DataSources.asDataSource(raf, zipInfo.cd.first, zipInfo.cd.size());
        DataSource eocd = DataSources.asDataSource(raf, zipInfo.eocd.first, zipInfo.eocd.size());

        ApkSignerEngine.OutputApkSigningBlockRequest outputApkSigningBlockRequest =
                signer.outputZipSections(beforeCentralDir, centralDir, eocd);
        outputApkSigningBlockRequest.done();

        // Write block
        return outputApkSigningBlockRequest.getApkSigningBlock();
    }
}
