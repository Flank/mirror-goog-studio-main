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

package com.android.repository.impl.remote;

import static com.android.repository.testframework.FakePackage.FakeRemotePackage;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.manager.RemoteRepoLoader;
import com.android.repository.impl.manager.RemoteRepoLoaderImpl;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import junit.framework.TestCase;

/** Tests for {@link RemoteRepoLoaderImpl} */
public class RemoteRepoLoaderImplTest extends TestCase {

    public void testRemoteRepo() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source))), null, null);
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        RemotePackage p1 = pkgs.get("dummy;foo");
        assertEquals(new Revision(1, 2, 3), p1.getVersion());
        assertEquals("the license text", p1.getLicense().getValue().trim());
        assertEquals(3, ((RemotePackageImpl) p1).getAllArchives().size());
        assertTrue(p1.getTypeDetails() instanceof TypeDetails.GenericType);
        Collection<Archive> archives = ((RemotePackageImpl) p1).getAllArchives();
        assertEquals(3, archives.size());
        Iterator<Archive> archiveIter = ((RemotePackageImpl) p1).getAllArchives().iterator();
        Archive a1 = archiveIter.next();
        assertEquals(1234, a1.getComplete().getSize());
        Archive a2 = archiveIter.next();
        Iterator<Archive.PatchType> patchIter = a2.getAllPatches().iterator();
        Archive.PatchType patch = patchIter.next();
        assertEquals(new Revision(1, 3, 2), patch.getBasedOn().toRevision());
        patch = patchIter.next();
        assertEquals(new Revision(2), patch.getBasedOn().toRevision());
    }

    public void testChannels() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                                                             "Source UI Name", true,
                                                             ImmutableSet.of(RepoManager.getCommonModule(),
                                                                     RepoManager.getGenericModule()),
                                                             null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepoWithChannels.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
          new FakeRepositorySourceProvider(ImmutableList.of(source))), null, null);
        FakeSettingsController settings = new FakeSettingsController(false);
        Map<String, RemotePackage> pkgs = loader
          .fetchPackages(progress, downloader, settings);
        progress.assertNoErrorsOrWarnings();

        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 3), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("dummy;bar").getVersion());

        settings.setChannel(Channel.create(1));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 4), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("dummy;bar").getVersion());

        settings.setChannel(Channel.create(2));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 5), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 6), pkgs.get("dummy;bar").getVersion());

        settings.setChannel(Channel.create(3));
        pkgs = loader.fetchPackages(progress, downloader, settings);
        assertEquals(2, pkgs.size());
        assertEquals(new Revision(1, 2, 5), pkgs.get("dummy;foo").getVersion());
        assertEquals(new Revision(4, 5, 7), pkgs.get("dummy;bar").getVersion());
    }

    public void testFallback() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource = new SimpleRepositorySource(legacyUrl,
                "Legacy UI Name", true, ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source, legacySource))), null,
                (source1, downloader1, settings, progress1) -> {
                    assertEquals(legacyUrl, source1.getUrl());
                    FakeRemotePackage legacy = new FakeRemotePackage("legacy");
                    legacy.setRevision(new Revision(1, 2, 9));
                    legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                    return ImmutableSet.of(legacy);
                });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(3, pkgs.size());
        assertEquals(new Revision(1, 2, 9), pkgs.get("legacy").getVersion());
    }

    public void testNonFallbackPreferred() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource = new SimpleRepositorySource(legacyUrl,
                "Legacy UI Name", true, ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source, legacySource))), null,
                (source1, downloader1, settings, progress1) -> {
                    assertEquals(legacyUrl, source1.getUrl());
                    FakeRemotePackage legacy = new FakeRemotePackage("dummy;foo");
                    legacy.setRevision(new Revision(1, 2, 3));
                    legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                    return ImmutableSet.of(legacy);
                });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        assertFalse(pkgs.get("dummy;foo") instanceof FakePackage);
    }

    private static final String TEST_LOCAL_PREFERRED_REPO = "\n"
            + "<repo:repository\n"
            + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
            + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
            + "    <remotePackage path=\"dummy;foo\">\n"
            + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
            + "        <revision><major>%1$d</major></revision>\n"
            + "        <display-name>%2$s</display-name>\n"
            + "        <archives>\n"
            + "            <archive>\n"
            + "                <complete>\n"
            + "                    <size>2345</size>\n"
            + "                    <checksum>e1b7e62ecc3925054900b70e6eb9038bba8f702a</checksum>\n"
            + "                    <url>%3$s</url>\n"
            + "                </complete>\n"
            + "            </archive>\n"
            + "        </archives>\n"
            + "    </remotePackage>\n"
            + "</repo:repository>\n"
            + "\n";

    public void testLocalPreferred() throws Exception {
        RepositorySource httpSource = new SimpleRepositorySource("http://www.example.com",
                "HTTP Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        RepositorySource fileSource = new SimpleRepositorySource("file:///foo/bar",
                "File Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        RepositorySource fileSource2 = new SimpleRepositorySource("file:///foo/bar2",
                "File Source UI Name 2", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList
                .of(new FakeRepositorySourceProvider(
                        ImmutableList.of(httpSource, fileSource, fileSource2))), null,
                null);

        // file preferred over url: relative paths
        downloader.registerUrl(new URL("http://www.example.com"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "http", "foo").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "bar").getBytes());
        Map<String, RemotePackage> pkgs =
                loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("file", pkgs.get("dummy;foo").getDisplayName());

        // file preferred over url: absolute paths
        downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "http", "http://example.com").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar2"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "file:///foo/bar2").getBytes());
        pkgs = loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("file", pkgs.get("dummy;foo").getDisplayName());

        // newer http preferred over file
        downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 2, "http", "foo").getBytes());
        downloader.registerUrl(new URL("file:///foo/bar"),
                String.format(TEST_LOCAL_PREFERRED_REPO, 1, "file", "bar").getBytes());
        pkgs = loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        assertEquals("http", pkgs.get("dummy;foo").getDisplayName());
    }

    public void testNewerFallbackPreferred() throws Exception {
        RepositorySource source = new SimpleRepositorySource("http://www.example.com",
                "Source UI Name", true,
                ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        final String legacyUrl = "http://www.example.com/legacy";
        RepositorySource legacySource = new SimpleRepositorySource(legacyUrl,
                "Legacy UI Name", true, ImmutableSet.of(RepoManager.getGenericModule()),
                null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(new URL(legacyUrl),
                "foo".getBytes());
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader = new RemoteRepoLoaderImpl(ImmutableList.of(
                new FakeRepositorySourceProvider(ImmutableList.of(source, legacySource))), null,
                (source1, downloader1, settings, progress1) -> {
                    assertEquals(legacyUrl, source1.getUrl());
                    FakeRemotePackage legacy = new FakeRemotePackage("dummy;foo");
                    legacy.setRevision(new Revision(1, 2, 4));
                    legacy.setCompleteUrl("http://www.example.com/legacy.zip");
                    return ImmutableSet.of(legacy);
                });
        Map<String, RemotePackage> pkgs = loader
                .fetchPackages(progress, downloader, new FakeSettingsController(false));
        progress.assertNoErrorsOrWarnings();
        assertEquals(2, pkgs.size());
        assertTrue(pkgs.get("dummy;foo") instanceof FakePackage);
    }


    public void testDownloadsAreParallel() throws Exception {
        RepositorySource httpSource =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "HTTP Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        RepositorySource httpSource2 =
                new SimpleRepositorySource(
                        "http://www.example2.com",
                        "HTTP Source2 UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        RepositorySource fileSource =
                new SimpleRepositorySource(
                        "file:///foo/bar",
                        "File Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        List<RepositorySource> sourceList = ImmutableList.of(httpSource, httpSource2, fileSource);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(new FakeRepositorySourceProvider(sourceList)), null, null);
        CyclicBarrier barrier = new CyclicBarrier(sourceList.size());
        FakeDownloader downloader =
                new FakeDownloader(new MockFileOp()) {
                    private void awaitBarrier() {
                        try {
                            barrier.await();
                        } catch (Exception e) {
                            fail(
                                    "Unexpected exception while waiting in download thread: "
                                            + e.getMessage());
                        }
                    }

                    @NonNull
                    @Override
                    public InputStream downloadAndStream(
                            @NonNull URL url, @NonNull ProgressIndicator indicator)
                            throws IOException {
                        InputStream stream = super.downloadAndStream(url, indicator);
                        awaitBarrier();
                        return stream;
                    }

                    @Override
                    public Path downloadFully(
                            @NonNull URL url, @NonNull ProgressIndicator indicator)
                            throws IOException {
                        Path path = super.downloadFully(url, indicator);
                        awaitBarrier();
                        return path;
                    }

                    @Override
                    public void downloadFully(
                            @NonNull URL url,
                            @NonNull File target,
                            @Nullable String checksum,
                            @NonNull ProgressIndicator indicator)
                            throws IOException {
                        super.downloadFully(url, target, checksum, indicator);
                        awaitBarrier();
                    }
                };

        downloader.registerUrl(
                new URL("http://www.example.com"), getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(
                new URL("http://www.example2.com"),
                getClass().getResourceAsStream("/testRepo.xml"));
        downloader.registerUrl(
                new URL("file:///foo/bar"), getClass().getResourceAsStream("/testRepo.xml"));

        Thread mainFetchThread =
                new Thread(
                        () -> {
                            try {
                                Map<String, RemotePackage> pkgs =
                                        loader.fetchPackages(
                                                progress,
                                                downloader,
                                                new FakeSettingsController(false));
                                // The same repo manifest comes from all sources, which just contains 2 packages and
                                // we sanity check it here. This test just verifies that the downloads are indeed parallel -
                                // the rest of the tests will therefore serve to verify the correctness of the concurrent
                                // data processing (i.e., assert other package details in various cases), in addition to
                                // the specific aspect of the implementation they are testing.
                                assertEquals(2, pkgs.size());
                            } catch (Throwable t) {
                                StringWriter stringWriter = new StringWriter();
                                PrintWriter printWriter = new PrintWriter(stringWriter);
                                t.printStackTrace(printWriter);
                                fail(
                                        "Exception in fetchPackages() thread: "
                                                + stringWriter.toString());
                            }
                        });
        mainFetchThread.start();
        mainFetchThread.join(60000);
        assertFalse(mainFetchThread.isAlive());
        assertFalse(mainFetchThread.isInterrupted());
    }

    public void testLegacyNdk() throws Exception {
        RepositorySource source =
                new SimpleRepositorySource(
                        "http://www.example.com",
                        "Source UI Name",
                        true,
                        ImmutableSet.of(RepoManager.getGenericModule()),
                        null);
        FakeDownloader downloader = new FakeDownloader(new MockFileOp());
        downloader.registerUrl(
                new URL("http://www.example.com"),
                getClass().getResourceAsStream("/testRepoLegacyNdk.xml"));
        FakeProgressIndicator progress = new FakeProgressIndicator(true);
        RemoteRepoLoader loader =
                new RemoteRepoLoaderImpl(
                        ImmutableList.of(
                                new FakeRepositorySourceProvider(ImmutableList.of(source))),
                        null,
                        null,
                        true);
        Map<String, RemotePackage> pkgs =
                loader.fetchPackages(progress, downloader, new FakeSettingsController(false));
        RepositoryPackages packages =
                new RepositoryPackages(ImmutableList.of(), new ArrayList<>(pkgs.values()));
        RepoManager repoManager = new FakeRepoManager(new File("./local-dir"), packages);
        progress.assertNoErrorsOrWarnings();
        assertEquals(5, pkgs.size());

        RemotePackage legacy = pkgs.get("ndk-bundle");
        assertEquals(new Revision(18, 1, 5063045), legacy.getVersion());
        assertThat(legacy.obsolete()).isTrue();
        assertThat(legacy.getDisplayName()).isEqualTo("NDK");
        assertThat(legacy.getInstallDir(repoManager, progress))
                .isEqualTo(new File("./local-dir/ndk-bundle"));

        RemotePackage r18 = pkgs.get("ndk;18.1.5063045");
        assertEquals(new Revision(18, 1, 5063045), r18.getVersion());
        assertThat(r18.obsolete()).isFalse();
        assertThat(r18.getDisplayName()).isEqualTo("NDK (Side by side) 18.1.5063045");
        assertThat(r18.getInstallDir(repoManager, progress))
                .isEqualTo(new File("./local-dir/ndk/18.1.5063045"));

        RemotePackage r17 = pkgs.get("ndk;17.2.4988734");
        assertEquals(new Revision(17, 2, 4988734), r17.getVersion());
        assertThat(r17.obsolete()).isFalse();
        assertThat(r17.getDisplayName()).isEqualTo("NDK (Side by side) 17.2.4988734");
        assertThat(r17.getInstallDir(repoManager, progress))
                .isEqualTo(new File("./local-dir/ndk/17.2.4988734"));
    }
}
