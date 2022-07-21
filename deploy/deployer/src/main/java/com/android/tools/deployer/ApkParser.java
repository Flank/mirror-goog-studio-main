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

import com.android.SdkConstants;
import com.android.tools.deployer.model.Apk;
import com.android.tools.manifest.parser.ManifestInfo;
import com.android.tools.tracer.Trace;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkParser {
    public static final int EOCD_SIGNATURE = 0x06054b50;
    private static final byte[] SIGNATURE_BLOCK_MAGIC = "APK Sig Block 42".getBytes();
    private static final long USHRT_MAX = 65535;
    public static final int EOCD_SIZE = 22;
    static final String NO_MANIFEST_MSG = "Missing AndroidManifest.xml entry";
    private static final String NO_MANIFEST_MSG_DETAILS = "in '%s'";

    public static class ApkArchiveMap {
        public static final long UNINITIALIZED = -1;
        long cdOffset = UNINITIALIZED;
        long cdSize = UNINITIALIZED;

        long signatureBlockOffset = UNINITIALIZED;
        long signatureBlockSize = UNINITIALIZED;
    }

    /** A class to manipulate .apk files. */
    public ApkParser() {}

    public List<Apk> parsePaths(List<String> paths) throws DeployerException {
        try (Trace ignored = Trace.begin("parseApks")) {
            List<Apk> newFiles = new ArrayList<>();
            for (String apkPath : paths) {
                newFiles.add(parse(apkPath));
            }
            return newFiles;
        } catch (IOException e) {
            throw DeployerException.parseFailed(e.getMessage());
        }
    }

    public ManifestInfo getApkDetails(String path) throws IOException {
        ManifestInfo manifestInfo;
        try (ZipFile zipFile = new ZipFile(path)) {
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
            if (manifestEntry == null) {
                StringBuilder msg = new StringBuilder(NO_MANIFEST_MSG);
                msg.append(" ");
                msg.append(String.format(Locale.US, NO_MANIFEST_MSG_DETAILS, path));
                throw new IOException(msg.toString());
            }
            InputStream stream = zipFile.getInputStream(manifestEntry);
            manifestInfo = ManifestInfo.parseBinaryFromStream(stream);
        }
        if (manifestInfo.getApplicationId() == null) {
            throw new IllegalArgumentException("Package name was not found in manifest");
        }
        return manifestInfo;
    }

    /**
     * Obtains the {@link File} for the apk passed in apkPath. If apkPath represents a path within a
     * jar file, the apk will be extracted and the returned {@link File} will point to a temporary
     * location.
     */
    private File getApkFileFromPath(String apkPath) throws IOException {
        if (apkPath.startsWith("jar:")) {
            int separatorIndex = apkPath.lastIndexOf('!');
            if (separatorIndex != -1) {
                String subPath = apkPath.substring(separatorIndex + 1);
                try (FileSystem fileSystem =
                        FileSystems.newFileSystem(URI.create(apkPath), Collections.emptyMap())) {
                    Path outputApk = Files.createTempFile("extracted", ".apk");
                    FileUtils.copyFile(fileSystem.getPath(subPath), outputApk);
                    return outputApk.toFile();
                }
            }
        }

        return new File(apkPath);
    }

    Apk parse(String apkPath) throws IOException, DeployerException {
        File file = getApkFileFromPath(apkPath);
        String absolutePath = file.getAbsolutePath();
        String digest;
        List<ZipUtils.ZipEntry> zipEntries;
        try (RandomAccessFile raf = new RandomAccessFile(absolutePath, "r");
             FileChannel fileChannel = raf.getChannel()) {
            ApkArchiveMap map = new ApkArchiveMap();
            findCDLocation(fileChannel, map);
            findSignatureLocation(fileChannel, map);
            digest = generateDigest(raf, map);
            zipEntries = readZipEntries(raf, map);
        }
        ManifestInfo manifest = getApkDetails(absolutePath);
        String apkFileName = manifest.getSplitName() == null
                             ? "base.apk"
                             : "split_" + manifest.getSplitName() + ".apk";
        Apk.Builder builder =
                Apk.builder()
                        .setName(apkFileName)
                        .setChecksum(digest)
                        .setPath(absolutePath)
                        .setPackageName(manifest.getApplicationId())
                        .setTargetPackages(manifest.getInstrumentationTargetPackages())
                        .setActivities(manifest.activities())
                        .setServices(manifest.services())
                        .setSdkLibraries(manifest.getSdkLibraries());

        for (ZipUtils.ZipEntry entry : zipEntries) {
            // Native libraries are stored in the APK under lib/<ABI>/
            if (entry.name.startsWith("lib/")) {
                String[] paths = entry.name.split("/");
                if (paths.length > 1) {
                    builder.addLibraryAbi(paths[1]);
                }
            }
            builder.addApkEntry(entry);
        }

        return builder.build();
    }

    public static void findSignatureLocation(FileChannel channel, ApkArchiveMap map) {
        try {
            // Search the Signature Block magic number
            ByteBuffer signatureBlockMagicNumber =
                    ByteBuffer.allocate(SIGNATURE_BLOCK_MAGIC.length);
            channel.read(signatureBlockMagicNumber, map.cdOffset - SIGNATURE_BLOCK_MAGIC.length);
            signatureBlockMagicNumber.rewind();
            if (!signatureBlockMagicNumber.equals(ByteBuffer.wrap(SIGNATURE_BLOCK_MAGIC))) {
                // This is not a signature block magic number.
                return;
            }

            // The magic number is not enough, we need to make sure the upper and lower size are the same.
            ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(sizeBuffer, map.cdOffset - SIGNATURE_BLOCK_MAGIC.length - Long.BYTES);
            sizeBuffer.rewind();
            long lowerSignatureBlockSize = sizeBuffer.getLong();

            sizeBuffer.rewind();
            channel.read(sizeBuffer, map.cdOffset - Long.BYTES - lowerSignatureBlockSize);
            sizeBuffer.rewind();
            long upperSignatureBlocSize = sizeBuffer.getLong();

            if (lowerSignatureBlockSize != upperSignatureBlocSize) {
                return;
            }

            // Everything matches (signature and upper/lower size, this is a confirmed signature block;
            map.signatureBlockOffset = map.cdOffset - Long.BYTES - lowerSignatureBlockSize;
            map.signatureBlockSize = lowerSignatureBlockSize;
        } catch (IOException e) {
            // It is not an error if there is not V2 signature.
        }
    }

    public static void findCDLocation(FileChannel channel, ApkArchiveMap map)
            throws IOException, DeployerException {
        long fileSize = channel.size();
        if (fileSize < EOCD_SIZE) {
            throw DeployerException.parseFailed("File is too small to be a valid zip file");
        }
        // Search the End of Central Directory Record
        // The End of Central Directory record size is 22 bytes if the comment section size is zero.
        // The comment section can be of any size, up to 65535 since it is stored on two bytes.
        // We start at the likely position of the beginning of the EoCD position and backtrack toward the
        // beginning of the buffer.

        // Fast path where no comment where used in the eocd.
        ByteBuffer eocdBuffer = ByteBuffer.allocate(EOCD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        channel.read(eocdBuffer, fileSize - EOCD_SIZE);
        eocdBuffer.rewind();
        if (readEOCD(map, eocdBuffer)) {
            return;
        }

        // Slow path where 65KiB of data needs to be retrieved from the zip file.
        ByteBuffer endofFileBuffer =
                ByteBuffer.allocate((int) Math.min(fileSize, USHRT_MAX + EOCD_SIZE))
                        .order(ByteOrder.LITTLE_ENDIAN);
        channel.read(endofFileBuffer, fileSize - endofFileBuffer.capacity());
        endofFileBuffer.position(endofFileBuffer.capacity() - EOCD_SIZE);
        while (true) {
            if (readEOCD(map, endofFileBuffer)) {
                return;
            }

            if (endofFileBuffer.position() - 5 < 0) {
                throw DeployerException.parseFailed("Unable to find apk's ECOD signature");
            }
            endofFileBuffer.position(endofFileBuffer.position() - 5);
        }
    }

    private static boolean readEOCD(ApkArchiveMap map, ByteBuffer buffer) {
        if (buffer.getInt() != EOCD_SIGNATURE) {
            return false;
        }
        buffer.position(buffer.position() + Short.BYTES * 4);
        map.cdSize = ZipUtils.uintToLong(buffer.getInt());
        map.cdOffset = ZipUtils.uintToLong(buffer.getInt());
        return true;
    }

    private List<ZipUtils.ZipEntry> readZipEntries(
            RandomAccessFile randomAccessFile, ApkArchiveMap map) throws IOException {
        ByteBuffer buffer;
        // There is no method to unmap a MappedByteBuffer so we cannot use FileChannel.map() on Windows.
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            byte[] cdContent = new byte[(int) map.cdSize];
            randomAccessFile.seek(map.cdOffset);
            randomAccessFile.readFully(cdContent);
            buffer = ByteBuffer.wrap(cdContent);
        } else {
            buffer =
                    randomAccessFile
                            .getChannel()
                            .map(FileChannel.MapMode.READ_ONLY, map.cdOffset, map.cdSize);
        }
        return ZipUtils.readZipEntries(buffer);
    }

    private String generateDigest(RandomAccessFile randomAccessFile, ApkArchiveMap map)
            throws IOException {
        byte[] sigContent;
        if (map.signatureBlockOffset != ApkArchiveMap.UNINITIALIZED) {
            sigContent = new byte[(int) map.signatureBlockSize];
            randomAccessFile.seek(map.signatureBlockOffset);
            randomAccessFile.readFully(sigContent);
        } else {
            sigContent = new byte[(int) map.cdSize];
            randomAccessFile.seek(map.cdOffset);
            randomAccessFile.readFully(sigContent);
        }
        ByteBuffer buffer = ByteBuffer.wrap(sigContent);
        return ZipUtils.digest(buffer);
    }
}
