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

package signflinger;

import com.android.annotations.NonNull;
import com.android.apksig.ApkSignerEngine;
import com.android.apksig.DefaultApkSignerEngine;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import zipflinger.Archive;
import zipflinger.Source;
import zipflinger.ZipArchive;
import zipflinger.ZipInfo;
import zipflinger.ZipSource;

public class SignedApk implements Archive {

    private final ZipArchive archive;
    private final ApkSignerEngine signer;

    public SignedApk(@NonNull File file, @NonNull SignedApkOptions options)
            throws InvalidKeyException, IOException {
        this.archive = new ZipArchive(file);

        // TODO: Exploit bottom-up parsing of Android and request
        // zipflinger to not generate virtual entries on close

        DefaultApkSignerEngine.SignerConfig signerConfig =
                new DefaultApkSignerEngine.SignerConfig.Builder(
                                "CERT", options.privateKey, options.certificates)
                        .build();
        List<DefaultApkSignerEngine.SignerConfig> signerConfigs = new ArrayList<>();
        signerConfigs.add(signerConfig);
        signer =
                new DefaultApkSignerEngine.Builder(signerConfigs, 0)
                        .setOtherSignersSignaturesPreserved(false)
                        .setV1SigningEnabled(false)
                        .setV2SigningEnabled(true)
                        .setV3SigningEnabled(false)
                        // TODO: Make this configurable as part of v1 signing options.
                        .setCreatedBy("1.0 (Android)")
                        .build();
        if (options.executor != null) {
            signer.setExecutor(options.executor);
        }
    }

    /** See Archive.add documentation */
    @Override
    public void add(@NonNull Source source) throws IOException {
        archive.add(source);
    }

    /** See Archive.add documentation */
    @Override
    public void add(@NonNull ZipSource sources) throws IOException {
        archive.add(sources);
    }

    /** See Archive.delete documentation */
    @Override
    public void delete(@NonNull String name) {
        archive.delete(name);
    }

    @Override
    public void close() throws IOException {
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

        // Write block
        return outputApkSigningBlockRequest.getApkSigningBlock();
    }
}
