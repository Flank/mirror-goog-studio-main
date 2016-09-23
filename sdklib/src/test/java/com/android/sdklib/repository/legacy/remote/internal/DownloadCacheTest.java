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

package com.android.sdklib.repository.legacy.remote.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.http.Header;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DownloadCacheTest {
    private static final File ANDROID_FOLDER = new File("/android-home");
    private OutputStreamMockFileOp mFileOp;

    static class OutputStreamMockFileOp extends MockFileOp {
        Set<File> mWrittenFiles = new LinkedHashSet<>();

        @NonNull
        @Override
        public OutputStream newFileOutputStream(@NonNull File file, boolean append)
                throws IOException {
            mWrittenFiles.add(file);
            return super.newFileOutputStream(file, append);
        }

        @NonNull
        @Override
        public OutputStream newFileOutputStream(@NonNull File file) throws IOException {
            mWrittenFiles.add(file);
            return super.newFileOutputStream(file);
        }

        @Override
        public void reset() {
            super.reset();
            mWrittenFiles.clear();
        }

        public String getWrittenFiles() {
            StringBuilder sb = new StringBuilder();
            for (File f : mWrittenFiles) {
                sb.append('<').append(getAgnosticAbsPath(f)).append(": ");
                byte[] data = super.getContent(f);
                if (data == null) {
                    sb.append("(stream not closed properly)>");
                } else {
                    sb.append('\'').append(new String(data)).append("'>");
                }
            }
            return sb.toString();
        }
    }

    /**
     * A private version of DownloadCache that never calls {@link UrlOpener}.
     */
    private static class NoDownloadCache extends DownloadCache {

        private final Map<String, Pair<InputStream, Integer>> mReplies =
            new HashMap<>();

        public NoDownloadCache(@NonNull FileOp fileOp, @NonNull Strategy strategy) {
            super(ANDROID_FOLDER, fileOp, strategy, new FakeSettingsController(false));
        }

        @Override
        protected Pair<InputStream, URLConnection> openUrl(
                @NonNull String url,
                boolean needsMarkResetSupport,
                @Nullable Header[] headers) throws IOException {

            Pair<InputStream, Integer> reply = mReplies.get(url);
            if (reply != null) {
                return Pair.of(reply.getFirst(),
                        new HttpURLConnection(new URL(url)) {
                            @Override
                            public void disconnect() {}

                            @Override
                            public boolean usingProxy() {
                                return false;
                            }

                            @Override
                            public void connect() throws IOException {}

                            @Override
                            public int getResponseCode() throws IOException {
                                return reply.getSecond();
                            }
                        });
            }

            // http-client's behavior is to return a FNF instead of 404.
            throw new FileNotFoundException(url);
        }

        public void registerResponse(@NonNull String url, int httpCode, @Nullable String content) {
            InputStream is = null;
            if (content != null) {
                is = new ByteArrayInputStream(content.getBytes(Charsets.UTF_8));
            }

            Pair<InputStream, Integer> reply = Pair.of(is, httpCode);

            mReplies.put(url, reply);
        }
    }

    @Before
    public void setUp() {
        mFileOp = new OutputStreamMockFileOp();
    }

    @Test
    public void testMissingResource() throws Exception {
        // Downloads must fail when using the only-cache strategy and there's nothing in the cache.
        // In that case, it returns null to indicate the resource is simply not found.
        // Since the mock implementation always returns a 404 and no stream, there is no
        // difference between the various cache strategies.

        mFileOp.reset();
        NoDownloadCache d1 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.ONLY_CACHE);
        InputStream is1 = d1.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is1).isNull();
        assertThat(mFileOp.hasRecordedExistingFolder(d1.getCacheRoot())).isTrue();
        assertThat(mFileOp.getWrittenFiles()).isEmpty();

        // HTTP-Client's behavior is to return a FNF instead of 404 so we'll try that first
        mFileOp.reset();
        NoDownloadCache d2 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.DIRECT);

        try {
            d2.openCachedUrl("http://www.example.com/download1.xml");
            fail("Expected: NoDownloadCache.openCachedUrl should have thrown a FileNotFoundException");
        } catch (FileNotFoundException e) {
            assertThat(e.getMessage()).isEqualTo("http://www.example.com/download1.xml");
        }
        assertThat(mFileOp.getWrittenFiles()).isEmpty();

        // Try again but this time we'll define a 404 reply to test the rest of the code path.
        mFileOp.reset();
        d2.registerResponse("http://www.example.com/download1.xml", 404, null);
        InputStream is2 = d2.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is2).isNull();
        assertThat(mFileOp.getWrittenFiles()).isEmpty();

        mFileOp.reset();
        NoDownloadCache d3 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.SERVE_CACHE);
        d3.registerResponse("http://www.example.com/download1.xml", 404, null);
        InputStream is3 = d3.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is3).isNull();
        assertThat(mFileOp.getWrittenFiles()).isEmpty();

        mFileOp.reset();
        NoDownloadCache d4 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.FRESH_CACHE);
        d4.registerResponse("http://www.example.com/download1.xml", 404, null);
        InputStream is4 = d4.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is4).isNull();
        assertThat(mFileOp.getWrittenFiles()).isEmpty();
    }

    @Test
    public void testExistingResource() throws Exception {
        // The resource exists but only-cache doesn't hit the network so it will
        // fail when the resource is not cached.
        mFileOp.reset();
        NoDownloadCache d1 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.ONLY_CACHE);
        d1.registerResponse("http://www.example.com/download1.xml", 200, "Blah blah blah");
        InputStream is1 = d1.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is1).isNull();
        assertThat(mFileOp.hasRecordedExistingFolder(d1.getCacheRoot())).isTrue();
        assertThat(mFileOp.getWrittenFiles()).isEmpty();

        // HTTP-Client's behavior is to return a FNF instead of 404 so we'll try that first
        mFileOp.reset();
        NoDownloadCache d2 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.DIRECT);
        d2.registerResponse("http://www.example.com/download1.xml", 200, "Blah blah blah");
        InputStream is2 = d2.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is2).isNotNull();
        assertThat(new BufferedReader(new InputStreamReader(is2, Charsets.UTF_8)).readLine())
                .isEqualTo("Blah blah blah");
        assertThat(mFileOp.getWrittenFiles()).isEmpty();

        mFileOp.reset();
        NoDownloadCache d3 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.SERVE_CACHE);
        d3.registerResponse("http://www.example.com/download1.xml", 200, "Blah blah blah");
        InputStream is3 = d3.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is3).isNotNull();
        assertThat(new BufferedReader(new InputStreamReader(is3, Charsets.UTF_8)).readLine())
                .isEqualTo("Blah blah blah");
        assertThat(sanitize(d3, mFileOp.getWrittenFiles())).isEqualTo(
                "<$CACHE/sdkbin-1_9b8dc757-download1_xml: 'Blah blah blah'>" +
                 "<$CACHE/sdkinf-1_9b8dc757-download1_xml: '### Meta data for SDK Manager cache. Do not modify.\n" +
                  "#<creation timestamp>\n" +
                  "URL=http\\://www.example.com/download1.xml\n" +
                  "Status-Code=200\n" +
                "'>");

        mFileOp.reset();
        NoDownloadCache d4 = new NoDownloadCache(mFileOp,
                DownloadCache.Strategy.FRESH_CACHE);
        d4.registerResponse("http://www.example.com/download1.xml", 200, "Blah blah blah");
        InputStream is4 = d4.openCachedUrl("http://www.example.com/download1.xml");
        assertThat(is4).isNotNull();
        assertThat(new BufferedReader(new InputStreamReader(is4, Charsets.UTF_8)).readLine())
                .isEqualTo("Blah blah blah");
        assertThat(sanitize(d4, mFileOp.getWrittenFiles())).isEqualTo(
                "<$CACHE/sdkbin-1_9b8dc757-download1_xml: 'Blah blah blah'>" +
                 "<$CACHE/sdkinf-1_9b8dc757-download1_xml: '### Meta data for SDK Manager cache. Do not modify.\n" +
                  "#<creation timestamp>\n" +
                  "URL=http\\://www.example.com/download1.xml\n" +
                  "Status-Code=200\n" +
                "'>");
    }

    @Test
    public void testCachedResource() throws Exception {
        mFileOp.reset();
        NoDownloadCache d1 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.ONLY_CACHE);
        d1.registerResponse("http://www.example.com/download1.xml", 200, "This is the new content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d1.getCacheRoot(), "sdkbin-1_9b8dc757-download1_xml")),
                123456L,
                "This is the cached content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d1.getCacheRoot(), "sdkinf-1_9b8dc757-download1_xml")),
                123456L,
                "URL=http\\://www.example.com/download1.xml\n" +
                        "Status-Code=200\n");
        InputStream is1 = d1.openCachedUrl("http://www.example.com/download1.xml");
        // Only-cache strategy returns the value from the cache, not the actual resource.
        assertThat(new BufferedReader(new InputStreamReader(is1, Charsets.UTF_8)).readLine())
                .isEqualTo("This is the cached content");
        assertThat(mFileOp.hasRecordedExistingFolder(d1.getCacheRoot())).isTrue();
        // The cache hasn't been modified, only read
        assertThat(mFileOp.getWrittenFiles()).isEmpty();
    }

    @Test
    public void testCachedResource2() throws Exception {
        // Direct ignores the cache.
        mFileOp.reset();
        NoDownloadCache d2 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.DIRECT);
        d2.registerResponse("http://www.example.com/download1.xml", 200, "This is the new content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d2.getCacheRoot(), "sdkbin-1_9b8dc757-download1_xml")),
                123456L,
                "This is the cached content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d2.getCacheRoot(), "sdkinf-1_9b8dc757-download1_xml")),
                123456L,
                "URL=http\\://www.example.com/download1.xml\n" +
                        "Status-Code=200\n");
        InputStream is2 = d2.openCachedUrl("http://www.example.com/download1.xml");
        // Direct strategy ignores the cache.
        assertThat(new BufferedReader(new InputStreamReader(is2, Charsets.UTF_8)).readLine())
                .isEqualTo("This is the new content");
        assertThat(mFileOp.hasRecordedExistingFolder(d2.getCacheRoot())).isTrue();
        // Direct strategy doesn't update the cache.
        assertThat(mFileOp.getWrittenFiles()).isEmpty();
    }

    @Test
    public void testCachedResource3() throws Exception {
        // Serve-cache reads from the cache if available, ignoring its freshness (here the timestamp
        // is way older than the 10-minute freshness encoded in the DownloadCache.)
        mFileOp.reset();
        NoDownloadCache d3 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.SERVE_CACHE);
        d3.registerResponse("http://www.example.com/download1.xml", 200, "This is the new content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d3.getCacheRoot(), "sdkbin-1_9b8dc757-download1_xml")),
                123456L,
                "This is the cached content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d3.getCacheRoot(), "sdkinf-1_9b8dc757-download1_xml")),
                123456L,
                "URL=http\\://www.example.com/download1.xml\n" +
                        "Status-Code=200\n");
        InputStream is3 = d3.openCachedUrl("http://www.example.com/download1.xml");
        // We get content from the cache.
        assertThat(new BufferedReader(new InputStreamReader(is3, Charsets.UTF_8)).readLine())
                .isEqualTo("This is the cached content");
        assertThat(mFileOp.hasRecordedExistingFolder(d3.getCacheRoot())).isTrue();
        // Cache isn't updated since nothing fresh was read.
        assertThat(mFileOp.getWrittenFiles()).isEmpty();
    }

    @Test
    public void testCachedResource4() throws Exception {
        // fresh-cache reads the cache, finds it stale (here the timestamp
        // is way older than the 10-minute freshness encoded in the DownloadCache)
        // and will fetch the new resource instead and update the cache.
        mFileOp.reset();
        NoDownloadCache d4 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.FRESH_CACHE);
        d4.registerResponse("http://www.example.com/download1.xml", 200, "This is the new content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d4.getCacheRoot(), "sdkbin-1_9b8dc757-download1_xml")),
                123456L,
                "This is the cached content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(
                        FileOpUtils.append(d4.getCacheRoot(), "sdkinf-1_9b8dc757-download1_xml")),
                123456L,
                "URL=http\\://www.example.com/download1.xml\n" +
                        "Status-Code=200\n");
        InputStream is4 = d4.openCachedUrl("http://www.example.com/download1.xml");
        // Cache is discarded, actual resource is returned.
        assertThat(new BufferedReader(new InputStreamReader(is4, Charsets.UTF_8)).readLine())
                .isEqualTo("This is the new content");
        assertThat(mFileOp.hasRecordedExistingFolder(d4.getCacheRoot())).isTrue();
        // Cache isn updated since something fresh was read.
        assertThat(sanitize(d4, mFileOp.getWrittenFiles())).isEqualTo(
                "<$CACHE/sdkbin-1_9b8dc757-download1_xml: 'This is the new content'>" +
                        "<$CACHE/sdkinf-1_9b8dc757-download1_xml: '### Meta data for SDK Manager cache. Do not modify.\n"
                        +
                        "#<creation timestamp>\n" +
                        "URL=http\\://www.example.com/download1.xml\n" +
                        "Status-Code=200\n" +
                        "'>");
    }

    @Test
    public void testCachedResource5() throws Exception {
        // fresh-cache reads the cache, finds it still valid stale (less than 10-minute old),
        // and uses the cached resource.
        mFileOp.reset();
        NoDownloadCache d5 = new NoDownloadCache(mFileOp, DownloadCache.Strategy.FRESH_CACHE);
        d5.registerResponse("http://www.example.com/download1.xml", 200, "This is the new content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(FileOpUtils.append(d5.getCacheRoot(), "sdkbin-1_9b8dc757-download1_xml")),
                System.currentTimeMillis() - 1000,
                "This is the cached content");
        mFileOp.recordExistingFile(
                mFileOp.getAgnosticAbsPath(FileOpUtils.append(d5.getCacheRoot(), "sdkinf-1_9b8dc757-download1_xml")),
                System.currentTimeMillis() - 1000,
                "URL=http\\://www.example.com/download1.xml\n" +
                "Status-Code=200\n");
        InputStream is5 = d5.openCachedUrl("http://www.example.com/download1.xml");
        // Cache is used.
        assertThat(new BufferedReader(new InputStreamReader(is5, Charsets.UTF_8)).readLine())
                .isEqualTo("This is the cached content");
        assertThat(mFileOp.hasRecordedExistingFolder(d5.getCacheRoot())).isTrue();
        // Cache isn't updated since nothing fresh was read.
        assertThat(mFileOp.getWrittenFiles()).isEmpty();
    }

    @Nullable
    private String sanitize(@NonNull DownloadCache dc, @Nullable String msg) {
        if (msg != null) {
            msg = msg.replace("\r\n", "\n");

            String absRoot = mFileOp.getAgnosticAbsPath(dc.getCacheRoot());
            msg = msg.replace(absRoot, "$CACHE");

            // Cached files also contain a creation timestamp which we need to find and remove.
            msg = msg.replaceAll("\n#[A-Z][A-Za-z0-9: ]+20[0-9]{2}\n", "\n#<creation timestamp>\n");
        }
        return msg;
    }
}
