/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

/**
 * Fake implementation of {@link Downloader} that returns some specified content for specified
 * URLs.
 */
public class FakeDownloader implements Downloader {

    private final MockFileOp mFileOp;

    private final Map<URL, byte[]> mRegisteredFiles = Maps.newHashMap();

    public FakeDownloader(MockFileOp fop) {
        mFileOp = fop;
    }

    public void registerUrl(URL url, byte[] data) {
        mRegisteredFiles.put(url, data);
    }

    public void registerUrl(URL url, InputStream content) throws IOException {
        byte[] data = ByteStreams.toByteArray(content);
        mRegisteredFiles.put(url, data);
    }

    @NonNull
    public String getFileName(URL url) {
        return "/tmp" + url.getFile();
    }

    @Override
    @NonNull
    public InputStream downloadAndStream(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        byte[] content = mRegisteredFiles.get(url);
        if (content == null) {
            throw new IOException("no content at " + url);
        }
        InputStream toWrap = new ByteArrayInputStream(content);
        return new ReopeningInputStream(toWrap);
    }

    @Nullable
    @Override
    public Path downloadFully(@NonNull URL url, @NonNull ProgressIndicator indicator)
            throws IOException {
        File file = new File(FileOpUtils.getNewTempDir("FakeDownloader", mFileOp),
                url.getFile());
        mFileOp.recordExistingFile(file.getPath(), mRegisteredFiles.get(url));
        return mFileOp.toPath(file);
    }

    @Override
    public void downloadFully(@NonNull URL url, @NonNull File target, @Nullable String checksum,
            @NonNull ProgressIndicator indicator) throws IOException {
        mFileOp.recordExistingFile(mFileOp.getAgnosticAbsPath(target), mRegisteredFiles.get(url));
    }

    /**
     * For convenience, so we can download from the same URL more than once with this downloader,
     * reset streams instead of closing them.
     */
    static class ReopeningInputStream extends InputStream {

        private InputStream mWrapped;

        public ReopeningInputStream(InputStream toWrap) {
            toWrap.mark(Integer.MAX_VALUE);
            mWrapped = toWrap;
        }

        @Override
        public int read() throws IOException {
            return mWrapped.read();
        }

        @Override
        public void close() throws IOException {
            mWrapped.reset();
        }

        public void reallyClose() throws IOException {
            mWrapped.close();
        }
    }
}
