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

package com.android.apkzlib.zfile;

import com.android.apkzlib.zip.AlignmentRule;
import com.android.apkzlib.zip.AlignmentRules;
import com.android.apkzlib.zip.StoredEntry;
import com.android.apkzlib.zip.ZFile;
import com.android.apkzlib.zip.ZFileOptions;
import com.google.common.base.Preconditions;
import com.google.common.io.Closer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@link ApkCreator} that uses {@link ZFileOptions} to generate the APK.
 */
class ApkZFileCreator implements ApkCreator {

    /**
     * Suffix for native libraries.
     */
    private static final String NATIVE_LIBRARIES_SUFFIX = ".so";

    /**
     * Shared libraries are alignment at 4096 boundaries.
     */
    private static final AlignmentRule SO_RULE =
            AlignmentRules.constantForSuffix(NATIVE_LIBRARIES_SUFFIX, 4096);

    /**
     * The zip file.
     */
    @Nonnull
    private final ZFile mZip;

    /**
     * Has the zip file been closed?
     */
    private boolean mClosed;

    /**
     * Predicate defining which files should not be compressed.
     */
    @Nonnull
    private final Predicate<String> mNoCompressPredicate;

    /**
     * Creates a new creator.
     *
     * @param creationData the data needed to create the APK
     * @param options zip file options
     * @throws IOException failed to create the zip
     */
    ApkZFileCreator(
            @Nonnull ApkCreatorFactory.CreationData creationData,
            @Nonnull ZFileOptions options)
            throws IOException {

        switch (creationData.getNativeLibrariesPackagingMode()) {
            case COMPRESSED:
                mNoCompressPredicate = creationData.getNoCompressPredicate();
                break;
            case UNCOMPRESSED_AND_ALIGNED:
                mNoCompressPredicate =
                        creationData.getNoCompressPredicate().or(
                                name -> name.endsWith(NATIVE_LIBRARIES_SUFFIX));
                options.setAlignmentRule(
                        AlignmentRules.compose(SO_RULE, options.getAlignmentRule()));
                break;
            default:
                throw new AssertionError();
        }

        mZip = ZFiles.apk(
                creationData.getApkPath(),
                options,
                creationData.getPrivateKey(),
                creationData.getCertificate(),
                creationData.isV1SigningEnabled(),
                creationData.isV2SigningEnabled(),
                creationData.getBuiltBy(),
                creationData.getCreatedBy(),
                creationData.getMinSdkVersion());
        mClosed = false;
    }

    @Override
    public void writeZip(@Nonnull File zip, @Nullable Function<String, String> transform,
            @Nullable Predicate<String> isIgnored) throws IOException {
        Preconditions.checkState(!mClosed, "mClosed == true");
        Preconditions.checkArgument(zip.isFile(), "!zip.isFile()");

        Closer closer = Closer.create();
        try {
            ZFile toMerge = closer.register(new ZFile(zip));

            Predicate<String> predicate;
            if (isIgnored == null) {
                predicate = s -> false;
            } else {
                predicate = isIgnored;
            }

            mZip.mergeFrom(toMerge, predicate);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    @Override
    public void writeFile(@Nonnull File inputFile, @Nonnull String apkPath) throws IOException {
        Preconditions.checkState(!mClosed, "mClosed == true");

        boolean mayCompress = !mNoCompressPredicate.test(apkPath);

        Closer closer = Closer.create();
        try {
            FileInputStream inputFileStream = closer.register(new FileInputStream(inputFile));
            mZip.add(apkPath, inputFileStream, mayCompress);
        } catch (IOException e) {
            throw closer.rethrow(e, IOException.class);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    @Override
    public void deleteFile(@Nonnull String apkPath) throws IOException {
        Preconditions.checkState(!mClosed, "mClosed == true");

        StoredEntry entry = mZip.get(apkPath);
        if (entry != null) {
            entry.delete();
        }
    }

    @Override
    public void close() throws IOException {
        if (mClosed) {
            return;
        }

        mZip.close();
        mClosed = true;
    }
}
